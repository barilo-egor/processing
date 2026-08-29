<h1>Описание</h1>
Сервис, обеспечивающий взаимодействие трейдеров и мерчантов.
<hr>
<h1>Первый запуск</h1>
<ul>
    <li>
        Запустить задачу <code>processingStartUp</code>.
    </li>
    <li>
        Перейти в браузере по адресу <code>http://localhost:8081</code>, 
авторизоваться логином <code>admin</code> и паролем <code>admin</code>.
    </li>
    <li>
        Перейти в "Manage realms", создать реалм с названием <code>processing</code>, тумблер "Enabled" должен быть включен.
        После создания щелкнуть на созданный реалм, чтобы около него была надпись "Current realm".
    </li>
    <li>
        Перейти в "Realm roles", создать три роли с именами: <code>ADMIN</code>, <code>OPERATOR</code>, <code>USER</code>.
    </li>
    <li>
        Перейти в Users, создать необходимых пользователей(для начала достаточно одного администратора):
        <ul>
            <li>
                Required user actions - остается пустым.
            </li>
            <li>
                Email verified - On.
            </li>
            <li>
                Username - произвольный.
            </li>
            <li>
                Email - произвольный.
            </li>
        </ul>
    </li>
    <li>
        Далее необходимо открыть созданного пользователя, перейти на вкладку <code>Role mapping</code> и добавить необходимые роли.
    </li>
    <li>
        Вернуться в проект, в корне создать директорию config и в ней файл config.yml:
<pre><code>
spring:
  datasource:
    username: "root"
    password: ''
    url: "jdbc:mysql://mysql:3306/processing"
    driver-class-name: "com.mysql.cj.jdbc.Driver"
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/realms/processing
      client:
        registration:
          keycloak-bff:
            provider: keycloak-provider
            client-id: processing
            client-secret: # СЮДА НЕОБХОДИМО ВСТАВИТЬ client-secret ИЗ keycloak Clients->Нужный клиент->Вкладка Credentials
            authorization-grant-type: authorization_code
            scope: openid,profile
        provider:
          keycloak-provider:
            issuer-uri: http://localhost:8081/realms/processing
  kafka:
    bootstrap-servers: 'kafka:9092'
cache:
  ttl:
    client-get-seconds: 3600
kafka:
  group-id: 'processing'
  topic:
    merchant-details:
      callback: 'merchant-details-callback-v1'
processing:
  keycloak:
    webhook:
      username: 'webhook_user'
      password: '{noop}webhook_password'
logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.web: DEBUG
</code></pre>
    </li>
    <li>
        Открыть Docker desktop и перезапустить контейнер <code>processing</code>
    </li>
</ul>
<hr>
<h1>Spring-профили</h1>
<h3>dev:</h3>
<ul>
  <li>Допускает значения для <code>spring.jpa.hibernate.ddl-auto</code> отличные от <code>validate</code>.</li>
</ul>
<h3>disable-security:</h3>
<ul>
    <li>Все маппинги становятся доступные без авторизации.</li>
    <li>Доступен заголовок <code>Test-User</code> для имитации клиента, в значении которого можно передать идентификатор 
из Keycloak(он же идентификатор сущности <code>Client</code>) и роль через точку с запятой. 
Например: <code>f74acb2c-23a0-41c5-b7bb-d84c64fc089d;ROLE_CLIENT</code>. В security-контекст будет подставлен 
<code>Principal</code> с данными значениями(все запросы будут считаться запросами от переданного в заголовке клиента).</li>
</ul>
<hr>
<h1>Словарь констант</h1>
Доступен по адресу <code>/api/private/dictionary</code>. Для добавления нового значения необходимо 
создать реализацию интерфейса <code>net.rcetech.meta.DictionaryField</code> и поместить ее в spring контекст.


<hr>
<h1>Gradle tasks</h1>
<ul>
    <li>
        <code>processingStartUp</code> - запускает инфраструктуру и приложение в докер контейнерах. Необходимы docker и docker compose.
 Также необходима директория <code>config</code> с конфигурационным файлом <code>config.yml</code> в ней. После первого запуска необходимо 
создать реалм и клиента в keycloak, а также добавить реквизиты для входа клиента в конфигурацию и перезапустить контейнер processing.<br>
    </li>
    <li>
        <code>processingShutdown</code> - останавливает докер контейнеры, запущенные задачей <code>processingStartUp</code>.
    </li>
    <li>
        <code>processingRedeployJar</code> - переподкидывает конфигурацию и jar архив приложения и перезапускает контейнер.
    </li>
</ul>
<hr>
<h1>Метрики для Prometheus</h1>
Ключи метрик с описанием доступны в классе <code>net.rcetech.meta.MetricsConstants</code>.