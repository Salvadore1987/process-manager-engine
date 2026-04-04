# Спецификация доработки — Этап 1 & Этап 2

Разработка проходит в **два этапа**:
- **Этап 1:** Новые BPMN-элементы (Timer Intermediate Catch Event, доработка Exclusive Gateway)
- **Этап 2:** Полноценная поддержка Call Activity

---

# Этап 1 — Новые BPMN-элементы

## 1. Timer Intermediate Catch Event

> Ссылка: https://docs.camunda.org/manual/7.24/reference/bpmn20/events/timer-events/

### 1.1. Обзор

Timer Intermediate Catch Event — промежуточное событие-таймер, которое приостанавливает выполнение процесса до срабатывания таймера. После срабатывания выполнение продолжается по исходящему потоку.

**Текущее состояние:** Не поддерживается. `BpmnValidator` помечает `intermediateCatchEvent` как неподдерживаемый элемент (строка 59). Отсутствуют модель, JAXB-маппинг, обработчик, `NodeType`.

### 1.2. Пример из `charge-payment-subprocess.bpmn`

В существующем подпроцессе `docs/processes/charge-payment-subprocess.bpmn` уже используется Timer Intermediate Catch Event для повторной попытки оплаты:

```xml
<bpmn:intermediateCatchEvent id="charge-wait">
  <bpmn:incoming>Flow_0a3qg4e</bpmn:incoming>
  <bpmn:outgoing>Flow_0wxby26</bpmn:outgoing>
  <bpmn:timerEventDefinition id="TimerEventDefinition_1j0k0dc">
    <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT5S</bpmn:timeDuration>
  </bpmn:timerEventDefinition>
</bpmn:intermediateCatchEvent>
```

Логика: если оплата не прошла и статус неуспешен — ожидание 5 секунд, затем повторная попытка `charge-payment`.

### 1.3. Поддерживаемые типы таймера

Необходимо поддержать **три типа** определения таймера (ISO 8601):

| Тип | XML-элемент | Формат | Пример | Поведение |
|-----|-------------|--------|--------|-----------|
| **Duration** | `<bpmn:timeDuration>` | ISO 8601 duration | `PT5S`, `PT10M`, `P1D` | Пауза на указанный интервал |
| **Date** | `<bpmn:timeDate>` | ISO 8601 date-time | `2026-04-10T12:00:00Z` | Ожидание до абсолютной даты/времени |
| **Cycle** | `<bpmn:timeCycle>` | ISO 8601 repeating | `R3/PT10H`, `R/PT5M` | Повторяющийся интервал |

#### 1.3.1. Duration (`timeDuration`)

```xml
<bpmn:timerEventDefinition>
  <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT5S</bpmn:timeDuration>
</bpmn:timerEventDefinition>
```

- Парсится через `java.time.Duration.parse()`
- Таймер планируется на `now() + duration`
- После срабатывания — токен переходит по исходящему потоку

#### 1.3.2. Date (`timeDate`)

```xml
<bpmn:timerEventDefinition>
  <bpmn:timeDate xsi:type="bpmn:tFormalExpression">2026-04-10T12:00:00Z</bpmn:timeDate>
</bpmn:timerEventDefinition>
```

- Парсится через `java.time.Instant.parse()` или `java.time.ZonedDateTime.parse()`
- Если дата в прошлом — таймер срабатывает немедленно
- Время без таймзоны интерпретируется как UTC

#### 1.3.3. Cycle (`timeCycle`)

```xml
<bpmn:timerEventDefinition>
  <bpmn:timeCycle xsi:type="bpmn:tFormalExpression">R3/PT10H</bpmn:timeCycle>
</bpmn:timerEventDefinition>
```

- Формат: `R[count]/duration` (ISO 8601 repeating interval)
- `R3/PT10H` — повторить 3 раза каждые 10 часов
- `R/PT5M` — повторять бесконечно каждые 5 минут
- После каждого срабатывания — токен активируется, выполняется переход, и таймер перепланируется (если остались итерации)

### 1.4. Изменения в компонентах

#### 1.4.1. `NodeType` — добавить значение

```java
// Файл: core/.../domain/enums/NodeType.java
TIMER_INTERMEDIATE_CATCH  // новое значение
```

#### 1.4.2. Новая модель: `TimerIntermediateCatchEvent`

```java
// Файл: core/.../domain/model/TimerIntermediateCatchEvent.java
public record TimerIntermediateCatchEvent(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows,
        TimerDefinition timerDefinition
) implements FlowNode {

    @Override
    public NodeType type() {
        return NodeType.TIMER_INTERMEDIATE_CATCH;
    }
}
```

#### 1.4.3. Новая модель: `TimerDefinition`

Унифицированная модель для всех типов таймеров (используется и в `TimerBoundaryEvent`, и в `TimerIntermediateCatchEvent`):

```java
// Файл: core/.../domain/model/TimerDefinition.java
public record TimerDefinition(
        TimerType type,
        String value   // raw ISO 8601 строка
) {

    public enum TimerType {
        DURATION,  // PT5S, P1D
        DATE,      // 2026-04-10T12:00:00Z
        CYCLE      // R3/PT10H
    }

    public Duration asDuration() {
        if (type != TimerType.DURATION) throw new IllegalStateException("Not a duration timer");
        return Duration.parse(value);
    }

    public Instant asDate() {
        if (type != TimerType.DATE) throw new IllegalStateException("Not a date timer");
        return Instant.parse(value);
    }

    public CycleTimer asCycle() {
        if (type != TimerType.CYCLE) throw new IllegalStateException("Not a cycle timer");
        return CycleTimer.parse(value);
    }
}
```

