# 🦊 FOXARIA — Сайт Minecraft Сервера

Полноценный сайт для Minecraft сервера с личным кабинетом, магазином, бан-листом, новостями и многим другим.

## 📦 Технологии
- **Frontend:** React 18 + Vite + Tailwind CSS
- **Backend:** Node.js + Express
- **База данных:** MySQL / MariaDB (ваша БД сервера)
- **Платежи:** YooKassa, FreeKassa, Robokassa, CryptoBot, LAVA

---

## ⚡ Быстрый старт

### 1. Настройка конфига (самое важное!)

Откройте файл `config.js` в корне папки SITE-FOXARIA и заполните:
- Данные для подключения к вашей **базе данных** MySQL
- **RCON** настройки для каждого сервера (включите RCON в `server.properties`)
- **Платёжные системы** (минимум одну)
- Измените `jwt.secret` на случайную строку (32+ символа)

### 2. Установка и запуск Backend

```bash
cd SITE-FOXARIA/backend
npm install
npm start
# Или для разработки:
npm run dev
```

### 3. Установка и запуск Frontend

```bash
cd SITE-FOXARIA/frontend
npm install
npm run dev
```

Сайт откроется на http://localhost:5173

---

## 🗄️ Требования к серверу

### Minecraft плагины (рекомендуемые)
| Плагин | Назначение |
|--------|-----------|
| **LuckPerms** | Права и группы (ОБЯЗАТЕЛЬНО) |
| **LiteBans** | Система банов |
| **AuthMe** | Авторизация (опционально) |
| **GriefPrevention** | Система приватов |
| **Guilds** | Гильдии |

### Настройки server.properties для RCON
```properties
enable-rcon=true
rcon.port=25575
rcon.password=ВАШ_ПАРОЛЬ
```

---

## 💳 Подключение платёжных систем

### YooKassa (рекомендуется)
1. Зарегистрируйтесь на [yookassa.ru](https://yookassa.ru)
2. Настройки магазина → API ключи
3. Заполните `payments.yookassa.shopId` и `payments.yookassa.secretKey` в `config.js`
4. В ЮKassa настройте webhook: `https://ВАШ_ДОМЕН/api/payments/webhook/yookassa`
5. Установите `enabled: true`

### FreeKassa
1. Зарегистрируйтесь на [freekassa.ru](https://freekassa.ru)
2. Мои магазины → Настройки
3. Заполните merchantId, secretWord1, secretWord2 в `config.js`
4. В FreeKassa укажите URL уведомлений: `https://ВАШ_ДОМЕН/api/payments/webhook/freekassa`
5. Установите `enabled: true`

### CryptoBot (Telegram)
1. Откройте [@CryptoBot](https://t.me/CryptoBot) в Telegram
2. Напишите `/pay` → My Apps → Create App
3. Скопируйте токен в `payments.cryptobot.token`
4. Установите `enabled: true`

---

## 🛒 Добавление товаров в магазин

Через **Панель управления** (нужна роль Admin):
1. Войдите в аккаунт
2. Перейдите в `/admin` → вкладка Магазин
3. Создайте категорию и добавьте товары

Команды для товаров (формат JSON):
```json
["lp user {player} parent set vip", "give {player} diamond 5"]
```

Переменные в командах:
- `{player}` — никнейм покупателя
- `{amount}` — сумма покупки
- `{item}` — название товара

---

## 🔧 Настройка под свою БД

Если у вас нестандартные таблицы плагинов, измените имена в `config.js → plugins`:

```js
plugins: {
  luckperms: {
    playersTable: 'luckperms_players',     // Изменить если нужно
    // ...
  },
  litebans: {
    bansTable: 'litebans_bans',            // Изменить если нужно
    // ...
  }
}
```

---

## 🚀 Деплой на продакшн

### Nginx + PM2

```bash
# Установите PM2
npm install -g pm2

# Запустите backend
cd SITE-FOXARIA/backend
pm2 start server.js --name foxaria-api

# Соберите frontend
cd SITE-FOXARIA/frontend
npm run build
```

Конфиг Nginx:
```nginx
server {
    server_name foxaria.ru www.foxaria.ru;
    
    location /api/ {
        proxy_pass http://localhost:3001;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    location / {
        root /path/to/SITE-FOXARIA/frontend/dist;
        try_files $uri $uri/ /index.html;
    }
}
```

---

## 📋 Структура проекта

```
SITE-FOXARIA/
├── config.js                 ← ГЛАВНЫЙ КОНФИГ (только этот файл!)
├── backend/
│   ├── server.js            ← Точка входа API
│   ├── routes/              ← API роуты
│   │   ├── auth.js          ← Авторизация
│   │   ├── profile.js       ← Профиль игрока
│   │   ├── guild.js         ← Гильдии
│   │   ├── bans.js          ← Бан-лист
│   │   ├── news.js          ← Новости
│   │   ├── shop.js          ← Магазин
│   │   ├── payments.js      ← Платежи + вебхуки
│   │   ├── admin.js         ← Администрирование
│   │   ├── servers.js       ← Статус серверов / RCON
│   │   ├── private.js       ← GriefPrevention привать
│   │   ├── wipes.js         ← Расписание вайпов
│   │   └── staff.js         ← Состав команды
│   ├── services/
│   │   ├── database.js      ← MySQL подключение
│   │   ├── rcon.js          ← RCON сервис
│   │   └── payments.js      ← Платёжные провайдеры
│   └── middleware/
│       └── auth.js          ← JWT авторизация
└── frontend/
    └── src/
        ├── pages/           ← Все страницы
        ├── components/      ← Компоненты
        ├── context/         ← Auth контекст
        └── api/             ← Axios клиент
```
