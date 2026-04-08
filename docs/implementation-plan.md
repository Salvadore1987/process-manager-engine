# Process Manager Engine — План реализации

## Context

Java 21 движок бизнес-процессов на основе BPMN 2.0 с event-sourced архитектурой и RabbitMQ транспортом. Проект состоит из 7 модулей: `core`, `rabbitmq-transport`, `redis-persistence`, `spring-integration`, `rest-api`, `security`, `worker-spring-boot-starter`. Спецификация в `docs/process-manager-engine.md`.

---

## Phase 0: Gradle Multi-Module Skeleton

**Цель:** 4-модульная структура проекта с правильными зависимостями, Java 21 toolchain.

**Что сделать:**
- `settings.gradle.kts` — добавить `include("core", "rabbitmq-transport", "spring-integration", "rest-api")`
- Корневой `build.gradle.kts` — общие настройки: Java 21 toolchain, mavenCentral, общие test-зависимости (JUnit 5, AssertJ), version catalog для shared версий
- `core/build.gradle.kts` — `java-library`: Jackson, JAXB (jakarta.xml.bind-api + glassfish jaxb-runtime), SLF4J 2.x, Micrometer core
- `rabbitmq-transport/build.gradle.kts` — `java-library`: `project(":core")`, `com.rabbitmq:amqp-client`, test: Testcontainers RabbitMQ
- `spring-integration/build.gradle.kts` — `java-library`: `project(":core")`, `project(":rabbitmq-transport")`, Spring Boot autoconfigure, actuator, Micrometer
- `rest-api/build.gradle.kts` — application: `project(":spring-integration")`, spring-boot-starter-web, spring-boot-starter-test

**Проверка:** `./gradlew build` без ошибок, корректный граф зависимостей

---

## Phase 1: Core Domain Model

**Цель:** Все domain-сущности, value objects, enums, port-интерфейсы. Чистый Java без инфраструктуры.

**Пакеты:**
```
uz.salvadore.processengine.core
  .domain.model    — ProcessDefinition, ProcessInstance, Token, FlowNode (sealed),
                     StartEvent, EndEvent, ServiceTask, ExclusiveGateway, ParallelGateway,
                     CallActivity, CompensationBoundaryEvent, TimerBoundaryEvent,
                     ErrorBoundaryEvent, SequenceFlow, ConditionExpression
  .domain.event    — ProcessEvent (sealed), ProcessStartedEvent, TokenMovedEvent,
                     TaskCompletedEvent, ProcessSuspended/ResumedEvent,
                     ProcessCompleted/ErrorEvent, TimerScheduled/FiredEvent,
                     CompensationTriggeredEvent, CallActivityStarted/CompletedEvent
  .domain.enums    — ProcessState, TokenState, NodeType
  .port.outgoing   — ProcessEventStore, MessageTransport, TimerService, DeploymentListener
  .port.incoming   — ActivityHandler
  .util            — UUIDv7
```

**Ключевые решения:**
- `FlowNode` — sealed interface, 9 concrete типов
- `ProcessEvent` — sealed interface, каждый тип как record
- ProcessDefinition и ProcessInstance — aggregate roots (DDD), factory methods
- UUIDv7 — утилита на основе `Instant.now()` + `SecureRandom`
- Все объекты иммутабельные, final поля, без сеттеров

**Тесты (~15):** создание сущностей, state transitions токенов, валидация ProcessDefinition, UUIDv7 ordering

**Проверка:** `./gradlew :core:test`, zero инфраструктурных зависимостей в domain

---

## Phase 2: BPMN Parser и Validator

**Цель:** Парсинг Camunda Modeler BPMN XML в domain-модель. Валидация whitelist элементов.

**Пакеты:**
```
uz.salvadore.processengine.core
  .parser          — BpmnParser, BpmnValidator, BpmnValidationResult,
                     UnsupportedElementError, BpmnParseException
  .parser.jaxb     — JAXB-аннотированные классы (hand-crafted для поддерживаемого
                     subset BPMN 2.0, НЕ XJC-генерация из полного XSD)
  .parser.mapper   — FlowNodeMapper, SequenceFlowMapper, ProcessDefinitionMapper
```

**Ключевые решения:**
- Hand-crafted JAXB для subset BPMN 2.0 (полный XSD генерирует тысячи классов)
- `camunda:topic` и `camunda:type="external"` через `@XmlAttribute(namespace = "...")`
- Line number tracking: StAX + element-to-line mapping перед JAXB unmarshal
- BpmnValidator работает на JAXB-модели до маппинга в domain

**Тесты (~12):** парсинг example-order-process.bpmn и example-payment-process.bpmn, валидация с неподдерживаемыми элементами, маппинг каждого типа, roundtrip-тест структуры графа

**Ресурсы:** копировать BPMN-файлы в `core/src/test/resources/bpmn/`, создать `invalid-unsupported-elements.bpmn`

**Проверка:** `./gradlew :core:test`, корректный парсинг обоих референсных BPMN

---

## Phase 3: Event Sourcing и State Reconstruction

**Цель:** Генерация событий, application (replay), InMemory event store.

