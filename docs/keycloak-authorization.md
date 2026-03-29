# Keycloak Authorization — Спецификация

## Overview

Интеграция системы авторизации на базе Keycloak в Process Manager Engine. Keycloak и движок развёртываются как единое приложение через Docker Compose. Новый модуль `security` обеспечивает Spring Security OAuth2/OIDC интеграцию с Keycloak.

## Требования

### Функциональные

1. Все REST API эндпоинты (`/api/v1/**`) защищены JWT-токенами, выданными Keycloak
2. Role-Based Access Control (RBAC) с 4 ролями:
   - `process-admin` — полный доступ: deploy/undeploy definitions, управление instances, incidents, просмотр history
   - `process-operator` — запуск/остановка/suspend/resume instances, отправка messages, управление variables, просмотр history
   - `process-viewer` — только чтение: list definitions, get instances, get variables, get history
   - `process-deployer` — deploy/undeploy/validate definitions (CI/CD service account)
3. Actuator эндпоинты (`/actuator/**`) доступны без авторизации (для k8s probes)
4. Service-to-service аутентификация через client credentials (для внешних сервисов, обрабатывающих ServiceTask)
5. Keycloak realm, клиенты, роли и тестовые пользователи создаются автоматически при первом запуске (realm import)

### Нефункциональные

- JWT валидация локальная (через JWKS endpoint), без обращения к Keycloak на каждый запрос
- Graceful degradation: если Keycloak недоступен при старте — retry с backoff
- Возможность отключить security для локальной разработки (`process-engine.security.enabled=false`)

## Архитектура

### Модули

```
process-manager-engine/
├── core/                      (без изменений)
├── rabbitmq-transport/        (без изменений)
├── spring-integration/        (без изменений)
├── security/                  ★ НОВЫЙ МОДУЛЬ
│   ├── build.gradle.kts
│   └── src/main/java/uz/salvadore/processengine/security/
│       ├── autoconfigure/
│       │   ├── SecurityAutoConfiguration.java
│       │   ├── SecurityProperties.java
│       │   └── KeycloakJwtAutoConfiguration.java
│       ├── config/
│       │   └── ResourceServerConfig.java
│       ├── converter/
│       │   └── KeycloakJwtAuthenticationConverter.java
│       ├── model/
│       │   └── ProcessEngineRole.java
│       └── filter/
│           └── SecurityDisabledFilter.java
└── rest-api/                  (добавить зависимость на security)
```

### Стек

| Компонент | Технология |
|-----------|-----------|
| Identity Provider | Keycloak 24.x |
| Spring Security | spring-boot-starter-oauth2-resource-server |
| JWT | Nimbus JOSE+JWT (транзитивно через Spring) |
| Realm provisioning | Keycloak realm import (JSON) |
| Тестирование | spring-security-test, WireMock (mock JWKS) |

### Схема взаимодействия

```
┌──────────┐     ┌──────────────┐     ┌──────────────────────┐
│  Client  │────▶│   Keycloak   │────▶│  JWT Token (RS256)   │
│ (UI/CLI) │     │  :8180       │     │  realm_access.roles  │
└──────────┘     └──────────────┘     └──────────┬───────────┘
                                                  │
                                                  ▼
                                      ┌──────────────────────┐
                                      │   Process Engine     │
                                      │   :8080              │
                                      │                      │
                                      │  SecurityFilter:     │
                                      │  1. Validate JWT     │
                                      │     (JWKS endpoint)  │
                                      │  2. Extract roles    │
                                      │  3. Map to Spring    │
                                      │     GrantedAuthority │
                                      └──────────────────────┘
```

## Keycloak Configuration (Realm Import)

### Realm: `process-engine`

Файл: `keycloak/realm-export.json`

#### Клиенты

| Client ID | Type | Access Type | Описание |
|-----------|------|-------------|----------|
| `process-engine-api` | public | — | Frontend/CLI клиент (authorization code + PKCE) |
| `process-engine-service` | confidential | client_credentials | Service-to-service (внешние обработчики ServiceTask) |

#### Realm Roles

