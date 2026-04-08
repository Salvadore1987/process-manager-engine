#!/usr/bin/env python3
"""
validate_process.py — Валидация выполнения BPMN-процесса по данным из Redis.

Парсит все BPMN-файлы из указанного каталога, строит дерево процессов
(включая CallActivity подпроцессы), затем по processInstanceId читает
события из Redis и проверяет что каждый шаг был выполнен.

=== Установка зависимостей ===

    pip install redis

=== Запуск ===

    python3 docs/validate_process.py <bpmn_directory> <process_instance_id> [--redis-host HOST] [--redis-port PORT]

=== Примеры ===

    # Каталог с BPMN-файлами (main process + подпроцессы)
    python3 docs/validate_process.py docs/processes 019d574d-ff1c-7007-9db1-26953c3b5a5f

    # Redis на удалённом хосте
    python3 docs/validate_process.py docs/processes 019d574d-ff1c-7007-9db1-26953c3b5a5f --redis-host redis.staging.internal

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
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

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
class BpmnProcess:
    """BPMN-процесс с задачами и ссылками на подпроцессы."""
    process_id: str
    name: str
    tasks: list[BpmnTask] = field(default_factory=list)


@dataclass
class ProcessEvent:
    """Событие из Redis event store."""
    event_type: str
    sequence_number: int
    node_id: str | None
    token_id: str | None
    occurred_at: str
    raw: dict


@dataclass
class InstanceValidation:
    """Результат валидации одного экземпляра процесса."""
    instance_id: str
    process_id: str
    events: list[ProcessEvent]
    tasks: list[BpmnTask]
    children: dict  # node_id -> InstanceValidation


# ─── BPMN Parsing ────────────────────────────────────────────────────────────


def parse_bpmn_file(bpmn_file: str) -> BpmnProcess | None:
    """Парсит один BPMN XML файл и возвращает BpmnProcess."""
    tree = ET.parse(bpmn_file)
    root = tree.getroot()

    process_el = root.find(f"{{{BPMN_NS}}}process")
    if process_el is None:
        return None

    process_id = process_el.get("id", "")
    process_name = process_el.get("name", process_id)

    tasks = []

    for task in process_el.iter(f"{{{BPMN_NS}}}serviceTask"):
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

    for activity in process_el.iter(f"{{{BPMN_NS}}}callActivity"):
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

    return BpmnProcess(process_id=process_id, name=process_name, tasks=tasks)


def parse_bpmn_directory(bpmn_dir: str) -> dict[str, BpmnProcess]:
    """Парсит все BPMN-файлы из каталога. Возвращает dict: process_id -> BpmnProcess."""
    processes = {}
    bpmn_path = Path(bpmn_dir)

    if not bpmn_path.is_dir():
        print(f"ERROR: '{bpmn_dir}' не является каталогом")
        sys.exit(1)

    bpmn_files = list(bpmn_path.glob("**/*.bpmn"))
    if not bpmn_files:
        print(f"ERROR: BPMN-файлы не найдены в '{bpmn_dir}'")
        sys.exit(1)

    for bpmn_file in sorted(bpmn_files):
        process = parse_bpmn_file(str(bpmn_file))
        if process:
            processes[process.process_id] = process

    return processes


# ─── Redis Event Reading ─────────────────────────────────────────────────────


def get_redis_client(redis_host: str, redis_port: int):
    """Создаёт и проверяет подключение к Redis."""
    try:
        import redis
    except ImportError:
        print("ERROR: модуль redis не установлен. Установите: pip install redis")
        sys.exit(1)

    client = redis.Redis(host=redis_host, port=redis_port, decode_responses=True)

    try:
        client.ping()
    except Exception:
        print(f"ERROR: не удалось подключиться к Redis на {redis_host}:{redis_port}")
        sys.exit(1)

    return client


def read_events(client, process_instance_id: str) -> list[ProcessEvent]:
    """Читает все события процесса из Redis."""
    key = f"pe:events:{process_instance_id}"
    raw_events = client.lrange(key, 0, -1)

    if not raw_events:
        return []

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

    events.sort(key=lambda e: e.sequence_number)
    return events


def find_child_instances(client, parent_instance_id: str) -> dict[str, str]:
    """
    Находит дочерние экземпляры процессов через события CALL_ACTIVITY_STARTED.
    Возвращает dict: nodeId -> childProcessInstanceId.
    """
    events = read_events(client, parent_instance_id)
    children = {}

    for event in events:
        if event.event_type == "CALL_ACTIVITY_STARTED":
            node_id = event.raw.get("nodeId", "")
            child_id = event.raw.get("childProcessInstanceId", "")
            if node_id and child_id:
                children[node_id] = child_id

    return children


# ─── Validation ──────────────────────────────────────────────────────────────


def build_validation_tree(
        client,
        instance_id: str,
        processes: dict[str, BpmnProcess],
        process_id: str | None = None,
) -> InstanceValidation | None:
    """
    Рекурсивно строит дерево валидации для экземпляра процесса и всех его CallActivity.
    """
    events = read_events(client, instance_id)
    if not events:
        return None

    # Определяем process_id из маппинга если не передан
    if process_id is None:
        definition_id = client.get(f"pe:inst-def:{instance_id}")
        if definition_id:
            def_json = client.get(f"pe:def:id:{definition_id}")
            if def_json:
                def_data = json.loads(def_json)
                process_id = def_data.get("key", "")

    process = processes.get(process_id) if process_id else None
    tasks = process.tasks if process else []

    # Находим дочерние экземпляры
    child_instances = find_child_instances(client, instance_id)

    children = {}
    for node_id, child_instance_id in child_instances.items():
        # Определяем calledElement для этого node_id
        call_activity = next((t for t in tasks if t.node_id == node_id and t.element_type == "callActivity"), None)
        child_process_id = call_activity.topic if call_activity else None

        child_validation = build_validation_tree(client, child_instance_id, processes, child_process_id)
        if child_validation:
            children[node_id] = child_validation

    return InstanceValidation(
        instance_id=instance_id,
        process_id=process_id or "unknown",
        events=events,
        tasks=tasks,
        children=children,
    )


def validate_instance(validation: InstanceValidation, indent: int = 0) -> bool:
    """Валидирует один экземпляр процесса. Возвращает True если всё ОК."""
    prefix = "  " * indent
    all_ok = True

    # Собираем статусы задач из событий
    task_completed = {}
    task_started = {}
    process_status = None
    process_event = None

    for event in validation.events:
        if event.event_type == "TOKEN_WAITING" and event.node_id:
            task_started[event.node_id] = event

        if event.event_type == "TASK_COMPLETED" and event.node_id:
            task_completed[event.node_id] = event

        if event.event_type == "CALL_ACTIVITY_STARTED" and event.node_id:
            task_started[event.node_id] = event

        if event.event_type == "CALL_ACTIVITY_COMPLETED" and event.node_id:
            task_completed[event.node_id] = event

        if event.event_type == "PROCESS_COMPLETED":
            process_status = "COMPLETED"
            process_event = event

        if event.event_type == "PROCESS_ERROR":
            process_status = "ERROR"
            process_event = event

        if event.event_type == "COMPENSATION_TRIGGERED":
            compensation_task = event.raw.get("compensationTaskId")
            if compensation_task:
                task_started.setdefault(compensation_task, event)

    main_tasks = [t for t in validation.tasks if not t.is_compensation]
    compensation_tasks = [t for t in validation.tasks if t.is_compensation]

    # Заголовок процесса
    print(f"{prefix}{'=' * 70}")
    print(f"{prefix}  Process: {validation.process_id} (instance: {validation.instance_id[:12]}...)")
    print(f"{prefix}  Tasks: {len(validation.tasks)} ({len(main_tasks)} main + {len(compensation_tasks)} compensation)")
    print(f"{prefix}  Events: {len(validation.events)}")
    print(f"{prefix}{'=' * 70}")

    # Основные задачи
    print(f"{prefix}")
    print(f"{prefix}  Main Tasks:")
    print(f"{prefix}  {'#':<4} {'Node ID':<25} {'Type':<14} {'Topic/Called':<25} {'Status':<12}")
    print(f"{prefix}  {'─'*4} {'─'*25} {'─'*14} {'─'*25} {'─'*12}")

    for i, task in enumerate(main_tasks, 1):
        if task.node_id in task_completed:
            status = "✅ DONE"
        elif task.node_id in task_started:
            status = "⏳ STARTED"
            all_ok = False
        else:
            status = "❌ MISSING"
            all_ok = False

        print(f"{prefix}  {i:<4} {task.node_id:<25} {task.element_type:<14} {task.topic:<25} {status}")

    # Compensation задачи
    if compensation_tasks:
        print(f"{prefix}")
        print(f"{prefix}  Compensation Tasks:")
        print(f"{prefix}  {'#':<4} {'Node ID':<25} {'Type':<14} {'Topic/Called':<25} {'Status':<12}")
        print(f"{prefix}  {'─'*4} {'─'*25} {'─'*14} {'─'*25} {'─'*12}")

        for i, task in enumerate(compensation_tasks, 1):
            if task.node_id in task_completed:
                status = "✅ DONE"
            elif task.node_id in task_started:
                status = "⏳ STARTED"
            else:
                status = "── SKIP"

            print(f"{prefix}  {i:<4} {task.node_id:<25} {task.element_type:<14} {task.topic:<25} {status}")

    # Статус процесса
    print(f"{prefix}")
    print(f"{prefix}  Process Status: ", end="")
    if process_status == "COMPLETED":
        print("✅ COMPLETED")
    elif process_status == "ERROR":
        error_code = process_event.raw.get("errorCode", "?") if process_event else "?"
        error_msg = process_event.raw.get("errorMessage", "?") if process_event else "?"
        print(f"❌ ERROR ({error_code}: {error_msg})")
    else:
        print("⏳ IN PROGRESS")
        all_ok = False

    # Хронология
    print(f"{prefix}")
    print(f"{prefix}  Execution Timeline:")
    print(f"{prefix}  {'Seq':<5} {'Event':<30} {'Node':<25} {'Time'}")
    print(f"{prefix}  {'─'*5} {'─'*30} {'─'*25} {'─'*25}")

    task_node_ids = {t.node_id for t in validation.tasks}
    relevant_types = {
        "TASK_COMPLETED", "TOKEN_WAITING", "PROCESS_COMPLETED",
        "PROCESS_ERROR", "COMPENSATION_TRIGGERED", "PROCESS_STARTED",
        "CALL_ACTIVITY_STARTED", "CALL_ACTIVITY_COMPLETED",
    }

    for event in validation.events:
        is_task_event = event.node_id in task_node_ids
        is_process_event = event.event_type in relevant_types

        if is_task_event or (is_process_event and event.event_type in {
            "PROCESS_STARTED", "PROCESS_COMPLETED", "PROCESS_ERROR",
            "COMPENSATION_TRIGGERED", "CALL_ACTIVITY_STARTED", "CALL_ACTIVITY_COMPLETED",
        }):
            time_short = event.occurred_at.split("T")[1][:12] if "T" in event.occurred_at else event.occurred_at
            node = event.node_id or ""
            print(f"{prefix}  {event.sequence_number:<5} {event.event_type:<30} {node:<25} {time_short}")

    # Рекурсивно валидируем дочерние процессы
    if validation.children:
        print(f"{prefix}")
        print(f"{prefix}  {'─' * 70}")
        print(f"{prefix}  Call Activity subprocesses ({len(validation.children)}):")

        for node_id, child_validation in validation.children.items():
            call_activity = next((t for t in validation.tasks if t.node_id == node_id), None)
            called_name = call_activity.name if call_activity else node_id
            print(f"{prefix}")
            print(f"{prefix}  ▼ {called_name} ({node_id}) → {child_validation.process_id}")

            child_ok = validate_instance(child_validation, indent + 2)
            if not child_ok:
                all_ok = False

    print(f"{prefix}")
    if indent == 0:
        print(f"{prefix}{'=' * 70}")
        if all_ok:
            print(f"{prefix}  ✅ Validation PASSED — all tasks completed successfully")
        else:
            print(f"{prefix}  ❌ Validation FAILED — some tasks not completed")
        print(f"{prefix}{'=' * 70}")

    return all_ok


# ─── Main ────────────────────────────────────────────────────────────────────


def main():
    parser = argparse.ArgumentParser(
        description="Валидация выполнения BPMN-процесса (включая CallActivity) по данным из Redis"
    )
    parser.add_argument("bpmn_directory", help="Каталог с BPMN XML файлами (main + подпроцессы)")
    parser.add_argument("process_instance_id", help="UUID процесса (processInstanceId)")
    parser.add_argument("--redis-host", default="localhost", help="Redis host (default: localhost)")
    parser.add_argument("--redis-port", type=int, default=6379, help="Redis port (default: 6379)")

    args = parser.parse_args()

    # Парсинг всех BPMN из каталога
    processes = parse_bpmn_directory(args.bpmn_directory)
    print(f"Найдено {len(processes)} BPMN-процессов: {', '.join(processes.keys())}")
    print()

    # Подключение к Redis
    client = get_redis_client(args.redis_host, args.redis_port)

    # Проверяем что события существуют
    events = read_events(client, args.process_instance_id)
    if not events:
        print(f"ERROR: события не найдены для processInstanceId={args.process_instance_id}")
        print(f"  Ключ: pe:events:{args.process_instance_id}")
        instances = client.smembers("pe:instances")
        if instances:
            print(f"  Доступные процессы: {', '.join(sorted(instances)[:5])}")
        sys.exit(1)

    # Строим дерево валидации
    validation = build_validation_tree(client, args.process_instance_id, processes)
    if not validation:
        print("ERROR: не удалось построить дерево валидации")
        sys.exit(1)

    # Валидация
    success = validate_instance(validation)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
