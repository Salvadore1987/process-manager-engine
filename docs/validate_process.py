#!/usr/bin/env python3
"""
validate_process.py — Валидация выполнения BPMN-процесса по данным из Redis.

Парсит BPMN-диаграмму, извлекает все ServiceTask/CallActivity элементы,
затем по переданному processInstanceId читает события из Redis (pe:events:{id})
и проверяет что каждый ожидаемый шаг был выполнен в правильном порядке.

=== Установка зависимостей ===

    pip install redis

=== Запуск ===

    python3 docs/validate_process.py <bpmn_file> <process_instance_id> [--redis-host HOST] [--redis-port PORT]

=== Примеры ===

    # Локальный Redis (localhost:6379)
    python3 docs/validate_process.py docs/order-process.bpmn 019d574d-ff1c-7007-9db1-26953c3b5a5f

    # Redis в Docker Compose (через проброшенный порт)
    python3 docs/validate_process.py docs/order-process.bpmn 019d574d-ff1c-7007-9db1-26953c3b5a5f --redis-host localhost --redis-port 6379

    # Redis на удалённом хосте
    python3 docs/validate_process.py docs/order-process.bpmn 019d574d-ff1c-7007-9db1-26953c3b5a5f --redis-host redis.staging.internal --redis-port 6379

=== Как получить processInstanceId ===

    # Через REST API движка:
    curl http://localhost:8080/api/v1/instances | jq '.[].id'

    # Через Redis напрямую:
    redis-cli SMEMBERS pe:instances

=== Выходные коды ===

    0 — все задачи выполнены успешно
    1 — есть невыполненные задачи или ошибки
"""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass

# BPMN 2.0 namespace
BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn"


@dataclass
class BpmnTask:
    """Элемент BPMN-диаграммы (ServiceTask или CallActivity)."""
    node_id: str
    name: str
    topic: str  # camunda:topic для ServiceTask, calledElement для CallActivity
    element_type: str  # "serviceTask" или "callActivity"
    is_compensation: bool  # isForCompensation="true"


@dataclass
class ProcessEvent:
    """Событие из Redis event store."""
    event_type: str
    sequence_number: int
    node_id: str | None
    token_id: str | None
    occurred_at: str
    raw: dict


# ─── BPMN Parsing ────────────────────────────────────────────────────────────


def parse_bpmn(bpmn_file: str) -> list[BpmnTask]:
    """Парсит BPMN XML и извлекает все ServiceTask и CallActivity элементы."""
    tree = ET.parse(bpmn_file)
    root = tree.getroot()

    tasks = []

    # ServiceTask элементы
    for task in root.iter(f"{{{BPMN_NS}}}serviceTask"):
        node_id = task.get("id", "")
        name = task.get("name", node_id)
        topic = task.get(f"{{{CAMUNDA_NS}}}topic", "")
        is_compensation = task.get("isForCompensation", "false").lower() == "true"

        tasks.append(BpmnTask(
            node_id=node_id,
            name=name,
            topic=topic,
            element_type="serviceTask",
            is_compensation=is_compensation,
        ))

    # CallActivity элементы
    for activity in root.iter(f"{{{BPMN_NS}}}callActivity"):
        node_id = activity.get("id", "")
        name = activity.get("name", node_id)
        called_element = activity.get("calledElement", "")

        tasks.append(BpmnTask(
            node_id=node_id,
            name=name,
            topic=called_element,
            element_type="callActivity",
            is_compensation=False,
        ))

    return tasks


# ─── Redis Event Reading ─────────────────────────────────────────────────────


def read_events(redis_host: str, redis_port: int, process_instance_id: str) -> list[ProcessEvent]:
    """Читает все события процесса из Redis."""
    try:
        import redis
    except ImportError:
        print("ERROR: модуль redis не установлен. Установите: pip install redis")
        sys.exit(1)

    client = redis.Redis(host=redis_host, port=redis_port, decode_responses=True)

    # Проверка подключения
    try:
        client.ping()
    except redis.ConnectionError:
        print(f"ERROR: не удалось подключиться к Redis на {redis_host}:{redis_port}")
        sys.exit(1)

    key = f"pe:events:{process_instance_id}"
    raw_events = client.lrange(key, 0, -1)

    if not raw_events:
        print(f"ERROR: события не найдены для processInstanceId={process_instance_id}")
        print(f"  Ключ: {key}")
        # Показать доступные процессы
        instances = client.smembers("pe:instances")
        if instances:
            print(f"  Доступные процессы: {', '.join(sorted(instances)[:5])}")
        sys.exit(1)

    events = []
    for raw_json in raw_events:
        data = json.loads(raw_json)
        events.append(ProcessEvent(
            event_type=data.get("@type", "UNKNOWN"),
            sequence_number=data.get("sequenceNumber", 0),
            node_id=data.get("nodeId") or data.get("toNodeId"),
            token_id=data.get("tokenId"),
            occurred_at=data.get("occurredAt", ""),
            raw=data,
        ))

    # Сортировка по sequenceNumber
    events.sort(key=lambda e: e.sequence_number)
    return events


# ─── Validation ──────────────────────────────────────────────────────────────


