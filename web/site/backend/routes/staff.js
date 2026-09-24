const express = require('express');
const router = express.Router();
const { query, queryOne } = require('../services/database');
const { adminMiddleware } = require('../middleware/auth');

router.get('/', async (req, res) => {
  try {
    const staff = await query('SELECT * FROM foxaria_staff WHERE active = 1 ORDER BY sort_order');
    res.json(staff);
  } catch (err) {
    if (err.message?.includes('недоступна')) return res.json([]);
    res.status(500).json({ error: 'Ошибка' });
  }
});

router.post('/', adminMiddleware, async (req, res) => {
  try {
    const { name, role, role_color, avatar, discord, vk, telegram, description, sort_order } = req.body;
    const [result] = await require('../services/database').getPool().execute(
      'INSERT INTO foxaria_staff (name, role, role_color, avatar, discord, vk, telegram, description, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
      [name, role, role_color || '#FF6B35', avatar, discord, vk, telegram, description, sort_order || 0]
    );
    res.json({ success: true, id: result.insertId });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

router.put('/:id', adminMiddleware, async (req, res) => {
  try {
    const { name, role, role_color, avatar, discord, vk, telegram, description, sort_order, active } = req.body;
    await query(
      'UPDATE foxaria_staff SET name=?, role=?, role_color=?, avatar=?, discord=?, vk=?, telegram=?, description=?, sort_order=?, active=? WHERE id=?',
      [name, role, role_color, avatar, discord, vk, telegram, description, sort_order, active ? 1 : 0, req.params.id]
    );
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

router.delete('/:id', adminMiddleware, async (req, res) => {
  try {
    await query('UPDATE foxaria_staff SET active = 0 WHERE id = ?', [req.params.id]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

module.exports = router;
