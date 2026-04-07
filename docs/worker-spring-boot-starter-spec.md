# Worker Spring Boot Starter — Specification

## Context

External services (workers) that implement BPMN ServiceTasks must manually:
- Connect to RabbitMQ
- Consume from shared `task.execute` queue
- Extract topic from `x-task-topic` header, `correlationId` from AMQP properties / `x-correlation-id` header
- Process the payload (all process variables as JSON)
- Publish result to exchange `process-engine.tasks` with routing key `task.result` and `x-task-topic` header
- Follow error format convention (`__error`, `__errorCode`)

The starter eliminates this boilerplate. Integration reduces to implementing one interface and annotating one method.

## Goal

Gradle module `worker-spring-boot-starter` — a standalone Spring Boot Starter that any external service includes as a dependency to interact with Process Manager Engine.

The module does **not** depend on `core` or `rabbitmq-transport`. It is a self-contained client library that replicates only the messaging protocol.

---

## Quick Start

### 1. Add dependency

```gradle
implementation("uz.salvadore:worker-spring-boot-starter:1.0-SNAPSHOT")
```

### 2. Configure (`application.yml`)

```yaml
process-engine:
  worker:
    engine-url: http://localhost:8080        # URL движка (обязателен для auto-deploy)
    rabbitmq:
      host: localhost
      port: 5672
      username: guest
      password: guest
      virtual-host: /
    auto-deploy:
      enabled: true                          # автодеплой BPMN при старте (default: true)
      resource-location: classpath:bpmn/     # каталог с BPMN-файлами (default: classpath:bpmn/)
      fail-on-error: true                    # остановить приложение при ошибке деплоя (default: true)
    auth:                                    # опционально: авторизация для REST API движка
      enabled: false                         # default: false
      token-uri: http://localhost:8180/realms/process-engine/protocol/openid-connect/token
      client-id: process-engine-service
      client-secret: process-engine-service-secret
      grant-type: client_credentials         # default: client_credentials
```

### 3. Place BPMN files in resources

```
src/main/resources/
└── bpmn/
    ├── order-process.bpmn                  # main process (with CallActivity)
    └── charge-payment-subprocess.bpmn      # subprocess (calledElement)
```

При старте приложения все `.bpmn` файлы из указанного каталога автоматически деплоятся в движок через REST API (`POST /api/v1/definitions` или `POST /api/v1/definitions/bundle`).

### 4. Implement a worker

```java
@Component
public class OrderValidationHandler implements ExternalTaskHandler {

    @Override
    @JobWorker(topic = "order.validate")
    public void execute(TaskContext context) {
        try {
            String orderId = (String) context.getVariable("orderId");
            Integer amount = (Integer) context.getVariable("amount");

            // business logic
            boolean valid = amount > 0;

            if (valid) {
                context.complete(Map.of("validationResult", "OK", "discount", 10));
            } else {
                context.error("VALIDATION_FAILED", "Amount must be positive");
            }
        } catch (Exception e) {
            context.error("UNEXPECTED_ERROR", e.getMessage());
        }
    }
}
```

---

## API Reference

### `ExternalTaskHandler` interface

```java
public interface ExternalTaskHandler {
    void execute(TaskContext context);
}
```

Every worker class must implement this interface.

### `@JobWorker` annotation

- **Target:** method (`execute`)
- **Required attribute:** `topic` — the ServiceTask topic name from the BPMN process

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `topic` | `String` | — (required) | ServiceTask topic name from the BPMN process |
| `retry` | `boolean` | `false` | Enable automatic retry on handler exceptions. Disabled by default |
| `retryCount` | `int` | `3` | Maximum retry attempts before giving up. Only applies when `retry = true` |
| `retryBackoff` | `long` | `1000` | Backoff interval between retries in milliseconds. Only applies when `retry = true` |

Retry applies only to **uncaught exceptions** thrown by the handler. Explicit `context.error()` calls are business errors and are never retried.

```java
@Override
@JobWorker(topic = "order.deliver", retry = true, retryCount = 5, retryBackoff = 2000)
public void execute(TaskContext context) {
    // retries up to 5 times with 2s backoff on exceptions
}
```

### `TaskContext`

| Method | Description |
|--------|-------------|
| `getCorrelationId()` | Returns the correlation ID (token UUID) |
| `getVariables()` | Returns all process variables as `Map<String, Object>` |
| `getVariable(String name)` | Returns a single variable by name, or `null` |
| `complete(Map<String, Object> result)` | Completes the task with result variables that merge into the process |
| `error(String errorCode, String errorMessage)` | Fails the task with an error code and message. Engine routes to `ErrorBoundaryEvent` if attached, otherwise triggers compensation and transitions to `ERROR` |

