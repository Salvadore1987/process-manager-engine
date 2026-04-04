# Process Manager Engine

## Overview

Java-движок на основе паттерна **Process Manager** (Enterprise Integration Patterns) с поддержкой **BPMN 2.0** (совместимость с Camunda 7 Modeler) для декларативного описания бизнес-процессов в виде графа. Все взаимодействие с тасками через RabbitMQ вместо БД, event-sourced архитектура для обработки миллионов одновременных процессов.

## Requirements

### Functional Requirements

1. **Process Manager Engine** — управление жизненным циклом экземпляров процессов: создание, выполнение, приостановка, возобновление, завершение, ошибка
2. **BPMN 2.0 граф-модель** с токен-ориентированным выполнением:
   - `StartEvent`, `EndEvent`
   - `ServiceTask`
   - `ExclusiveGateway`, `ParallelGateway`
   - `CompensationBoundaryEvent`, `TimerBoundaryEvent`, `ErrorBoundaryEvent`
   - `CallActivity`
3. **Декларативная обработка ошибок** — retry через DLQ + requeue с exponential backoff, compensation boundary events, error end events
4. **Корреляция сообщений** — correlation-id в AMQP header для привязки ответов к экземплярам процессов
5. **Кастомные шаги** — регистрация activities через Java-интерфейс `ActivityHandler`
6. **Опциональная персистентность** — включается/выключается свойством. При включении events сохраняются через адаптер `ProcessEventStore` (JDBC, MongoDB и т.д.). При выключении — `NoOpEventStore`
7. **Таймеры** — `TimerBoundaryEvent` через RabbitMQ delayed message exchange plugin
8. **BPMN-парсер** — описание процессов через Camunda Modeler, парсинг через XSD + JAXB
9. **BPMN-валидатор** — при деплое определения валидирует, что XML содержит только поддерживаемые элементы (StartEvent, EndEvent, ServiceTask, ExclusiveGateway, ParallelGateway, CallActivity, CompensationBoundaryEvent, TimerBoundaryEvent, ErrorBoundaryEvent). Процессы с неподдерживаемыми элементами (UserTask, ScriptTask, EventBasedGateway, SubProcess и т.д.) отклоняются с детальным списком неподдерживаемых элементов и их позиций в XML. Валидация свойств элементов не требуется — процессы описываются в Camunda Modeler, который ограничивает набор доступных свойств стандартом Camunda BPMN, поэтому невалидные атрибуты/свойства не могут появиться в XML
10. **RabbitMQ-транспорт** — каждый тип ServiceTask имеет свой topic (exchange + routing key), внешние сервисы слушают свои топики
10. **REST API** — полный Camunda-like API: definition CRUD, instance lifecycle, variables, history, incidents

### Non-Functional Requirements

- Java 21 LTS (virtual threads для параллельного выполнения шагов)
- Gradle с Kotlin DSL
- Независимость от Spring в core (опциональный модуль интеграции)
- SLF4J 2.x для логирования, Micrometer для метрик
- XSD + JAXB для валидации и парсинга BPMN XML
- Standalone engine — работает без фреймворков

## Architecture

### High-Level Design

Event-sourced архитектура: состояние экземпляров процессов восстанавливается из потока событий. Минимум in-memory state, максимум масштабируемости. Каждое изменение состояния — событие, сохраняемое в event store (опционально).

```
┌─────────────────────────────────────────────────────────────┐
│                      rest-api (Spring Boot MVC)             │
│  Definition CRUD │ Instance lifecycle │ Variables │ History  │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│               spring-integration (Spring Boot Starter)       │
│    Auto-configuration │ DeploymentListener │ Health/Metrics  │
├─────────────────────────────────────────────────────────────┤
│               security (OAuth2/Keycloak)                     │
│         ResourceServerConfig │ JWT Converter │ Roles         │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                        core (standalone engine)              │
│  BPMN Parser │ Token Engine │ Event Sourcing │ Timer Svc    │
│  Ports: ProcessEventStore, MessageTransport, DeploymentListener│
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
┌──────────────▼──────────┐   ┌───────────────▼───────────────┐
│   rabbitmq-transport    │   │   persistence adapters        │
│  AMQP Client (official) │   │   (JDBC, MongoDB, etc.)       │
│  Topic per task type     │   │   implements ProcessEventStore│
└──────────────┬──────────┘   └───────────────────────────────┘
               │
     ┌─────────▼──────────────────────────────────────────────┐
     │          worker-spring-boot-starter (client)            │
     │  ExternalTaskHandler │ @JobWorker │ TaskContext          │
     │  (standalone module for external worker services)       │
     └────────────────────────────────────────────────────────┘
```

