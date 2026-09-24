const express = require('express');
const router = express.Router();
const { query, queryOne } = require('../services/database');
const { authMiddleware, adminMiddleware } = require('../middleware/auth');

// GET /api/shop/categories
router.get('/categories', async (req, res) => {
  try {
    const categories = await query('SELECT * FROM foxaria_shop_categories WHERE visible=1 ORDER BY sort_order');
    res.json(categories);
  } catch (err) {
    if (err.message?.includes('недоступна')) return res.json([]);
    res.status(500).json({ error: 'Ошибка загрузки категорий' });
  }
});

// GET /api/shop/items?category=1&featured=true
router.get('/items', async (req, res) => {
  try {
    const { category, featured, server } = req.query;
    let where = 'WHERE i.visible = 1';
    const params = [];

    if (category) { where += ' AND i.category_id = ?'; params.push(category); }
    if (featured === 'true') { where += ' AND i.featured = 1'; }
    if (server) { where += ' AND (i.server_id = ? OR i.server_id = "all")'; params.push(server); }

    const items = await query(
      `SELECT i.*, c.name as category_name, c.icon as category_icon
       FROM foxaria_shop_items i
       LEFT JOIN foxaria_shop_categories c ON c.id = i.category_id
       ${where}
       ORDER BY i.featured DESC, i.sort_order`,
      params
    );

    res.json(items);
  } catch (err) {
    if (err.message?.includes('недоступна')) return res.json([]);
    res.status(500).json({ error: 'Ошибка загрузки товаров' });
  }
});

// GET /api/shop/items/:id
router.get('/items/:id', async (req, res) => {
  try {
    const item = await queryOne(
      `SELECT i.*, c.name as category_name FROM foxaria_shop_items i
       LEFT JOIN foxaria_shop_categories c ON c.id = i.category_id
       WHERE i.id = ? AND i.visible = 1`,
      [req.params.id]
    );
    if (!item) return res.status(404).json({ error: 'Товар не найден' });
    res.json(item);
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/shop/items (admin)
router.post('/items', adminMiddleware, async (req, res) => {
  try {
    const { category_id, name, description, image, price, original_price, commands, server_id, item_type, featured, visible } = req.body;
    if (!name || !price) return res.status(400).json({ error: 'Название и цена обязательны' });

    const cmds = Array.isArray(commands) ? commands : (commands || '').split('\n').map(c => c.trim()).filter(Boolean);
    const result = await query(
      'INSERT INTO foxaria_shop_items (category_id, name, description, image, price, original_price, commands, server_id, item_type, featured, visible) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
      [category_id || null, name, description || '', image || '', parseFloat(price), original_price ? parseFloat(original_price) : null,
       JSON.stringify(cmds), server_id || 'anarchy', item_type || 'donate', featured ? 1 : 0, visible !== false ? 1 : 0]
    );

    res.json({ success: true, id: result.lastInsertRowid });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка создания товара: ' + err.message });
  }
});

// PUT /api/shop/items/:id (admin)
router.put('/items/:id', adminMiddleware, async (req, res) => {
  try {
    const { category_id, name, description, image, price, original_price, commands, server_id, item_type, featured, visible } = req.body;
    const cmds = Array.isArray(commands) ? commands : (commands || '').split('\n').map(c => c.trim()).filter(Boolean);
    await query(
      'UPDATE foxaria_shop_items SET category_id=?, name=?, description=?, image=?, price=?, original_price=?, commands=?, server_id=?, item_type=?, featured=?, visible=? WHERE id=?',
      [category_id || null, name, description || '', image || '', parseFloat(price),
       original_price ? parseFloat(original_price) : null, JSON.stringify(cmds),
       server_id || 'anarchy', item_type || 'donate', featured ? 1 : 0, visible ? 1 : 0, req.params.id]
    );
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка обновления' });
  }
});

// DELETE /api/shop/items/:id (admin)
router.delete('/items/:id', adminMiddleware, async (req, res) => {
  try {
    await query('DELETE FROM foxaria_shop_items WHERE id = ?', [req.params.id]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка удаления' });
  }
});

// POST /api/shop/categories (admin)
router.post('/categories', adminMiddleware, async (req, res) => {
  try {
    const { name, icon, sort_order } = req.body;
    if (!name) return res.status(400).json({ error: 'Укажите название' });
    const result = await query(
      'INSERT INTO foxaria_shop_categories (name, icon, sort_order) VALUES (?, ?, ?)',
      [name, icon || '🎁', sort_order || 0]
    );
    res.json({ success: true, id: result.lastInsertRowid });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// DELETE /api/shop/categories/:id (admin)
router.delete('/categories/:id', adminMiddleware, async (req, res) => {
  try {
    await query('DELETE FROM foxaria_shop_categories WHERE id = ?', [req.params.id]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

module.exports = router;
