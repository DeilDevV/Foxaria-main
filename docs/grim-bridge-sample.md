# Пример anti-cheat bridge

Foxaria даёт командный bridge для внешнего античита, чтобы не жёстко зависеть от внутренних API стороннего плагина.

## Команды Foxaria

- `/security verbose`
- `/grimhook alert <player> <check> <details...>`
- `/grimhook punish <player> <check> <details...>`

## Рекомендуемая интеграция

Настрой внешний античит так, чтобы alerts/punishments вызывали команды вида:

```text
grimhook alert %player% %check% %verbose%
grimhook punish %player% %check% punished
```

Эффект:

- события пишутся в `fx_security_events`
- в аудит добавляется trail
- стафф с правом verbose может получать live feed через `/security verbose`

Подход специально runtime-decoupled, чтобы Foxaria продолжал работать даже при изменениях внутреннего API внешнего античита.
