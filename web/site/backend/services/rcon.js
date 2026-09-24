const { Rcon } = require('rcon-client');
const config = require('../../config');

// Кэш статуса серверов — TTL 15с, чтобы RCON не долбило при частых запросах с фронта
const _statusCache = new Map(); // serverId → { data, expiresAt }
const CACHE_TTL = 15000;

async function getConnection(serverId) {
  const serverConfig = config.servers.find(s => s.id === serverId);
  if (!serverConfig) throw new Error(`Сервер '${serverId}' не найден в config.js`);

  const rconCfg = serverConfig.rcon;
  const rcon = new Rcon({
    host: rconCfg.host,
    port: rconCfg.port,
    password: rconCfg.password,
    timeout: rconCfg.timeout || 5000,
  });

  await rcon.connect();
  return rcon;
}

async function getBungeeConnection() {
  const bCfg = config.bungeecord?.rcon;
  if (!bCfg) throw new Error('BungeeCord RCON не настроен в config.js');
  const rcon = new Rcon({
    host: bCfg.host,
    port: bCfg.port,
    password: bCfg.password,
    timeout: bCfg.timeout || 5000,
  });
  await rcon.connect();
  return rcon;
}

async function executeCommand(serverId, command) {
  let rcon;
  try {
    rcon = await getConnection(serverId);
    const response = await rcon.send(command);
    return { success: true, response: response || '' };
  } catch (err) {
    console.error(`RCON [${serverId}] error:`, err.message);
    return { success: false, error: err.message };
  } finally {
    if (rcon) { try { rcon.end(); } catch {} }
  }
}

async function executeBungeeCommand(command) {
  let rcon;
  try {
    rcon = await getBungeeConnection();
    const response = await rcon.send(command);
    return { success: true, response: response || '' };
  } catch (err) {
    console.error(`RCON [bungeecord] error:`, err.message);
    return { success: false, error: err.message };
  } finally {
    if (rcon) { try { rcon.end(); } catch {} }
  }
}

async function executeBungeeCommands(commands) {
  let rcon;
  try {
    rcon = await getBungeeConnection();
    const results = [];
    for (const cmd of commands) {
      const response = await rcon.send(cmd);
      results.push({ command: cmd, response: response || '' });
      await sleep(100);
    }
    return { success: true, results };
  } catch (err) {
    console.error(`RCON [bungeecord] batch error:`, err.message);
    return { success: false, error: err.message };
  } finally {
    if (rcon) { try { rcon.end(); } catch {} }
  }
}

async function executeCommands(serverId, commands) {
  let rcon;
  try {
    rcon = await getConnection(serverId);
    const results = [];
    for (const cmd of commands) {
      const response = await rcon.send(cmd);
      results.push({ command: cmd, response: response || '' });
      await sleep(100);
    }
    return { success: true, results };
  } catch (err) {
    console.error(`RCON [${serverId}] batch error:`, err.message);
    return { success: false, error: err.message };
  } finally {
    if (rcon) { try { rcon.end(); } catch {} }
  }
}

async function isServerOnline(serverId) {
  try {
    const result = await executeCommand(serverId, 'list');
    return result.success;
  } catch {
    return false;
  }
}

async function getPlayerCount(serverId) {
  // Проверяем кэш
  const cached = _statusCache.get(serverId);
  if (cached && cached.expiresAt > Date.now()) {
    return cached.data;
  }

  try {
    const result = await executeCommand(serverId, 'list');
    if (!result.success) {
      const empty = { online: 0, max: 0, players: [], offline: true };
      _statusCache.set(serverId, { data: empty, expiresAt: Date.now() + CACHE_TTL });
      return empty;
    }

    // Parse: "There are X of a max of Y players online: ..."
    const match = result.response.match(/(\d+).*?(\d+).*?online[:\s]*(.*)/i);
    const data = match
      ? {
          online: parseInt(match[1]),
          max: parseInt(match[2]),
          players: match[3] ? match[3].split(',').map(p => p.trim()).filter(Boolean) : [],
          offline: false,
        }
      : { online: 0, max: 0, players: [], offline: false };

    _statusCache.set(serverId, { data, expiresAt: Date.now() + CACHE_TTL });
    return data;
  } catch {
    const empty = { online: 0, max: 0, players: [], offline: true };
    _statusCache.set(serverId, { data: empty, expiresAt: Date.now() + CACHE_TTL });
    return empty;
  }
}

// Fallback: query each network server individually when BungeeCord RCON unavailable
async function getNetworkPlayerCount(cacheKey) {
  const networkServers = config.networkServers || [];
  if (!networkServers.length) {
    const d = { total: null, available: false, servers: {} };
    _statusCache.set(cacheKey, { data: d, expiresAt: Date.now() + CACHE_TTL });
    return d;
  }
  let total = 0;
  const servers = {};
  await Promise.all(networkServers.map(async (srv) => {
    try {
      const rc = srv.rcon;
      const rcon = new Rcon({ host: rc.host, port: rc.port, password: rc.password, timeout: rc.timeout || 3000 });
      await rcon.connect();
      const response = await rcon.send('list');
      try { rcon.end(); } catch {}
      const m = (response || '').match(/(\d+)[^\d]+(\d+)[^\d]+online/i);
      if (m) {
        const count = parseInt(m[1], 10);
        total += count;
        servers[srv.id] = { count, players: [] };
      }
    } catch {}
  }));
  const d = { total, available: true, servers };
  _statusCache.set(cacheKey, { data: d, expiresAt: Date.now() + CACHE_TTL });
  return d;
}

// Получить суммарный онлайн через BungeeCord glist (все серверы сети)
async function getBungeePlayerCount() {
  const cacheKey = '__bungee_glist';
  const cached = _statusCache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) return cached.data;

  try {
    const result = await executeBungeeCommand('glist');
    if (!result.success) {
      return await getNetworkPlayerCount(cacheKey);
    }

    const text = result.response || '';
    // "Total players online: N" (Bungee/Waterfall) или русская локаль
    let totalMatch = text.match(/total\s+players\s+online[:\s]+(\d+)/i);
    if (!totalMatch) {
      totalMatch = text.match(/игроков?\s+онлайн[:\s]+(\d+)/i);
    }
    if (!totalMatch) {
      totalMatch = text.match(/онлайн[:\s]+(\d+)/i);
    }
    let total = totalMatch ? parseInt(totalMatch[1], 10) : 0;

    // Per-server lines: "ServerName (N): player1, player2" or "[ServerName] (N): ..."
    const servers = {};
    const lineRegex = /\[?([^\]()\n]+?)\]?\s*\((\d+)\):\s*([^\n]*)/gi;
    let m;
    while ((m = lineRegex.exec(text)) !== null) {
      const name = m[1].trim();
      const count = parseInt(m[2]);
      const players = m[3].split(',').map(p => p.trim()).filter(Boolean);
      servers[name] = { count, players };
    }

    if (!totalMatch) {
      total = Object.values(servers).reduce((s, v) => s + v.count, 0);
    }

    const data = { total, available: true, servers };
    _statusCache.set(cacheKey, { data, expiresAt: Date.now() + CACHE_TTL });
    return data;
  } catch {
    return await getNetworkPlayerCount(cacheKey);
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

module.exports = { executeCommand, executeBungeeCommand, executeBungeeCommands, executeCommands, isServerOnline, getPlayerCount, getBungeePlayerCount };
