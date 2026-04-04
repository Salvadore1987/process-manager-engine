# CALL_ACTIVITY_TODO

> Детальный план задач по спецификации `docs/call-activity.md`

---

## Этап 1 — Новые BPMN-элементы

### 1.1. Timer Intermediate Catch Event — Доменные модели

- ✅ **1.1.1.** Создать `TimerDefinition` record (`core/.../domain/model/TimerDefinition.java`)
  - Поля: `TimerType type`, `String value`
  - Enum `TimerType`: `DURATION`, `DATE`, `CYCLE`
  - Методы: `asDuration()`, `asDate()`, `asCycle()`

- ✅ **1.1.2.** Создать `CycleTimer` record (`core/.../domain/model/CycleTimer.java`)
  - Поля: `int repetitions` (-1 = бесконечно), `Duration interval`
  - Статический метод `parse(String value)` — парсинг ISO 8601 repeating interval (`R3/PT10H`, `R/PT5M`)
  - Метод `isInfinite()`

- ✅ **1.1.3.** Создать `TimerIntermediateCatchEvent` record (`core/.../domain/model/TimerIntermediateCatchEvent.java`)
  - Поля: `id`, `name`, `incomingFlows`, `outgoingFlows`, `TimerDefinition timerDefinition`
  - Implements `FlowNode`, возвращает `NodeType.TIMER_INTERMEDIATE_CATCH`

- ✅ **1.1.4.** Добавить `TIMER_INTERMEDIATE_CATCH` в `NodeType` enum (`core/.../domain/enums/NodeType.java`)

- ✅ **1.1.5.** Расширить `FlowNode` sealed interface (`core/.../domain/model/FlowNode.java`)
  - Добавить `TimerIntermediateCatchEvent` в `permits`
  - Добавить `@JsonSubTypes.Type(value = TimerIntermediateCatchEvent.class, name = "TIMER_INTERMEDIATE_CATCH")`

### 1.2. Timer Intermediate Catch Event — JAXB / Парсинг

- ✅ **1.2.1.** Создать `BpmnIntermediateCatchEvent` JAXB-класс (`core/.../parser/jaxb/BpmnIntermediateCatchEvent.java`)
  - Extends `BpmnFlowElement`
  - Поле: `BpmnTimerEventDefinition timerEventDefinition`
  - Методы: `isTimerEvent()`, `getTimerEventDefinition()`

- ✅ **1.2.2.** Доработать `BpmnTimerEventDefinition` (`core/.../parser/jaxb/BpmnTimerEventDefinition.java`)
  - Добавить поле `timeDate` (`BpmnTimeDuration`)
  - Добавить поле `timeCycle` (`BpmnTimeDuration`)
  - Добавить геттеры `getTimeDate()`, `getTimeCycle()`

- ✅ **1.2.3.** Зарегистрировать `BpmnIntermediateCatchEvent` в `BpmnProcess` (`core/.../parser/jaxb/BpmnProcess.java`)
  - Добавить `@XmlElement(name = "intermediateCatchEvent", ...)` в `@XmlElements` списка `flowElements`

- ✅ **1.2.4.** Обновить `BpmnValidator` (`core/.../parser/BpmnValidator.java`)
  - Добавить `"intermediateCatchEvent"`, `"timeDate"`, `"timeCycle"` в `SUPPORTED_ELEMENTS`
  - Убрать `"intermediateCatchEvent"` из `FLOW_NODE_ELEMENTS` (больше не unsupported)

- ✅ **1.2.5.** Добавить маппинг в `FlowNodeMapper` (`core/.../parser/mapper/FlowNodeMapper.java`)
  - Добавить `case BpmnIntermediateCatchEvent` в `map()` switch
  - Реализовать `mapIntermediateCatchEvent()` — проверка `isTimerEvent()`, вызов `mapTimerDefinition()`
  - Реализовать `mapTimerDefinition(BpmnTimerEventDefinition)` — определение типа (duration/date/cycle), создание `TimerDefinition`

### 1.3. Timer Intermediate Catch Event — Обработчик и движок

