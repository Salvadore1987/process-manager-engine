# Worker Spring Boot Starter — Specification

## Context

External services (workers) that implement BPMN ServiceTasks must manually:
- Connect to RabbitMQ
- Consume from `task.{topic}.execute` queue
- Extract `correlationId` from AMQP properties / `x-correlation-id` header
- Process the payload (all process variables as JSON)
- Publish result to exchange `process-engine.tasks` with routing key `task.{topic}.result`
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

### 2. Configure RabbitMQ (`application.yml`)

```yaml
process-engine:
  worker:
    rabbitmq:
      host: localhost
      port: 5672
      username: guest
      password: guest
      virtual-host: /
```

### 3. Implement a worker

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

### `TaskContext`

| Method | Description |
|--------|-------------|
| `getCorrelationId()` | Returns the correlation ID (token UUID) |
| `getVariables()` | Returns all process variables as `Map<String, Object>` |
| `getVariable(String name)` | Returns a single variable by name, or `null` |
| `complete(Map<String, Object> result)` | Completes the task with result variables that merge into the process |
| `error(String errorCode, String errorMessage)` | Fails the task with an error code and message |

**Rules:**
- Either `complete()` or `error()` must be called exactly once
- Calling both or calling either twice throws `IllegalStateException`
- If neither is called, the starter auto-completes with an empty result and logs a warning

### `TaskExecutionException`

Optional exception class with `errorCode` and `message` fields. Can be used for structured error handling in custom middleware or interceptors.

---

## Messaging Protocol

### Receiving a task

Worker consumes from queue `task.{topic}.execute`:

- **Body:** JSON — all process variables
- **correlationId:** from AMQP `correlationId` property or `x-correlation-id` header

### Sending a result

Worker publishes to exchange `process-engine.tasks` with routing key `task.{topic}.result`:

- **Body:** JSON — result from `complete()` or error payload from `error()`
- **correlationId:** same UUID from the received task
- **contentType:** `application/json`
- **deliveryMode:** 2 (persistent)
- **headers:** `x-correlation-id` with the same UUID

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
| `TaskHandlerRegistry` | Registry: topic -> handler. One handler per topic |
| `TaskHandlerBeanPostProcessor` | Scans beans for `ExternalTaskHandler` + `@JobWorker`, registers them |
| `TaskListenerContainer` | `SmartLifecycle`: creates RabbitMQ consumers, dispatches to handlers |
| `WorkerAutoConfiguration` | Auto-configuration: connection, registry, listeners, health |
| `WorkerProperties` | `@ConfigurationProperties("process-engine.worker")` |
| `WorkerHealthIndicator` | Health check: RabbitMQ connection + consumer status |

### Lifecycle

1. **Startup** — `WorkerAutoConfiguration` creates RabbitMQ `ConnectionFactory`
2. **Bean scanning** — `BeanPostProcessor` finds `ExternalTaskHandler` beans with `@JobWorker`, registers in `TaskHandlerRegistry`
3. **Consumer startup** — `TaskListenerContainer` (SmartLifecycle) for each registered topic:
   - Passive declares queue `task.{topic}.execute` (does not conflict with engine's DLX arguments)
   - Starts consumer
4. **Message received:**
   - Deserialize JSON payload
   - Create `TaskContext` with correlation ID, variables, and response sender
   - Call `handler.execute(context)`
   - Handler calls `context.complete()` or `context.error()`
   - Message is ACKed
   - On unexpected exception: `basicNack` + log error
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
    │   └── TaskHandlerBeanPostProcessor.java
    ├── listener/
    │   └── TaskListenerContainer.java
    └── autoconfigure/
        ├── WorkerAutoConfiguration.java
        ├── WorkerProperties.java
        └── WorkerHealthIndicator.java
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

## Out of Scope

- REST client for engine API (starting processes, lifecycle management)
- Retry logic on worker side (engine's responsibility)
- Exchange declaration (engine does this at startup)
- Dependency on `core` or `rabbitmq-transport` modules
