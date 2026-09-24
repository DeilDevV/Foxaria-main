## Lobby server (Paper backend) для Foxaria

Это **backend-сервер**, к которому игроки подключаются **только через прокси** (`servers/proxy`).

### Главное правило
Игроки не должны иметь возможность зайти на lobby напрямую. Закрой порт lobby в firewall.

### Порты по умолчанию
- Proxy: `25565`
- Auth backend (`servers/auth`): `25566`
- Lobby backend (эта папка): `25567`
- Game backend (`servers/game`): `25568`

### Настройки, которые должны быть включены (обязательно)
1) `server.properties`
   - `online-mode=false`
   - `server-port=25567`
   - `server-ip=127.0.0.1` (если всё на одной машине) или внутренний IP

2) `spigot.yml`
   - `settings.bungeecord: true`

3) `config/paper-global.yml` (создастся при первом запуске)
   - `proxies.bungee-cord.online-mode: true`
   - `proxies.velocity.enabled: false`

### Плагины
Пока что здесь не требуется отдельный набор плагинов.
Позже мы подключим общий Foxaria-jar и сетевые модули (донат/права/привязки), которые должны работать на всех backend.

### Запуск
1) Положи Paper jar в эту папку под именем `paper.jar`
2) Запусти `start-lobby.bat` или `start-lobby.ps1`

