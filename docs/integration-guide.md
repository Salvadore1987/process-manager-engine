# Integration Guide — Process Manager Engine

Руководство по интеграции внешних сервисов с движком бизнес-процессов.

## Общая схема взаимодействия

```
                         ┌──────────────────────────────┐
                         │        REST API               │
                         │  Deploy │ Start │ Control      │
                         └────────────┬─────────────────┘
                                      │
                         ┌────────────▼─────────────────┐
                         │      Process Engine           │
                         │  BPMN Parser │ Token Engine   │
                         │  Event Sourcing               │
                         └──┬─────────────────────────┬──┘
                            │                         │
               ┌────────────▼──────────┐   ┌─────────▼──────────────┐
               │     RabbitMQ          │   │  REST: POST /messages  │
               │  task.{topic}.execute │   │  (ручная корреляция)   │
               │  task.{topic}.result  │   └────────────────────────┘
               └────────────┬──────────┘
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                  │
   ┌──────▼──────┐  ┌──────▼──────┐  ┌───────▼─────┐
   │  Service A  │  │  Service B  │  │  Service C  │
   │  (validate) │  │  (payment)  │  │  (shipping) │
   └─────────────┘  └─────────────┘  └─────────────┘
```

Внешние сервисы взаимодействуют с движком двумя способами:
1. **RabbitMQ** — основной асинхронный канал для выполнения задач (`ServiceTask`)
2. **REST API** — управление процессами, ручная отправка результатов, мониторинг

---

## ServiceTask и RabbitMQ

### Как работает ServiceTask

Каждый `ServiceTask` в BPMN-модели имеет атрибут `topic` — имя очереди задач. Когда токен процесса достигает ServiceTask:

1. Движок публикует сообщение в очередь `task.{topic}.execute`
2. Токен переходит в состояние `WAITING`
3. Внешний сервис читает сообщение, выполняет бизнес-логику
4. Сервис отправляет результат в очередь `task.{topic}.result`
5. Движок получает результат, мержит переменные и продвигает токен дальше

```
Engine                          RabbitMQ                        Your Service
  │                                │                                │
  │── publish(execute queue) ─────>│                                │
  │   token = WAITING              │── deliver ────────────────────>│
  │                                │                                │
  │                                │                                │── business logic
  │                                │                                │
  │                                │<── publish(result queue) ──────│
  │<── deliver ────────────────────│                                │
  │   merge variables              │                                │
  │   advance token                │                                │
```

### Топология RabbitMQ

```
Exchange: process-engine.tasks (topic, durable)
  └─ Для каждого topic:
      task.{topic}.execute   — задачи ОТ движка К сервису
      task.{topic}.result    — результаты ОТ сервиса К движку

Exchange: process-engine.retry (topic, durable)
  └─ task.{topic}.retry    — retry с exponential backoff
      (DLX → process-engine.tasks → task.{topic}.execute)

Exchange: process-engine.dlq (fanout, durable)
  └─ process-engine.dlq   — сообщения после исчерпания retry

Exchange: process-engine.timers (topic, durable)
  └─ process-engine.timers.fired — сработавшие таймеры
```

### Формат сообщений

**Сообщение в execute-очереди (от движка к сервису):**

```
AMQP Properties:
  contentType:   "application/json"
  deliveryMode:  2 (persistent)
  correlationId: "{token-uuid}"
  headers:
    x-correlation-id: "{token-uuid}"

Body (JSON — все переменные процесса):
{
  "orderAmount": 5000,
  "customerId": "cust-123"
}
```

**Ответ в result-очереди (от сервиса к движку):**

```
AMQP Properties:
  contentType:   "application/json"
  deliveryMode:  2 (persistent)
  correlationId: "{тот же token-uuid}"
  headers:
    x-correlation-id: "{тот же token-uuid}"

Body — успешный результат:
{
  "approved": true,
  "validatedAt": "2026-03-29T12:00:00Z"
}

Body — ошибка:
{
  "__error": true,
  "__errorCode": "VALIDATION_FAILED",
  "reason": "Order amount is negative"
}
```