**Rules:**
- Either `complete()` or `error()` must be called exactly once
- Calling both or calling either twice throws `IllegalStateException`
- If neither is called, the starter auto-completes with an empty result and logs a warning

### `TaskExecutionException`

Optional exception class with `errorCode` and `message` fields. Can be used for structured error handling in custom middleware or interceptors.

---

## Messaging Protocol

### Receiving a task

Worker consumes from shared queue `task.execute`:

- **Body:** JSON — all process variables
- **correlationId:** from AMQP `correlationId` property or `x-correlation-id` header
- **topic:** from `x-task-topic` header — determines which handler processes the task

### Sending a result

Worker publishes to exchange `process-engine.tasks` with routing key `task.result`:

- **Body:** JSON — result from `complete()` or error payload from `error()`
- **correlationId:** same UUID from the received task
- **contentType:** `application/json`
- **deliveryMode:** 2 (persistent)
- **headers:** `x-correlation-id` with the same UUID, `x-task-topic` with the same topic

### Success payload

```json
{"validationResult": "OK", "discount": 10}
```

### Error payload (generated automatically by `context.error()`)

```json
{"__error": true, "__errorCode": "VALIDATION_FAILED", "message": "Amount must be positive"}
```

---

## Architecture

### Components

| Component | Purpose |
|-----------|---------|
| `@JobWorker(topic)` | Method annotation — binds `execute()` to a topic |
| `ExternalTaskHandler` | Interface — contract for worker implementations |
| `TaskContext` | Context with variables + `complete()`/`error()` response methods |
| `TaskHandlerRegistry` | Registry: topic -> handler + retry config. One handler per topic |
| `WorkerRetryConfig` | Record holding per-topic retry settings (enabled, maxAttempts, backoffMs) |
| `TaskHandlerBeanPostProcessor` | Scans beans for `ExternalTaskHandler` + `@JobWorker`, extracts retry config, registers them |
| `TaskListenerContainer` | `SmartLifecycle`: creates RabbitMQ consumers with `basicQos(1)`, dispatches to handlers with optional retry |
| `ProcessEngineClient` | HTTP-клиент для REST API движка (JDK HttpClient, multipart, JWT auth) |
| `BpmnAutoDeployer` | `SmartLifecycle`: сканирует ресурсы, деплоит BPMN-файлы через REST API при старте |
| `WorkerAutoConfiguration` | Auto-configuration: connection, registry, listeners, health, auto-deploy |
| `WorkerProperties` | `@ConfigurationProperties("process-engine.worker")` |
| `WorkerHealthIndicator` | Health check: RabbitMQ connection + consumer status |

### Lifecycle

1. **Startup** — `WorkerAutoConfiguration` creates RabbitMQ `ConnectionFactory`
2. **Bean scanning** — `BeanPostProcessor` finds `ExternalTaskHandler` beans with `@JobWorker`, registers in `TaskHandlerRegistry`
3. **BPMN auto-deploy** — `BpmnAutoDeployer` (SmartLifecycle, phase `Integer.MIN_VALUE + 300`) scans resource directory and deploys all BPMN files через REST API движка:
   - 1 файл → `POST /api/v1/definitions` (single file upload)
   - Несколько файлов → `POST /api/v1/definitions/bundle` (все файлы одним запросом, сервер сам валидирует CallActivity)
4. **Consumer startup** — `TaskListenerContainer` (SmartLifecycle) creates a single consumer on shared `task.execute` queue:
   - Sets `basicQos(1)` to prevent message flooding and duplicate delivery on reconnect
   - Passive declares queue `task.execute`
   - Starts single shared consumer
4. **Message received:**
   - Extract topic from `x-task-topic` header
   - Look up handler from registry; if not found — throw `IllegalStateException` (message goes to DLQ)
   - Deserialize JSON payload
   - Create `TaskContext` with correlation ID, variables, and response sender
   - If `@JobWorker(retry = true)`: wrap execution in retry loop (up to `retryCount` attempts with `retryBackoff` ms delay between attempts)
   - Call `handler.execute(context)`
   - Handler calls `context.complete()` or `context.error()`
   - Message is ACKed
   - On unexpected exception (after retry exhaustion if enabled): `basicNack` + log error
5. **Shutdown** — graceful cancel of all consumers, close connection

### Module structure