### Technology Stack

| Компонент               | Технология                          |
|-------------------------|-------------------------------------|
| Язык                    | Java 21                             |
| Сборка                  | Gradle (Kotlin DSL)                 |
| Транспорт сообщений     | RabbitMQ / AMQP (amqp-client)       |
| Формат данных           | JSON (Jackson)                      |
| XML-парсинг             | XSD + JAXB                          |
| Логирование             | SLF4J 2.x                           |
| Метрики                 | Micrometer                          |
| Тестирование            | JUnit 5, AssertJ, Testcontainers    |
| REST API                | Spring Boot 3 MVC + virtual threads |
| Таймеры                 | RabbitMQ delayed message exchange    |

### Module Structure

Проект состоит из 6 Gradle-модулей:

#### 1. `core`
Standalone движок, не зависит от Spring и RabbitMQ.

**Ответственность:**
- BPMN XML парсинг (XSD + JAXB) — разбор процесс-определений из Camunda Modeler
- BPMN валидация — whitelist поддерживаемых элементов, отклонение определений с неподдерживаемыми нотациями (возвращает список неподдерживаемых элементов с позициями в XML)
- Token-based execution engine — продвижение токенов по графу BPMN
- Event sourcing — генерация и replay событий для восстановления состояния
- Process lifecycle management — создание, выполнение, приостановка, возобновление, завершение, обработка ошибок задач с маршрутизацией через error boundary и компенсацией
- Timer scheduling — абстракция для таймеров
- Определение портов (interfaces): `ProcessEventStore`, `MessageTransport`, `ActivityHandler`, `TimerService`

**Ключевые порты:**
- `MessageTransport` — отправка/получение сообщений (реализуется в rabbitmq-transport)
- `ProcessEventStore` — сохранение/чтение событий (реализуется адаптером БД или `NoOpEventStore`)
- `DeploymentListener` — callback при деплое определения (реализуется в spring-integration для создания RabbitMQ очередей)
- `ActivityHandler` — интерфейс для кастомных шагов
- `TimerService` — планирование и отмена таймеров

#### 2. `rabbitmq-transport`
Реализация `MessageTransport` поверх RabbitMQ.

**Ответственность:**
- Подключение через официальный `com.rabbitmq:amqp-client`
- Topic per task type: каждый ServiceTask использует тип external-task, свойство `topic` определяет routing key
- Корреляция через correlation-id в AMQP header
- Retry: DLQ + requeue с exponential backoff
- Таймеры через delayed message exchange plugin
- Управление connection/channel lifecycle
- Поддержка virtual threads через custom executor

**Топология RabbitMQ:**
```
Exchange: process-engine.tasks (topic)
  Shared queues (маршрутизация по x-task-topic header):
    task.execute  → общая очередь задач для воркеров
    task.result   → общая очередь результатов от воркеров

Exchange: process-engine.retry (topic)
  Queue:
    task.retry    → retry с TTL/backoff (DLX → task.execute)

Exchange: process-engine.dlq (fanout)
  Queue: process-engine.dlq

Exchange: process-engine.timers (topic)
  Routing keys:
    timer.{processInstanceId}
```

#### 3. `spring-integration`
Spring Boot Starter для автоконфигурации движка.

**Ответственность:**
- Auto-configuration: регистрация engine, transport, event store как Spring beans
- Properties mapping: `process-engine.*` → конфигурация движка
- Подключение Micrometer метрик
- Health indicators для RabbitMQ connectivity и engine status
- Conditional beans: `@ConditionalOnProperty("process-engine.persistence.enabled")`

#### 4. `rest-api`
Spring Boot MVC приложение с полным Camunda-like REST API.