#### 1.4.4. Новая модель: `CycleTimer`

```java
// Файл: core/.../domain/model/CycleTimer.java
public record CycleTimer(
        int repetitions,       // -1 = бесконечно
        Duration interval
) {
    /**
     * Парсит ISO 8601 repeating interval: R3/PT10H, R/PT5M
     */
    public static CycleTimer parse(String value) { ... }

    public boolean isInfinite() {
        return repetitions == -1;
    }
}
```

#### 1.4.5. Обновить `FlowNode` sealed interface

```java
// Файл: core/.../domain/model/FlowNode.java
public sealed interface FlowNode permits
        StartEvent, EndEvent, ServiceTask, ExclusiveGateway, ParallelGateway,
        CallActivity, CompensationBoundaryEvent, TimerBoundaryEvent, ErrorBoundaryEvent,
        TimerIntermediateCatchEvent {  // <-- НОВОЕ
    // ...
}
```

Также добавить `@JsonSubTypes.Type` для `TimerIntermediateCatchEvent`.

#### 1.4.6. JAXB: Новый класс `BpmnIntermediateCatchEvent`

```java
// Файл: core/.../parser/jaxb/BpmnIntermediateCatchEvent.java
@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnIntermediateCatchEvent extends BpmnFlowElement {

    @XmlElement(name = "timerEventDefinition", namespace = BpmnNamespaces.BPMN)
    private BpmnTimerEventDefinition timerEventDefinition;

    public boolean isTimerEvent() {
        return timerEventDefinition != null;
    }

    public BpmnTimerEventDefinition getTimerEventDefinition() {
        return timerEventDefinition;
    }
}
```

#### 1.4.7. JAXB: Доработать `BpmnTimerEventDefinition`

Текущая реализация поддерживает только `timeDuration`. Добавить `timeDate` и `timeCycle`:

```java
// Файл: core/.../parser/jaxb/BpmnTimerEventDefinition.java
@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnTimerEventDefinition {

    @XmlElement(name = "timeDuration", namespace = BpmnNamespaces.BPMN)
    private BpmnExpression timeDuration;

    @XmlElement(name = "timeDate", namespace = BpmnNamespaces.BPMN)    // НОВОЕ
    private BpmnExpression timeDate;

    @XmlElement(name = "timeCycle", namespace = BpmnNamespaces.BPMN)   // НОВОЕ
    private BpmnExpression timeCycle;

    // геттеры...
}
```

#### 1.4.8. JAXB: Зарегистрировать в `BpmnProcess`

Добавить `BpmnIntermediateCatchEvent` в `@XmlElements` списка `flowElements` в `BpmnProcess`:

```java
@XmlElement(name = "intermediateCatchEvent", namespace = BpmnNamespaces.BPMN,
            type = BpmnIntermediateCatchEvent.class)
```

#### 1.4.9. `BpmnValidator` — добавить в поддерживаемые

```java
// В SUPPORTED_ELEMENTS добавить:
"intermediateCatchEvent",
"timeDate",    // новое
"timeCycle"    // новое

// Из FLOW_NODE_ELEMENTS убрать intermediateCatchEvent (он больше не unsupported)
```

#### 1.4.10. `FlowNodeMapper` — добавить маппинг

```java
case BpmnIntermediateCatchEvent e -> mapIntermediateCatchEvent(e);

private TimerIntermediateCatchEvent mapIntermediateCatchEvent(BpmnIntermediateCatchEvent element) {
    if (!element.isTimerEvent()) {
        throw new BpmnParseException(
            "Only timer intermediate catch events are supported: " + element.getId());
    }
    TimerDefinition timerDef = mapTimerDefinition(element.getTimerEventDefinition());
    return new TimerIntermediateCatchEvent(
            element.getId(),
            element.getName(),
            List.copyOf(element.getIncoming()),
            List.copyOf(element.getOutgoing()),
            timerDef);
}

private TimerDefinition mapTimerDefinition(BpmnTimerEventDefinition def) {
    if (def.getTimeDuration() != null) {
        return new TimerDefinition(TimerDefinition.TimerType.DURATION,
                                   def.getTimeDuration().getValue().trim());
    }
    if (def.getTimeDate() != null) {
        return new TimerDefinition(TimerDefinition.TimerType.DATE,
                                   def.getTimeDate().getValue().trim());
    }
    if (def.getTimeCycle() != null) {
        return new TimerDefinition(TimerDefinition.TimerType.CYCLE,
                                   def.getTimeCycle().getValue().trim());
    }
    throw new BpmnParseException("Timer event definition must have timeDuration, timeDate, or timeCycle");
}
```

#### 1.4.11. Новый обработчик: `TimerIntermediateCatchEventHandler`

