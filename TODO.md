# TODO — Process Manager Engine

## Phase 0: Gradle Multi-Module Skeleton

- ✅ 0.1. Обновить `settings.gradle.kts` — добавить `include("core", "rabbitmq-transport", "spring-integration", "rest-api")`
- ✅ 0.2. Создать `gradle/libs.versions.toml` — version catalog:
  - Jackson 2.17.x, JAXB 4.0.x, SLF4J 2.0.x, Micrometer 1.13.x
  - RabbitMQ amqp-client 5.21.x
  - Spring Boot 3.3.x, Spring Framework 6.1.x
  - JUnit 5.10.x, AssertJ 3.26.x, Testcontainers 1.19.x
- ✅ 0.3. Переписать корневой `build.gradle.kts`:
  - `subprojects {}`: Java 21 toolchain, mavenCentral, JUnit 5 + AssertJ для всех модулей
  - Убрать текущие зависимости из корня (они переедут в модули)
- ✅ 0.4. Создать `core/build.gradle.kts` — plugin `java-library`:
  - `api`: jackson-databind, jackson-datatype-jsr310, jakarta.xml.bind-api, SLF4J API, micrometer-core
  - `implementation`: jaxb-runtime (glassfish)
  - `testImplementation`: JUnit 5, AssertJ
- ✅ 0.5. Создать `rabbitmq-transport/build.gradle.kts` — plugin `java-library`:
  - `api`: `project(":core")`
  - `implementation`: amqp-client, SLF4J
  - `testImplementation`: JUnit 5, AssertJ, testcontainers, testcontainers-rabbitmq
- ✅ 0.6. Создать `spring-integration/build.gradle.kts` — plugin `java-library`:
  - `api`: `project(":core")`
  - `implementation`: `project(":rabbitmq-transport")`, spring-boot-autoconfigure, spring-boot-actuator, micrometer-registry-prometheus
  - `compileOnly`: spring-boot-configuration-processor
- ✅ 0.7. Создать `rest-api/build.gradle.kts` — plugins `java`, `org.springframework.boot`:
  - `implementation`: `project(":spring-integration")`, spring-boot-starter-web, spring-boot-starter-actuator
  - `testImplementation`: spring-boot-starter-test, testcontainers
- ✅ 0.8. Создать директории `src/main/java` и `src/test/java` для всех 4 модулей
- ✅ 0.9. Проверить: `./gradlew build` проходит без ошибок

---

## Phase 1: Core Domain Model

### 1.1. Enums

- ✅ 1.1.1. Создать `ProcessState` enum — RUNNING, SUSPENDED, COMPLETED, ERROR, TERMINATED
- ✅ 1.1.2. Создать `TokenState` enum — ACTIVE, WAITING, COMPLETED
- ✅ 1.1.3. Создать `NodeType` enum — START_EVENT, END_EVENT, SERVICE_TASK, EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY, CALL_ACTIVITY, COMPENSATION_BOUNDARY, TIMER_BOUNDARY, ERROR_BOUNDARY

### 1.2. Утилиты

- ✅ 1.2.1. Реализовать `UUIDv7` — генерация UUID version 7 (timestamp + random)
- ✅ 1.2.2. Написать `UUIDv7Test` — version=7, time-ordered, unique

### 1.3. FlowNode (sealed interface) и наследники

- ✅ 1.3.1. Создать sealed interface `FlowNode` (id, name, type, incomingFlows, outgoingFlows)
- ✅ 1.3.2. Реализовать `StartEvent` record implements FlowNode
- ✅ 1.3.3. Реализовать `EndEvent` record (+ errorCode для error end event)
- ✅ 1.3.4. Реализовать `ServiceTask` record (+ topic, retryCount, retryInterval)
- ✅ 1.3.5. Реализовать `ExclusiveGateway` record
- ✅ 1.3.6. Реализовать `ParallelGateway` record
- ✅ 1.3.7. Реализовать `CallActivity` record (+ calledElement)
- ✅ 1.3.8. Реализовать `CompensationBoundaryEvent` record (+ attachedToRef)
- ✅ 1.3.9. Реализовать `TimerBoundaryEvent` record (+ attachedToRef, duration, cancelActivity)
- ✅ 1.3.10. Реализовать `ErrorBoundaryEvent` record (+ attachedToRef, errorCode, cancelActivity)