**Ответственность:**
- REST controllers для управления движком
- Сериализация/десериализация через Jackson
- Error handling и стандартизированные ответы
- Virtual threads через `spring.threads.virtual.enabled=true`

#### 5. `security`
OAuth2/Keycloak интеграция для авторизации REST API.

**Ответственность:**
- `ResourceServerConfig` — настройка endpoint-доступа по ролям
- `KeycloakJwtAuthenticationConverter` — конвертация JWT → Spring Security authorities
- Роли: `process-admin`, `process-operator`, `process-viewer`, `process-deployer`

#### 6. `worker-spring-boot-starter`
Клиентский Spring Boot Starter для внешних сервисов (workers).

**Ответственность:**
- Автоматическое подключение к RabbitMQ и прослушивание execute-очередей
- `ExternalTaskHandler` — интерфейс с методом `execute(TaskContext)`
- `@JobWorker(topic = "...")` — аннотация на методе `execute` для привязки к топику
- `TaskContext` — доступ к переменным процесса + методы `complete()` / `error()` для ответа
- Health indicator для мониторинга подключения и consumers
- Не зависит от `core` или `rabbitmq-transport` — самостоятельная клиентская библиотека

### Component Overview

#### Token Engine (core)
Центральный компонент — продвигает токены по BPMN-графу:
- `ProcessInstance` хранит набор активных `Token`
- Каждый `Token` указывает на текущий `FlowNode`
- При выполнении `ServiceTask` (всегда external-task): токен приостанавливается, сообщение отправляется в RabbitMQ топик указанный в свойстве `topic`, при получении ответа — токен продвигается
- `ExclusiveGateway`: выбирает один исходящий поток по условию
- `ParallelGateway`: fork — создаёт токен на каждый исходящий поток, join — ждёт все входящие токены
- `CallActivity`: запускает новый экземпляр вызываемого процесса (по `calledElement`). Переменные не маппятся — дочерний процесс читает данные из EventStore родительского экземпляра. Токен родителя ждёт завершения дочернего процесса

#### Event Sourcing (core)
Все изменения состояния — события:
- `ProcessStartedEvent`
- `TokenMovedEvent`
- `TaskCompletedEvent`
- `ProcessSuspendedEvent` / `ProcessResumedEvent`
- `ProcessCompletedEvent` / `ProcessErrorEvent`
- `TimerScheduledEvent` / `TimerFiredEvent`
- `CompensationTriggeredEvent`
- `CallActivityStartedEvent` / `CallActivityCompletedEvent`
- `TokenWaitingEvent`

Восстановление: replay всех событий для `processInstanceId` → актуальное состояние.

#### Конкурентная безопасность
`completeTask()` и `failTask()` синхронизируются по `processInstanceId` — два конкурентных вызова для одного экземпляра сериализуются, исключая двойную обработку токена.

## Data Model

### Entities

**ProcessDefinition**
- `id: UUID (v7)` — уникальный идентификатор определения
- `key: String` — BPMN process key (уникальный бизнес-идентификатор)
- `version: int` — версия определения
- `name: String` — человекочитаемое имя
- `bpmnXml: String` — исходный BPMN XML
- `flowNodes: List<FlowNode>` — разобранные узлы графа
- `sequenceFlows: List<SequenceFlow>` — связи между узлами
- `deployedAt: Instant`

**ProcessInstance**
- `id: UUID (v7)` — уникальный идентификатор экземпляра
- `definitionId: UUID` — ссылка на определение
- `parentProcessInstanceId: UUID` — ссылка на родительский экземпляр (null для корневого процесса, заполняется при вызове через CallActivity)
- `state: ProcessState` — RUNNING, SUSPENDED, COMPENSATING, COMPLETED, ERROR, TERMINATED
- `tokens: List<Token>` — активные токены
- `variables: Map<String, Object>` — переменные процесса
- `startedAt: Instant`
- `completedAt: Instant`

**Token**
- `id: UUID (v7)`
- `currentNodeId: String` — ID текущего FlowNode
- `state: TokenState` — ACTIVE, WAITING, COMPLETED