| Role | Описание |
|------|----------|
| `process-admin` | Полный доступ ко всем операциям |
| `process-operator` | Управление экземплярами процессов |
| `process-viewer` | Только чтение |
| `process-deployer` | Deploy/undeploy определений |

#### Тестовые пользователи (только для dev/local)

| Username | Password | Roles |
|----------|----------|-------|
| `admin` | `admin` | `process-admin` |
| `operator` | `operator` | `process-operator` |
| `viewer` | `viewer` | `process-viewer` |
| `deployer` | `deployer` | `process-deployer` |

#### Service Account

Client `process-engine-service` получает роль `process-operator` через service account roles.

## Security Module

### Зависимости (`security/build.gradle.kts`)

```kotlin
plugins {
    id("java-library")
}

dependencies {
    api(project(":core"))
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.autoconfigure)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
}
```

### Новые зависимости в `libs.versions.toml`

```toml
[libraries]
spring-boot-starter-oauth2-resource-server = { module = "org.springframework.boot:spring-boot-starter-oauth2-resource-server", version.ref = "spring-boot" }
spring-security-test = { module = "org.springframework.security:spring-security-test", version = "6.3.4" }
```

### Классы

#### `SecurityProperties.java`

```java
@ConfigurationProperties(prefix = "process-engine.security")
public class SecurityProperties {
    private boolean enabled = true;
    private String issuerUri;           // http://localhost:8180/realms/process-engine
    private String jwkSetUri;           // auto-derived from issuerUri if not set
    private String roleClaimPath = "realm_access.roles"; // путь к ролям в JWT
}
```

#### `ResourceServerConfig.java`

```java
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "process-engine.security.enabled", havingValue = "true", matchIfMissing = true)
public class ResourceServerConfig {

    // SecurityFilterChain:
    // - /actuator/** → permitAll
    // - /api/v1/definitions POST, DELETE → hasAnyRole(ADMIN, DEPLOYER)
    // - /api/v1/definitions/validate → hasAnyRole(ADMIN, DEPLOYER)
    // - /api/v1/definitions GET → hasAnyRole(ADMIN, OPERATOR, VIEWER, DEPLOYER)
    // - /api/v1/instances POST → hasAnyRole(ADMIN, OPERATOR)
    // - /api/v1/instances/{id}/suspend, resume → hasAnyRole(ADMIN, OPERATOR)
    // - /api/v1/instances/{id} DELETE → hasAnyRole(ADMIN, OPERATOR)
    // - /api/v1/instances GET → hasAnyRole(ADMIN, OPERATOR, VIEWER)
    // - /api/v1/variables PUT → hasAnyRole(ADMIN, OPERATOR)
    // - /api/v1/variables GET → hasAnyRole(ADMIN, OPERATOR, VIEWER)
    // - /api/v1/messages POST → hasAnyRole(ADMIN, OPERATOR)
    // - /api/v1/history/** → hasAnyRole(ADMIN, OPERATOR, VIEWER)
    // - /api/v1/incidents GET → hasAnyRole(ADMIN, OPERATOR, VIEWER)
    // - /api/v1/incidents/{id}/resolve → hasAnyRole(ADMIN, OPERATOR)
    // - все остальные → authenticated
}
```

#### `KeycloakJwtAuthenticationConverter.java`

```java
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    // Извлекает роли из claim "realm_access.roles"
    // Маппит в GrantedAuthority с префиксом "ROLE_"
    // Пример: "process-admin" → ROLE_PROCESS_ADMIN
}
```

#### `ProcessEngineRole.java`

```java
public enum ProcessEngineRole {
    PROCESS_ADMIN("process-admin"),
    PROCESS_OPERATOR("process-operator"),
    PROCESS_VIEWER("process-viewer"),
    PROCESS_DEPLOYER("process-deployer");

    private final String keycloakRole;
    // ...
}
```

#### `SecurityDisabledFilter.java`

```java
@ConditionalOnProperty(name = "process-engine.security.enabled", havingValue = "false")
// Когда security отключён — все запросы проходят без аутентификации
// SecurityFilterChain с permitAll для всех путей
```