**Пакеты:**
```
uz.salvadore.processengine.core
  .engine.eventsourcing — EventApplier, EventSequencer, ProcessInstanceProjection
  .adapter.inmemory    — InMemoryEventStore (ConcurrentHashMap, fallback при отсутствии Redis)
```

**Ключевые решения:**
- EventApplier — pattern matching (Java 21 switch с sealed interface) для каждого типа события
- ProcessInstanceProjection — fold событий через EventApplier → иммутабельный ProcessInstance
- InMemoryEventStore — `ConcurrentHashMap<UUID, List<ProcessEvent>>`, thread-safe

**Тесты (~10):** apply каждого типа события, полный replay линейного процесса, InMemoryEventStore CRUD + concurrent access

**Проверка:** `./gradlew :core:test`, reconstruction из событий даёт идентичный state

---

## Phase 4: Token Execution Engine

**Цель:** Ядро — продвижение токенов по BPMN-графу, генерация событий. Сердце системы.

**Пакеты:**
```
uz.salvadore.processengine.core
  .engine            — ProcessEngine (главный фасад), TokenExecutor,
                       ProcessDefinitionRepository
  .engine.handler    — NodeHandler (interface), StartEventHandler, EndEventHandler,
                       ServiceTaskHandler, ExclusiveGatewayHandler,
                       ParallelGatewayHandler, CallActivityHandler,
                       TimerBoundaryEventHandler, ErrorBoundaryEventHandler,
                       CompensationBoundaryEventHandler
  .engine.condition  — ConditionEvaluator, SimpleConditionEvaluator
  .engine.context    — ExecutionContext
```

**Ключевые решения:**
- ProcessEngine — constructor injection: `(ProcessEventStore, MessageTransport, TimerService, ProcessDefinitionRepository)`
- NodeHandler — strategy interface, dispatch через `Map<NodeType, NodeHandler>`
- ParallelGateway: fork создаёт N токенов, join ждёт все входящие
- ServiceTask: токен → WAITING, `MessageTransport.send()`, не блокирует
- CallActivity: создаёт child ProcessInstance, parent токен → WAITING
- SimpleConditionEvaluator: базовый `${variable > value}` для ExclusiveGateway (без JUEL/SpEL)

**Тесты (~22):** handler для каждого из 9 типов, TokenExecutor dispatch, ProcessEngine lifecycle, **E2E in-memory тест** с example-order-process.bpmn (оба пути ExclusiveGateway), event replay совпадение

**Проверка:** `./gradlew :core:test`, полный процесс (Start → ServiceTask → ExclusiveGateway → ParallelGateway fork → 2 ветки → join → End) выполняется с mock transport

---

## Phase 5: RabbitMQ Transport Adapter

**Цель:** Реализация `MessageTransport` и `TimerService` поверх RabbitMQ.

**Пакеты:**
```
uz.salvadore.processengine.rabbitmq
  RabbitMqMessageTransport.java
  RabbitMqTimerService.java
  RabbitMqConnectionManager.java
  RabbitMqTopologyInitializer.java
  RetryPolicy.java
  .config       — RabbitMqTransportConfig
  .correlation  — CorrelationIdResolver
  .consumer     — TaskResultConsumer, DeadLetterConsumer
```

**Ключевые решения:**
- Один `Connection` (thread-safe), channels pooled (не thread-safe)
- Topology: 4 exchanges (tasks, retry, dlq, timers) + shared queues (task.execute, task.result, task.retry) with x-task-topic header routing
- Retry: exponential backoff через TTL + dead-letter routing или x-delayed-message
- Timer: `x-delay` header на timers exchange
- Virtual threads: `Executors.newVirtualThreadPerTaskExecutor()` для consumer handlers

**Тесты (~8, integration с Testcontainers):** send/consume + correlation, timer scheduling (1s delay), retry flow с backoff, DLQ при exhausted retries, connection recovery

**Проверка:** `./gradlew :rabbitmq-transport:test` (требует Docker)

---

## Phase 6: Spring Boot Integration (Starter)

**Цель:** Auto-configuration, properties, health checks, metrics.

**Пакеты:**
```
uz.salvadore.processengine.spring
  .autoconfigure — ProcessEngineAutoConfiguration, ProcessEngineProperties,
                   RabbitMqTransportAutoConfiguration, EventStoreAutoConfiguration,
                   MetricsAutoConfiguration
  .health        — ProcessEngineHealthIndicator, RabbitMqTransportHealthIndicator
  .metrics       — ProcessEngineMetrics
```

**Ключевые решения:**
- `@ConfigurationProperties("process-engine")` — persistence.enabled, rabbitmq.*, retry.*
- `@ConditionalOnMissingBean` → Redis или InMemory fallback
- Micrometer: counters instances.started/completed/errors, timer task.duration по topic
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Тесты (~6):** auto-configuration с default properties, conditional Redis/InMemory EventStore, health indicators, property binding

**Проверка:** `./gradlew :spring-integration:test`, Spring context загружается с корректными beans

---

## Phase 7: REST API