- ✅ **1.3.1.** Создать `TimerIntermediateCatchEventHandler` (`core/.../engine/handler/TimerIntermediateCatchEventHandler.java`)
  - Implements `NodeHandler`
  - Зависимости: `TimerService`, `SequenceGenerator`
  - Логика: вычислить `Duration` из `TimerDefinition`, вызвать `timerService.schedule()`, вернуть `TimerScheduledEvent`
  - Для `DATE` — `Duration.between(now, date)`, если отрицательный → `Duration.ZERO`
  - Для `CYCLE` — использовать `interval()` первого цикла

- ✅ **1.3.2.** Зарегистрировать обработчик в `TokenExecutor`
  - Добавить `Map.entry(NodeType.TIMER_INTERMEDIATE_CATCH, new TimerIntermediateCatchEventHandler(...))`

- ✅ **1.3.3.** Обновить `ProcessEngine.advanceActiveTokens()` (`core/.../engine/ProcessEngine.java`)
  - Добавить `NodeType.TIMER_INTERMEDIATE_CATCH` в условие `if (node.type() == NodeType.SERVICE_TASK || node.type() == NodeType.CALL_ACTIVITY || ...)`
  - Таймер должен переходить в `WAITING` аналогично `SERVICE_TASK`

- ✅ **1.3.4.** Реализовать обработку callback от `TimerService` для intermediate catch event
  - Метод `ProcessEngine.completeTimer(UUID processInstanceId, UUID tokenId, String nodeId)`
  - Найти `ProcessInstance` по `processInstanceId`
  - Найти `WAITING` токен на данном узле
  - Переместить токен на следующий узел по исходящему потоку (`TokenMovedEvent`)
  - Продолжить `advanceActiveTokens`

### 1.4. Timer Intermediate Catch Event — Рефакторинг (опционально)

- ✅ **1.4.1.** Перевести `TimerBoundaryEvent` на использование `TimerDefinition` вместо `Duration duration`
  - Обновить record `TimerBoundaryEvent`
  - Обновить `TimerBoundaryEventHandler` — использовать `TimerDefinition`
  - Обновить `FlowNodeMapper.mapBoundaryEvent()` — создавать `TimerDefinition`
  - Обновить тесты `TimerBoundaryEventHandler`, `FlowNodeTest`, `BpmnParserTest`

### 1.5. Timer Intermediate Catch Event — Тесты

- ✅ **1.5.1.** Unit-тесты `TimerDefinition`
  - `asDuration()` — парсинг `PT5S`, `PT10M`, `P1D`
  - `asDate()` — парсинг ISO 8601 date-time
  - `asDate()` — дата в прошлом
  - `asCycle()` — делегация в `CycleTimer.parse()`
  - Исключения при вызове неправильного метода (например `asDuration()` на `DATE`)

- ✅ **1.5.2.** Unit-тесты `CycleTimer`
  - `parse("R3/PT10H")` → `repetitions=3, interval=10h`
  - `parse("R/PT5M")` → `repetitions=-1, interval=5m` (infinite)
  - `isInfinite()` — true/false

- ✅ **1.5.3.** Unit-тесты `BpmnValidator`
  - BPMN с `intermediateCatchEvent` — валидация проходит (больше не unsupported)

- ✅ **1.5.4.** Unit-тесты парсинга (`BpmnParser` / `FlowNodeMapper`)
  - Парсинг BPMN с `intermediateCatchEvent` + `timeDuration` → `TimerIntermediateCatchEvent`
  - Парсинг BPMN с `timeDate` → корректный `TimerDefinition.DATE`
  - Парсинг BPMN с `timeCycle` → корректный `TimerDefinition.CYCLE`
  - Парсинг `charge-payment-subprocess.bpmn` — timer + gateway default

- ✅ **1.5.5.** Unit-тесты `TimerIntermediateCatchEventHandler`
  - Планирование таймера — `timerService.schedule()` вызывается с правильным `Duration`
  - Возвращает `TimerScheduledEvent`
  - Для `DATE` в прошлом — `Duration.ZERO`
  - Для `CYCLE` — используется `interval()` первого цикла

- ✅ **1.5.6.** Unit-тест `ProcessEngine` — токен на intermediate catch event переходит в `WAITING`
  - Реализован в `ChargePaymentSubprocessE2ETest`

---

### 1.6. Exclusive Gateway — Атрибут `default`

