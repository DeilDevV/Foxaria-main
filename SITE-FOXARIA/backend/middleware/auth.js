const jwt = require('jsonwebtoken');
const config = require('../../config');
const { queryOne } = require('../services/database');

async function authMiddleware(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Токен авторизации отсутствует' });
  }

  const token = authHeader.slice(7);
  try {
    const decoded = jwt.verify(token, config.jwt.secret);
    req.user = decoded;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Недействительный или истёкший токен' });
  }
}

async function adminMiddleware(req, res, next) {
  await authMiddleware(req, res, async () => {
    if (!req.user.isAdmin) {
      return res.status(403).json({ error: 'Доступ запрещён: требуются права администратора' });
    }
    next();
  });
}

async function modMiddleware(req, res, next) {
  await authMiddleware(req, res, async () => {
    if (!req.user.isMod && !req.user.isAdmin) {
      return res.status(403).json({ error: 'Доступ запрещён: требуются права модератора' });
    }
    next();
  });
}

module.exports = { authMiddleware, adminMiddleware, modMiddleware };