```java
// Файл: core/.../engine/handler/TimerIntermediateCatchEventHandler.java
public final class TimerIntermediateCatchEventHandler implements NodeHandler {

    private final TimerService timerService;
    private final SequenceGenerator eventSequencer;

    @Override
    public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
        TimerIntermediateCatchEvent timerEvent = (TimerIntermediateCatchEvent) node;
        TimerDefinition timerDef = timerEvent.timerDefinition();

        Duration scheduleDuration = switch (timerDef.type()) {
            case DURATION -> timerDef.asDuration();
            case DATE -> {
                Duration until = Duration.between(Instant.now(), timerDef.asDate());
                yield until.isNegative() ? Duration.ZERO : until;
            }
            case CYCLE -> timerDef.asCycle().interval();
        };

        timerService.schedule(
                context.getProcessInstance().getId(),
                token.getId(),
                node.id(),
                scheduleDuration,
                callback -> {}
        );

        TimerScheduledEvent scheduledEvent = new TimerScheduledEvent(
                UUIDv7.generate(),
                context.getProcessInstance().getId(),
                token.getId(),
                node.id(),
                scheduleDuration,
                Instant.now(),
                eventSequencer.next(context.getProcessInstance().getId())
        );

        return List.of(scheduledEvent);
    }
}
```

#### 1.4.12. `TokenExecutor` — зарегистрировать обработчик

Добавить в `Map<NodeType, NodeHandler>`:

```java
Map.entry(NodeType.TIMER_INTERMEDIATE_CATCH, new TimerIntermediateCatchEventHandler(timerService, sequenceGenerator))
```

#### 1.4.13. `ProcessEngine.advanceActiveTokens()` — обработка нового типа

В блоке `advanceActiveTokens` добавить `TIMER_INTERMEDIATE_CATCH` к типам, которые переходят в `WAITING`:

```java
if (node.type() == NodeType.SERVICE_TASK
        || node.type() == NodeType.CALL_ACTIVITY
        || node.type() == NodeType.TIMER_INTERMEDIATE_CATCH) {  // <-- НОВОЕ
    // ... flush events, execute handler, set WAITING ...
}
```

#### 1.4.14. Обработка срабатывания таймера

При срабатывании таймера (callback от `TimerService`):
1. Найти `ProcessInstance` по `processInstanceId`
2. Найти токен в состоянии `WAITING` на данном узле
3. Переместить токен на следующий узел по исходящему потоку
4. Продолжить `advanceActiveTokens`

Это аналогично существующему механизму для `TimerBoundaryEvent`, но вместо отмены привязанной задачи — просто продвигаем токен дальше.

#### 1.4.15. Рефакторинг `TimerBoundaryEvent`

Рекомендуется перевести `TimerBoundaryEvent` на использование `TimerDefinition` вместо `Duration duration`, чтобы унифицировать модель таймеров. Это позволит поддержать `timeDate` и `timeCycle` и для boundary-таймеров.

### 1.5. Последовательность выполнения Timer Intermediate Catch Event

```
Process Instance            ProcessEngine              TimerService
     │                           │                          │
     │── Token reaches Timer ──►│                          │
     │                           │                          │
     │   TimerScheduledEvent     │── schedule(duration) ───►│
     │◄── (token → WAITING) ────│                          │
     │                           │                          │
     │                           │    ... ожидание ...      │
     │                           │                          │
     │                           │◄── callback(fired) ─────│
     │                           │                          │
     │   TokenMovedEvent         │                          │
     │◄── (token → next node) ──│                          │
```

---

## 2. Exclusive Gateway — доработка поддержки `default` flow

> Ссылка: https://docs.camunda.org/manual/7.24/reference/bpmn20/gateways/sequence-flow/

### 2.1. Обзор

Exclusive Gateway уже поддерживается в движке: `ExclusiveGatewayHandler`, `SimpleConditionEvaluator`, условия `${...}` на sequence flows. Однако поддержка `default` flow реализована **функционально** (flow без `conditionExpression` считается default), а не через явный BPMN-атрибут `default`.

### 2.2. Пример из `charge-payment-subprocess.bpmn`

```xml
<bpmn:exclusiveGateway id="payment-success" default="Flow_18ud5gi">
  <bpmn:incoming>Flow_12rnn5h</bpmn:incoming>
  <bpmn:outgoing>Flow_18ud5gi</bpmn:outgoing>
  <bpmn:outgoing>check-payment-flow</bpmn:outgoing>
</bpmn:exclusiveGateway>

<bpmn:sequenceFlow id="Flow_18ud5gi" sourceRef="payment-success" targetRef="check-status" />
<bpmn:sequenceFlow id="check-payment-flow" sourceRef="payment-success" targetRef="Event_0v6t5nz">
  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${isPaymentSuccess}</bpmn:conditionExpression>
</bpmn:sequenceFlow>
```

Условие `${isPaymentSuccess}` — это boolean expression без оператора сравнения. Текущий `SimpleConditionEvaluator` его **не поддерживает** (регулярка требует `variable operator value`).

### 2.3. Что нужно доработать

#### 2.3.1. JAXB: Атрибут `default` на `BpmnExclusiveGateway`

```java
// Файл: core/.../parser/jaxb/BpmnExclusiveGateway.java
@XmlAccessorType(XmlAccessType.FIELD)
public class BpmnExclusiveGateway extends BpmnFlowElement {

    @XmlAttribute(name = "default")
    private String defaultFlow;        // <-- НОВОЕ

    public String getDefaultFlow() {
        return defaultFlow;
    }
}
```

