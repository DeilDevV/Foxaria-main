const path = require('path');

/** Корень монорепозитория Foxaria-main (папка над web/). */
const REPO_ROOT = path.join(__dirname, '..', '..');

//       [!ХОСТИНГ] — обязательно заменить

module.exports = {

  site: {
    name: 'Foxaria',
    port: 3001,                            // Порт backend API
    frontendUrl: 'http://localhost:5173',  // [!ХОСТИНГ] URL фронтенда → ваш домен, напр. https://foxaria.ru
    productionUrl: 'https://foxaria.ru',   // [!ХОСТИНГ] Замените на ваш реальный домен
    // [!ХОСТИНГ] Сгенерируйте случайную строку: node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
    sessionSecret: 'a7f3c2e1d9b4082f6a1e3c5d7b9f2a4c6e8d1b3f5a7c9e2d4f6b8a1c3e5d7f9',
  },

  jwt: {
    // [!ХОСТИНГ] Сгенерируйте отдельный ключ: node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
    secret: 'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7',
    expiresIn: '7d',
  },

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

  bungeecord: {
    rcon: {
      host: '127.0.0.1',
      port: 25580,              // [!ХОСТИНГ] rcon.port BungeeCord прокси
      password: 'BungeeRcon2024', // [!ХОСТИНГ] rcon.password BungeeCord
      timeout: 5000,
    },
  },

  payments: {

    yookassa: {
      enabled: false,           // [!ХОСТИНГ] Поставьте true и заполните shopId / secretKey
      shopId: '123456',         // [!ХОСТИНГ] Ваш реальный Shop ID из ЮKassa (числовой ID)
      secretKey: 'test_xXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxX', // [!ХОСТИНГ] Ключ из ЮKassa (live_...)
    },

    freekassa: {
      enabled: false,
      merchantId: '12345',          // [!ХОСТИНГ] ID магазина из FreeKassa
      secretWord1: 'TestSecret1',   // [!ХОСТИНГ] Секретное слово 1 из настроек FreeKassa
      secretWord2: 'TestSecret2',   // [!ХОСТИНГ] Секретное слово 2 из настроек FreeKassa
    },

    robokassa: {
      enabled: false,
      merchantLogin: 'foxaria_test',   // [!ХОСТИНГ] Логин магазина в Robokassa
      password1: 'TestRoboPass1',      // [!ХОСТИНГ] Пароль #1 из настроек Robokassa
      password2: 'TestRoboPass2',      // [!ХОСТИНГ] Пароль #2 из настроек Robokassa
    },

    cryptobot: {
      enabled: false,           // [!ХОСТИНГ] Поставьте true и вставьте реальный токен
      token: '1234567890:AABBCCDDEEFFaabbccddeeff', // [!ХОСТИНГ] Токен из @CryptoBot
      network: 'testnet',       // [!ХОСТИНГ] Поменяйте на 'mainnet' для боевого режима
    },

    lava: {
      enabled: false,
      shopId: 'test-shop-uuid-0000-0000', // [!ХОСТИНГ] UUID проекта из lava.ru
      secretKey: 'TestLavaSecret2024',    // [!ХОСТИНГ] Секретный ключ из lava.ru
    },
  },

  plugins: {
    litebans: {
      bansTable: 'litebans_bans',
      muteTable: 'litebans_mutes',
      warnTable: 'litebans_warnings',
      historyTable: 'litebans_history',
      serverTable: 'litebans_servers',
    },

    luckperms: {
      playersTable: 'luckperms_players',
      userPermissionsTable: 'luckperms_user_permissions',
      donateGroups: ['vip', 'vip+', 'premium', 'deluxe', 'legend', 'god', 'supreme'],
      adminGroups: ['admin', 'owner', 'co-owner', 'headadmin'],
      modGroups: ['moderator', 'moder', 'helper', 'jr.moderator', 'senioradmin'],
    },

    guilds: {
      enabled: true,
      guildsTable: 'fx_guilds',
      membersTable: 'fx_guild_members',
    },

    griefprevention: {
      enabled: true,
      claimsTable: 'griefprevention_claims',
    },

    stats: {
      playersTable: 'players',
      statsTable: 'player_stats',
      miningTable: 'player_mining_stats',
    },
  },

  pluginDatabases: {
    foxaria: path.join(REPO_ROOT, 'servers', 'game', 'plugins', 'Foxaria', 'foxaria.db'),
    proxy: path.join(REPO_ROOT, 'servers', 'proxy', 'plugins', 'FoxariaProxy', 'foxaria-proxy.db'),
  },

  networkServers: [
    { id: 'auth',  rcon: { host: '127.0.0.1', port: 25576, password: 'OTv4e2UzD9rKuUbwe67Jv+HwUFID1euh', timeout: 3000 } },
    { id: 'lobby', rcon: { host: '127.0.0.1', port: 25577, password: 'OTv4e2UzD9rKuUbwe67Jv+HwUFID1euh', timeout: 3000 } },
    { id: 'game',  rcon: { host: '127.0.0.1', port: 25578, password: 'OTv4e2UzD9rKuUbwe67Jv+HwUFID1euh', timeout: 3000 } },
  ],

};