### 1.4. SequenceFlow и ConditionExpression

- ✅ 1.4.1. Создать `SequenceFlow` record (id, sourceRef, targetRef, conditionExpression)
- ✅ 1.4.2. Создать `ConditionExpression` record (expression string)

### 1.5. Aggregate Roots

- ✅ 1.5.1. Реализовать `Token` (id UUIDv7, currentNodeId, state) с методами state transition
- ✅ 1.5.2. Реализовать `ProcessDefinition` (id, key, version, name, bpmnXml, flowNodes, sequenceFlows, deployedAt) — factory method, валидация (1 start event, >=1 end event)
- ✅ 1.5.3. Реализовать `ProcessInstance` (id, definitionId, parentProcessInstanceId, state, tokens, variables, startedAt, completedAt) — factory method, lifecycle methods (suspend, resume, complete, error, terminate)

### 1.6. Domain Events (sealed interface + records)

- ✅ 1.6.1. Создать sealed interface `ProcessEvent` (id, processInstanceId, type, payload, occurredAt, sequenceNumber)
- ✅ 1.6.2. Реализовать `ProcessStartedEvent` record
- ✅ 1.6.3. Реализовать `TokenMovedEvent` record
- ✅ 1.6.4. Реализовать `TaskCompletedEvent` record
- ✅ 1.6.5. Реализовать `ProcessSuspendedEvent` record
- ✅ 1.6.6. Реализовать `ProcessResumedEvent` record
- ✅ 1.6.7. Реализовать `ProcessCompletedEvent` record
- ✅ 1.6.8. Реализовать `ProcessErrorEvent` record
- ✅ 1.6.9. Реализовать `TimerScheduledEvent` record
- ✅ 1.6.10. Реализовать `TimerFiredEvent` record
- ✅ 1.6.11. Реализовать `CompensationTriggeredEvent` record
- ✅ 1.6.12. Реализовать `CallActivityStartedEvent` record
- ✅ 1.6.13. Реализовать `CallActivityCompletedEvent` record

### 1.7. Port Interfaces

- ✅ 1.7.1. Создать `ProcessEventStore` interface (append, getEvents by processInstanceId)
- ✅ 1.7.2. Создать `MessageTransport` interface (send, subscribe)
- ✅ 1.7.3. Создать `TimerService` interface (schedule, cancel)
- ✅ 1.7.4. Создать `ActivityHandler` interface (handle)

### 1.8. Тесты Phase 1

- ✅ 1.8.1. `TokenTest` — state transitions (ACTIVE→WAITING→COMPLETED), invalid transitions throw
- ✅ 1.8.2. `ProcessDefinitionTest` — factory creation, validation (must have start/end events)
- ✅ 1.8.3. `ProcessInstanceTest` — state transitions, suspend/resume/complete/error/terminate
- ✅ 1.8.4. `FlowNodeTest` — sealed permits only known types, ServiceTask carries topic
- ✅ 1.8.5. `ProcessEventTest` — each event record creates correctly, sequenceNumber ordering
- ✅ 1.8.6. Проверить: `./gradlew :core:test` проходит

---

## Phase 2: BPMN Parser и Validator

### 2.1. JAXB модель (hand-crafted)

- ✅ 2.1.1. Создать `BpmnDefinitions` — корневой JAXB-элемент (`bpmn:definitions`)
- ✅ 2.1.2. Создать `BpmnProcess` — `bpmn:process` (id, name, isExecutable, elements list)
- ✅ 2.1.3. Создать `BpmnStartEvent` — `bpmn:startEvent`
- ✅ 2.1.4. Создать `BpmnEndEvent` — `bpmn:endEvent` (+ errorEventDefinition)
- ✅ 2.1.5. Создать `BpmnServiceTask` — `bpmn:serviceTask` (+ camunda:type, camunda:topic, isForCompensation)
- ✅ 2.1.6. Создать `BpmnExclusiveGateway` — `bpmn:exclusiveGateway`
- ✅ 2.1.7. Создать `BpmnParallelGateway` — `bpmn:parallelGateway`
- ✅ 2.1.8. Создать `BpmnCallActivity` — `bpmn:callActivity` (+ calledElement)
- ✅ 2.1.9. Создать `BpmnBoundaryEvent` — `bpmn:boundaryEvent` (+ attachedToRef, timerEventDefinition, errorEventDefinition, compensateEventDefinition)
- ✅ 2.1.10. Создать `BpmnSequenceFlow` — `bpmn:sequenceFlow` (+ conditionExpression)
- ✅ 2.1.11. Создать `BpmnAssociation` — `bpmn:association` (sourceRef, targetRef)
- ✅ 2.1.12. Создать `BpmnError` — `bpmn:error` (id, name, errorCode)