#### 2.3.2. Модель: Добавить `defaultFlowId` в `ExclusiveGateway`

```java
// Файл: core/.../domain/model/ExclusiveGateway.java
public record ExclusiveGateway(
        String id,
        String name,
        List<String> incomingFlows,
        List<String> outgoingFlows,
        String defaultFlowId           // <-- НОВОЕ, nullable
) implements FlowNode {
    // ...
}
```

#### 2.3.3. `FlowNodeMapper` — передать `defaultFlow`

```java
private ExclusiveGateway mapExclusiveGateway(BpmnExclusiveGateway element) {
    return new ExclusiveGateway(
            element.getId(),
            element.getName(),
            List.copyOf(element.getIncoming()),
            List.copyOf(element.getOutgoing()),
            element.getDefaultFlow());    // <-- НОВОЕ
}
```

#### 2.3.4. `ExclusiveGatewayHandler` — использовать `defaultFlowId`

Текущая логика определяет default flow как flow без `conditionExpression`. Нужно доработать: использовать явный `defaultFlowId`, и **игнорировать условия на default flow** (по спецификации BPMN):

```java
@Override
public List<ProcessEvent> handle(Token token, FlowNode node, ExecutionContext context) {
    ExclusiveGateway gateway = (ExclusiveGateway) node;
    List<SequenceFlow> outgoingFlows = context.findOutgoingFlows(node.id());

    if (outgoingFlows.isEmpty()) {
        throw new IllegalStateException(
            "ExclusiveGateway '" + node.id() + "' has no outgoing flows");
    }

    String defaultFlowId = gateway.defaultFlowId();
    SequenceFlow defaultFlow = null;

    for (SequenceFlow flow : outgoingFlows) {
        // Default flow — пропускаем при оценке условий
        if (flow.id().equals(defaultFlowId)) {
            defaultFlow = flow;
            continue;
        }
        // Flow без условия и не default — пропускаем (backward compatibility)
        if (flow.conditionExpression() == null) {
            if (defaultFlow == null) {
                defaultFlow = flow;  // fallback: flow без условия = implicit default
            }
            continue;
        }
        if (conditionEvaluator.evaluate(
                flow.conditionExpression().expression(), context.getVariables())) {
            return List.of(createTokenMovedEvent(token, node, flow, context));
        }
    }

    if (defaultFlow != null) {
        return List.of(createTokenMovedEvent(token, node, defaultFlow, context));
    }

    throw new IllegalStateException(
        "No matching condition found for ExclusiveGateway '" + node.id() + "'");
}
```

#### 2.3.5. `SimpleConditionEvaluator` — поддержка boolean expressions

Текущий evaluator поддерживает только формат `${variable operator value}`. Нужно добавить поддержку:

**a) Simple boolean variable:** `${isPaymentSuccess}`
- Если переменная boolean — вернуть её значение
- Если переменная не найдена — исключение

**b) Negation:** `${!isPaymentSuccess}`
- Отрицание boolean переменной

**c) Поддерживаемый формат (expression only):**

```
${variableName}              → variables.get("variableName") as Boolean
${!variableName}             → !variables.get("variableName") as Boolean
${variable == value}         → comparison (уже поддерживается)
${variable != value}         → comparison (уже поддерживается)
${variable > value}          → comparison (уже поддерживается)
${variable < value}          → comparison (уже поддерживается)
${variable >= value}         → comparison (уже поддерживается)
${variable <= value}         → comparison (уже поддерживается)
```

Более сложные выражения (логические операторы `&&`, `||`, вложенные скобки, доступ к свойствам через `.`) — **вне скоупа** текущего этапа.

**Изменения:**

```java
// Файл: core/.../engine/condition/SimpleConditionEvaluator.java

private static final Pattern COMPARISON_PATTERN =
        Pattern.compile("^\\$\\{\\s*(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+?)\\s*}$");

private static final Pattern BOOLEAN_PATTERN =
        Pattern.compile("^\\$\\{\\s*(!?)\\s*(\\w+)\\s*}$");

@Override
public boolean evaluate(String expression, Map<String, Object> variables) {
    // 1. Попробовать comparison
    Matcher compMatcher = COMPARISON_PATTERN.matcher(expression.trim());
    if (compMatcher.matches()) {
        return evaluateComparison(compMatcher, variables);
    }

    // 2. Попробовать boolean variable
    Matcher boolMatcher = BOOLEAN_PATTERN.matcher(expression.trim());
    if (boolMatcher.matches()) {
        boolean negated = "!".equals(boolMatcher.group(1));
        String variableName = boolMatcher.group(2);
        Object value = variables.get(variableName);
        if (value == null) {
            throw new IllegalArgumentException(
                "Variable '" + variableName + "' not found in process variables");
        }
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(
                "Variable '" + variableName + "' is not boolean: " + value.getClass());
        }
        boolean result = (Boolean) value;
        return negated ? !result : result;
    }

    throw new IllegalArgumentException("Invalid condition expression: " + expression);
}
```

### 2.4. Обратная совместимость