### Auto-configuration

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:
uz.salvadore.processengine.security.autoconfigure.SecurityAutoConfiguration
uz.salvadore.processengine.security.autoconfigure.KeycloakJwtAutoConfiguration
```

## Конфигурация (`application.yml`)

Новые параметры:

```yaml
process-engine:
  security:
    enabled: ${PROCESS_ENGINE_SECURITY_ENABLED:true}
    issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/process-engine}

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/process-engine}
```

## Переменные окружения

| Переменная | Property | По умолчанию | Описание |
|-----------|----------|-------------|----------|
| `PROCESS_ENGINE_SECURITY_ENABLED` | `process-engine.security.enabled` | `true` | Включить/выключить security |
| `KEYCLOAK_ISSUER_URI` | `process-engine.security.issuer-uri` | `http://localhost:8180/realms/process-engine` | Keycloak realm URI |
| `KEYCLOAK_ADMIN` | — | `admin` | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | — | `admin` | Keycloak admin password |
| `KEYCLOAK_PORT` | — | `8180` | Keycloak HTTP порт |

## Docker Compose

Добавить сервис Keycloak:

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    container_name: process-engine-keycloak
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN:-admin}
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-admin}
      KC_HTTP_PORT: 8180
    ports:
      - "${KEYCLOAK_PORT:-8180}:8180"
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
      - keycloak-data:/opt/keycloak/data
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/localhost/8180 && echo -e 'GET /health/ready HTTP/1.1\r\nHost: localhost\r\n\r\n' >&3 && cat <&3 | grep -q '200'"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s

  process-engine:
    # ... существующая конфигурация ...
    environment:
      PROCESS_ENGINE_RABBITMQ_HOST: rabbitmq
      KEYCLOAK_ISSUER_URI: http://keycloak:8180/realms/process-engine
    depends_on:
      rabbitmq:
        condition: service_healthy
      keycloak:
        condition: service_healthy

volumes:
  rabbitmq-data:
  keycloak-data:
```

## Keycloak Realm Export (`keycloak/realm-export.json`)

Полный JSON-файл для автоматического импорта realm при первом запуске Keycloak. Содержит:

- Realm `process-engine` (enabled, registration allowed = false)
- Client `process-engine-api` (public, standard flow enabled, PKCE)
  - Valid redirect URIs: `http://localhost:8080/*`, `http://localhost:3000/*`
  - Web origins: `*`
- Client `process-engine-service` (confidential, service accounts enabled)
  - Secret: настраивается через env variable
- 4 realm roles: `process-admin`, `process-operator`, `process-viewer`, `process-deployer`
- 4 тестовых пользователя с соответствующими ролями
- Token settings: access token lifespan = 5 min, refresh token = 30 min

## Матрица доступа

| Эндпоинт | Method | admin | operator | viewer | deployer |
|----------|--------|-------|----------|--------|----------|
| `/api/v1/definitions` | POST | :white_check_mark: | :x: | :x: | :white_check_mark: |
| `/api/v1/definitions/validate` | POST | :white_check_mark: | :x: | :x: | :white_check_mark: |
| `/api/v1/definitions` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| `/api/v1/definitions/{key}` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| `/api/v1/definitions/{key}/versions` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| `/api/v1/definitions/{key}` | DELETE | :white_check_mark: | :x: | :x: | :white_check_mark: |
| `/api/v1/instances` | POST | :white_check_mark: | :white_check_mark: | :x: | :x: |
| `/api/v1/instances/{id}` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| `/api/v1/instances/{id}/suspend` | PUT | :white_check_mark: | :white_check_mark: | :x: | :x: |
| `/api/v1/instances/{id}/resume` | PUT | :white_check_mark: | :white_check_mark: | :x: | :x: |
| `/api/v1/instances/{id}` | DELETE | :white_check_mark: | :white_check_mark: | :x: | :x: |
| `/api/v1/instances/{id}/variables` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| `/api/v1/instances/{id}/variables` | PUT | :white_check_mark: | :white_check_mark: | :x: | :x: |
| `/api/v1/instances/{id}/variables/{name}` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| `/api/v1/messages` | POST | :white_check_mark: | :white_check_mark: | :x: | :x: |
| `/api/v1/history/**` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| `/api/v1/incidents` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| `/api/v1/incidents/{id}` | GET | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| `/api/v1/incidents/{id}/resolve` | PUT | :white_check_mark: | :white_check_mark: | :x: | :x: |
| `/actuator/**` | GET | public | public | public | public |