**FlowNode** (абстрактный)
- `id: String` — BPMN element ID
- `name: String`
- `type: NodeType` — START_EVENT, END_EVENT, SERVICE_TASK, EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY, CALL_ACTIVITY, COMPENSATION_BOUNDARY, TIMER_BOUNDARY, ERROR_BOUNDARY
- `incomingFlows: List<String>`
- `outgoingFlows: List<String>`

**ServiceTask** extends FlowNode (тип: external-task)
- `topic: String` — топик RabbitMQ, берётся из свойства `topic` в Camunda Modeler (Implementation → External → Topic)
- `retryCount: int` — количество retry
- `retryInterval: Duration` — базовый ��нтервал retry

**CallActivity** extends FlowNode
- `calledElement: String` — process key вызываемого процесса (берётся из атрибута `calledElement` в Camunda Modeler)
- Без маппинга входящих/исходящих переменных — дочерний процесс читает данные из EventStore по parentProcessInstanceId

**ProcessEvent** (для event sourcing)
- `id: UUID (v7)`
- `processInstanceId: UUID`
- `type: String` — тип события
- `payload: JsonNode` — данные события
- `occurredAt: Instant`
- `sequenceNumber: long` — порядковый номер для replay

### Relationships

```
ProcessDefinition 1──* ProcessInstance
ProcessInstance 1──* Token
ProcessInstance 1──* ProcessEvent
ProcessDefinition 1──* FlowNode
ProcessDefinition 1──* SequenceFlow
FlowNode *──* SequenceFlow (incoming/outgoing)
```

### Storage

- **Event Store**: порт `ProcessEventStore` с адаптерами (JDBC для PostgreSQL/MySQL, MongoDB, etc.)
- **При выключенной персистентности**: `NoOpEventStore` — события генерируются для in-memory replay, но не сохраняются
- **Process definitions**: хранятся в памяти после деплоя, опционально в event store

## API Design

### Process Definitions

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| POST | `/api/v1/definitions` | Deploy BPMN definition (валидирует XML, отклоняет неподдерживаемые элементы) | `multipart/form-data` (bpmn file) | `ProcessDefinitionDto` |
| POST | `/api/v1/definitions/validate` | Validate BPMN without deploying | `multipart/form-data` (bpmn file) | `ValidationResultDto` |
| GET | `/api/v1/definitions` | List definitions | — | `List<ProcessDefinitionDto>` |
| GET | `/api/v1/definitions/{key}` | Get latest version | — | `ProcessDefinitionDto` |
| GET | `/api/v1/definitions/{key}/versions` | List all versions | — | `List<ProcessDefinitionDto>` |
| DELETE | `/api/v1/definitions/{key}` | Undeploy definition | — | `204 No Content` |

### Process Instances

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| POST | `/api/v1/instances` | Start new instance | `{"definitionKey": "...", "variables": {...}}` | `ProcessInstanceDto` |
| GET | `/api/v1/instances` | List instances (paginated, filterable) | — | `Page<ProcessInstanceDto>` |
| GET | `/api/v1/instances/{id}` | Get instance details | — | `ProcessInstanceDto` |
| PUT | `/api/v1/instances/{id}/suspend` | Suspend instance | — | `ProcessInstanceDto` |
| PUT | `/api/v1/instances/{id}/resume` | Resume instance | — | `ProcessInstanceDto` |
| DELETE | `/api/v1/instances/{id}` | Terminate instance | — | `204 No Content` |

### Variables

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| GET | `/api/v1/instances/{id}/variables` | Get all variables | — | `Map<String, Object>` |
| PUT | `/api/v1/instances/{id}/variables` | Update variables | `{"key": "value", ...}` | `Map<String, Object>` |
| GET | `/api/v1/instances/{id}/variables/{name}` | Get single variable | — | `Object` |

### Messages (Signal)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| POST | `/api/v1/messages` | Send message/signal | `{"correlationId": "...", "payload": {...}}` | `202 Accepted` |

### History

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| GET | `/api/v1/history/instances` | Query completed instances | — | `Page<HistoricInstanceDto>` |
| GET | `/api/v1/history/instances/{id}/events` | Get event log | — | `List<ProcessEventDto>` |
| GET | `/api/v1/history/instances/{id}/activities` | Get activity history | — | `List<ActivityHistoryDto>` |

