# Чек-лист перед выходом в прод

Схема: приложение и Keycloak стоят на отдельных серверах и связаны через WireGuard. MySQL и Kafka работают своими кластерами. Перед каждым сервисом стоит nginx с TLS. `docker/docker-compose.yml` используется только для разработки и в прод не попадает.

## Keycloak

### Запуск
- [ ] Keycloak запущен командой `start`, а не `start-dev`.
- [ ] Пароль администратора Keycloak не `admin/admin`, а консоль администратора закрыта от интернета: доступ только через WireGuard/VPN.
- [ ] Keycloak хранит данные во внешней БД, а не во встроенной H2.
- [ ] `KC_HOSTNAME` содержит публичный адрес с `https://`, например `https://auth.example.com`.
- [ ] `KC_PROXY_HEADERS=xforwarded` выставлен, потому что Keycloak стоит за nginx (см. раздел «nginx»).

### Реалм
- [ ] Создан реалм `processing` с включённым тумблером «Enabled».
- [ ] В «Realm roles» созданы роли `ADMIN`, `OPERATOR` и `CLIENT`.
- [ ] Роль `CLIENT` назначена ролью по умолчанию: «Realm settings → User registration → Default roles → Assign role → CLIENT».
- [ ] В «Realm settings → Security defenses → Brute force detection» включена защита от перебора паролей.
- [ ] Саморегистрация пользователей («Realm settings → Login → User registration») включена или выключена осознанно.

### Передача ролей в токене
Приложение читает роли из **ID-токена**, из claim `realm_access.roles`, а маппер Keycloak по умолчанию кладёт их только в access token.
- [ ] В «Client scopes → roles → Mappers → realm roles» включён тумблер «Add to ID token».
- [ ] Значение «Token Claim Name» в этом маппере осталось `realm_access.roles`.

### Клиент приложения
- [ ] Создан клиент с Client ID `processing`, «Client authentication» включён (confidential), «Standard flow» включён.
- [ ] «Valid redirect URIs» содержит `https://<домен приложения>/login/oauth2/code/keycloak-bff`.
- [ ] «Valid post logout redirect URIs» содержит `https://<домен приложения>/`.
- [ ] Client secret из вкладки «Credentials» перенесён в `spring.security.oauth2.client.registration.keycloak-bff.client-secret` конфига приложения.

### Back-channel logout
Без него пользователь, заблокированный в Keycloak, сохраняет доступ к приложению до истечения сессии.
- [ ] В клиенте `processing` поле «Backchannel logout URL» содержит `https://<домен приложения>/logout/connect/back-channel/keycloak-bff`.
- [ ] Тумблер «Backchannel logout session required» включён.
- [ ] Keycloak может достучаться до этого URL: если запрос идёт через WireGuard, домен приложения должен разрешаться в адрес, доступный с сервера Keycloak.

### Темы
- [ ] Папка `frontend-keycloak/processing` (в ней темы `login` и `account`) скопирована на сервер Keycloak в `/opt/keycloak/themes/processing`.
- [ ] В «Realm settings → Themes» выбраны «Login theme» = `processing` и «Account theme» = `processing`.
- [ ] После обновления тем на проде Keycloak перезапущен или кэш тем сброшен: в режиме `start` темы кэшируются.

## Приложение

### Конфиг `config/config.yml`
- [ ] В `spring.profiles.active` нет `disable-security`, `md-test-callback`, `mh-test` и `dev`.
- [ ] `spring.security.oauth2.client.provider.keycloak-provider.issuer-uri` указывает на **публичный** адрес реалма, `https://auth.example.com/realms/processing`. Он должен совпадать с `iss` в токенах.
- [ ] Сервер приложения может открыть этот публичный адрес. Если трафик к Keycloak должен идти через WireGuard, на сервере приложения домен Keycloak указывает на его WireGuard-адрес (например, через `/etc/hosts`), и nginx Keycloak слушает на этом интерфейсе с тем же сертификатом.
- [ ] `processing.uri.manage-account` указывает на публичный адрес: `https://auth.example.com/realms/processing/account`.
- [ ] `processing.uri.manage-users` указывает на публичный адрес консоли администратора Keycloak: `https://auth.example.com/admin/processing/console/`.
- [ ] Уровни логирования `org.springframework.security` и `org.springframework.web` не `DEBUG`.
- [ ] Для доступа к MySQL и Kafka используются отдельные пользователи с минимальными правами, а не `root`.

### Сеть
- [ ] Порт приложения (`8080`) открыт только для nginx, напрямую из интернета он недоступен.
- [ ] Порт actuator (`8091`, задан в `application.yml`) доступен только из внутреннего VPN: `management.server.address` указывает на VPN-адрес сервера, а в Docker порт публикуется только на этот адрес. Через nginx порт не проксируется. Без авторизации на нём открыты только `/actuator/health` и `/actuator/prometheus`.
- [ ] Приложение запускается без `-agentlib:jdwp`: отладчик в проде даёт возможность выполнить код на сервере.

### Задачи в коде, которые ещё не сделаны
- [ ] Решить, что делать с пользователями без ролей: сейчас они получают сессию, хотя ни одно API им не доступно.

## nginx

### Перед приложением
nginx снимает TLS и передаёт приложению сведения об исходном запросе. Без этих заголовков приложение считает запрос HTTP-шным: куки уходят без `Secure`, а адреса возврата для Keycloak строятся с `http://`.

```nginx
server {
    listen 443 ssl http2;
    server_name app.example.com;

    ssl_certificate     /etc/ssl/app.example.com/fullchain.pem;
    ssl_certificate_key /etc/ssl/app.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host  $host;
        proxy_set_header X-Forwarded-Port  $server_port;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    }
}

server {
    listen 80;
    server_name app.example.com;
    return 301 https://$host$request_uri;
}
```

- [ ] nginx выставляет заголовки `X-Forwarded-*` сам и перезаписывает те, что прислал клиент.
- [ ] HTTP перенаправляется на HTTPS.
- [ ] Если nginx стоит не на сервере приложения, его адрес входит во внутренние диапазоны Tomcat (`10.x`, `192.168.x`, `172.16–31.x`, `127.x`) или явно указан в `server.tomcat.remoteip.internal-proxies`. Иначе приложение проигнорирует заголовки.

### Перед Keycloak
- [ ] nginx перед Keycloak передаёт те же заголовки `X-Forwarded-*`, а у Keycloak выставлен `KC_PROXY_HEADERS=xforwarded`.
- [ ] Путь `/admin` закрыт снаружи, если консоль администратора не должна быть доступна из интернета.

## Проверка после выкатки
- [ ] После входа куки `JSESSIONID` и `XSRF-TOKEN` в DevTools отмечены флагом `Secure`.
- [ ] Ответы приходят с заголовком `Strict-Transport-Security`.
- [ ] Запрос к `/api/v1/order` с кукой сессии, но без `Api-Key` получает 401.
- [ ] Если заблокировать пользователя в Keycloak, следующий его запрос к приложению перестаёт проходить.
