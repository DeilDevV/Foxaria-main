const express = require('express');
const router = express.Router();
const { query, queryOne } = require('../services/database');
const { authMiddleware, adminMiddleware } = require('../middleware/auth');

// GET /api/news — список новостей
router.get('/', async (req, res) => {
  try {
    const { page = 1, limit = 10, server } = req.query;
    const offset = (parseInt(page) - 1) * parseInt(limit);
    let where = 'WHERE published = 1';
    const params = [];
    if (server && server !== 'all') { where += ' AND (server = ? OR server = "all")'; params.push(server); }

    const total = await queryOne(`SELECT COUNT(*) as count FROM foxaria_news ${where}`, params);
    const news = await query(
      `SELECT id, title, preview, image, server, tags, pinned, views, author, created_at
       FROM foxaria_news ${where}
       ORDER BY pinned DESC, created_at DESC LIMIT ? OFFSET ?`,
      [...params, parseInt(limit), offset]
    );

    res.json({ news, total: total?.count || 0, page: parseInt(page) });
  } catch (err) {
    if (err.message?.includes('недоступна')) return res.json({ news: [], total: 0, page: 1 });
    res.status(500).json({ error: 'Ошибка загрузки новостей' });
  }
});

// GET /api/news/:id — одна новость
router.get('/:id', async (req, res) => {
  try {
    const news = await queryOne(
      'SELECT * FROM foxaria_news WHERE id = ? AND published = 1',
      [req.params.id]
    );
    if (!news) return res.status(404).json({ error: 'Новость не найдена' });

    await query('UPDATE foxaria_news SET views = views + 1 WHERE id = ?', [news.id]);
    res.json(news);
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/news — создать новость (только адмн)
router.post('/', adminMiddleware, async (req, res) => {
  try {
    const { title, content, preview, image, server = 'all', tags, pinned = 0 } = req.body;
    if (!title || !content) return res.status(400).json({ error: 'Заголовок и текст обязательны' });

    const [result] = await require('../services/database').getPool().execute(
      'INSERT INTO foxaria_news (title, content, preview, image, server, tags, pinned, author) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
      [title, content, preview || content.slice(0, 200), image, server, tags, pinned, req.user.username]
    );

    res.json({ success: true, id: result.insertId });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка создания новости' });
  }
});

// PUT /api/news/:id — обновить новость
router.put('/:id', adminMiddleware, async (req, res) => {
  try {
    const { title, content, preview, image, server, tags, pinned, published } = req.body;
    await query(
      'UPDATE foxaria_news SET title=?, content=?, preview=?, image=?, server=?, tags=?, pinned=?, published=? WHERE id=?',
      [title, content, preview, image, server, tags, pinned, published, req.params.id]
    );
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка обновления' });
  }
});

// DELETE /api/news/:id
router.delete('/:id', adminMiddleware, async (req, res) => {
  try {
    await query('DELETE FROM foxaria_news WHERE id = ?', [req.params.id]);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка удаления' });
  }
});

module.exports = router;
