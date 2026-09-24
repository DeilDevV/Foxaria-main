const express = require('express');
const router = express.Router();
const { query, queryOne } = require('../services/database');
const { adminMiddleware, modMiddleware } = require('../middleware/auth');
const { executeCommand } = require('../services/rcon');
const config = require('../../config');

const lb = config.plugins.litebans;

// GET /api/bans — список банов с поиском и пагинацией
router.get('/', async (req, res) => {
  try {
    const rawType = req.query.type;
    const typeStr = Array.isArray(rawType) ? rawType[0] : rawType;
    const kind = String(typeStr || 'ban').toLowerCase() === 'mute' ? 'mute' : 'ban';
    const { search, page = 1, limit = 20 } = req.query;
    const offset = (parseInt(page) - 1) * parseInt(limit);
    const table = kind === 'mute' ? lb.muteTable : lb.bansTable;

    // Имя одной строкой на uuid — не делаем JOIN к history (там могли быть дубликаты до фикса VIEW)
    const nameSub = `(SELECT h2.name FROM ${lb.historyTable} h2 WHERE h2.uuid = b.uuid ORDER BY h2.date DESC LIMIT 1)`;

    let whereClause = 'WHERE b.active = 1';
    let params = [];

    if (search) {
      whereClause += ` AND (${nameSub} LIKE ? OR b.reason LIKE ?)`;
      params.push(`%${search}%`, `%${search}%`);
    }

    const totalRow = await queryOne(
      `SELECT COUNT(*) as total FROM ${table} b ${whereClause}`,
      params
    );

    const bans = await query(
      `SELECT b.uuid, ${nameSub} as username, b.reason, b.banned_by_name,
              b.server, b.\`until\`, b.time, b.active,
              b.ipban, b.silent
       FROM ${table} b
       ${whereClause}
       ORDER BY b.time DESC
       LIMIT ? OFFSET ?`,
      [...params, parseInt(limit), offset]
    );

    res.json({
      bans: bans.map(ban => ({
        uuid: ban.uuid,
        username: ban.username || 'Неизвестно',
        avatar: ban.username ? `https://mc-heads.net/avatar/${ban.username}/40` : null,
        reason: ban.reason || 'Причина не указана',
        bannedBy: ban.banned_by_name,
        server: ban.server || 'Все серверы',
        until: ban.until > 0 ? new Date(ban.until).toISOString() : null,
        permanent: ban.until <= 0,
        bannedAt: new Date(ban.time).toISOString(),
        active: ban.active === 1,
        ipBan: ban.ipban === 1,
      })),
      total: totalRow?.total || 0,
      page: parseInt(page),
      totalPages: Math.ceil((totalRow?.total || 0) / parseInt(limit)),
    });
  } catch (err) {
    console.error('Bans error:', err);
    res.status(500).json({ error: 'Ошибка загрузки банлиста' });
  }
});

// GET /api/bans/player/:username — бан конкретного игрока
router.get('/player/:username', async (req, res) => {
  try {
    const ban = await queryOne(
      `SELECT b.*, h.name as username FROM ${lb.bansTable} b
       LEFT JOIN ${lb.historyTable} h ON h.uuid = b.uuid
       WHERE h.name = ? AND b.active = 1 ORDER BY b.time DESC LIMIT 1`,
      [req.params.username]
    );
    res.json({ ban: ban || null });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/bans/ban — забанить игрока (для модераторов)
router.post('/ban', modMiddleware, async (req, res) => {
  try {
    const { username, reason, duration, serverId = 'survival' } = req.body;
    if (!username || !reason) return res.status(400).json({ error: 'Укажите ник и причину' });

    const command = duration
      ? `tempban ${username} ${duration} ${reason}`
      : `ban ${username} ${reason}`;

    const result = await executeCommand(serverId, command);
    if (!result.success) return res.status(500).json({ error: result.error });

    res.json({ success: true, message: `${username} заблокирован` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// POST /api/bans/unban — разбанить игрока
router.post('/unban', modMiddleware, async (req, res) => {
  try {
    const { username, serverId = 'survival' } = req.body;
    if (!username) return res.status(400).json({ error: 'Укажите ник' });

    const result = await executeCommand(serverId, `unban ${username}`);
    if (!result.success) return res.status(500).json({ error: result.error });

    res.json({ success: true, message: `${username} разблокирован` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// GET /api/bans/stats — статистика банов
router.get('/stats', async (req, res) => {
  try {
    const totalBans = await queryOne(`SELECT COUNT(*) as count FROM ${lb.bansTable} WHERE active = 1`);
    const totalMutes = await queryOne(`SELECT COUNT(*) as count FROM ${lb.muteTable} WHERE active = 1`);
    const recentBans = await queryOne(
      `SELECT COUNT(*) as count FROM ${lb.bansTable} WHERE active = 1 AND time > ?`,
      [Date.now() - 7 * 24 * 60 * 60 * 1000]
    );

    res.json({
      activeBans: totalBans?.count || 0,
      activeMutes: totalMutes?.count || 0,
      recentBans: recentBans?.count || 0,
    });
  } catch {
    res.json({ activeBans: 0, activeMutes: 0, recentBans: 0 });
  }
});

module.exports = router;
