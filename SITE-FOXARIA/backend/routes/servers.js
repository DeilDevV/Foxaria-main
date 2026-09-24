const express = require('express');
const router = express.Router();
const { executeCommand, executeBungeeCommand, getPlayerCount, getBungeePlayerCount } = require('../services/rcon');
const { adminMiddleware } = require('../middleware/auth');
const config = require('../../config');

// GET /api/servers/online — суммарный онлайн по BungeeCord (все серверы сети)
router.get('/online', async (req, res) => {
  try {
    const data = await getBungeePlayerCount();
    res.json(data);
  } catch {
    res.json({ total: 0, servers: {} });
  }
});

// GET /api/servers — статус всех серверов
router.get('/', async (req, res) => {
  try {
    const statuses = await Promise.allSettled(
      config.servers.map(async (server) => {
        try {
          const info = await getPlayerCount(server.id);
          return {
            id: server.id,
            name: server.name,
            description: server.description,
            ip: server.ip,
            port: server.port,
            icon: server.icon,
            color: server.color,
            online: true,
            players: info.online,
            maxPlayers: info.max,
            playerList: info.players,
          };
        } catch {
          return {
            id: server.id,
            name: server.name,
            description: server.description,
            ip: server.ip,
            port: server.port,
            icon: server.icon,
            color: server.color,
            online: false,
            players: 0,
            maxPlayers: 0,
            playerList: [],
          };
        }
      })
    );

    const servers = statuses.map(s => s.status === 'fulfilled' ? s.value : s.reason);
    res.json(servers);
  } catch (err) {
    res.status(500).json({ error: 'Ошибка получения статуса серверов' });
  }
});

// GET /api/servers/:id — статус конкретного сервера
router.get('/:id', async (req, res) => {
  try {
    const server = config.servers.find(s => s.id === req.params.id);
    if (!server) return res.status(404).json({ error: 'Сервер не найден' });

    const info = await getPlayerCount(server.id);
    res.json({
      ...server,
      rcon: undefined,
      online: true,
      players: info.online,
      maxPlayers: info.max,
      playerList: info.players,
    });
  } catch (err) {
    const server = config.servers.find(s => s.id === req.params.id);
    res.json({ ...server, rcon: undefined, online: false, players: 0, maxPlayers: 0, playerList: [] });
  }
});

// POST /api/servers/:id/command — выполнить команду (только для админов)
router.post('/:id/command', adminMiddleware, async (req, res) => {
  try {
    const { command } = req.body;
    if (!command) return res.status(400).json({ error: 'Команда не указана' });

    const server = config.servers.find(s => s.id === req.params.id);
    if (!server) return res.status(404).json({ error: 'Сервер не найден' });

    // Блокируем опасные команды
    const forbidden = ['stop', 'restart', 'reload', 'rm', 'del', 'format'];
    const cmdLower = command.toLowerCase().trim();
    if (forbidden.some(f => cmdLower.startsWith(f))) {
      return res.status(403).json({ error: 'Эта команда запрещена через сайт' });
    }

    const result = await executeCommand(req.params.id, command);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Ошибка выполнения команды' });
  }
});

// POST /api/servers/bungeecord/command — выполнить команду через BungeeCord RCON
router.post('/bungeecord/command', adminMiddleware, async (req, res) => {
  try {
    const { command } = req.body;
    if (!command) return res.status(400).json({ error: 'Команда не указана' });

    const forbidden = ['stop', 'restart', 'reload', 'rm', 'del', 'format'];
    if (forbidden.some(f => command.toLowerCase().trim().startsWith(f))) {
      return res.status(403).json({ error: 'Эта команда запрещена' });
    }

    const result = await executeBungeeCommand(command);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: 'Ошибка выполнения команды' });
  }
});

module.exports = router;
