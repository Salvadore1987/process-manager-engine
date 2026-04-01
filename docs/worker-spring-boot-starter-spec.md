# Worker Spring Boot Starter — Specification

## Context

External services (workers) that implement BPMN ServiceTasks must manually:
- Connect to RabbitMQ
- Consume from `task.{topic}.execute` queue
- Extract `correlationId` from AMQP properties / `x-correlation-id` header
- Process the payload (all process variables as JSON)
- Publish result to exchange `process-engine.tasks` with routing key `task.{topic}.result`
- Follow error format convention (`__error`, `__errorCode`)

This is boilerplate that will be duplicated across every worker service. The starter reduces integration to a single annotation and a business method.

## Goal

New Gradle module `worker-spring-boot-starter` — a standalone Spring Boot Starter that any external service includes as a dependency to interact with Process Manager Engine.

**Important:** the module does NOT depend on `core` or `rabbitmq-transport`. It is a self-contained client library that only replicates the messaging protocol.

---

## Developer Experience

### 1. Dependency

```gradle
implementation("uz.salvadore:worker-spring-boot-starter:1.0-SNAPSHOT")
```

### 2. Configuration (`application.yml`)

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

### 3. Task handler — `@TaskHandler` annotation

```java
@Component
public class OrderValidationHandler {

    @TaskHandler(topic = "order.validate")
    public Map<String, Object> validate(TaskContext context) {
        String orderId = (String) context.getVariable("orderId");
        // business logic
        return Map.of("validationResult", "OK", "discount", 10);
    }
}
```

### 4. Error handling

```java
@TaskHandler(topic = "order.validate")
public Map<String, Object> validate(TaskContext context) {
    if (invalid) {
        throw new TaskExecutionException("VALIDATION_FAILED", "Invalid order");
    }
    return Map.of("result", "OK");
}
```

When `TaskExecutionException` is thrown, the starter automatically builds a response with `__error: true` and `__errorCode`.

---

## Components

| Component | Purpose |
|-----------|---------|
| `@TaskHandler(topic)` | Method-level annotation — binds method to a topic |
| `TaskContext` | Wrapper: `correlationId`, `variables` (payload from execute queue) |
| `TaskExecutionException` | Exception → automatic error response with `__errorCode` |
| `WorkerProperties` | `@ConfigurationProperties("process-engine.worker")` — RabbitMQ settings |
| `WorkerAutoConfiguration` | Auto-configuration: RabbitMQ connection, handler scanning, consumer startup |
| `TaskHandlerRegistry` | Registry: topic → handler method. Populated at startup via `BeanPostProcessor` |
| `TaskListenerContainer` | For each registered topic: creates consumer on `task.{topic}.execute`, invokes handler, publishes result to `task.{topic}.result` |
| `WorkerHealthIndicator` | Health check: RabbitMQ connection + consumer status |

---

## Messaging Protocol (mirrors existing engine protocol)

### Receiving a task (consume from `task.{topic}.execute`)

- **Body:** JSON — all process variables
- **correlationId:** from AMQP `correlationId` property or `x-correlation-id` header

### Sending a result (publish to exchange `process-engine.tasks`, routing key `task.{topic}.result`)

- **Body:** JSON — handler return value
- **correlationId:** same UUID from the received task
- **contentType:** `application/json`
- **deliveryMode:** 2 (persistent)
- **headers:** `x-correlation-id` with the same UUID

### Error format

```json
{"__error": true, "__errorCode": "CODE", "message": "description"}
```

---

## Lifecycle

1. **Application startup** → `WorkerAutoConfiguration` creates RabbitMQ connection (ConnectionFactory)
2. **BeanPostProcessor** scans beans, finds methods annotated with `@TaskHandler`, registers them in `TaskHandlerRegistry`
3. **SmartLifecycle** (`TaskListenerContainer`) for each registered topic:
   - Declares queue `task.{topic}.execute` via passive declare (does not conflict with engine's DLX arguments)
   - Starts consumer
4. **On message received:**
   - Deserialize JSON payload
   - Create `TaskContext(correlationId, variables)`
   - Invoke handler method
   - On success: publish result to `task.{topic}.result`
   - On `TaskExecutionException`: publish error response
   - On unexpected exception: `basicNack` + log error
5. **Application shutdown** → graceful shutdown of consumers via `SmartLifecycle.stop()`

---

## Module File Structure

```
worker-spring-boot-starter/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/uz/salvadore/processengine/worker/
    │   │   ├── annotation/
    │   │   │   └── TaskHandler.java
    │   │   ├── TaskContext.java
    │   │   ├── TaskExecutionException.java
    │   │   ├── registry/
    │   │   │   ├── TaskHandlerRegistry.java
    │   │   │   └── TaskHandlerBeanPostProcessor.java
    │   │   ├── listener/
    │   │   │   └── TaskListenerContainer.java
    │   │   └── autoconfigure/
    │   │       ├── WorkerAutoConfiguration.java
    │   │       ├── WorkerProperties.java
    │   │       └── WorkerHealthIndicator.java
    │   └── resources/
    │       └── META-INF/spring/
    │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/uz/salvadore/processengine/worker/
            └── ... (tests with Testcontainers + RabbitMQ)
```

---

## Dependencies (`build.gradle.kts`)

```kotlin
plugins {
    id("java-library")
}

dependencies {
    implementation(libs.rabbitmq.client)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.actuator)
    implementation(libs.slf4j.api)

    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.rabbitmq)
}
```

---

## Out of Scope

- REST client for engine API (starting processes, lifecycle management)
- Retry logic on worker side (engine's responsibility)
- Exchange declaration (engine does this at startup)
- Dependency on `core` or `rabbitmq-transport` modules
