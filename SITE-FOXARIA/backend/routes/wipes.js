const express = require('express');
const router = express.Router();
const { query, queryOne } = require('../services/database');
const { adminMiddleware } = require('../middleware/auth');
const { executeBungeeCommand, executeCommand } = require('../services/rcon');
const config = require('../../config');

router.get('/', async (req, res) => {
  try {
    const wipes = await query('SELECT * FROM foxaria_wipes ORDER BY wipe_date ASC');
    const now = new Date();
    res.json(wipes.map(w => ({
      ...w,
      isPast: new Date(w.wipe_date) < now,
      daysLeft: Math.ceil((new Date(w.wipe_date) - now) / 86400000),
    })));
  } catch (err) {
    if (err.message?.includes('недоступна')) return res.json([]);
    res.status(500).json({ error: 'Ошибка' });
  }
});

router.post('/', adminMiddleware, async (req, res) => {
  try {
    const { server_id, server_name, wipe_date, description, wipe_type, kick_now } = req.body;
    const result = await query(
      'INSERT INTO foxaria_wipes (server_id, server_name, wipe_date, description, wipe_type) VALUES (?, ?, ?, ?, ?)',
      [server_id, server_name, wipe_date, description, wipe_type || 'full']
    );

    // Если kick_now=true — немедленно кикаем всех и оповещаем
    if (kick_now) {
      const msg = `[ВАЙП] Сервер ${server_name || server_id} уходит на вайп. Все игроки будут отключены.`;
      // Сначала broadcast через BungeeCord, потом кикаем всех
      await executeBungeeCommand(`broadcast &c&l${msg}`).catch(() => {});
      await executeCommand(server_id, `broadcast &c&l${msg}`).catch(() => {});
      // Небольшая задержка перед киком
      await new Promise(r => setTimeout(r, 2000));
      // kickall — стандартная команда большинства плагинов
      let kickResult = await executeBungeeCommand(`kickall &cВайп на сервере ${server_name || server_id}`);
      if (!kickResult.success) {
        await executeCommand(server_id, `kickall &cВайп`).catch(() => {});
      }
    }

    res.json({ success: true, id: result.lastInsertRowid });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

router.delete('/:id', adminMiddleware, async (req, res) => {
  try {
    await query('DELETE FROM foxaria_wipes WHERE id = ?', [req.params.id]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

module.exports = router;