```
worker-spring-boot-starter/
├── build.gradle.kts
└── src/main/java/uz/salvadore/processengine/worker/
    ├── ExternalTaskHandler.java
    ├── TaskContext.java
    ├── TaskExecutionException.java
    ├── annotation/
    │   └── JobWorker.java
    ├── registry/
    │   ├── TaskHandlerRegistry.java
    │   ├── WorkerRetryConfig.java
    │   └── TaskHandlerBeanPostProcessor.java
    ├── listener/
    │   └── TaskListenerContainer.java
    └── autoconfigure/
        ├── WorkerAutoConfiguration.java
        ├── WorkerProperties.java
        ├── WorkerHealthIndicator.java
        ├── ProcessEngineClient.java
        └── BpmnAutoDeployer.java
```

---

## Dependencies

```kotlin
dependencies {
    implementation(libs.rabbitmq.client)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.actuator)
    implementation(libs.slf4j.api)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
```

---

## Auto-Deploy

При наличии `engine-url` в конфигурации, модуль автоматически сканирует BPMN-файлы из ресурсного каталога и деплоит их через REST API движка при старте приложения.

### Конфигурация

| Property | По умолчанию | Описание |
|----------|-------------|----------|
| `process-engine.worker.engine-url` | — | URL движка (обязателен для auto-deploy) |
| `process-engine.worker.auto-deploy.enabled` | `true` | Включить/выключить автодеплой |
| `process-engine.worker.auto-deploy.resource-location` | `classpath:bpmn/` | Каталог с BPMN-файлами (поддерживает `classpath:` и `file:` префиксы) |
| `process-engine.worker.auto-deploy.fail-on-error` | `true` | При ошибке деплоя остановить запуск приложения |
| `process-engine.worker.auth.enabled` | `false` | Включить OAuth2 client_credentials авторизацию |
| `process-engine.worker.auth.token-uri` | — | URL для получения JWT-токена |
| `process-engine.worker.auth.client-id` | — | Client ID |
| `process-engine.worker.auth.client-secret` | — | Client Secret |
| `process-engine.worker.auth.grant-type` | `client_credentials` | OAuth2 grant type |

### Алгоритм

1. Сканирует `resource-location + **/*.bpmn` через `ResourcePatternResolver`
2. Если найден 1 файл → `POST /api/v1/definitions` (single file multipart upload)
3. Если найдено несколько файлов → `POST /api/v1/definitions/bundle` (все файлы одним multipart запросом)
4. Сервер движка сам парсит BPMN, валидирует CallActivity зависимости и деплоит

Worker-сервис **не** анализирует содержимое BPMN-файлов — вся валидация происходит на стороне движка.

### Авторизация

Если в движке включена Keycloak-авторизация, воркер должен получать JWT-токен для деплоя:

```yaml
process-engine:
  worker:
    auth:
      enabled: true
      token-uri: http://localhost:8180/realms/process-engine/protocol/openid-connect/token
      client-id: process-engine-service
      client-secret: process-engine-service-secret
```

`ProcessEngineClient` автоматически получает и кеширует JWT-токен, обновляя его при истечении (за 30 секунд до expiry).

### SmartLifecycle фаза

`BpmnAutoDeployer` запускается на фазе `Integer.MIN_VALUE + 300` — до того, как `TaskListenerContainer` (`MAX_VALUE`) начнёт слушать очередь задач.

### Пример структуры ресурсов

```
src/main/resources/bpmn/
├── order-process.bpmn                    # main (содержит CallActivity → charge-payment-subprocess)
├── charge-payment-subprocess.bpmn        # subprocess
└── notification-process.bpmn             # standalone
```

При наличии нескольких файлов все они отправляются одним bundle-запросом. Сервер движка сам определяет main process и валидирует подпроцессы.

### Для не-Java сервисов

Если worker написан не на Java/Spring, BPMN-файлы можно задеплоить вручную через REST API:

```bash
# Один файл
curl -X POST http://localhost:8080/api/v1/definitions \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@order-process.bpmn"

# Bundle (main + подпроцессы)
curl -X POST http://localhost:8080/api/v1/definitions/bundle \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@order-process.bpmn" \
  -F "files=@charge-payment-subprocess.bpmn"
```

---

## Out of Scope

- REST client for engine API beyond deployment (starting processes, lifecycle management)
- Advanced retry strategies (exponential backoff, circuit breaker) — `@JobWorker` provides simple linear retry; complex patterns should be implemented in the handler
- Exchange declaration (engine does this at startup)
- Dependency on `core` or `rabbitmq-transport` modules — worker is a self-contained client library