> **Важно:** `correlationId` из входящего сообщения необходимо передать обратно без изменений. Это UUID токена, по которому движок определяет, какой процесс продвинуть.

---

## Реализация внешнего сервиса

### Рекомендуемый способ: `worker-spring-boot-starter`

Самый простой способ интеграции — подключить клиентский Spring Boot Starter.

**1. Добавить зависимость:**

```gradle
implementation("uz.salvadore:worker-spring-boot-starter:1.0-SNAPSHOT")
```

**2. Настроить подключение к RabbitMQ (`application.yml`):**

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

**3. Реализовать обработчик:**

```java
@Component
public class OrderValidationHandler implements ExternalTaskHandler {

    @Override
    @JobWorker(topic = "order.validate")
    public void execute(TaskContext context) {
        try {
            int orderAmount = ((Number) context.getVariable("orderAmount")).intValue();
            boolean approved = orderAmount > 0 && orderAmount < 1_000_000;

            context.complete(Map.of(
                    "approved", approved,
                    "validatedAt", Instant.now().toString()
            ));
        } catch (Exception e) {
            context.error("VALIDATION_ERROR", e.getMessage());
        }
    }
}
```

Starter автоматически:
- Подключается к RabbitMQ
- Слушает очередь `task.order.validate.execute`
- Извлекает `correlationId` и переменные процесса
- Передаёт их в `TaskContext`
- При вызове `context.complete()` — публикует результат в `task.order.validate.result`
- При вызове `context.error()` — публикует ошибку с `__error` и `__errorCode`

Подробная документация: [`docs/worker-spring-boot-starter-spec.md`](worker-spring-boot-starter-spec.md)

---

### Альтернатива: ручная интеграция на Spring AMQP

Если `worker-spring-boot-starter` не подходит, можно реализовать worker вручную:

```java
@Component
public class OrderValidationWorker {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OrderValidationWorker(RabbitTemplate rabbitTemplate,
                                 ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "task.order.validate.execute")
    public void handleTask(Message message) {
        String correlationId = message.getMessageProperties().getCorrelationId();

        Map<String, Object> variables = objectMapper.readValue(
                message.getBody(), new TypeReference<>() {});

        Map<String, Object> result = new HashMap<>();

        try {
            int orderAmount = ((Number) variables.get("orderAmount")).intValue();
            boolean approved = orderAmount > 0 && orderAmount < 1_000_000;

            result.put("approved", approved);
            result.put("validatedAt", Instant.now().toString());
        } catch (Exception e) {
            result.put("__error", true);
            result.put("__errorCode", "VALIDATION_ERROR");
            result.put("reason", e.getMessage());
        }

        rabbitTemplate.convertAndSend(
                "process-engine.tasks",
                "task.order.validate.result",
                result,
                msg -> {
                    msg.getMessageProperties().setCorrelationId(correlationId);
                    msg.getMessageProperties()
                       .setHeader("x-correlation-id", correlationId);
                    return msg;
                }
        );
    }
}
```

### Альтернатива: чистый amqp-client

```java
Channel channel = connection.createChannel();

channel.basicConsume("task.order.validate.execute", false,
        (consumerTag, delivery) -> {
            String correlationId = delivery.getProperties().getCorrelationId();
            Map<String, Object> variables = objectMapper.readValue(
                    delivery.getBody(), new TypeReference<>() {});

            // бизнес-логика
            Map<String, Object> result = Map.of("approved", true);

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .correlationId(correlationId)
                    .contentType("application/json")
                    .deliveryMode(2)
                    .headers(Map.of("x-correlation-id", correlationId))
                    .build();

            channel.basicPublish(
                    "process-engine.tasks",
                    "task.order.validate.result",
                    props,
                    objectMapper.writeValueAsBytes(result));

            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        },
        consumerTag -> {});
```