- Существующие процессы без `defaultFlowId` продолжают работать (implicit default = flow без условия)
- Существующие comparison expressions работают без изменений
- Новый boolean pattern не конфликтует с comparison pattern

---

## 3. Тест-план (Этап 1)

### 3.1. Timer Intermediate Catch Event

| # | Тест | Что проверяет |
|---|------|---------------|
| 1 | Парсинг BPMN с `intermediateCatchEvent` + `timeDuration` | JAXB маппинг и `FlowNodeMapper` |
| 2 | Парсинг BPMN с `timeDate` | Корректный парсинг абсолютной даты |
| 3 | Парсинг BPMN с `timeCycle` | Корректный парсинг цикла R3/PT10H |
| 4 | `BpmnValidator` — `intermediateCatchEvent` не ошибка | Больше не unsupported |
| 5 | `TimerIntermediateCatchEventHandler` — планирование таймера | `TimerService.schedule()` вызывается |
| 6 | `TimerIntermediateCatchEventHandler` — токен переходит в WAITING | Состояние токена |
| 7 | Callback таймера — токен продвигается | Переход по исходящему потоку |
| 8 | `TimerDefinition.asDuration()` — парсинг PT5S | Duration корректен |
| 9 | `TimerDefinition.asDate()` — дата в прошлом | Duration.ZERO |
| 10 | `CycleTimer.parse()` — R3/PT10H | repetitions=3, interval=10h |
| 11 | `CycleTimer.parse()` — R/PT5M | repetitions=-1 (infinite) |

### 3.2. Exclusive Gateway — `default` flow и boolean expressions

| # | Тест | Что проверяет |
|---|------|---------------|
| 1 | `ExclusiveGatewayHandler` — маршрутизация по `defaultFlowId` | Explicit default flow |
| 2 | `ExclusiveGatewayHandler` — backward compatibility (implicit default) | Flow без условия = default |
| 3 | `ExclusiveGatewayHandler` — условие на default flow игнорируется | По спецификации BPMN |
| 4 | `SimpleConditionEvaluator` — `${isPaymentSuccess}` (true) | Boolean variable = true |
| 5 | `SimpleConditionEvaluator` — `${isPaymentSuccess}` (false) | Boolean variable = false |
| 6 | `SimpleConditionEvaluator` — `${!isPaymentSuccess}` | Negation |
| 7 | `SimpleConditionEvaluator` — `${nonExistent}` | Исключение: variable not found |
| 8 | `SimpleConditionEvaluator` — `${stringVar}` | Исключение: not boolean |
| 9 | JAXB парсинг `default` атрибута на `exclusiveGateway` | `BpmnExclusiveGateway.getDefaultFlow()` |
| 10 | Существующие comparison expressions не сломаны | Регрессия |

### 3.3. E2E: Подпроцесс `charge-payment-subprocess.bpmn`

| # | Тест | Что проверяет |
|---|------|---------------|
| 1 | Полный цикл: charge → gateway(success=true) → end | Прямой путь к завершению |
| 2 | charge → gateway(success=false) → check-status → gateway(success=true) → end | Успех после проверки статуса |
| 3 | charge → gateway → check-status → gateway → timer(5s) → charge (retry loop) | Цикл с таймером |

---

## 4. Порядок реализации (Этап 1)

1. **`TimerDefinition`**, **`CycleTimer`** — новые модели
2. **`TimerIntermediateCatchEvent`** — доменная модель
3. **`NodeType.TIMER_INTERMEDIATE_CATCH`** — новое значение в enum
4. **`FlowNode`** — расширить sealed interface
5. **JAXB:** `BpmnIntermediateCatchEvent`, доработать `BpmnTimerEventDefinition` (timeDate, timeCycle)
6. **`BpmnValidator`** — перенести `intermediateCatchEvent` в поддерживаемые, добавить `timeDate`, `timeCycle`
7. **`BpmnProcess`** — зарегистрировать `BpmnIntermediateCatchEvent` в `@XmlElements`
8. **`FlowNodeMapper`** — маппинг `BpmnIntermediateCatchEvent` → `TimerIntermediateCatchEvent`
9. **`TimerIntermediateCatchEventHandler`** — обработчик
10. **`ProcessEngine.advanceActiveTokens()`** — добавить `TIMER_INTERMEDIATE_CATCH` к типам, переходящим в WAITING
11. **`BpmnExclusiveGateway`** — добавить атрибут `default`
12. **`ExclusiveGateway`** — добавить поле `defaultFlowId`
13. **`FlowNodeMapper`** — передать `defaultFlow` при маппинге
14. **`ExclusiveGatewayHandler`** — использовать явный `defaultFlowId`
15. **`SimpleConditionEvaluator`** — поддержка boolean expressions `${var}`, `${!var}`
16. **Unit-тесты** и **E2E-тесты**

---

---

# Этап 2 — Call Activity

## 5. Обзор

Реализовать полноценную поддержку **Call Activity** в Process Manager Engine. Call Activity позволяет из основного (родительского) процесса вызвать дочерний BPMN-процесс (подпроцесс), который выполняется как самостоятельный экземпляр процесса (`ProcessInstance`) со своим `processInstanceId`, но связан с родительским через `parentProcessInstanceId`.

### Текущее состояние

