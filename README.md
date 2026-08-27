<h1>Описание</h1>
Сервис, обеспечивающий взаимодействие трейдеров и мерчантов.
<hr>
<h1>Spring-профили</h1>
<h3>dev:</h3>
<ul>
  <li>Допускает значения для <code>spring.jpa.hibernate.ddl-auto</code> отличные от <code>validate</code>.</li>
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