### 2.2. Validator

- ✅ 2.2.1. Создать `UnsupportedElementError` record (element, id, name, line)
- ✅ 2.2.2. Создать `BpmnValidationResult` (valid boolean, List<UnsupportedElementError>)
- ✅ 2.2.3. Реализовать `BpmnValidator` — whitelist поддерживаемых элементов, StAX-парсинг для line numbers, сбор всех неподдерживаемых элементов

### 2.3. Mappers

- ✅ 2.3.1. Реализовать `FlowNodeMapper` — JAXB → domain FlowNode (для каждого из 9 типов)
- ✅ 2.3.2. Реализовать `SequenceFlowMapper` — JAXB → domain SequenceFlow
- ✅ 2.3.3. Реализовать `ProcessDefinitionMapper` — собирает полный ProcessDefinition из JAXB модели

### 2.4. Parser

- ✅ 2.4.1. Создать `BpmnParseException` checked exception
- ✅ 2.4.2. Реализовать `BpmnParser` — JAXB unmarshal + validation + mapping → ProcessDefinition
  - Кэширование JAXBContext (static, thread-safe)
  - Поддержка нескольких `bpmn:process` в одном файле

### 2.5. Тестовые ресурсы

- ✅ 2.5.1. Скопировать `docs/example-order-process.bpmn` → `core/src/test/resources/bpmn/`
- ✅ 2.5.2. Скопировать `docs/example-payment-process.bpmn` → `core/src/test/resources/bpmn/`
- ✅ 2.5.3. Создать `core/src/test/resources/bpmn/invalid-unsupported-elements.bpmn` (с UserTask, ScriptTask)

### 2.6. Тесты Phase 2

- ✅ 2.6.1. `BpmnParserTest` — парсинг example-order-process.bpmn: проверить все FlowNodes, SequenceFlows, topics, calledElement, boundary events
- ✅ 2.6.2. `BpmnParserTest` — парсинг example-payment-process.bpmn: StartEvent → 2 ServiceTask → EndEvent
- ✅ 2.6.3. `BpmnValidatorTest` — valid BPMN проходит валидацию
- ✅ 2.6.4. `BpmnValidatorTest` — BPMN с UserTask/ScriptTask → ошибка с element details и line numbers
- ✅ 2.6.5. `FlowNodeMapperTest` — маппинг каждого JAXB типа → domain тип
- ✅ 2.6.6. `BpmnParserRoundtripTest` — граф order-processing: 17 nodes, корректные connections
- ✅ 2.6.7. `BpmnParserTest` — malformed XML → BpmnParseException
- ✅ 2.6.8. Проверить: `./gradlew :core:test` проходит

---

## Phase 3: Event Sourcing и State Reconstruction

### 3.1. Event Sourcing Core

- ✅ 3.1.1. Реализовать `EventSequencer` — assigns monotonic sequenceNumber per processInstanceId
- ✅ 3.1.2. Реализовать `EventApplier` — pattern matching switch по sealed ProcessEvent, мутирует builder → иммутабельный ProcessInstance
- ✅ 3.1.3. Реализовать `ProcessInstanceProjection` — fold List<ProcessEvent> через EventApplier → ProcessInstance

### 3.2. Event Store Adapters

- ✅ 3.2.1. Реализовать `NoOpEventStore` — append = no-op, getEvents = empty list
- ✅ 3.2.2. Реализовать `InMemoryEventStore` — ConcurrentHashMap<UUID, CopyOnWriteArrayList<ProcessEvent>>

### 3.3. Тесты Phase 3