Частичная поддержка уже реализована:
- Модель `CallActivity` с полем `calledElement`
- `CallActivityHandler` — генерирует `CallActivityStartedEvent`, переводит родительский токен в `WAITING`
- `CallActivityStartedEvent` / `CallActivityCompletedEvent` — события для event sourcing
- `ProcessEngine.completeCallActivity(UUID childInstanceId)` — возобновляет родительский процесс после завершения дочернего
- `ProcessInstance.createChild()` — фабричный метод для дочерних экземпляров
- `ProcessStartedEvent.parentProcessInstanceId` — связь с родителем

### Что НЕ реализовано

1. **Валидация при деплое**: проверка наличия BPMN-файла подпроцесса рядом с основным
2. **Совместный деплой**: автоматический деплой подпроцесса вместе с основным
3. **Автоматический запуск дочернего процесса**: при достижении Call Activity дочерний процесс должен стартовать автоматически (сейчас `CallActivityHandler` только генерирует событие, но не вызывает `startProcess`)
4. **Автоматическое завершение родителя**: при завершении дочернего процесса должен автоматически вызываться `completeCallActivity`
5. **REST API для деплоя нескольких файлов**: текущий эндпоинт принимает один BPMN-файл

---

## 6. Требования

### 6.1. Деплой процесса с Call Activity

#### 6.1.1. Определение подпроцесса из BPMN

При парсинге BPMN основного процесса для каждого элемента `<bpmn:callActivity>` извлекается атрибут `calledElement`. Имя файла подпроцесса определяется как:

```
{calledElement}.bpmn
```

**Пример:** `calledElement="charge-payment-subprocess"` → файл `charge-payment-subprocess.bpmn`

#### 6.1.2. Валидация наличия подпроцесса

При деплое основного процесса:
1. Парсер извлекает все `calledElement` из `CallActivity` элементов
2. Для каждого `calledElement` проверяется наличие соответствующего `.bpmn` файла **в том же каталоге/наборе файлов**, что и основной процесс
3. Если хотя бы один файл подпроцесса отсутствует — деплой **всего набора** отклоняется с исключением

**Исключение:**
```java
public class CallActivitySubprocessNotFoundException extends RuntimeException {
    private final String calledElement;
    private final String expectedFileName;
    private final String parentProcessKey;
}
```

**Сообщение об ошибке:**
```
Cannot deploy process 'order-process': Call Activity references subprocess 
'charge-payment-subprocess' but file 'charge-payment-subprocess.bpmn' 
was not found in the deployment bundle
```

#### 6.1.3. Совместный деплой

Если все подпроцессы найдены — деплой выполняется **атомарно**:
1. Парсятся и валидируются все BPMN-файлы (основной + все подпроцессы)
2. Все `ProcessDefinition` деплоятся в `ProcessDefinitionStore`
3. Если деплой любого из определений падает — откатываются все (транзакционность)

Подпроцесс может сам содержать Call Activity (вложенные подпроцессы) — валидация и деплой рекурсивные.

---

### 6.2. Выполнение Call Activity

#### 6.2.1. Запуск дочернего процесса

Когда токен родительского процесса достигает узла `CallActivity`:

1. `CallActivityHandler` генерирует `CallActivityStartedEvent` (уже реализовано)
2. **НОВОЕ:** После применения `CallActivityStartedEvent` движок автоматически запускает дочерний процесс:
   - Определяет `calledElement` из узла `CallActivity`
   - Находит `ProcessDefinition` по ключу `calledElement` в `ProcessDefinitionStore`
   - Создаёт новый `ProcessInstance` через `ProcessInstance.createChild(definitionId, parentInstanceId, variables)`
   - Генерирует новый `processInstanceId` (UUIDv7)
   - Запускает выполнение дочернего процесса (аналогично `startProcess`)
   - Передаёт переменные из родительского контекста в дочерний

3. Токен родительского процесса остаётся в состоянии `WAITING` до завершения дочернего

#### 6.2.2. Связь родителя и потомка

| Поле | Значение |
|------|----------|
| `childInstance.parentProcessInstanceId` | `parentInstance.id` |
| `childInstance.definitionId` | ID определения из `calledElement` |
| `CallActivityStartedEvent.childProcessInstanceId` | `childInstance.id` |

Дополнительно необходимо хранить маппинг `childInstanceId → parentInstanceId` для быстрого поиска при завершении дочернего процесса.

#### 6.2.3. Завершение дочернего процесса

Когда дочерний процесс достигает `EndEvent` и завершается (`ProcessCompletedEvent`):

1. Движок определяет, что это дочерний процесс (через `parentProcessInstanceId`)
2. Автоматически вызывает `completeCallActivity(childInstanceId)`
3. Родительский токен на `CallActivity` переходит из `WAITING` в `ACTIVE`
4. Продолжается выполнение родительского процесса

**Важно:** переменные дочернего процесса **не** передаются обратно в родительский автоматически. Доступ к данным дочернего — через `ProcessEventStore` по `childProcessInstanceId`.

---

### 6.3. Обработка ошибок в дочернем процессе

#### 6.3.1. Error Boundary Event

Если на `CallActivity` в родительском процессе установлен `ErrorBoundaryEvent`:
- При ошибке дочернего процесса (`ProcessErrorEvent`) — маршрутизация через `ErrorBoundaryEvent` в родительском процессе
- `errorCode` из дочернего процесса пробрасывается в `ErrorBoundaryEvent` родителя