### Пример на Python (pika)

```python
import pika
import json

connection = pika.BlockingConnection(
    pika.ConnectionParameters('localhost'))
channel = connection.channel()

def on_message(ch, method, properties, body):
    variables = json.loads(body)
    correlation_id = properties.correlation_id

    # бизнес-логика
    result = {"approved": True, "validatedAt": "2026-03-29T12:00:00Z"}

    ch.basic_publish(
        exchange='process-engine.tasks',
        routing_key='task.order.validate.result',
        properties=pika.BasicProperties(
            correlation_id=correlation_id,
            content_type='application/json',
            delivery_mode=2,
            headers={'x-correlation-id': correlation_id}
        ),
        body=json.dumps(result)
    )
    ch.basic_ack(delivery_tag=method.delivery_tag)

channel.basic_consume(
    queue='task.order.validate.execute',
    on_message_callback=on_message)
channel.start_consuming()
```

---

## Retry и Dead Letter Queue

Движок автоматически обрабатывает ошибки при выполнении задач:

```
Попытка 1 (ошибка) → retry через 5s
Попытка 2 (ошибка) → retry через 10s
Попытка 3 (ошибка) → DLQ + создание инцидента
```

| Параметр | Property | По умолчанию |
|----------|----------|-------------|
| Макс. попыток | `process-engine.retry.max-attempts` | `3` |
| Базовый интервал | `process-engine.retry.base-interval` | `5s` |
| Макс. интервал | `process-engine.retry.max-interval` | `5m` |

Формула: `baseInterval × 2^attempt`, ограничено `maxInterval`.

При исчерпании попыток:
- Сообщение попадает в `process-engine.dlq`
- В движке создаётся **инцидент**, который можно обработать через REST API

### Обработка инцидентов

```bash
# Список инцидентов
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/incidents

# Повторить задачу
curl -X PUT -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/api/v1/incidents/{id}/resolve \
  -d '{"action": "RETRY"}'

# Пропустить задачу
curl -X PUT ... -d '{"action": "SKIP"}'

# Отменить процесс
curl -X PUT ... -d '{"action": "CANCEL"}'
```

---

## REST API: управление процессами

### Деплой BPMN-определения

```bash
curl -X POST http://localhost:8080/api/v1/definitions \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@process.bpmn"
```

### Запуск экземпляра процесса

```bash
curl -X POST http://localhost:8080/api/v1/instances \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "definitionKey": "order-processing",
    "variables": {
      "orderAmount": 5000,
      "customerId": "cust-123"
    }
  }'
```

При запуске движок:
1. Создаёт `ProcessInstance` с начальными переменными
2. Размещает токен на `StartEvent`
3. Продвигает токен по графу, пока не достигнет `ServiceTask` или `EndEvent`
4. Для каждого `ServiceTask` отправляет сообщение в соответствующую RabbitMQ-очередь

### Отправка результата через REST API

Альтернатива RabbitMQ — ручная отправка результата задачи:

```bash
curl -X POST http://localhost:8080/api/v1/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "correlationId": "{token-uuid}",
    "payload": {
      "approved": true,
      "validatedAt": "2026-03-29T12:00:00Z"
    }
  }'
```

Это полезно для:
- Ручного завершения задач через UI/дашборд
- Тестирования процессов без внешних сервисов
- Интеграции с системами, не поддерживающими RabbitMQ

### Управление экземпляром

```bash
# Приостановить (все токены замораживаются)
curl -X PUT http://localhost:8080/api/v1/instances/{id}/suspend

# Возобновить
curl -X PUT http://localhost:8080/api/v1/instances/{id}/resume

# Принудительно завершить
curl -X DELETE http://localhost:8080/api/v1/instances/{id}
```

### Переменные процесса

```bash
# Получить все переменные
curl http://localhost:8080/api/v1/instances/{id}/variables

# Обновить переменные (мерж с существующими)
curl -X PUT http://localhost:8080/api/v1/instances/{id}/variables \
  -H "Content-Type: application/json" \
  -d '{"priority": "high"}'
```