- ✅ 3.3.1. `EventApplierTest` — ProcessStartedEvent → state=RUNNING, tokens created
- ✅ 3.3.2. `EventApplierTest` — TokenMovedEvent → token.currentNodeId updated
- ✅ 3.3.3. `EventApplierTest` — TaskCompletedEvent → token advances
- ✅ 3.3.4. `EventApplierTest` — ProcessSuspendedEvent → state=SUSPENDED
- ✅ 3.3.5. `EventApplierTest` — ProcessResumedEvent → state=RUNNING
- ✅ 3.3.6. `EventApplierTest` — ProcessCompletedEvent → state=COMPLETED
- ✅ 3.3.7. `EventApplierTest` — ProcessErrorEvent → state=ERROR
- ✅ 3.3.8. `EventApplierTest` — CallActivityStarted/CompletedEvent
- ✅ 3.3.9. `ProcessInstanceProjectionTest` — replay полной последовательности (Start → ServiceTask → End)
- ✅ 3.3.10. `InMemoryEventStoreTest` — append, getEvents, ordering by sequenceNumber
- ✅ 3.3.11. `InMemoryEventStoreTest` — concurrent appends (thread safety)
- ✅ 3.3.12. `NoOpEventStoreTest` — append = no-op, getEvents = empty
- ✅ 3.3.13. Проверить: `./gradlew :core:test` проходит

---

## Phase 4: Token Execution Engine

### 4.1. Condition Evaluator

- ✅ 4.1.1. Создать interface `ConditionEvaluator` (evaluate(expression, variables) → boolean)
- ✅ 4.1.2. Реализовать `SimpleConditionEvaluator` — парсинг `${variable > value}`, `${variable == value}`, `${variable < value}`

### 4.2. Execution Context

- ✅ 4.2.1. Реализовать `ExecutionContext` — carries ProcessInstance state, variables, collects events

### 4.3. Node Handlers

- ✅ 4.3.1. Создать interface `NodeHandler` — handle(Token, FlowNode, ExecutionContext) → List<ProcessEvent>
- ✅ 4.3.2. Реализовать `StartEventHandler` — создаёт token на первый outgoing flow
- ✅ 4.3.3. Реализовать `EndEventHandler` — completes token; если error end event → ProcessErrorEvent; если все токены completed → ProcessCompletedEvent
- ✅ 4.3.4. Реализовать `ServiceTaskHandler` — token → WAITING, вызов MessageTransport.send(topic, correlationId, variables)
- ✅ 4.3.5. Реализовать `ExclusiveGatewayHandler` — evaluate conditions, выбрать первый matching outgoing flow
- ✅ 4.3.6. Реализовать `ParallelGatewayHandler` — fork: N tokens по outgoing flows; join: ждать все incoming, consume waiting tokens
- ✅ 4.3.7. Реализовать `CallActivityHandler` — создать child ProcessInstance (parentProcessInstanceId), parent token → WAITING
- ✅ 4.3.8. Реализовать `TimerBoundaryEventHandler` — вызов TimerService.schedule(), при срабатывании → cancel attached task, redirect flow
- ✅ 4.3.9. Реализовать `ErrorBoundaryEventHandler` — catch error по errorCode, redirect flow
- ✅ 4.3.10. Реализовать `CompensationBoundaryEventHandler` — trigger compensation task (association)

### 4.4. Engine Core

- ✅ 4.4.1. Реализовать `TokenExecutor` — Map<NodeType, NodeHandler>, dispatch token to correct handler
- ✅ 4.4.2. Реализовать `ProcessDefinitionRepository` — in-memory registry (deploy, undeploy, getByKey, getById, list)
- ✅ 4.4.3. Реализовать `ProcessEngine` — главный фасад:
  - `deploy(ProcessDefinition)` — регистрация определения
  - `startProcess(definitionKey, variables)` → ProcessInstance
  - `completeTask(correlationId, result)` — продвижение token после ответа от внешнего сервиса
  - `completeCallActivity(childInstanceId)` — продвижение parent token после завершения child
  - `suspendProcess(instanceId)` / `resumeProcess(instanceId)`
  - `terminateProcess(instanceId)`
  - `getProcessInstance(instanceId)` — rebuild из events
  - `sendMessage(correlationId, payload)`

### 4.5. Тесты Phase 4