#### 6.3.2. Без Error Boundary

Если `ErrorBoundaryEvent` не установлен:
- Ошибка дочернего процесса приводит к ошибке родительского процесса
- Запускается компенсация в родительском процессе (если настроена)

---

## 7. Изменения в компонентах (Этап 2)

### 7.1. Модуль `core`

#### 7.1.1. Новый класс: `DeploymentBundle`

Контейнер для группы BPMN-файлов, деплоящихся совместно:

```java
public final class DeploymentBundle {
    private final Map<String, String> bpmnFiles; // fileName → bpmnXml

    public DeploymentBundle(Map<String, String> bpmnFiles);
    public String getMainProcess();
    public Map<String, String> getSubprocesses();
    public boolean containsFile(String fileName);
}
```

#### 7.1.2. Новый класс: `CallActivityValidator`

Валидация наличия подпроцессов при деплое:

```java
public final class CallActivityValidator {
    
    /**
     * Извлекает все calledElement из CallActivity элементов процесса.
     */
    public List<String> extractCalledElements(ProcessDefinition definition);
    
    /**
     * Проверяет, что для каждого calledElement есть соответствующий файл в бандле.
     * @throws CallActivitySubprocessNotFoundException если файл не найден
     */
    public void validate(ProcessDefinition definition, DeploymentBundle bundle);
}
```

#### 7.1.3. Изменения в `ProcessEngine`

**Новый метод `deployBundle`:**

```java
public List<ProcessDefinition> deployBundle(DeploymentBundle bundle) {
    // 1. Парсить все BPMN-файлы из бандла
    // 2. Для каждого CallActivity проверить наличие подпроцесса
    // 3. Рекурсивная валидация вложенных Call Activity
    // 4. Атомарный деплой всех ProcessDefinition
    // 5. Уведомить DeploymentListener-ы
}
```

**Изменения в `advanceActiveTokens`:**

При обработке `CallActivity`:
- После `CallActivityStartedEvent` — автоматически вызывать `startProcess(calledElement, variables)` для создания и запуска дочернего процесса

**Изменения в обработке `ProcessCompletedEvent`:**

В `EndEventHandler` или в `advanceActiveTokens`:
- Если завершённый процесс имеет `parentProcessInstanceId` ≠ null — автоматически вызвать `completeCallActivity(childInstanceId)`

#### 7.1.4. Новый порт: `ChildInstanceMapping`

Маппинг для быстрого поиска родительского процесса по дочернему:

```java
public interface ChildInstanceMapping {
    void put(UUID childInstanceId, UUID parentInstanceId);
    UUID getParent(UUID childInstanceId);
    List<UUID> getChildren(UUID parentInstanceId);
    void remove(UUID childInstanceId);
}
```

**Реализации:**
- `InMemoryChildInstanceMapping` — для тестов и in-memory режима
- `RedisChildInstanceMapping` — для Redis-персистенции (модуль `redis-persistence`)

#### 7.1.5. Изменения в `EventApplier`

При обработке `CallActivityStartedEvent`:
- Сохранить маппинг `childProcessInstanceId → parentProcessInstanceId` в `ChildInstanceMapping`

---

### 7.2. Модуль `rest-api`

#### 7.2.1. Новый эндпоинт для деплоя бандла

```
POST /api/v1/definitions/bundle
Content-Type: multipart/form-data

files: [order-process.bpmn, charge-payment-subprocess.bpmn, ...]
```

**Ответ:**
```json
{
  "definitions": [
    {
      "id": "...",
      "key": "order-process",
      "version": 1,
      "name": "Order Process"
    },
    {
      "id": "...",
      "key": "charge-payment-subprocess",
      "version": 1,
      "name": "Charge Payment Subprocess"
    }
  ]
}
```

#### 7.2.2. Обратная совместимость

Существующий эндпоинт `POST /api/v1/definitions` (один файл) продолжает работать:
- Если BPMN не содержит `CallActivity` — деплой как раньше
- Если содержит `CallActivity` — выбрасывается ошибка с указанием использовать `/bundle`

---

### 7.3. Модуль `redis-persistence`

- Реализовать `RedisChildInstanceMapping` — хранение маппинга child→parent и parent→children в Redis

---

### 7.4. Модуль `rabbitmq-transport`

Изменений не требуется. Call Activity не использует `MessageTransport` напрямую — дочерний процесс запускается внутри движка, а его задачи (`ServiceTask`) уже используют RabbitMQ через существующий механизм.

---

## 8. Последовательность выполнения Call Activity (Sequence Diagram)

```
Parent Process                    ProcessEngine                    Child Process
     │                                 │                                │
     │──── Token reaches CallActivity ─►│                                │
     │                                 │                                │
     │     CallActivityStartedEvent    │                                │
     │◄──── (token → WAITING) ─────────│                                │
     │                                 │                                │
     │                                 │── startProcess(calledElement) ─►│
     │                                 │                                │
     │                                 │   ProcessStartedEvent          │
     │                                 │◄── (new processInstanceId) ────│
     │                                 │                                │
     │                                 │   ... дочерний процесс         │
     │                                 │   выполняет свои задачи ...    │
     │                                 │                                │
     │                                 │   ProcessCompletedEvent        │
     │                                 │◄── (child completed) ──────────│
     │                                 │                                │
     │  completeCallActivity(childId)  │                                │
     │◄──── (token → ACTIVE) ──────────│                                │
     │                                 │                                │
     │──── Token advances to next ─────►│                                │
```

