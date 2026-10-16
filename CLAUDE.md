# CLAUDE.md

Этот файл содержит инструкции для Claude Code (claude.ai/code) по работе с кодом в этом репозитории.

## Обзор

Сервис на Spring Boot 4 / Java 21, обеспечивающий взаимодействие трейдеров и мерчантов («процессинг»). Это многомодульный Gradle-монолит: одно запускаемое приложение (`processing-app`) собирается из функциональных модулей. Аутентификация через Keycloak (OIDC-вход + JWT resource server). Хранение данных в MySQL с Flyway, также используются Kafka, gRPC и два фронтенда на React/Vite. Комментарии в коде, документация и `@DisplayName` в тестах пишутся на русском.

## Команды

```bash
./gradlew build                                   # компиляция + все тесты (тестам нужен Docker: Testcontainers MySQL/Kafka)
./gradlew :processing-orders:test                 # тесты одного модуля
./gradlew :processing-domain:test --tests 'net.rcetech.domain.service.clients.ClientServiceTest'
./gradlew :processing-domain:test --tests '*ClientServiceTest.save_shouldSaveWithDefaultValues'
./gradlew :processing-app:bootJar                 # -> build/libs/processing.jar (build-директория корня)
./gradlew :processing-api:generateProto           # перегенерировать gRPC-стабы из processing-api/src/main/proto
```

Локальное окружение (docker compose в `docker/`: MySQL 3306, Kafka 9092 внутри сети / 9094 с хоста, Keycloak 8081, отладчик приложения на 5005):

- `./gradlew processingStartUp`: собирает jar и фронтенды, копирует их вместе с `config/` в `docker/mount/processing`, копирует темы `frontend-keycloak`, затем выполняет `docker compose up -d --build`.
- `./gradlew processingRedeployJar`: пересобирает и пересоздаёт только контейнер `processing`.
- `./gradlew processingRedeployFrontend`: пересобирает фронтенды и копирует их в docker mount.
- `./gradlew processingShutdown`: `docker compose down`.
- На системах кроме Windows задачи вызывают `/usr/local/bin/docker` напрямую.

Контейнер `processing` использует сетевое пространство Keycloak (`network_mode: service:keycloak`), поэтому приложение доступно через порты, опубликованные у сервиса keycloak.

Фронтенды (`frontend/` — npm workspace с `client` и `support`): `npm run dev:client` / `npm run dev:support`, `npm run build`. Vite складывает результат сборки в `config/frontend/<app>`, а Spring раздаёт его как статические ресурсы (`spring.web.resources.static-locations: file:config/frontend/`).

## Конфигурация

- `processing-app/src/main/resources/application.yml` импортирует `file:config/config.yml` (в .gitignore, у каждого разработчика свой) и `classpath:application-domain.yml`. Шаблон локального конфига — `config_template/config.yml`. В README.md описана первичная настройка Keycloak: реалм `processing`, роли `ADMIN`/`OPERATOR`/`CLIENT`, client secret.
- Spring-профили (см. README и `net.rcetech.meta.Profiles`):
  - `dev`: допускает значения `ddl-auto`, отличные от `validate`.
  - `disable-security`: всё открыто без авторизации, заголовок `Test-User: <keycloakId>;ROLE_X` подставляет principal.
  - `md-test-details`, `md-test-callback`, `mh-test`: заглушки внешних микросервисов merchant-details и merchant-history.
- Изменения схемы БД — только Flyway-миграциями в `processing-domain/src/main/resources/database/migration` (`ddl-auto: validate`).

## Архитектура

Направление зависимостей модулей: `meta` ← `rates` ← `domain` ← функциональные модули (`clients`, `orders`, `billing`, `support`, `api`, `security`, `web`) ← `app`. Convention-плагин `buildSrc/src/main/groovy/processing.gradle` (подключается как `id 'processing'`) задаёт Java toolchain, BOM-ы Boot/Cloud, Lombok, actuator/Prometheus и MapStruct `defaultComponentModel=spring`. Во всех модулях, кроме `app`, `bootJar` отключён.

- **processing-meta**: общий слой контрактов без доменного кода и персистентности. Содержит DTO, enum-ы, события, исключения, `GlobalControllerAdvice`, константы `WebPath`, `MetricsConstants` и глобальную конфигурацию безопасности.
- **processing-domain**: все JPA-сущности, репозитории, MapStruct-мапперы, основные доменные сервисы и Flyway-миграции. Функциональные модули зависят от него, а не определяют собственные сущности.
- **Функциональные модули**: контроллеры, Kafka-консьюмеры и обработчики событий своей области. `processing-api` — API для мерчантов: `/api/v1`, gRPC-клиенты/серверы и `.proto`-файлы.

Сквозные паттерны:

- **Безопасность**: `MetaSecurityConfig` строит основной `SecurityFilterChain` с правилами URL→роль. Модули дополняют эту цепочку, регистрируя бины `SpringSecurityConfigurer`; отдельная цепочка есть только у `/api/v1/**`. Префиксы URL берутся из `WebPath`:
  - `/api/support/**`: роли `ADMIN`/`OPERATOR`, сессия + CSRF.
  - `/api/client/**`: роль `CLIENT`, кабинет клиента, сессия + CSRF.
  - `/api/common/**`: любой вошедший пользователь (словарь).
  - `/api/v1/**`: программное API мерчантов, отдельная stateless-цепочка `ApiSecurityConfig`, только заголовок `Api-Key`; сессия игнорируется, CSRF не проверяется.
  - `/api/public/**`: открыто, CSRF не проверяется.
  - Иерархия ролей: `ADMIN` включает `OPERATOR`.
- **Словарь констант**: `/api/common/dictionary`. Чтобы добавить значение, реализуйте `net.rcetech.meta.DictionaryField` и зарегистрируйте как Spring-бин.
- **Взаимодействие модулей**: модули общаются через Spring application events (классы `*Event` в meta, обрабатываются слушателями вроде `TransactionEventListener` и `TransactionListener`) и через Kafka, а не через прямые вызовы внутренностей друг друга.
- **Метрики**: ключи метрик лежат в `net.rcetech.meta.MetricsConstants`.
- **Postman-коллекция**: `processing.postman_collection.json` документирует HTTP API, в том числе как инициировать тестовые колбэки мерчантов.

## Тесты

- JUnit 5 со Spring slice-тестами (`@DataJpaTest`, `@WebMvcTest`).
- Тесты персистентности поднимают собственный `MySQLContainer("mysql:8.0.x")` через `@Testcontainers` + `@DynamicPropertySource`. Общего базового класса нет.
- WireMock (`wiremock-spring-boot`) заменяет внешние HTTP-сервисы.
- У каждого модуля свой `src/test/resources/application.yml`.
- В тестах активно используется `@ParameterizedTest` с `@DisplayName` на русском.