- ✅ 4.5.1. `SimpleConditionEvaluatorTest` — `${amount > 10000}` с разными значениями
- ✅ 4.5.2. `StartEventHandlerTest` — creates token on outgoing flow
- ✅ 4.5.3. `EndEventHandlerTest` — normal end completes token
- ✅ 4.5.4. `EndEventHandlerTest` — error end emits ProcessErrorEvent
- ✅ 4.5.5. `ServiceTaskHandlerTest` — sets token WAITING, calls MessageTransport.send
- ✅ 4.5.6. `ExclusiveGatewayHandlerTest` — correct branch by condition
- ✅ 4.5.7. `ExclusiveGatewayHandlerTest` — no matching condition → error
- ✅ 4.5.8. `ParallelGatewayHandlerTest` — fork creates N tokens
- ✅ 4.5.9. `ParallelGatewayHandlerTest` — join waits for all, then creates outgoing token
- ✅ 4.5.10. `CallActivityHandlerTest` — creates child instance, parent token WAITING
- ✅ 4.5.11. `TimerBoundaryEventHandlerTest` — schedules timer via TimerService
- ✅ 4.5.12. `ErrorBoundaryEventHandlerTest` — catches error, redirects flow
- ✅ 4.5.13. `CompensationBoundaryEventHandlerTest` — triggers compensation task
- ✅ 4.5.14. `TokenExecutorTest` — dispatches to correct handler by NodeType
- ✅ 4.5.15. `ProcessDefinitionRepositoryTest` — deploy, undeploy, getByKey (latest version)
- ✅ 4.5.16. `ProcessEngineTest` — deploy + start → ProcessStartedEvent + token на первом ServiceTask
- ✅ 4.5.17. `ProcessEngineTest` — completeTask → token advances to next node
- ✅ 4.5.18. `ProcessEngineTest` — full linear process (Start → ServiceTask → End) → ProcessCompletedEvent
- ✅ 4.5.19. `ProcessEngineTest` — suspend/resume lifecycle
- ✅ 4.5.20. `ProcessEngineTest` — terminate running instance
- ✅ 4.5.21. `OrderProcessE2ETest` — parse example-order-process.bpmn, deploy, start, complete all tasks (standard path: amount <= 10000), verify ProcessCompletedEvent
- ✅ 4.5.22. `OrderProcessE2ETest` — high-value path (amount > 10000): fraud check → parallel → end
- ✅ 4.5.23. `OrderProcessE2ETest` — event replay produces identical ProcessInstance state
- ✅ 4.5.24. Проверить: `./gradlew :core:test` проходит

---

## Phase 5: RabbitMQ Transport Adapter

### 5.1. Configuration

- ✅ 5.1.1. Создать `RabbitMqTransportConfig` — host, port, username, password, exchange names, retry settings (maxAttempts, baseInterval)

### 5.2. Connection Management

- ✅ 5.2.1. Реализовать `RabbitMqConnectionManager` — создание Connection, channel pooling, graceful shutdown, auto-recovery

### 5.3. Topology

- ✅ 5.3.1. Реализовать `RabbitMqTopologyInitializer` — declare exchanges (tasks, retry, dlq, timers), DLQ queue, bindings
- ✅ 5.3.2. Lazy per-topic queue declaration: `task.{topic}.execute` и `task.{topic}.result` при первом использовании

### 5.4. Transport Implementation

- ✅ 5.4.1. Реализовать `CorrelationIdResolver` — extract/set correlation-id из AMQP headers
- ✅ 5.4.2. Реализовать `RetryPolicy` — exponential backoff: baseInterval × 2^attempt, max cap
- ✅ 5.4.3. Реализовать `RabbitMqMessageTransport` implements MessageTransport:
  - `send(topic, correlationId, payload)` → publish в `task.{topic}.execute`
  - subscribe на `task.{topic}.result` → callback с correlation matching
- ✅ 5.4.4. Реализовать `TaskResultConsumer` — listen result queues, resolve correlation, callback to engine (virtual threads executor)
- ✅ 5.4.5. Реализовать `DeadLetterConsumer` — listen DLQ, create incident records

### 5.5. Timer Implementation

- ✅ 5.5.1. Реализовать `RabbitMqTimerService` implements TimerService:
  - `schedule(processInstanceId, duration)` → publish в timers exchange с x-delay header
  - `cancel(processInstanceId)` — best-effort (отметить как cancelled, игнорировать при получении)
  - Consumer на timer queue → callback при срабатывании

### 5.6. Тесты Phase 5

