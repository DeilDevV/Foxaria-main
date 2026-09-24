# Flow выдачи Tebex / Store

## Queue strategy

1. Покупка завершается на стороне checkout/store.
2. Tebex или внешний worker отправляет команду / callback.
3. Foxaria ставит запрос в очередь `fx_tebex_fulfillments`.
4. Async worker забирает `PENDING` или `FAILED` jobs.
5. Выполняется grant/revoke action.
6. При успехе статус становится `FULFILLED`, при ошибке растёт счётчик retry.

## Правило идемпотентности

- каждый заказ Tebex должен иметь стабильный внешний transaction id
- Foxaria хранит lifecycle-варианты как:
  - `<transaction>#grant`
  - `<transaction>#revoke`
- поэтому повторное выполнение не должно выдавать пакет дважды

## Поддерживаемые action types

- `rank:<group>`
- `temp_rank:<group>:<seconds>`
- `subscription:<group>:<seconds>`
- `remove_rank:<group>`
- `coins:<amount>`
- `take_coins:<amount>`
- `command:<console command>`
- `command_batch:<cmd1||cmd2||cmd3>`

## Subscription lifecycle

- `subscription:*` пишет запись в `fx_store_subscriptions`
- revoke flow может перевести подписку в статус `REVOKED`
- это даёт аудитируемый donor lifecycle без жёсткой сцепки с внутренностями Tebex

## Official queue mode

Foxaria умеет работать и через официальный queue polling mode Tebex.

Поведение:

1. poll `/queue`
2. соблюдать `next_check`
3. забирать offline-команды, если это разрешено
4. забирать online-команды для подходящих игроков
5. валидировать slot conditions
6. dispatch на main thread
7. подтверждать выполненные command ids через `DELETE /queue`