- ✅ **1.6.1.** Добавить атрибут `default` в `BpmnExclusiveGateway` (`core/.../parser/jaxb/BpmnExclusiveGateway.java`)
  - `@XmlAttribute(name = "default") private String defaultFlow`
  - Геттер `getDefaultFlow()`

- ✅ **1.6.2.** Добавить поле `defaultFlowId` в `ExclusiveGateway` record (`core/.../domain/model/ExclusiveGateway.java`)
  - Новое поле: `String defaultFlowId` (nullable)

- ✅ **1.6.3.** Обновить `FlowNodeMapper.mapExclusiveGateway()` (`core/.../parser/mapper/FlowNodeMapper.java`)
  - Передавать `element.getDefaultFlow()` в конструктор `ExclusiveGateway`

- ✅ **1.6.4.** Обновить `ExclusiveGatewayHandler` (`core/.../engine/handler/ExclusiveGatewayHandler.java`)
  - Привести `FlowNode` к `ExclusiveGateway` для доступа к `defaultFlowId()`
  - Определять default flow по `defaultFlowId` (explicit) с fallback на flow без условия (implicit)
  - Игнорировать условия на default flow (по спецификации BPMN)

- ✅ **1.6.5.** Обновить все места создания `ExclusiveGateway` в тестах — добавить параметр `defaultFlowId`

### 1.7. Exclusive Gateway — Boolean expressions в `SimpleConditionEvaluator`

- ✅ **1.7.1.** Добавить `BOOLEAN_PATTERN` в `SimpleConditionEvaluator` (`core/.../engine/condition/SimpleConditionEvaluator.java`)
  - Паттерн: `^\$\{\s*(!?)\s*(\w+)\s*\}$`
  - Поддержка: `${isPaymentSuccess}`, `${!isPaymentSuccess}`

- ✅ **1.7.2.** Обновить метод `evaluate()` в `SimpleConditionEvaluator`
  - Сначала пробовать comparison pattern (существующий)
  - Затем пробовать boolean pattern (новый)
  - Boolean: извлечь имя переменной, проверить что значение `Boolean`, применить negation если есть `!`
  - Исключение если переменная не найдена или не boolean

### 1.8. Exclusive Gateway — Тесты

- ✅ **1.8.1.** Unit-тесты `SimpleConditionEvaluator` — boolean expressions
  - `${isPaymentSuccess}` с `true` → `true`
  - `${isPaymentSuccess}` с `false` → `false`
  - `${!isPaymentSuccess}` с `true` → `false`
  - `${!isPaymentSuccess}` с `false` → `true`
  - `${nonExistent}` → исключение "variable not found"
  - `${stringVar}` → исключение "not boolean"
  - Регрессия: существующие comparison expressions (`${x > 5}`, `${y == 'test'}`) работают

- ✅ **1.8.2.** Unit-тесты `ExclusiveGatewayHandler` — explicit default flow
  - Маршрутизация по `defaultFlowId` когда ни одно условие не совпало
  - Backward compatibility: implicit default (flow без условия, `defaultFlowId = null`)
  - Условие на default flow игнорируется
  - Приоритет condition match над default flow

- ✅ **1.8.3.** Unit-тест JAXB — парсинг атрибута `default` на `exclusiveGateway`
  - Реализован в `BpmnParserTest.shouldParseExclusiveGatewayWithDefaultAttribute()`

### 1.9. E2E-тесты Этапа 1

- ✅ **1.9.1.** E2E: парсинг и деплой `charge-payment-subprocess.bpmn` из `docs/processes/`
  - Процесс содержит `intermediateCatchEvent`, `exclusiveGateway` с `default`, boolean expressions
  - Деплой должен пройти без ошибок

- ✅ **1.9.2.** E2E: `charge-payment-subprocess` — прямой путь
  - charge → gateway(`isPaymentSuccess=true`) → end
  - Процесс завершается с `COMPLETED`

- ✅ **1.9.3.** E2E: `charge-payment-subprocess` — проверка статуса
  - charge(`isPaymentSuccess=false`) → gateway → check-status(`isPaymentSuccess=true`) → gateway → end

- ✅ **1.9.4.** E2E: `charge-payment-subprocess` — retry loop с таймером
  - charge → gateway → check-status → gateway → timer(PT5S) → charge (loop)
  - Проверить: таймер запланирован, после callback — токен возвращается на charge-payment