---

## 9. Пример процессов из `docs/processes/`

### 9.1. Основной процесс: `order-process.bpmn`

Полный flow:
```
StartEvent → validate-order (ServiceTask) → ParallelGateway(fork) 
  → book-order (ServiceTask)
  → notify-booking (ServiceTask)
→ ParallelGateway(join) → charge-payment (CallActivity) → deliver-order (ServiceTask) → EndEvent
```

Особенности:
- `charge-payment` — **CallActivity** с `calledElement="charge-payment-subprocess"`
- На `charge-payment` установлен `CompensationBoundaryEvent` → `refund-payment` (ServiceTask)
- При ошибке в charge-payment — срабатывает компенсация (refund)

### 9.2. Подпроцесс: `charge-payment-subprocess.bpmn`

Полный flow:
```
StartEvent → charge-payment (ServiceTask) → payment-success (ExclusiveGateway)
  → [${isPaymentSuccess} = true] → EndEvent
  → [default] → check-status (ServiceTask) → payment-status (ExclusiveGateway)
      → [${isPaymentSuccess} = true] → EndEvent
      → [default] → charge-wait (TimerIntermediateCatchEvent, PT5S) → charge-payment (loop)
```

Особенности:
- Использует **ExclusiveGateway** с `default` атрибутом и boolean expression `${isPaymentSuccess}`
- Использует **Timer Intermediate Catch Event** (`PT5S`) для retry-паузы
- Цикл: если оплата не прошла и статус неуспешен — ждём 5 секунд и повторяем
- `process id` = `charge-payment-subprocess` совпадает с `calledElement` в родителе

---

## 10. Тест-план (Этап 2)

### 10.1. Unit-тесты

| # | Тест | Что проверяет |
|---|------|---------------|
| 1 | `CallActivityValidator` — все подпроцессы найдены | Валидация проходит успешно |
| 2 | `CallActivityValidator` — подпроцесс отсутствует | Выбрасывается `CallActivitySubprocessNotFoundException` |
| 3 | `CallActivityValidator` — вложенные подпроцессы | Рекурсивная валидация |
| 4 | `DeploymentBundle` — извлечение файлов | Корректная работа контейнера |
| 5 | `ProcessEngine.deployBundle()` — успешный деплой | Все определения задеплоены |
| 6 | `ProcessEngine.deployBundle()` — откат при ошибке | Ни одно определение не задеплоено |
| 7 | Единичный деплой с `CallActivity` | Ошибка с указанием использовать bundle |

### 10.2. Интеграционные тесты (E2E)

| # | Тест | Что проверяет |
|---|------|---------------|
| 1 | Полный цикл: деплой → старт order-process → CallActivity → charge-payment-subprocess → завершение → продолжение → deliver-order → end | Весь flow с реальными BPMN из `docs/processes/` |
| 2 | Ошибка в дочернем процессе с ErrorBoundaryEvent | Маршрутизация через error boundary |
| 3 | Ошибка в дочернем процессе без ErrorBoundaryEvent | Компенсация в родителе (refund-payment) |
| 4 | Вложенные Call Activity (A → B → C) | Многоуровневая цепочка |
| 5 | Параллельные Call Activity | Несколько дочерних одновременно |
| 6 | Retry loop в подпроцессе: charge → gateway → check → timer → charge | Цикл с таймером в дочернем |

---

## 11. Порядок реализации (Этап 2)

1. **`CallActivitySubprocessNotFoundException`** — новое исключение
2. **`DeploymentBundle`** — контейнер для группы BPMN-файлов
3. **`CallActivityValidator`** — валидация наличия подпроцессов
4. **`ChildInstanceMapping`** — порт + InMemory реализация
5. **`ProcessEngine.deployBundle()`** — атомарный деплой нескольких процессов
6. **`ProcessEngine` — автозапуск дочернего** при достижении CallActivity
7. **`ProcessEngine` — автозавершение родителя** при завершении дочернего
8. **`ProcessEngine` — проброс ошибок** из дочернего в родительский
9. **REST API: `POST /bundle`** — эндпоинт деплоя бандла
10. **`RedisChildInstanceMapping`** — Redis реализация
11. **Unit-тесты** и **E2E-тесты**

---

## 12. Ограничения и допущения

- Подпроцесс **не** может быть задеплоен отдельно от основного процесса (только через bundle)
- Переменные передаются из родителя в дочерний при старте, но **не** возвращаются обратно автоматически
- `calledElement` должен точно совпадать с `process id` в BPMN-файле подпроцесса
- Циклические зависимости (процесс A вызывает B, B вызывает A) — должны быть обнаружены при валидации и запрещены
- Один и тот же подпроцесс может использоваться несколькими родительскими процессами
- Timer Intermediate Catch Event поддерживает только timer-тип (не message, signal и т.д.)
- `SimpleConditionEvaluator` поддерживает только expression-тип (`${...}`), без `&&`, `||`, вложенных выражений