**Цель:** Полный Camunda-like REST API на Spring MVC.

**Пакеты:**
```
uz.salvadore.processengine.rest
  .controller  — ProcessDefinitionController, ProcessInstanceController,
                 VariableController, MessageController, HistoryController,
                 IncidentController
  .dto         — ProcessDefinitionDto, ProcessInstanceDto, StartProcessRequestDto,
                 ValidationResultDto, UnsupportedElementDto, ProcessEventDto,
                 ActivityHistoryDto, IncidentDto, IncidentResolveDto, PageDto,
                 ErrorResponseDto (все как records)
  .mapper      — ProcessDefinitionDtoMapper, ProcessInstanceDtoMapper, ProcessEventDtoMapper
  .exception   — GlobalExceptionHandler (@ControllerAdvice), ProcessNotFoundException,
                 DefinitionNotFoundException, ValidationFailedException
  .config      — WebConfig
```

**Ключевые решения:**
- DTOs как records
- `spring.threads.virtual.enabled=true`
- POST `/api/v1/definitions` — multipart BPMN → parse → validate → deploy
- POST `/api/v1/definitions/validate` — parse → validate без deploy
- `GlobalExceptionHandler` → стандартный формат ошибок из спецификации
- Pagination через Spring `Pageable`

**Тесты (~16):** MockMvc для каждого controller, error format, **E2E тест с Testcontainers** (deploy → start → complete → verify через REST)

**Проверка:** `./gradlew :rest-api:test`, все эндпоинты возвращают корректные status codes и body

---

## Phase 8: Security (OAuth2/Keycloak)

**Цель:** Авторизация REST API через JWT-токены Keycloak.

**Пакеты:**
```
uz.salvadore.processengine.security
  .config  — ResourceServerConfig
  .converter — KeycloakJwtAuthenticationConverter
  .model   — ProcessEngineRole
```

**Ключевые решения:**
- 4 роли: `process-admin`, `process-operator`, `process-viewer`, `process-deployer`
- Endpoint-access matrix через `HttpSecurity` authorizeHttpRequests
- JWT конвертер извлекает роли из Keycloak realm_access/resource_access claims
- Условное включение: `process-engine.security.enabled=true`

**Тесты:** SecurityConfig тесты с mock JWT, endpoint access matrix

**Проверка:** `./gradlew :security:test`

---

## Phase 9: Worker Spring Boot Starter

**Цель:** Клиентский Spring Boot Starter для внешних worker-сервисов.

**Пакеты:**
```
uz.salvadore.processengine.worker
  .annotation      — @JobWorker
  ExternalTaskHandler.java — интерфейс с execute(TaskContext)
  TaskContext.java — переменные + complete()/error()
  TaskExecutionException.java
  .registry        — TaskHandlerRegistry, TaskHandlerBeanPostProcessor
  .listener        — TaskListenerContainer (SmartLifecycle)
  .autoconfigure   — WorkerAutoConfiguration, WorkerProperties, WorkerHealthIndicator
```

**Ключевые решения:**
- `ExternalTaskHandler` + `@JobWorker(topic)` на методе `execute` — явный контракт
- `TaskContext.complete(Map)` / `TaskContext.error(code, message)` — ручное управление ответом
- `TaskListenerContainer` — SmartLifecycle, passive queue declare (не конфликтует с engine DLX args)
- Модуль не зависит от `core` и `rabbitmq-transport` — самостоятельная библиотека
- `BeanPostProcessor` сканирует `ExternalTaskHandler` бины с `@JobWorker`

**Тесты:** интеграция с Testcontainers RabbitMQ, handler dispatch, error handling

**Проверка:** `./gradlew :worker-spring-boot-starter:test`

---

## Граф зависимостей между фазами

```
Phase 0 (Gradle skeleton)
   │
Phase 1 (Domain model)
   │
   ├──→ Phase 2 (BPMN parser)      ← можно параллельно с Phase 3
   │       │
   ├──→ Phase 3 (Event sourcing)   ← можно параллельно с Phase 2
   │       │
   │       ▼
   └──→ Phase 4 (Token engine)     ← зависит от 1, 2, 3
          │
          ├──→ Phase 5 (RabbitMQ)  ← зависит от портов Phase 1
          │
          ▼
       Phase 6 (Spring integration) ← зависит от 4, 5
          │
          ├──→ Phase 7 (REST API)           ← зависит от 6
          │
          ├──→ Phase 8 (Security)           ← зависит от 7
          │
          └──→ Phase 9 (Worker starter)     ← независим, параллельно с 7-8
```

## Оценка объёма

| Phase | Модуль | Тесты (ест.) |
|-------|--------|---------------|
| 0 | all | 0 (build only) |
| 1 | core | ~15 |
| 2 | core | ~12 |
| 3 | core | ~10 |
| 4 | core | ~22 |
| 5 | rabbitmq-transport | ~8 |
| 6 | spring-integration | ~6 |
| 7 | rest-api | ~16 |
| 8 | security | ~10 |
| 9 | worker-spring-boot-starter | ~8 |
| **Итого** | | **~107 тестов** |
