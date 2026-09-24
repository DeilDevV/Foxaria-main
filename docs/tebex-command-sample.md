# Примеры Tebex-команд

Рекомендуемые команды для пакетов:

```text
storegrant grant {transaction} {username} supporter_rank
storegrant grant {transaction} {username} vip_30d
storegrant grant {transaction} {username} coins_500
```

Рекомендуемые revoke / chargeback команды:

```text
storegrant revoke {transaction} {username} supporter_rank
storegrant revoke {transaction} {username} vip_30d
storegrant revoke {transaction} {username} coins_500
```

Примечания:

- `{transaction}` должен быть стабильным Tebex order/transaction id
- `{username}` переводится в UUID через Bukkit offline lookup
- поведение пакета задаётся в `modules/store.yml`
- для подписок лучше использовать `subscription:*` вместе с `remove_rank:*`
