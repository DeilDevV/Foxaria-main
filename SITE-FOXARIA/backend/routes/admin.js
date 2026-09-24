const express = require('express');
const router = express.Router();
const { query, queryOne } = require('../services/database');
const { adminMiddleware, modMiddleware } = require('../middleware/auth');
const { executeCommand, executeBungeeCommand } = require('../services/rcon');
const config = require('../../config');

const ANARCHY = 'anarchy';

// GET /api/admin/stats
router.get('/stats', adminMiddleware, async (req, res) => {
  try {
    const lp = config.plugins.luckperms;
    const lb = config.plugins.litebans;

    const [playersCount, bansCount, paymentsTotal, paymentsMonth, newsCount] = await Promise.all([
      queryOne(`SELECT COUNT(*) as count FROM ${lp.playersTable}`).catch(() => ({ count: 0 })),
      queryOne(`SELECT COUNT(*) as count FROM ${lb.bansTable} WHERE active = 1`).catch(() => ({ count: 0 })),
      queryOne(`SELECT SUM(amount) as total FROM foxaria_payments WHERE status = "completed"`).catch(() => ({ total: 0 })),
      queryOne(`SELECT SUM(amount) as total FROM foxaria_payments WHERE status = 'completed' AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)`).catch(() => ({ total: 0 })),
      queryOne(`SELECT COUNT(*) as count FROM foxaria_news WHERE published = 1`).catch(() => ({ count: 0 })),
    ]);

    res.json({
      totalPlayers: playersCount?.count || 0,
      activeBans: bansCount?.count || 0,
      totalRevenue: parseFloat(paymentsTotal?.total || 0).toFixed(2),
      monthRevenue: parseFloat(paymentsMonth?.total || 0).toFixed(2),
      newsCount: newsCount?.count || 0,
    });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка загрузки статистики' });
  }
});