### История и аудит

```bash
# Лог событий (event sourcing)
curl http://localhost:8080/api/v1/history/instances/{id}/events

# История активностей (какие ноды прошёл токен)
curl http://localhost:8080/api/v1/history/instances/{id}/activities
```

---

## CallActivity: вызов подпроцессов

`CallActivity` запускает дочерний процесс по ключу (`calledElement`). Родительский токен ждёт завершения дочернего процесса.

```xml
<bpmn:callActivity id="CallActivity_Payment"
                   name="Payment Processing"
                   calledElement="payment-processing">
  <bpmn:incoming>Flow_1</bpmn:incoming>
  <bpmn:outgoing>Flow_2</bpmn:outgoing>
</bpmn:callActivity>
```

Порядок выполнения:

```
Родительский процесс           Дочерний процесс
  │                               │
  │── токен на CallActivity       │
  │   состояние: WAITING          │
  │                               │── StartEvent
  │                               │── ServiceTask → RabbitMQ
  │                               │── ...
  │                               │── EndEvent
  │                               │
  │<── дочерний завершён ─────────│
  │   токен продвигается          │
  │                               │
```

Дочерний процесс (`payment-processing`) должен быть задеплоен до запуска родительского.

---

## Boundary Events

### Timer Boundary Event (таймаут задачи)

Если задача не завершилась за указанное время — процесс идёт по альтернативному пути.

```xml
<bpmn:boundaryEvent id="Timeout" attachedToRef="Task_Reserve"
                    cancelActivity="true">
  <bpmn:outgoing>Flow_Timeout</bpmn:outgoing>
  <bpmn:timerEventDefinition>
    <bpmn:timeDuration>PT30M</bpmn:timeDuration>
  </bpmn:timerEventDefinition>
</bpmn:boundaryEvent>
```

- При `cancelActivity="true"` — задача отменяется, токен уходит по timeout-пути
- Таймер реализован через RabbitMQ TTL-очереди

### Error Boundary Event (обработка ошибок)

Перехватывает ошибки от дочернего процесса (CallActivity) или задачи.

```xml
<bpmn:boundaryEvent id="PaymentError" attachedToRef="CallActivity_Payment"
                    cancelActivity="true">
  <bpmn:outgoing>Flow_Error</bpmn:outgoing>
  <bpmn:errorEventDefinition errorRef="Error_PaymentFailed" />
</bpmn:boundaryEvent>
```

Если дочерний процесс завершается `ErrorEndEvent` с `errorCode="PAYMENT_FAILED"` — токен родителя перенаправляется по пути ошибки.

### Compensation Boundary Event (откат)

Запускает компенсацию (undo) если последующая задача упала.

```xml
<bpmn:boundaryEvent id="Compensate" attachedToRef="CallActivity_Payment">
  <bpmn:compensateEventDefinition />
</bpmn:boundaryEvent>

<bpmn:serviceTask id="Task_Refund" name="Refund"
                  camunda:topic="payment.refund"
                  isForCompensation="true" />

<bpmn:association sourceRef="Compensate" targetRef="Task_Refund" />
```

---

## Пример: полный цикл заказа

Пример BPMN-процесса `order-processing` из `docs/example-order-process.bpmn`:

```
StartEvent
  │
  ▼
[order.validate] ─── ServiceTask: валидация заказа
  │
  ▼
<ExclusiveGateway> ─── amount > 10000?
  │ да                    │ нет
  ▼                       │
[order.fraud-check]       │
  │                       │
  ▼───────────────────────▼
<ParallelGateway> ─── fork
  │                       │
  ▼                       ▼
[warehouse.reserve]    CallActivity: payment-processing
  │ ⏱ 30min timeout       │ ⚠ error boundary
  │                       │ ↩ compensation boundary
  ▼                       ▼
<ParallelGateway> ─── join
  │
  ▼
[shipping.arrange] ─── ServiceTask: организация доставки
  │
  ▼
[notification.order-confirmed] ─── ServiceTask: уведомление
  │
  ▼
EndEvent
```

