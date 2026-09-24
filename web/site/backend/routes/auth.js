const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const { query, queryOne } = require('../services/database');
const { authMiddleware } = require('../middleware/auth');
const config = require('../../config');

// POST /api/auth/login
// Вход через никнейм (аккаунт должен существовать в БД сервера)
router.post('/login', async (req, res) => {
  try {
    const { username, password } = req.body;
    if (!username) return res.status(400).json({ error: 'Введите никнейм' });
    if (!password) return res.status(400).json({ error: 'Введите пароль' });

    // Ищем игрока среди зарегистрированных аккаунтов Foxaria
    const lpCfg = config.plugins.luckperms;
    const player = await queryOne(
      `SELECT uuid, username FROM ${lpCfg.playersTable} WHERE LOWER(username) = LOWER(?) LIMIT 1`,
      [username]
    );

    if (!player) {
      return res.status(404).json({
        error: 'Игрок не найден. Сначала зайдите на сервер.',
      });
    }

    // Проверяем пароль через fx_auth_accounts (Foxaria плагин, PBKDF2-SHA256)
    try {
      const authRecord = await queryOne(
        'SELECT username, password_hash, salt, iterations FROM foxaria_auth WHERE LOWER(username) = LOWER(?) LIMIT 1',
        [player.username]
      );
      if (!authRecord) {
        return res.status(401).json({ error: 'Аккаунт не зарегистрирован. Используйте /register на сервере.' });
      }
      const isValid = verifyFoxariaPassword(password, authRecord);
      if (!isValid) {
        return res.status(401).json({ error: 'Неверный пароль' });
      }
    } catch (err) {
      console.error('Auth check error:', err);
      return res.status(500).json({ error: 'Ошибка проверки пароля' });
    }

    // Получаем группу/права игрока
    const groupRow = await queryOne(
      `SELECT primary_group FROM ${lpCfg.playersTable} WHERE uuid = ? LIMIT 1`,
      [player.uuid]
    );
    const group = groupRow?.primary_group || 'default';
    const isAdmin = lpCfg.adminGroups.includes(group.toLowerCase());
    const isMod = lpCfg.modGroups.includes(group.toLowerCase());
    const isDonate = lpCfg.donateGroups.includes(group.toLowerCase());

    // Генерируем JWT токен
    const token = jwt.sign(
      {
        uuid: player.uuid,
        username: player.username,
        group,
        isAdmin,
        isMod,
        isDonate,
      },
      config.jwt.secret,
      { expiresIn: config.jwt.expiresIn }
    );

    res.json({
      token,
      user: {
        uuid: player.uuid,
        username: player.username,
        group,
        isAdmin,
        isMod,
        isDonate,
        avatar: `https://mc-heads.net/avatar/${player.username}/100`,
      },
    });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: 'Ошибка сервера при входе' });
  }
});

// GET /api/auth/me
router.get('/me', authMiddleware, async (req, res) => {
  try {
    const lpCfg = config.plugins.luckperms;
    const player = await queryOne(
      `SELECT uuid, username, primary_group FROM ${lpCfg.playersTable} WHERE uuid = ? LIMIT 1`,
      [req.user.uuid]
    );

    if (!player) return res.status(404).json({ error: 'Игрок не найден' });

    const group = player.primary_group || 'default';
    const isAdmin = lpCfg.adminGroups.includes(group.toLowerCase());
    const isMod = lpCfg.modGroups.includes(group.toLowerCase());
    const isDonate = lpCfg.donateGroups.includes(group.toLowerCase());

    res.json({
      uuid: player.uuid,
      username: player.username,
      group,
      isAdmin,
      isMod,
      isDonate,
      avatar: `https://mc-heads.net/avatar/${player.username}/100`,
    });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// Проверка пароля Foxaria плагина (PBKDF2, base64)
// Пробуем все варианты SHA — алгоритм определяется по совпадению хэша
function verifyFoxariaPassword(inputPassword, record) {
  if (!record || !record.password_hash || !record.salt) return false;
  try {
    const crypto = require('crypto');
    const salt = Buffer.from(record.salt, 'base64');
    const iterations = record.iterations || 120000;
    const stored = record.password_hash;
    // Определяем длину выходного ключа по длине base64-хэша
    const keylen = Math.floor(Buffer.byteLength(stored, 'base64'));
    // Пробуем sha256, sha1, sha512 — по тому что совпадёт
    for (const algo of ['sha256', 'sha1', 'sha512']) {
      const derived = crypto.pbkdf2Sync(inputPassword, salt, iterations, keylen, algo);
      if (derived.toString('base64') === stored) return true;
    }
    return false;
  } catch {
    return false;
  }
}

module.exports = router;