### Incidents

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| GET | `/api/v1/incidents` | List incidents | — | `Page<IncidentDto>` |
| GET | `/api/v1/incidents/{id}` | Get incident details | — | `IncidentDto` |
| PUT | `/api/v1/incidents/{id}/resolve` | Resolve incident | `{"action": "RETRY\|SKIP\|CANCEL"}` | `IncidentDto` |

## Error Handling

### Retry Strategy (DLQ + Requeue with Backoff)

1. ServiceTask отправляет сообщение в общую очередь `task.execute` с заголовком `x-task-topic`
2. Воркер определяет topic по заголовку, обрабатывает и отвечает в `task.result` с тем же `x-task-topic`
3. При ошибке (exception/timeout):
   - Сообщение отправляется в `retry.{taskType}` с TTL = baseInterval × 2^attempt
   - Header `x-retry-count` инкрементируется
   - После `maxRetries` — сообщение в DLQ, создаётся Incident
4. **Task failure** (`context.error()` / `failTask()`):
   - Движок ищет `ErrorBoundaryEvent` с `attachedToRef` == текущей задаче
   - **Если найден** — токен перенаправляется через error boundary flow
   - **Если не найден** — запускается компенсация: для всех завершённых задач с `CompensationBoundaryEvent` создаются `CompensationTriggeredEvent` в обратном порядке (LIFO), процесс переходит в `COMPENSATING`, затем в `ERROR`
5. ErrorBoundaryEvent — ловит конкретный тип ошибки и перенаправляет поток
6. CompensationBoundaryEvent — при ошибке запускается компенсационная цепочка (обратный порядок выполненных шагов)

### BPMN Validation Error (400)

```json
{
  "error": "UNSUPPORTED_BPMN_ELEMENTS",
  "message": "Process definition contains 2 unsupported BPMN elements",
  "unsupportedElements": [
    {"element": "bpmn:userTask", "id": "Task_1", "name": "Approve Request", "line": 15},
    {"element": "bpmn:scriptTask", "id": "Task_2", "name": "Calculate Total", "line": 28}
  ],
  "supportedElements": [
    "StartEvent", "EndEvent", "ServiceTask", "ExclusiveGateway",
    "ParallelGateway", "CallActivity", "CompensationBoundaryEvent",
    "TimerBoundaryEvent", "ErrorBoundaryEvent"
  ]
}
```

### REST API Error Format

```json
{
  "error": "PROCESS_NOT_FOUND",
  "message": "Process instance with id '...' not found",
  "timestamp": "2026-03-29T12:00:00Z",
  "path": "/api/v1/instances/..."
}
```

## Testing Plan

### Unit Tests
- Token engine: продвижение токенов по всем типам FlowNode
- BPMN parser: разбор XML для каждого поддерживаемого элемента
- Event sourcing: генерация событий и replay
- ExclusiveGateway: выбор ветки по условиям
- ParallelGateway: fork/join логика
- Retry policy: подсчёт backoff intervals

### Integration Tests
- **RabbitMQ через Testcontainers**: publish/consume сообщений, correlation, retry/DLQ flow, delayed messages (таймеры)
- **End-to-end process execution**: deploy definition → start instance → complete tasks → verify completion
- **Persistence**: save/replay events через event store adapter

### End-to-End Tests
- Полный процесс: StartEvent → ServiceTask → ExclusiveGateway → EndEvent
- Параллельное выполнение: ParallelGateway fork/join
- Обработка ошибок: retry exhaustion → DLQ → incident
- CallActivity: вызов дочернего процесса, ожидание завершения, доступ к EventStore родителя
- Compensation flow
- Timer boundary event: timeout → alternative path
- REST API: deploy → start → monitor → complete

## Open Questions

- Нужна ли поддержка multi-instance (loop) для ServiceTask?
- Как обрабатывать versioning при hot deploy новой версии определения (running instances остаются на старой версии)?
- Нужен ли WebSocket/SSE для real-time мониторинга состояния процессов?
- Какой формат условий для ExclusiveGateway: JUEL expressions (как в Camunda), SpEL, или custom DSL?
- Стратегия шардирования при горизонтальном масштабировании нескольких инстансов движка