## Тестирование

### Модуль `security` (~8 тестов)

| Тест | Тип | Описание |
|------|-----|----------|
| `SecurityAutoConfigurationTest` | Unit | Проверка создания beans при enabled=true/false |
| `KeycloakJwtConverterTest` | Unit | Маппинг realm_access.roles → GrantedAuthority |
| `ResourceServerConfigTest` | Integration | SecurityFilterChain корректно создаётся |
| `SecurityDisabledTest` | Integration | При enabled=false все запросы проходят |

### Модуль `rest-api` — обновить существующие тесты (~6 новых)

| Тест | Тип | Описание |
|------|-----|----------|
| `DefinitionControllerSecurityTest` | @WebMvcTest | Проверка ролей для deploy/undeploy/list |
| `InstanceControllerSecurityTest` | @WebMvcTest | Проверка ролей для start/suspend/resume/terminate |
| `VariableControllerSecurityTest` | @WebMvcTest | Проверка ролей для read/write variables |
| `MessageControllerSecurityTest` | @WebMvcTest | Проверка ролей для send message |
| `HistoryControllerSecurityTest` | @WebMvcTest | Проверка ролей для history endpoints |
| `IncidentControllerSecurityTest` | @WebMvcTest | Проверка ролей для incidents |

### Подход к тестированию

- `@WithMockUser(roles = "PROCESS_ADMIN")` для unit-тестов контроллеров
- `spring-security-test` `SecurityMockMvcRequestPostProcessors.jwt()` для @WebMvcTest
- Существующие тесты контроллеров обновить: добавить `.with(jwt().authorities(...))` к MockMvc запросам
- Integration test: Testcontainers Keycloak для полного E2E (опционально)

## Изменения в существующих модулях

### `rest-api/build.gradle.kts`

```kotlin
dependencies {
    implementation(project(":security"))  // ★ ДОБАВИТЬ
    // ... остальное без изменений
}
```

### `settings.gradle.kts`

```kotlin
include("core", "rabbitmq-transport", "spring-integration", "security", "rest-api")
```

### `.env/local.env` — новые переменные

```env
# === Keycloak ===
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin
KEYCLOAK_PORT=8180
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/process-engine
PROCESS_ENGINE_SECURITY_ENABLED=true
```

## План реализации

### Phase 8A: Security Module (модуль + auto-configuration)

1. Создать `security/build.gradle.kts`
2. Обновить `settings.gradle.kts`
3. Обновить `libs.versions.toml` (spring-security зависимости)
4. Реализовать `ProcessEngineRole`, `SecurityProperties`
5. Реализовать `KeycloakJwtAuthenticationConverter`
6. Реализовать `ResourceServerConfig` + `SecurityDisabledFilter`
7. Реализовать `SecurityAutoConfiguration` + `KeycloakJwtAutoConfiguration`
8. Тесты модуля security (~8)

### Phase 8B: Keycloak Provisioning

1. Создать `keycloak/realm-export.json` с полной конфигурацией realm
2. Обновить `docker-compose.yaml` — добавить Keycloak сервис
3. Обновить `.env/local.env` — добавить Keycloak переменные
4. Обновить `application.yml` — добавить security конфигурацию

### Phase 8C: REST API Security Integration

1. Добавить зависимость `project(":security")` в `rest-api`
2. Обновить существующие @WebMvcTest тесты (добавить JWT mock)
3. Добавить security-тесты для каждого контроллера (~6)
4. Проверить все 324+ тестов проходят

## Open Questions

- Нужен ли audit log (кто выполнил какое действие)?
- Нужна ли multi-tenancy (разные tenant'ы видят только свои процессы)?
- Нужен ли rate limiting по ролям?
- Keycloak themes (кастомная страница логина)?
