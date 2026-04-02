# Process Manager Engine

Java 21 движок бизнес-процессов на основе **BPMN 2.0** с event-sourced архитектурой и RabbitMQ транспортом. Совместим с [Camunda Modeler](https://camunda.com/download/modeler/) для визуального проектирования процессов.

## Возможности

- **BPMN 2.0 парсинг и валидация** — загрузка XML из Camunda Modeler с проверкой поддерживаемых элементов
- **Token-based execution** — продвижение токенов по графу: StartEvent, EndEvent, ServiceTask, ExclusiveGateway, ParallelGateway, CallActivity, Boundary Events
- **Error handling & Compensation** — маршрутизация ошибок через ErrorBoundaryEvent, автоматическая компенсация завершённых задач в обратном порядке (LIFO) при отсутствии error boundary
- **Event Sourcing** — все изменения состояния записываются как события, состояние восстанавливается через replay, персистентность через Redis
- **RabbitMQ транспорт** — каждый ServiceTask отправляет сообщения в свой topic, retry с exponential backoff, DLQ
- **Spring Boot Starter** — автоконфигурация, health indicators, Micrometer метрики
- **REST API** — полный Camunda-like API для управления процессами
- **Virtual Threads** — Java 21 virtual threads для масштабируемости

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                      rest-api (Spring Boot MVC)             │
│  Definition CRUD │ Instance lifecycle │ Variables │ History  │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│               spring-integration (Spring Boot Starter)       │
│         Auto-configuration │ Health │ Metrics                │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                        core (standalone engine)              │
│  BPMN Parser │ Token Engine │ Event Sourcing │ Conditions   │
│  Ports: ProcessEventStore, ProcessDefinitionStore,          │
│         SequenceGenerator, InstanceDefinitionMapping,       │
│         MessageTransport, TimerService                      │
└──────────────┬───────────────────┬──────────────────────────┘
               │                   │
┌──────────────▼──────────┐ ┌──────▼──────────────────┐
│   rabbitmq-transport    │ │   redis-persistence     │
│  AMQP Client │ Retry    │ │  Event Store │ Def Store│
│  DLQ │ Timer queues     │ │  Sequence Gen │ Mapping │
└─────────────────────────┘ └─────────────────────────┘
```

### Модули

| Модуль | Описание |
|--------|----------|
| `core` | Standalone движок: BPMN-парсер, token engine, event sourcing, port-интерфейсы |
| `rabbitmq-transport` | Реализация `MessageTransport` и `TimerService` поверх RabbitMQ |
| `redis-persistence` | Redis-реализация `ProcessEventStore`, `ProcessDefinitionStore`, `SequenceGenerator`, `InstanceDefinitionMapping` |
| `spring-integration` | Spring Boot Starter: auto-configuration, health indicators, метрики |
| `security` | Spring Security OAuth2 Resource Server + Keycloak JWT интеграция |
| `rest-api` | Spring Boot MVC приложение с REST API |
| `worker-spring-boot-starter` | Starter для внешних worker-сервисов с аннотацией `@JobWorker` |

## Технологии

| Компонент | Технология |
|-----------|-----------|
| Язык | Java 21 |
| Сборка | Gradle 9.2 (Kotlin DSL) |
| Транспорт | RabbitMQ (amqp-client 5.21) |
| Персистентность | Redis (Spring Data Redis / Lettuce) |
| XML-парсинг | JAXB (hand-crafted subset BPMN 2.0) |
| REST | Spring Boot 3.3 + Virtual Threads |
| Метрики | Micrometer + Prometheus |
| Тестирование | JUnit 5, AssertJ, Testcontainers |

## Быстрый старт

### Требования

- Java 21+
- Docker & Docker Compose

### 1. Клонирование и запуск

```bash
git clone https://github.com/Salvadore1987/process-manager-engine
cd process-manager-engine

# Запуск RabbitMQ + Redis
docker compose --env-file .env/local.env up -d rabbitmq redis

# Сборка и запуск приложения
./gradlew :rest-api:bootRun
```

Приложение доступно на `http://localhost:8080`.
RabbitMQ Management UI: `http://localhost:15672` (guest/guest).
Redis: `localhost:6379`.

### 2. Запуск через Docker Compose (все сервисы)

```bash
docker compose --env-file .env/local.env up -d
```

### 3. Деплой BPMN-процесса

```bash
# Деплой
curl -X POST http://localhost:8080/api/v1/definitions \
  -F "file=@docs/example-order-process.bpmn"

# Запуск экземпляра
curl -X POST http://localhost:8080/api/v1/instances \
  -H "Content-Type: application/json" \
  -d '{"definitionKey": "order-processing", "variables": {"orderAmount": 5000}}'
```

## Конфигурация

Все параметры настраиваются через переменные окружения или `application.yml`:

| Переменная окружения | Property | По умолчанию | Описание |
|---------------------|----------|-------------|----------|
| `SERVER_PORT` | `server.port` | `8080` | Порт HTTP-сервера |
| `PROCESS_ENGINE_REDIS_HOST` | `spring.data.redis.host` | `localhost` | Хост Redis |
| `PROCESS_ENGINE_REDIS_PORT` | `spring.data.redis.port` | `6379` | Порт Redis |
| `PROCESS_ENGINE_RABBITMQ_HOST` | `process-engine.rabbitmq.host` | `localhost` | Хост RabbitMQ |
| `PROCESS_ENGINE_RABBITMQ_PORT` | `process-engine.rabbitmq.port` | `5672` | Порт RabbitMQ |
| `PROCESS_ENGINE_RABBITMQ_USERNAME` | `process-engine.rabbitmq.username` | `guest` | Логин RabbitMQ |
| `PROCESS_ENGINE_RABBITMQ_PASSWORD` | `process-engine.rabbitmq.password` | `guest` | Пароль RabbitMQ |
| `PROCESS_ENGINE_RETRY_MAX_ATTEMPTS` | `process-engine.retry.max-attempts` | `3` | Макс. кол-во retry |
| `PROCESS_ENGINE_RETRY_BASE_INTERVAL` | `process-engine.retry.base-interval` | `5s` | Базовый интервал retry |
| `PROCESS_ENGINE_RETRY_MAX_INTERVAL` | `process-engine.retry.max-interval` | `5m` | Макс. интервал retry |
| `PROCESS_ENGINE_SECURITY_ENABLED` | `process-engine.security.enabled` | `true` | Включить/выключить авторизацию |
| `KEYCLOAK_ISSUER_URI` | `process-engine.security.issuer-uri` | `http://localhost:8180/realms/process-engine` | Keycloak realm URI |
| `KEYCLOAK_ADMIN` | — | `admin` | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | — | `admin` | Keycloak admin password |
| `KEYCLOAK_PORT` | — | `8180` | Keycloak HTTP порт |

Файл переменных: `.env/local.env`

## Авторизация (Keycloak)

Движок использует [Keycloak](https://www.keycloak.org/) для аутентификации и авторизации через JWT-токены (OAuth2 Resource Server).

### Роли

| Роль | Описание |
|------|----------|
| `process-admin` | Полный доступ ко всем операциям |
| `process-operator` | Запуск/остановка/suspend/resume экземпляров, отправка сообщений, управление переменными |
| `process-viewer` | Только чтение: определения, экземпляры, переменные, история |
| `process-deployer` | Deploy/undeploy определений процессов (CI/CD) |

### Тестовые пользователи (dev/local)

| Username | Password | Роль |
|----------|----------|------|
| `admin` | `admin` | `process-admin` |
| `operator` | `operator` | `process-operator` |
| `viewer` | `viewer` | `process-viewer` |
| `deployer` | `deployer` | `process-deployer` |

### Получение JWT-токена

```bash
# Получить токен
TOKEN=$(curl -s -X POST http://localhost:8180/realms/process-engine/protocol/openid-connect/token \
  -d "grant_type=password&client_id=process-engine-api&username=admin&password=admin" | jq -r '.access_token')

# Использовать токен
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/definitions
```

### Service-to-service (client credentials)

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/process-engine/protocol/openid-connect/token \
  -d "grant_type=client_credentials&client_id=process-engine-service&client_secret=process-engine-service-secret" | jq -r '.access_token')
```

### Отключение авторизации (для разработки)

```bash
export PROCESS_ENGINE_SECURITY_ENABLED=false
```

Или в `.env/local.env`:
```
PROCESS_ENGINE_SECURITY_ENABLED=false
```

При отключённой авторизации все эндпоинты доступны без токена. Actuator-эндпоинты (`/actuator/**`) всегда доступны без авторизации.

## REST API

Базовый URL: `http://localhost:8080/api/v1`

### Process Definitions

#### Deploy BPMN

```
POST /api/v1/definitions
Content-Type: multipart/form-data
```

Загружает BPMN XML файл, валидирует и деплоит.

**Параметры:** `file` — BPMN XML файл (multipart)

**Ответ (201 Created):**
```json
{
  "id": "019577a0-...",
  "key": "order-processing",
  "version": 1,
  "name": "Order Processing",
  "deployedAt": "2026-03-29T12:00:00Z"
}
```

**Ошибка валидации (400 Bad Request):**
```json
{
  "valid": false,
  "unsupportedElements": [
    {"element": "userTask", "id": "Task_1", "name": "Manual Review", "line": 42}
  ]
}
```

#### Validate BPMN (без деплоя)

```
POST /api/v1/definitions/validate
Content-Type: multipart/form-data
```

**Ответ (200 OK):**
```json
{
  "valid": true,
  "unsupportedElements": []
}
```

#### List Definitions

```
GET /api/v1/definitions
```

**Ответ (200 OK):** `ProcessDefinitionDto[]`

#### Get by Key (latest version)

```
GET /api/v1/definitions/{key}
```

**Ответ (200 OK):** `ProcessDefinitionDto`
**Ошибка (404):** если определение не найдено

#### Get Versions

```
GET /api/v1/definitions/{key}/versions
```

**Ответ (200 OK):** `ProcessDefinitionDto[]`

#### Undeploy

```
DELETE /api/v1/definitions/{key}
```

**Ответ:** `204 No Content`

---

### Process Instances

#### Start Instance

```
POST /api/v1/instances
Content-Type: application/json
```

**Тело запроса:**
```json
{
  "definitionKey": "order-processing",
  "variables": {
    "orderAmount": 15000,
    "customerId": "cust-123"
  }
}
```

**Ответ (201 Created):**
```json
{
  "id": "019577a1-...",
  "definitionId": "019577a0-...",
  "parentProcessInstanceId": null,
  "state": "RUNNING",
  "variables": {"orderAmount": 15000, "customerId": "cust-123"},
  "startedAt": "2026-03-29T12:01:00Z",
  "completedAt": null
}
```

#### Get Instance

```
GET /api/v1/instances/{id}
```

**Ответ (200 OK):** `ProcessInstanceDto`

#### Suspend Instance

```
PUT /api/v1/instances/{id}/suspend
```

**Ответ (200 OK):** `ProcessInstanceDto` со `state: "SUSPENDED"`

#### Resume Instance

```
PUT /api/v1/instances/{id}/resume
```

**Ответ (200 OK):** `ProcessInstanceDto` со `state: "RUNNING"`

#### Terminate Instance

```
DELETE /api/v1/instances/{id}
```

**Ответ (200 OK):** `ProcessInstanceDto`

---

### Variables

#### Get All Variables

```
GET /api/v1/instances/{id}/variables
```

**Ответ (200 OK):**
```json
{
  "orderAmount": 15000,
  "customerId": "cust-123"
}
```

#### Get Single Variable

```
GET /api/v1/instances/{id}/variables/{name}
```

**Ответ (200 OK):** значение переменной
**Ошибка (404):** если переменная не найдена

#### Update Variables

```
PUT /api/v1/instances/{id}/variables
Content-Type: application/json
```

**Тело запроса:** `{"key": "value", ...}`

---

### Messages

#### Send Message

```
POST /api/v1/messages
Content-Type: application/json
```

**Тело запроса:**
```json
{
  "correlationId": "019577a2-...",
  "payload": {"result": "approved"}
}
```

**Ответ:** `202 Accepted`

---

### History

#### Get Event Log

```
GET /api/v1/history/instances/{id}/events
```

**Ответ (200 OK):**
```json
[
  {
    "id": "019577a3-...",
    "processInstanceId": "019577a1-...",
    "type": "ProcessStartedEvent",
    "occurredAt": "2026-03-29T12:01:00Z",
    "sequenceNumber": 1
  },
  {
    "id": "019577a4-...",
    "processInstanceId": "019577a1-...",
    "type": "TokenMovedEvent",
    "occurredAt": "2026-03-29T12:01:00Z",
    "sequenceNumber": 2
  }
]
```

#### Get Activity History

```
GET /api/v1/history/instances/{id}/activities
```

**Ответ (200 OK):**
```json
[
  {
    "tokenId": "019577a5-...",
    "nodeId": "Task_ValidateOrder",
    "state": "MOVED",
    "occurredAt": "2026-03-29T12:01:00Z"
  }
]
```

---

### Incidents

#### List Incidents

```
GET /api/v1/incidents
```

**Ответ (200 OK):** `IncidentDto[]`

#### Get Incident

```
GET /api/v1/incidents/{id}
```

#### Resolve Incident

```
PUT /api/v1/incidents/{id}/resolve
Content-Type: application/json
```

**Тело запроса:**
```json
{"action": "RETRY"}
```

Допустимые действия: `RETRY`, `SKIP`, `CANCEL`

---

### Формат ошибок

Все ошибки возвращаются в едином формате:

```json
{
  "error": "NOT_FOUND",
  "message": "Process instance not found: 019577a1-...",
  "timestamp": "2026-03-29T12:05:00Z",
  "path": "/api/v1/instances/019577a1-..."
}
```

| HTTP код | error | Когда |
|----------|-------|-------|
| 400 | `BAD_REQUEST` | Невалидный BPMN, некорректные параметры |
| 404 | `NOT_FOUND` | Экземпляр или определение не найдено |
| 409 | `CONFLICT` | Недопустимый переход состояния (suspend завершённого и т.п.) |
| 500 | `INTERNAL_SERVER_ERROR` | Внутренняя ошибка |

## Поддерживаемые BPMN-элементы

| Элемент | Описание |
|---------|----------|
| `StartEvent` | Начало процесса |
| `EndEvent` | Завершение процесса (обычное или с ошибкой) |
| `ServiceTask` | Внешняя задача (external task pattern) через RabbitMQ topic |
| `ExclusiveGateway` | Условный роутинг (XOR) |
| `ParallelGateway` | Параллельное выполнение (AND fork/join) |
| `CallActivity` | Вызов подпроцесса по `calledElement` |
| `TimerBoundaryEvent` | Таймер на задаче (таймаут) |
| `ErrorBoundaryEvent` | Обработка ошибок на задаче |
| `CompensationBoundaryEvent` | Компенсация при откате |

Элементы вне этого списка (UserTask, ScriptTask, SubProcess и т.д.) отклоняются при валидации.

## RabbitMQ Topology

```
Exchange: process-engine.tasks (topic, durable)
  Queues per topic:
    task.{topic}.execute   — задачи для внешних сервисов
    task.{topic}.result    — результаты от внешних сервисов

Exchange: process-engine.retry (topic, durable)
  Queues per topic:
    task.{topic}.retry     — retry с exponential backoff (DLX → tasks exchange)

Exchange: process-engine.dlq (fanout, durable)
  Queue: process-engine.dlq — сообщения, исчерпавшие retry

Exchange: process-engine.timers (topic, durable)
  Queue: process-engine.timers.fired — сработавшие таймеры
```

## Сборка и тестирование

```bash
# Сборка
./gradlew build -PexcludeTags=integration

# Все тесты (требует Docker)
./gradlew test

# Тесты одного модуля
./gradlew :core:test
./gradlew :rabbitmq-transport:test
./gradlew :redis-persistence:test
./gradlew :spring-integration:test
./gradlew :rest-api:test

# Конкретный тест
./gradlew test --tests "uz.salvadore.processengine.core.engine.OrderProcessE2ETest"
```

### Статистика тестов

| Модуль | Тесты |
|--------|-------|
| core | 200 |
| rabbitmq-transport | 49 (22 unit + 27 integration) |
| redis-persistence | 23 (Testcontainers Redis) |
| spring-integration | 43 |
| security | 42 |
| rest-api | 52 (29 functional + 23 security) |
| **Итого** | **409** |

## Развёртывание

### Docker Compose (рекомендуется)

```bash
# Все сервисы
docker compose --env-file .env/local.env up -d

# Только инфраструктура (для локальной разработки)
docker compose --env-file .env/local.env up -d rabbitmq redis keycloak

# Логи
docker compose logs -f process-engine

# Остановка
docker compose down
```

### Standalone JAR

```bash
./gradlew :rest-api:bootJar
java -jar rest-api/build/libs/rest-api-1.0-SNAPSHOT.jar
```

### Переменные окружения для production

```bash
export PROCESS_ENGINE_REDIS_HOST=redis.prod.internal
export PROCESS_ENGINE_RABBITMQ_HOST=rabbitmq.prod.internal
export PROCESS_ENGINE_RABBITMQ_USERNAME=prod_user
export PROCESS_ENGINE_RABBITMQ_PASSWORD=<secret>
export PROCESS_ENGINE_RETRY_MAX_ATTEMPTS=5
java -jar rest-api/build/libs/rest-api-1.0-SNAPSHOT.jar
```

## Мониторинг

### Health Endpoints

```
GET /actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "processEngine": {"status": "UP", "details": {"status": "running"}},
    "rabbitMqTransport": {"status": "UP", "details": {"status": "connected"}}
  }
}
```

### Метрики (Micrometer)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `process.engine.instances.started` | Counter | Запущенные экземпляры |
| `process.engine.instances.completed` | Counter | Завершённые экземпляры |
| `process.engine.instances.errors` | Counter | Экземпляры с ошибкой |
| `process.engine.task.duration` | Timer | Длительность задач (tag: topic) |

Prometheus endpoint: `GET /actuator/prometheus`

## Лицензия

Proprietary.