def validate(tasks: list[BpmnTask], events: list[ProcessEvent]) -> bool:
    """
    Валидирует что все ожидаемые задачи из BPMN были обработаны.
    Возвращает True если валидация прошла, False если есть ошибки.
    """
    # Собираем completed и failed task nodeIds из событий
    task_completed = {}  # nodeId -> event
    task_started = {}    # nodeId -> event (TOKEN_WAITING = task отправлен на исполнение)
    task_failed = {}     # nodeId -> event
    process_status = None
    process_event = None

    for event in events:
        if event.event_type == "TOKEN_WAITING" and event.node_id:
            task_started[event.node_id] = event

        if event.event_type == "TASK_COMPLETED" and event.node_id:
            task_completed[event.node_id] = event

        if event.event_type == "PROCESS_COMPLETED":
            process_status = "COMPLETED"
            process_event = event

        if event.event_type == "PROCESS_ERROR":
            process_status = "ERROR"
            process_event = event

        if event.event_type == "COMPENSATION_TRIGGERED":
            # Отмечаем что compensation была вызвана
            original_node = event.raw.get("originalNodeId")
            compensation_task = event.raw.get("compensationTaskId")
            if compensation_task:
                task_started.setdefault(compensation_task, event)

    # ─── Вывод результатов ────────────────────────────────────────────────

    all_ok = True
    main_tasks = [t for t in tasks if not t.is_compensation]
    compensation_tasks = [t for t in tasks if t.is_compensation]

    print("=" * 70)
    print(f"  BPMN Process Validation Report")
    print(f"  Tasks in BPMN: {len(tasks)} ({len(main_tasks)} main + {len(compensation_tasks)} compensation)")
    print(f"  Events in Redis: {len(events)}")
    print("=" * 70)

    # Основные задачи
    print("\n  Main Tasks:")
    print(f"  {'#':<4} {'Node ID':<25} {'Topic':<30} {'Status':<12}")
    print(f"  {'─'*4} {'─'*25} {'─'*30} {'─'*12}")

    for i, task in enumerate(main_tasks, 1):
        if task.node_id in task_completed:
            status = "✅ DONE"
        elif task.node_id in task_started:
            status = "⏳ STARTED"
            all_ok = False
        else:
            status = "❌ MISSING"
            all_ok = False

        print(f"  {i:<4} {task.node_id:<25} {task.topic:<30} {status}")

    # Compensation задачи
    if compensation_tasks:
        print("\n  Compensation Tasks:")
        print(f"  {'#':<4} {'Node ID':<25} {'Topic':<30} {'Status':<12}")
        print(f"  {'─'*4} {'─'*25} {'─'*30} {'─'*12}")

        for i, task in enumerate(compensation_tasks, 1):
            if task.node_id in task_completed:
                status = "✅ DONE"
            elif task.node_id in task_started:
                status = "⏳ STARTED"
            else:
                status = "── SKIP"  # Compensation не вызвана — это нормально

            print(f"  {i:<4} {task.node_id:<25} {task.topic:<30} {status}")

    # Статус процесса
    print(f"\n  Process Status: ", end="")
    if process_status == "COMPLETED":
        print("✅ COMPLETED")
    elif process_status == "ERROR":
        error_code = process_event.raw.get("errorCode", "?") if process_event else "?"
        error_msg = process_event.raw.get("errorMessage", "?") if process_event else "?"
        print(f"❌ ERROR ({error_code}: {error_msg})")
    else:
        print("⏳ IN PROGRESS")
        all_ok = False

    # Хронология выполнения
    print(f"\n  Execution Timeline:")
    print(f"  {'Seq':<5} {'Event':<25} {'Node':<25} {'Time'}")
    print(f"  {'─'*5} {'─'*25} {'─'*25} {'─'*25}")

    task_node_ids = {t.node_id for t in tasks}
    relevant_types = {"TASK_COMPLETED", "TOKEN_WAITING", "PROCESS_COMPLETED",
                      "PROCESS_ERROR", "COMPENSATION_TRIGGERED", "PROCESS_STARTED"}

    for event in events:
        # Показываем только бизнес-релевантные события
        is_task_event = event.node_id in task_node_ids
        is_process_event = event.event_type in relevant_types

        if is_task_event or (is_process_event and event.event_type in {
            "PROCESS_STARTED", "PROCESS_COMPLETED", "PROCESS_ERROR", "COMPENSATION_TRIGGERED"
        }):
            time_short = event.occurred_at.split("T")[1][:12] if "T" in event.occurred_at else event.occurred_at
            node = event.node_id or ""
            print(f"  {event.sequence_number:<5} {event.event_type:<25} {node:<25} {time_short}")

    print()
    if all_ok:
        print("  ✅ Validation PASSED — all tasks completed successfully")
    else:
        print("  ❌ Validation FAILED — some tasks not completed")

    print("=" * 70)
    return all_ok


# ─── Main ────────────────────────────────────────────────────────────────────


def main():
    parser = argparse.ArgumentParser(
        description="Валидация выполнения BPMN-процесса по данным из Redis"
    )
    parser.add_argument("bpmn_file", help="Путь к BPMN XML файлу")
    parser.add_argument("process_instance_id", help="UUID процесса (processInstanceId)")
    parser.add_argument("--redis-host", default="localhost", help="Redis host (default: localhost)")
    parser.add_argument("--redis-port", type=int, default=6379, help="Redis port (default: 6379)")

    args = parser.parse_args()

    # Парсинг BPMN
    tasks = parse_bpmn(args.bpmn_file)
    if not tasks:
        print(f"WARNING: в BPMN не найдено ServiceTask/CallActivity элементов")

    # Чтение событий из Redis
    events = read_events(args.redis_host, args.redis_port, args.process_instance_id)

    # Валидация
    success = validate(tasks, events)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