### Какие сервисы нужно реализовать

| Topic | Очередь | Что делает сервис |
|-------|---------|-------------------|
| `order.validate` | `task.order.validate.execute` | Проверяет данные заказа |
| `order.fraud-check` | `task.order.fraud-check.execute` | Антифрод-проверка |
| `warehouse.reserve` | `task.warehouse.reserve.execute` | Резервирует товар на складе |
| `payment.charge` | `task.payment.charge.execute` | Списание средств |
| `payment.confirm` | `task.payment.confirm.execute` | Подтверждение платежа |
| `payment.refund` | `task.payment.refund.execute` | Возврат средств (компенсация) |
| `shipping.arrange` | `task.shipping.arrange.execute` | Организация доставки |
| `notification.order-confirmed` | `task.notification.order-confirmed.execute` | Отправка уведомления |

Каждый сервис реализует `ExternalTaskHandler` с аннотацией `@JobWorker(topic = "...")`:
1. Получает переменные процесса через `TaskContext`
2. Выполняет бизнес-логику
3. Вызывает `context.complete(result)` или `context.error(code, message)`

---

## Авторизация

При включённой авторизации (`process-engine.security.enabled=true`) все REST-запросы требуют JWT-токен.

```bash
# Получить токен
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/process-engine/protocol/openid-connect/token \
  -d "grant_type=password&client_id=process-engine-api&username=admin&password=admin" \
  | jq -r '.access_token')

# Service-to-service (client credentials)
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/process-engine/protocol/openid-connect/token \
  -d "grant_type=client_credentials&client_id=process-engine-service&client_secret=process-engine-service-secret" \
  | jq -r '.access_token')

# Использование
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/instances
```

### Роли и доступ к эндпоинтам

| Эндпоинт | Метод | process-admin | process-operator | process-viewer | process-deployer |
|-----------|-------|:---:|:---:|:---:|:---:|
| `/api/v1/definitions` | POST | + | | | + |
| `/api/v1/definitions/**` | GET | + | + | + | + |
| `/api/v1/definitions/{key}` | DELETE | + | | | + |
| `/api/v1/instances` | POST | + | + | | |
| `/api/v1/instances/**` | GET | + | + | + | |
| `/api/v1/instances/*/suspend` | PUT | + | + | | |
| `/api/v1/instances/*/resume` | PUT | + | + | | |
| `/api/v1/instances/*` | DELETE | + | + | | |
| `/api/v1/instances/*/variables` | PUT | + | + | | |
| `/api/v1/instances/*/variables/**` | GET | + | + | + | |
| `/api/v1/messages` | POST | + | + | | |
| `/api/v1/history/**` | GET | + | + | + | |
| `/api/v1/incidents/**` | GET | + | + | + | |
| `/api/v1/incidents/*/resolve` | PUT | + | + | | |
| `/actuator/**` | GET | public | public | public | public |

---

## Чеклист интеграции

1. **Задеплоить BPMN-определение** через `POST /api/v1/definitions`
   - При деплое движок автоматически создаёт RabbitMQ очереди и bindings для всех ServiceTask топиков
2. **Реализовать worker-сервисы** для каждого `topic` из BPMN:
   - Подключить `worker-spring-boot-starter` как зависимость
   - Реализовать `ExternalTaskHandler` с `@JobWorker(topic = "...")`
   - Вызывать `context.complete(result)` или `context.error(code, message)`
3. **Запустить экземпляр процесса** через `POST /api/v1/instances`
4. **Мониторить** через `/api/v1/incidents` и `/api/v1/history/instances/{id}/events`
5. **Обрабатывать инциденты** через `PUT /api/v1/incidents/{id}/resolve`
