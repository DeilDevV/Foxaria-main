# Foxaria

Репозиторий плагина **Foxaria** (много модулей Gradle) и тестового сервера.

## Что куда смотреть

- **`test-server/`** — рабочая копия сервера Paper: миры, `plugins/`, скрипты запуска. Именно её имеет смысл копировать на машину как «один готовый сервер».
- **`foxaria-*`**, **`foxaria-bootstrap/`**, `build.gradle`, `settings.gradle` — **исходный код плагина**. Без этих папок нельзя собрать обновлённый `Foxaria.jar`. Их не стоит удалять, если планируешь менять функциональность или получать фиксы.

Сборка плагина (нужна JDK 17+):

```bat
gradlew.bat :foxaria-bootstrap:shadowJar
```

Деплой в тестовый сервер: `test-server\update-foxaria.cmd`.

## Конфигурация Foxaria на сервере

Всё в **`test-server/plugins/Foxaria/`** (`config.yml`, `messages.yml`, `modules/*.yml`).