- ✅ 5.6.1. `RetryPolicyTest` (unit) — backoff: attempt 0=base, 1=2x, 2=4x, max cap
- ✅ 5.6.2. `RabbitMqMessageTransportTest` (Testcontainers) — send message, consume, verify headers + content
- ✅ 5.6.3. `RabbitMqMessageTransportTest` (Testcontainers) — send result, verify correlation-id matching
- ✅ 5.6.4. `RabbitMqTopologyInitializerTest` (Testcontainers) — verify exchanges и queues created
- ✅ 5.6.5. `RabbitMqTimerServiceTest` (Testcontainers) — schedule 1s timer, verify callback fires
- ✅ 5.6.6. `RabbitMqRetryFlowTest` (Testcontainers) — task failure → retry with correct delay
- ✅ 5.6.7. `RabbitMqRetryFlowTest` (Testcontainers) — exhaust retries → message in DLQ
- ✅ 5.6.8. `RabbitMqConnectionManagerTest` (Testcontainers) — connection recovery
- ✅ 5.6.9. Проверить: `./gradlew :rabbitmq-transport:test` проходит (Docker required)

---

## Phase 6: Spring Boot Integration (Starter)

### 6.1. Properties

- ✅ 6.1.1. Создать `ProcessEngineProperties` (@ConfigurationProperties):
  - `process-engine.persistence.enabled` (boolean, default false)
  - `process-engine.rabbitmq.host/port/username/password`
  - `process-engine.rabbitmq.exchanges.tasks/retry/dlq/timers`
  - `process-engine.retry.max-attempts`, `process-engine.retry.base-interval`

### 6.2. Auto-Configuration

- ✅ 6.2.1. Реализовать `ProcessEngineAutoConfiguration` — создание ProcessEngine bean, ProcessDefinitionRepository bean
- ✅ 6.2.2. Реализовать `RabbitMqTransportAutoConfiguration` — @ConditionalOnClass(ConnectionFactory), создание RabbitMq* beans
- ✅ 6.2.3. Реализовать `EventStoreAutoConfiguration`:
  - @ConditionalOnProperty("process-engine.persistence.enabled", havingValue="true") → placeholder для real store
  - @ConditionalOnMissingBean(ProcessEventStore.class) → NoOpEventStore
