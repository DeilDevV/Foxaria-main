## Auth server (Paper backend) для Foxaria

Этот сервер используется **только для входа/регистрации**.  
Геймплея тут нет: пустая/техническая карта, после логина игрок переводится в `lobby`.

### Роль в сети
- `servers/proxy` принимает игроков и отправляет на `auth`.
- `auth` проверяет регистрацию/логин.
- после успешного входа игрок переводится на `lobby`, а оттуда выбирает `test-game`.

### Порты по умолчанию
- Proxy: `25565`
- Auth backend (эта папка): `25566`
- Lobby backend: `25567`
- Game backend (`servers/game`): `25568`

### Обязательные настройки
1) `server.properties`
   - `online-mode=false`
   - `server-ip=127.0.0.1`
   - `server-port=25566`

2) `spigot.yml`
   - `settings.bungeecord: true`

3) `config/paper-global.yml` (после первого запуска)
   - `proxies.bungee-cord.online-mode: true`
   - `proxies.velocity.enabled: false`

### Что важно
- Порт `25566` закрыт снаружи (firewall), доступ только от прокси.
- На этом сервере не держим игровую карту/ресурсы.
- `servers/game` — это твой игровой backend с основной картой.