---

## Этап 2 — Call Activity

### 2.1. Исключения и модели

- [ ] **2.1.1.** Создать `CallActivitySubprocessNotFoundException` (`core/.../domain/exception/CallActivitySubprocessNotFoundException.java`)
  - Поля: `calledElement`, `expectedFileName`, `parentProcessKey`
  - Сообщение: `Cannot deploy process '{parentProcessKey}': Call Activity references subprocess '{calledElement}' but file '{expectedFileName}' was not found in the deployment bundle`

- [ ] **2.1.2.** Создать `DeploymentBundle` (`core/.../domain/model/DeploymentBundle.java`)
  - Поле: `Map<String, String> bpmnFiles` (fileName → bpmnXml)
  - Методы: `getMainProcess()`, `getSubprocesses()`, `containsFile(String fileName)`

### 2.2. Валидация

- [ ] **2.2.1.** Создать `CallActivityValidator` (`core/.../engine/CallActivityValidator.java`)
  - Метод `extractCalledElements(ProcessDefinition)` — извлечь все `calledElement` из `CallActivity` узлов
  - Метод `validate(ProcessDefinition, DeploymentBundle)` — проверить наличие `{calledElement}.bpmn` в бандле
  - Рекурсивная валидация вложенных подпроцессов
  - Детекция циклических зависимостей (A→B→A) — выбрасывать исключение

### 2.3. Порт `ChildInstanceMapping`

- [ ] **2.3.1.** Создать интерфейс `ChildInstanceMapping` (`core/.../port/outgoing/ChildInstanceMapping.java`)
  - `put(UUID childInstanceId, UUID parentInstanceId)`
  - `UUID getParent(UUID childInstanceId)`
  - `List<UUID> getChildren(UUID parentInstanceId)`
  - `void remove(UUID childInstanceId)`

- [ ] **2.3.2.** Создать `InMemoryChildInstanceMapping` (`core/.../adapter/inmemory/InMemoryChildInstanceMapping.java`)

### 2.4. Движок — Деплой

- [ ] **2.4.1.** Добавить метод `deployBundle(DeploymentBundle)` в `ProcessEngine`
  - Парсить все BPMN-файлы из бандла через `BpmnParser`
  - Валидировать наличие подпроцессов через `CallActivityValidator`
  - Атомарный деплой всех `ProcessDefinition` в `ProcessDefinitionStore`
  - При ошибке деплоя любого определения — откат (undeploy) всех уже задеплоенных
  - Уведомить `DeploymentListener`-ы для каждого определения

- [ ] **2.4.2.** Обновить `ProcessEngine.deploy()` — проверка наличия `CallActivity`
  - Если BPMN содержит `CallActivity` элементы — выбросить ошибку с указанием использовать `deployBundle()`
  - Если не содержит — деплой как раньше

### 2.5. Движок — Автозапуск дочернего процесса

- [ ] **2.5.1.** Доработать `ProcessEngine.advanceActiveTokens()` — автозапуск дочернего при `CallActivity`
  - После `CallActivityStartedEvent` извлечь `calledElement` и `childProcessInstanceId`
  - Найти `ProcessDefinition` по ключу `calledElement` в `ProcessDefinitionStore`
  - Запустить дочерний процесс: создать `ProcessStartedEvent` с `parentProcessInstanceId`, создать токен, выполнить `StartEvent`
  - Сохранить маппинг в `ChildInstanceMapping`
  - Сохранить маппинг в `InstanceDefinitionMapping`
  - Передать переменные из родительского контекста

### 2.6. Движок — Автозавершение родителя

- [ ] **2.6.1.** Доработать обработку `ProcessCompletedEvent` — автозавершение родителя
  - После `ProcessCompletedEvent` проверить `parentProcessInstanceId`
  - Если `parentProcessInstanceId != null` — автоматически вызвать `completeCallActivity(childInstanceId)`
  - Родительский токен на `CallActivity` переходит из `WAITING` → `ACTIVE`
  - Продолжить `advanceActiveTokens` родительского процесса

### 2.7. Движок — Проброс ошибок