- ✅ 6.2.4. Реализовать `MetricsAutoConfiguration` — Micrometer counters/timers
- ✅ 6.2.5. Создать `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 6.3. Health & Metrics

- ✅ 6.3.1. Реализовать `ProcessEngineHealthIndicator` — UP when engine running
- ✅ 6.3.2. Реализовать `RabbitMqTransportHealthIndicator` — UP when RabbitMQ connection alive
- ✅ 6.3.3. Реализовать `ProcessEngineMetrics` — counters: instances.started/completed/errors, timer: task.duration (tagged by topic)

### 6.4. Тесты Phase 6

- ✅ 6.4.1. `ProcessEngineAutoConfigurationTest` — verify beans created with defaults
- ✅ 6.4.2. `EventStoreAutoConfigurationTest` — NoOpEventStore when persistence disabled
- ✅ 6.4.3. `ProcessEnginePropertiesTest` — property binding from application.yml
- ✅ 6.4.4. `ProcessEngineHealthIndicatorTest` — UP/DOWN states
- ✅ 6.4.5. Проверить: `./gradlew :spring-integration:test` проходит

---

## Phase 7: REST API

### 7.1. DTOs (records)

- ✅ 7.1.1. Создать `ProcessDefinitionDto` record
- ✅ 7.1.2. Создать `ProcessInstanceDto` record
- ✅ 7.1.3. Создать `StartProcessRequestDto` record (definitionKey, variables)
- ✅ 7.1.4. Создать `ValidationResultDto` record (valid, unsupportedElements, supportedElements)
- ✅ 7.1.5. Создать `UnsupportedElementDto` record (element, id, name, line)
- ✅ 7.1.6. Создать `ProcessEventDto` record
- ✅ 7.1.7. Создать `ActivityHistoryDto` record
- ✅ 7.1.8. Создать `IncidentDto` record
- ✅ 7.1.9. Создать `IncidentResolveDto` record (action: RETRY|SKIP|CANCEL)
- ✅ 7.1.10. Создать `ErrorResponseDto` record (error, message, timestamp, path)
- ✅ 7.1.11. Создать `PageDto<T>` generic wrapper

### 7.2. Mappers

- ✅ 7.2.1. Реализовать `ProcessDefinitionDtoMapper`
- ✅ 7.2.2. Реализовать `ProcessInstanceDtoMapper`
- ✅ 7.2.3. Реализовать `ProcessEventDtoMapper`

### 7.3. Exception Handling

- ✅ 7.3.1. Создать `ProcessNotFoundException`
- ✅ 7.3.2. Создать `DefinitionNotFoundException`
- ✅ 7.3.3. Создать `ValidationFailedException` (carries BpmnValidationResult)
- ✅ 7.3.4. Реализовать `GlobalExceptionHandler` (@ControllerAdvice) — стандартный ErrorResponseDto формат

### 7.4. Controllers

- ✅ 7.4.1. Реализовать `ProcessDefinitionController`:
  - POST `/api/v1/definitions` — multipart upload → parse → validate → deploy
  - POST `/api/v1/definitions/validate` — multipart → parse → validate (без deploy)
  - GET `/api/v1/definitions` — list all
  - GET `/api/v1/definitions/{key}` — get latest version
  - GET `/api/v1/definitions/{key}/versions` — list versions
  - DELETE `/api/v1/definitions/{key}` — undeploy
- ✅ 7.4.2. Реализовать `ProcessInstanceController`:
  - POST `/api/v1/instances` — start new instance
  - GET `/api/v1/instances` — list (paginated)
  - GET `/api/v1/instances/{id}` — get details
  - PUT `/api/v1/instances/{id}/suspend`
  - PUT `/api/v1/instances/{id}/resume`
  - DELETE `/api/v1/instances/{id}` — terminate
- ✅ 7.4.3. Реализовать `VariableController`:
  - GET `/api/v1/instances/{id}/variables`
  - PUT `/api/v1/instances/{id}/variables`
  - GET `/api/v1/instances/{id}/variables/{name}`
- ✅ 7.4.4. Реализовать `MessageController`:
  - POST `/api/v1/messages` — send message/signal
- ✅ 7.4.5. Реализовать `HistoryController`:
  - GET `/api/v1/history/instances` — query completed
  - GET `/api/v1/history/instances/{id}/events` — event log
  - GET `/api/v1/history/instances/{id}/activities` — activity history
- ✅ 7.4.6. Реализовать `IncidentController`:
  - GET `/api/v1/incidents` — list
  - GET `/api/v1/incidents/{id}` — details
  - PUT `/api/v1/incidents/{id}/resolve`

### 7.5. Configuration

- ✅ 7.5.1. Создать `WebConfig` — Jackson ObjectMapper config
- ✅ 7.5.2. Настроить `application.yml` — `spring.threads.virtual.enabled=true`, server port, process-engine properties

### 7.6. Тесты Phase 7

- ✅ 7.6.1. `ProcessDefinitionControllerTest` (MockMvc) — POST deploy valid BPMN → 201
- ✅ 7.6.2. `ProcessDefinitionControllerTest` (MockMvc) — POST deploy invalid BPMN → 400 с unsupported elements
- ✅ 7.6.3. `ProcessDefinitionControllerTest` (MockMvc) — POST validate → ValidationResultDto
- ✅ 7.6.4. `ProcessDefinitionControllerTest` (MockMvc) — GET list, GET by key, DELETE
- ✅ 7.6.5. `ProcessInstanceControllerTest` (MockMvc) — POST start → 201
- ✅ 7.6.6. `ProcessInstanceControllerTest` (MockMvc) — GET by id, PUT suspend/resume, DELETE terminate
- ✅ 7.6.7. `VariableControllerTest` (MockMvc) — GET/PUT variables
- ✅ 7.6.8. `MessageControllerTest` (MockMvc) — POST message → 202
- ✅ 7.6.9. `HistoryControllerTest` (MockMvc) — GET events/activities
- ✅ 7.6.10. `IncidentControllerTest` (MockMvc) — GET list, GET by id, PUT resolve
- ✅ 7.6.11. `GlobalExceptionHandlerTest` — verify error response format matches spec
- ✅ 7.6.12. `RestApiE2ETest` (Testcontainers) — deploy BPMN → start instance → complete tasks → verify completion via REST
- ✅ 7.6.13. Проверить: `./gradlew :rest-api:test` проходит

---

## Final Verification

- ✅ `./gradlew build` — все модули компилируются
- ✅ `./gradlew test` — все ~297 тестов проходят (201 core + 22 rabbitmq + 45 spring + 29 rest-api)
- ✅ Парсинг обоих референсных BPMN (order + payment) корректен
- ✅ E2E: deploy → start → complete → verify через REST API с реальным RabbitMQ