// GET /api/admin/payments
router.get('/payments', adminMiddleware, async (req, res) => {
  try {
    const { page = 1, status, limit = 30 } = req.query;
    const offset = (parseInt(page) - 1) * parseInt(limit);
    let where = 'WHERE 1=1';
    const params = [];
    if (status) { where += ' AND status = ?'; params.push(status); }

    const payments = await query(
      `SELECT * FROM foxaria_payments ${where} ORDER BY created_at DESC LIMIT ? OFFSET ?`,
      [...params, parseInt(limit), offset]
    );
    const total = await queryOne(`SELECT COUNT(*) as count FROM foxaria_payments ${where}`, params);
    res.json({ payments, total: total?.count || 0 });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// GET /api/admin/logs
router.get('/logs', adminMiddleware, async (req, res) => {
  try {
    const logs = await query('SELECT * FROM foxaria_action_logs ORDER BY created_at DESC LIMIT 200');
    res.json(logs);
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/admin/player/give-rank
// Пишет напрямую в proxy_privileges (работает и оффлайн, grantpriv требует онлайн)
router.post('/player/give-rank', adminMiddleware, async (req, res) => {
  try {
    const { username, rank } = req.body;
    if (!username || !rank) return res.status(400).json({ error: 'Укажите ник и ранг' });

    const player = await queryOne(
      'SELECT uuid, username FROM luckperms_players WHERE LOWER(username) = LOWER(?)', [username]
    );
    if (!player) return res.status(404).json({ error: 'Игрок не найден в базе данных' });

    const mcName = player.username || username;

    await query(
      `INSERT INTO proxy_privileges(player_uuid, primary_group, temp_group, temp_expires_at)
       VALUES(?, ?, NULL, NULL)
       ON DUPLICATE KEY UPDATE
         primary_group = VALUES(primary_group),
         temp_group = VALUES(temp_group),
         temp_expires_at = VALUES(temp_expires_at)`,
      [player.uuid, rank]
    );

    // Прокси: таб Bungee + рассылка ChatPrefixSync на текущий backend (единый источник ранга)
    await executeBungeeCommand(`grantpriv ${mcName} ${rank}`);

    res.json({ success: true, message: `${mcName} получил ранг ${rank}` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка сервера: ' + err.message });
  }
});

// POST /api/admin/player/kick — через BungeeCord (кик работает на всех серверах)
router.post('/player/kick', modMiddleware, async (req, res) => {
  try {
    const { username, reason = 'Кик администратором' } = req.body;
    if (!username) return res.status(400).json({ error: 'Укажите ник' });

    // Сначала пробуем BungeeCord (cross-server kick)
    let result = await executeBungeeCommand(`kick ${username} ${reason}`);
    if (!result.success) {
      // Fallback: анархия напрямую
      result = await executeCommand(ANARCHY, `kick ${username} ${reason}`);
    }
    if (!result.success) return res.status(500).json({ error: result.error });

    res.json({ success: true, message: `${username} кикнут` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/admin/player/ban — Foxaria plugin: /punish <player> <code>
// Код 1.1 = TEMPBAN читы (7д), 1.3 = PERMBAN, используй нужный код
router.post('/player/ban', modMiddleware, async (req, res) => {
  try {
    const { username, reason = '1.1', duration } = req.body;
    if (!username) return res.status(400).json({ error: 'Укажите ник' });

    // Если передан код причины (1.1, 1.3 и т.д.) — /punish <player> <code>
    // Если нет — стандартный /ban <player> <reason>
    const isReasonCode = /^\d+\.\d+$/.test(reason);
    const cmd = isReasonCode ? `punish ${username} ${reason}` : `ban ${username} ${reason}`;

    const result = await executeCommand(ANARCHY, cmd);
    if (!result.success) return res.status(500).json({ error: result.error });
    res.json({ success: true, message: `${username} забанен (${reason})` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/admin/player/unban
router.post('/player/unban', adminMiddleware, async (req, res) => {
  try {
    const { username } = req.body;
    if (!username) return res.status(400).json({ error: 'Укажите ник' });

    const result = await executeCommand(ANARCHY, `unpunish ${username}`);
    if (!result.success) {
      // Fallback: стандартный /pardon
      const r2 = await executeCommand(ANARCHY, `pardon ${username}`);
      if (!r2.success) return res.status(500).json({ error: result.error });
    }
    res.json({ success: true, message: `${username} разбанен` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/admin/player/mute
router.post('/player/mute', modMiddleware, async (req, res) => {
  try {
    const { username, reason = '2.1' } = req.body;
    if (!username) return res.status(400).json({ error: 'Укажите ник' });

    const isReasonCode = /^\d+\.\d+$/.test(reason);
    const cmd = isReasonCode ? `punish ${username} ${reason}` : `mute ${username} ${reason}`;
    const result = await executeCommand(ANARCHY, cmd);
    if (!result.success) return res.status(500).json({ error: result.error });
    res.json({ success: true, message: `${username} замьючен` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/admin/player/unmute
router.post('/player/unmute', modMiddleware, async (req, res) => {
  try {
    const { username } = req.body;
    if (!username) return res.status(400).json({ error: 'Укажите ник' });

    const result = await executeCommand(ANARCHY, `unpunish ${username}`);
    if (!result.success) return res.status(500).json({ error: result.error });
    res.json({ success: true, message: `${username} размьючен` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/admin/player/warn
router.post('/player/warn', modMiddleware, async (req, res) => {
  try {
    const { username, reason = '3.1' } = req.body;
    if (!username) return res.status(400).json({ error: 'Укажите ник' });

    const isReasonCode = /^\d+\.\d+$/.test(reason);
    const cmd = isReasonCode ? `punish ${username} ${reason}` : `warn ${username} ${reason}`;
    const result = await executeCommand(ANARCHY, cmd);
    if (!result.success) return res.status(500).json({ error: result.error });
    res.json({ success: true, message: `${username} предупреждён` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

function escapeTellrawSegment(s) {
  return String(s ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"')
    .replace(/\n/g, '\\n');
}

// POST /api/admin/broadcast — единый tellraw на каждом игровом сервере из config (без «ломаных» префиксов Bungee broadcast)
router.post('/broadcast', adminMiddleware, async (req, res) => {
  try {
    const { message } = req.body;
    if (!message) return res.status(400).json({ error: 'Введите сообщение' });

    const body = escapeTellrawSegment(message);
    const json = `["",{"text":"[Foxaria] ","color":"gold","bold":true},{"text":"${body}","color":"white","bold":false}]`;
    const servers = Array.isArray(config.servers) ? config.servers : [];
    let anyOk = false;
    for (const srv of servers) {
      if (!srv?.id) continue;
      const r = await executeCommand(srv.id, `tellraw @a ${json}`);
      if (r.success) anyOk = true;
    }
    if (!anyOk) {
      const r2 = await executeBungeeCommand(`alert ${message}`);
      if (!r2.success) {
        await executeBungeeCommand(`broadcast ${message}`);
      }
    }

    res.json({ success: true, message: 'Сообщение отправлено' });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/admin/player/give-money — монеты через /eco give (Bukkit-команда анархии)
router.post('/player/give-money', adminMiddleware, async (req, res) => {
  try {
    const { username, amount } = req.body;
    if (!username || !amount) return res.status(400).json({ error: 'Укажите ник и сумму' });

    const result = await executeCommand(ANARCHY, `eco give ${username} ${amount}`);
    if (!result.success) return res.status(500).json({ error: result.error });
    res.json({ success: true, message: `${username} получил ${amount} монет` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// POST /api/admin/player/give-tokens — токены через /eco token give (Bukkit-команда анархии)
router.post('/player/give-tokens', adminMiddleware, async (req, res) => {
  try {
    const { username, amount } = req.body;
    if (!username || !amount) return res.status(400).json({ error: 'Укажите ник и количество токенов' });

    const result = await executeCommand(ANARCHY, `eco token give ${username} ${amount}`);
    if (!result.success) return res.status(500).json({ error: result.error });
    res.json({ success: true, message: `${username} получил ${amount} токенов` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// GET /api/admin/players — список игроков
router.get('/players', adminMiddleware, async (req, res) => {
  try {
    const { page = 1, search, limit = 30 } = req.query;
    const offset = (parseInt(page) - 1) * parseInt(limit);
    const lp = config.plugins.luckperms;
    let where = 'WHERE 1=1';
    const params = [];
    if (search) { where += ' AND LOWER(p.username) LIKE ?'; params.push(`%${search.toLowerCase()}%`); }

    const players = await query(
      `SELECT p.uuid, p.username, p.primary_group,
              pt.time as playtime,
              ea.balance,
              (SELECT COUNT(*) FROM ${config.plugins.litebans.bansTable} b WHERE b.uuid = p.uuid AND b.active = 1) as active_bans
       FROM ${lp.playersTable} p
       LEFT JOIN playtime pt ON pt.uuid = p.uuid
       LEFT JOIN economy_accounts ea ON ea.player_uuid = p.uuid
       ${where}
       ORDER BY p.username ASC LIMIT ? OFFSET ?`,
      [...params, parseInt(limit), offset]
    );
    const total = await queryOne(`SELECT COUNT(*) as count FROM ${lp.playersTable} p ${where}`, params);
    res.json({ players, total: total?.count || 0 });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// GET /api/admin/settings
router.get('/settings', adminMiddleware, async (req, res) => {
  try {
    const settings = await query('SELECT `key`, value FROM foxaria_settings');
    const result = {};
    settings.forEach(s => { result[s.key] = s.value; });
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// PUT /api/admin/settings
router.put('/settings', adminMiddleware, async (req, res) => {
  try {
    for (const [key, value] of Object.entries(req.body)) {
      await query(
        'INSERT INTO foxaria_settings (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)',
        [key, value]
      );
    }
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/admin/payment/refund
router.post('/payment/refund', adminMiddleware, async (req, res) => {
  try {
    const { orderId } = req.body;
    await query('UPDATE foxaria_payments SET status = "refunded" WHERE id = ?', [orderId]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

module.exports = router;