- [ ] **2.7.1.** Реализовать проброс ошибок из дочернего в родительский процесс
  - При `ProcessErrorEvent` в дочернем процессе — найти родительский через `ChildInstanceMapping`
  - Если на `CallActivity` есть `ErrorBoundaryEvent` — маршрутизировать через error boundary (аналогично `failTask`)
  - Если `ErrorBoundaryEvent` нет — вызвать ошибку родительского процесса + компенсацию

### 2.8. REST API

- [ ] **2.8.1.** Создать эндпоинт `POST /api/v1/definitions/bundle` (`rest-api/.../controller/ProcessDefinitionController.java`)
  - Принимает `MultipartFile[] files`
  - Создаёт `DeploymentBundle` из файлов
  - Вызывает `processEngine.deployBundle(bundle)`
  - Возвращает список `ProcessDefinitionDto` с HTTP 201

- [ ] **2.8.2.** Обновить `POST /api/v1/definitions` — проверка на `CallActivity`
  - Если BPMN содержит `CallActivity` — вернуть HTTP 400 с указанием использовать `/bundle`

### 2.9. Redis-персистенция

- [ ] **2.9.1.** Создать `RedisChildInstanceMapping` (`redis-persistence/.../RedisChildInstanceMapping.java`)
  - Хранение `child→parent` и `parent→[children]` в Redis
  - Реализовать все методы интерфейса `ChildInstanceMapping`

### 2.10. Spring-интеграция

- [ ] **2.10.1.** Зарегистрировать `ChildInstanceMapping` bean в `ProcessEngineAutoConfiguration`
  - `InMemoryChildInstanceMapping` — fallback
  - `RedisChildInstanceMapping` — если Redis доступен

- [ ] **2.10.2.** Обновить конструктор `ProcessEngine` — принять `ChildInstanceMapping`

### 2.11. Unit-тесты Этапа 2

- [ ] **2.11.1.** Тесты `CallActivityValidator`
  - Все подпроцессы найдены в бандле → валидация успешна
  - Подпроцесс отсутствует → `CallActivitySubprocessNotFoundException`
  - Вложенные подпроцессы (A→B→C) — рекурсивная валидация
  - Циклические зависимости (A→B→A) — исключение

- [ ] **2.11.2.** Тесты `DeploymentBundle`
  - `containsFile()` — true/false
  - `getMainProcess()` / `getSubprocesses()`

- [ ] **2.11.3.** Тесты `ProcessEngine.deployBundle()`
  - Успешный деплой — все определения в `ProcessDefinitionStore`
  - Откат при ошибке — ни одно определение не задеплоено

- [ ] **2.11.4.** Тест `ProcessEngine.deploy()` с `CallActivity` — ошибка

- [ ] **2.11.5.** Тесты `InMemoryChildInstanceMapping`
  - `put` / `getParent` / `getChildren` / `remove`

### 2.12. E2E-тесты Этапа 2

- [ ] **2.12.1.** E2E: полный цикл с реальными BPMN из `docs/processes/`
  - Деплой бандла: `order-process.bpmn` + `charge-payment-subprocess.bpmn`
  - Старт `order-process` → validate-order → parallel(book + notify) → charge-payment (CallActivity)
  - Автозапуск `charge-payment-subprocess` → charge → gateway(success) → end
  - Автозавершение родителя → deliver-order → end
  - Проверить: оба процесса в `COMPLETED`, parent-child связь корректна

- [ ] **2.12.2.** E2E: ошибка в дочернем с ErrorBoundaryEvent
  - Дочерний процесс завершается ошибкой
  - Родительский маршрутизирует через error boundary

- [ ] **2.12.3.** E2E: ошибка в дочернем без ErrorBoundaryEvent
  - Дочерний процесс завершается ошибкой
  - Родительский запускает компенсацию (refund-payment)

- [ ] **2.12.4.** E2E: вложенные Call Activity (A → B → C)
  - Три уровня процессов
  - Корректная цепочка автозавершения C → B → A

- [ ] **2.12.5.** E2E: retry loop в подпроцессе
  - charge → gateway(false) → check-status → gateway(false) → timer → charge → gateway(true) → end
  - Проверить: таймер, цикл, итоговое завершение

- [ ] **2.12.6.** E2E: деплой бандла с отсутствующим подпроцессом → ошибка
