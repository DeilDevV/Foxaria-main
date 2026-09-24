const path = require('path');

/** Корень монорепозитория Foxaria-main (папка над SITE-FOXARIA). */
const REPO_ROOT = path.join(__dirname, '..');

// ================================================================
//   FOXARIA SITE — ГЛАВНЫЙ ФАЙЛ КОНФИГУРАЦИИ
//   Измените только этот файл для подключения к вашему серверу
// ================================================================
//
//   ⚠️  ПЕРЕД ДЕПЛОЕМ НА ХОСТИНГ замените все значения помеченные:
//       [!ХОСТИНГ] — обязательно заменить
//       [!ЖЕЛАТЕЛЬНО] — рекомендуется заменить
//
// ================================================================

module.exports = {

  // ─── Настройки сайта ──────────────────────────────────────────
  site: {
    name: 'Foxaria',
    port: 3001,                            // Порт backend API
    frontendUrl: 'http://localhost:5173',  // [!ХОСТИНГ] URL фронтенда → ваш домен, напр. https://foxaria.ru
    productionUrl: 'https://foxaria.ru',   // [!ХОСТИНГ] Замените на ваш реальный домен
    // [!ХОСТИНГ] Сгенерируйте случайную строку: node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
    sessionSecret: 'a7f3c2e1d9b4082f6a1e3c5d7b9f2a4c6e8d1b3f5a7c9e2d4f6b8a1c3e5d7f9',
  },

  // ─── JWT авторизация ──────────────────────────────────────────
  jwt: {
    // [!ХОСТИНГ] Сгенерируйте отдельный ключ: node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
    secret: 'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7',
    expiresIn: '7d',
  },

  // ─── База данных MySQL/MariaDB вашего Minecraft сервера ───────
  database: {
    host: 'localhost',          // [!ХОСТИНГ] IP адрес БД — если БД на другом сервере, укажите его IP
    port: 3306,                 // Порт БД (обычно 3306)
    user: 'foxaria',
    password: 'Grnkbq2GS7rBj71TWmx',
    database: 'foxaria',
    waitForConnections: true,
    connectionLimit: 15,
    queueLimit: 0,
    charset: 'utf8mb4',
  },

  // ─── Серверы Minecraft ────────────────────────────────────────
  servers: [
    {
      id: 'anarchy',
      name: 'Анархия',
      description: 'Анархия без правил. Выживай любой ценой.',
      ip: 'play.foxaria.ru',    // [!ХОСТИНГ] Замените на реальный игровой IP
      port: 25565,
      icon: '💀',
      color: '#FF6B35',
      rcon: {
        host: '127.0.0.1',
        port: 25578,
        password: 'OTv4e2UzD9rKuUbwe67Jv+HwUFID1euh',
        timeout: 5000,
      },
    },
  ],

  // ─── BungeeCord прокси (для кросс-серверных команд) ──────────
  bungeecord: {
    rcon: {
      host: '127.0.0.1',
      port: 25580,              // [!ХОСТИНГ] rcon.port BungeeCord прокси
      password: 'BungeeRcon2024', // [!ХОСТИНГ] rcon.password BungeeCord
      timeout: 5000,
    },
  },

  // ─── Платёжные системы ────────────────────────────────────────
  payments: {

    // YooKassa (Юкасса / ЮMoney бизнес)
    // 1. Зарегистрируйтесь на yookassa.ru
    // 2. Личный кабинет → Настройки магазина → API ключи
    yookassa: {
      enabled: false,           // [!ХОСТИНГ] Поставьте true и заполните shopId / secretKey
      shopId: '123456',         // [!ХОСТИНГ] Ваш реальный Shop ID из ЮKassa (числовой ID)
      secretKey: 'test_xXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxX', // [!ХОСТИНГ] Ключ из ЮKassa (live_...)
    },

    // FreeKassa (поддерживает QIWI, карты, СБП, крипту и др.)
    // 1. Зарегистрируйтесь на freekassa.ru
    // 2. Кабинет → Мои магазины → Настройки
    freekassa: {
      enabled: false,
      merchantId: '12345',          // [!ХОСТИНГ] ID магазина из FreeKassa
      secretWord1: 'TestSecret1',   // [!ХОСТИНГ] Секретное слово 1 из настроек FreeKassa
      secretWord2: 'TestSecret2',   // [!ХОСТИНГ] Секретное слово 2 из настроек FreeKassa
    },

    // Robokassa (популярна в РФ, банковские карты, СБП)
    // 1. Зарегистрируйтесь на robokassa.com
    // 2. Личный кабинет → Магазины → Настройка → Пароли
    robokassa: {
      enabled: false,
      merchantLogin: 'foxaria_test',   // [!ХОСТИНГ] Логин магазина в Robokassa
      password1: 'TestRoboPass1',      // [!ХОСТИНГ] Пароль #1 из настроек Robokassa
      password2: 'TestRoboPass2',      // [!ХОСТИНГ] Пароль #2 из настроек Robokassa
    },

    // CryptoBot (Telegram) — оплата криптовалютой
    // 1. Откройте @CryptoBot в Telegram
    // 2. /pay → My Apps → Create App → получите токен
    cryptobot: {
      enabled: false,           // [!ХОСТИНГ] Поставьте true и вставьте реальный токен
      token: '1234567890:AABBCCDDEEFFaabbccddeeff', // [!ХОСТИНГ] Токен из @CryptoBot
      network: 'testnet',       // [!ХОСТИНГ] Поменяйте на 'mainnet' для боевого режима
    },

    // LAVA (lava.ru) — популярная российская платёжка
    // 1. Зарегистрируйтесь на lava.ru
    // 2. Личный кабинет → Проекты → API ключи
    lava: {
      enabled: false,
      shopId: 'test-shop-uuid-0000-0000', // [!ХОСТИНГ] UUID проекта из lava.ru
      secretKey: 'TestLavaSecret2024',    // [!ХОСТИНГ] Секретный ключ из lava.ru
    },
  },

  // ─── Настройки плагинов (названия таблиц в БД) ───────────────
  // [!ЖЕЛАТЕЛЬНО] Проверьте что названия таблиц совпадают с вашими плагинами
  plugins: {
    // LiteBans — система банов
    litebans: {
      bansTable: 'litebans_bans',
      muteTable: 'litebans_mutes',
      warnTable: 'litebans_warnings',
      historyTable: 'litebans_history',
      serverTable: 'litebans_servers',
    },

    // LuckPerms — права и группы
    luckperms: {
      playersTable: 'luckperms_players',
      userPermissionsTable: 'luckperms_user_permissions',
      // [!ЖЕЛАТЕЛЬНО] Замените на реальные названия ваших групп из LuckPerms
      donateGroups: ['vip', 'vip+', 'premium', 'deluxe', 'legend', 'god', 'supreme'],
      // [!ЖЕЛАТЕЛЬНО] Группы с доступом к Admin Panel сайта
      adminGroups: ['admin', 'owner', 'co-owner', 'headadmin'],
      // [!ЖЕЛАТЕЛЬНО] Группы модераторов
      modGroups: ['moderator', 'moder', 'helper', 'jr.moderator', 'senioradmin'],
    },

    // Guilds плагин (github.com/guilds-plugin/Guilds)
    guilds: {
      enabled: true,
      guildsTable: 'fx_guilds',
      membersTable: 'fx_guild_members',
    },

    // GriefPrevention — система приватов
    griefprevention: {
      enabled: true,
      claimsTable: 'griefprevention_claims',
    },

    // [!ЖЕЛАТЕЛЬНО] Настройте под свой плагин статистики
    stats: {
      playersTable: 'players',
      statsTable: 'player_stats',
      miningTable: 'player_mining_stats',
    },
  },

  // ─── SQLite-файлы плагинов Minecraft сервера ─────────────────
  // Если ваш сервер хранит данные в SQLite (не MySQL) — укажите пути к файлам плагинов.
  // Сайт подключится к ним напрямую и прочитает данные игроков, банов и т.д.
  // Оставьте пустую строку '' если плагин использует MySQL или файл не нужен.
  //
  // Пример путей:
  //   Windows: 'C:/minecraft-server/plugins/LuckPerms/luckperms-sqlite.db'
  //   Linux:   '/home/minecraft/plugins/LuckPerms/luckperms-sqlite.db'
  // Runtime сайта больше не читает SQLite напрямую.
  // Эти пути нужны только как источники для разовой миграции в общую MySQL базу.
  pluginDatabases: {
    // Путь к SQLite базе плагина Foxaria (единая БД для всего: игроки, баны, гильдии, приваты)
    // По умолчанию — относительно репозитория; на хостинге можно заменить на абсолютный путь.
    foxaria: path.join(REPO_ROOT, 'test-server', 'plugins', 'Foxaria', 'foxaria.db'),
    // Путь к SQLite базе BungeeCord прокси (foxaria-proxy.db) — баны/муты с /punish и привилегии
    proxy: path.join(REPO_ROOT, 'proxy-bungeecord', 'plugins', 'FoxariaProxy', 'foxaria-proxy.db'),
  },

  // ─── Серверы сети для суммарного онлайна (RCON каждого) ──────
  // Используются как fallback когда BungeeCord RCON недоступен.
  // На хостинге замените порты на реальные rcon.port из server.properties каждого сервера.
  networkServers: [
    { id: 'auth',  rcon: { host: '127.0.0.1', port: 25576, password: 'OTv4e2UzD9rKuUbwe67Jv+HwUFID1euh', timeout: 3000 } },
    { id: 'lobby', rcon: { host: '127.0.0.1', port: 25577, password: 'OTv4e2UzD9rKuUbwe67Jv+HwUFID1euh', timeout: 3000 } },
    { id: 'game',  rcon: { host: '127.0.0.1', port: 25578, password: 'OTv4e2UzD9rKuUbwe67Jv+HwUFID1euh', timeout: 3000 } },
  ],

};
