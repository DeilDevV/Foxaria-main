const { queryOne } = require('./database');
const config = require('../../config');

function anarchyServerId() {
  const list = config.servers || [];
  const found = list.find((s) => s && s.id === 'anarchy');
  return found?.id || list[0]?.id || 'anarchy';
}

/** UUID по нику из luckperms_players VIEW (Foxaria DB). */
async function resolvePlayerRowByName(username) {
  if (!username) return null;
  return queryOne(
    'SELECT uuid, username FROM luckperms_players WHERE LOWER(username) = LOWER(?)',
    [username.trim()]
  );
}

module.exports = { resolvePlayerRowByName, anarchyServerId };
