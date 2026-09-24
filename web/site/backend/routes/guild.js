const express = require('express');
const router = express.Router();
const { query, queryOne, logAction } = require('../services/database');
const { authMiddleware } = require('../middleware/auth');
const { executeCommand } = require('../services/rcon');
const config = require('../../config');

const guildsCfg = config.plugins.guilds;
const ANARCHY = 'anarchy';

// GET /api/guild/my — гильдия текущего игрока
router.get('/my', authMiddleware, async (req, res) => {
  try {
    if (!guildsCfg.enabled) return res.json({ guild: null });

    // fx_guild_members использует player_uuid и player_name (не uuid/username)
    const member = await queryOne(
      `SELECT m.guild_id, m.role
       FROM ${guildsCfg.membersTable} m
       WHERE m.player_uuid = ? LIMIT 1`,
      [req.user.uuid]
    );
    if (!member) return res.json({ guild: null });

    const guild = await queryOne(
      `SELECT * FROM ${guildsCfg.guildsTable} WHERE id = ? LIMIT 1`,
      [member.guild_id]
    );
    if (!guild) return res.json({ guild: null });

    const [members, stats] = await Promise.all([
      query(
        `SELECT player_uuid AS uuid, player_name AS username, role, joined_at
         FROM ${guildsCfg.membersTable}
         WHERE guild_id = ?
         ORDER BY CASE role WHEN 'OWNER' THEN 0 WHEN 'MASTER' THEN 1 WHEN 'LEADER' THEN 2 WHEN 'OFFICER' THEN 3 WHEN 'ADMIN' THEN 4 ELSE 5 END`,
        [guild.id]
      ),
      queryOne('SELECT total_kills FROM fx_guild_stats WHERE guild_id = ? LIMIT 1', [guild.id]).catch(() => null),
    ]);

    res.json({
      guild: {
        id: guild.id,
        name: guild.name,
        motd: guild.motd || guild.description,
        tier: guild.level || guild.tier || 1,
        bankBalance: parseFloat(guild.bank_balance) || 0,
        guildCoins: guild.guild_coins || 0,
        guildPoints: guild.guild_points || 0,
        friendlyFire: !!guild.friendly_fire,
        tagColor: guild.tag_color || 'WHITE',
        chestRows: guild.chest_rows || 3,
        shopTier: guild.shop_tier || 1,
        totalKills: stats?.total_kills || 0,
        members: members.map(m => ({
          uuid: m.uuid,
          username: m.username || 'Неизвестно',
          role: m.role,
          joinedAt: m.joined_at,
          avatar: `https://mc-heads.net/avatar/${m.username || 'Steve'}/40`,
        })),
        myRole: member.role,
        isLeader: ['OWNER', 'MASTER', 'LEADER'].includes(member.role),
        isOfficer: ['OFFICER', 'ADMIN'].includes(member.role),
      },
    });
  } catch (err) {
    console.error('Guild error:', err);
    res.status(500).json({ error: 'Ошибка загрузки гильдии' });
  }
});

const ROLE_HIERARCHY = ['MEMBER', 'OFFICER', 'LEADER', 'MASTER'];

function nextRole(current, direction) {
  const idx = ROLE_HIERARCHY.indexOf(current?.toUpperCase());
  if (idx < 0) return null;
  const newIdx = idx + direction;
  if (newIdx < 0 || newIdx >= ROLE_HIERARCHY.length) return null;
  return ROLE_HIERARCHY[newIdx];
}

// POST /api/guild/kick — прямая DB операция (RCON работает только как игрок, не как консоль)
router.post('/kick', authMiddleware, async (req, res) => {
  try {
    if (!guildsCfg.enabled) return res.status(400).json({ error: 'Гильдии отключены' });
    const { targetUsername } = req.body;
    if (!targetUsername) return res.status(400).json({ error: 'Укажите игрока' });

    const myMember = await queryOne(
      `SELECT guild_id, role FROM ${guildsCfg.membersTable} WHERE player_uuid = ? LIMIT 1`,
      [req.user.uuid]
    );
    if (!myMember) return res.status(400).json({ error: 'Вы не состоите в гильдии' });
    if (!['OWNER', 'MASTER', 'LEADER', 'OFFICER', 'ADMIN'].includes(myMember.role)) {
      return res.status(403).json({ error: 'Недостаточно прав в гильдии' });
    }

    const target = await queryOne(
      `SELECT player_uuid AS uuid, role FROM ${guildsCfg.membersTable} WHERE guild_id = ? AND LOWER(player_name) = LOWER(?) LIMIT 1`,
      [myMember.guild_id, targetUsername]
    );
    if (!target) return res.status(404).json({ error: 'Игрок не найден в вашей гильдии' });
    if (target.role === 'OWNER') return res.status(403).json({ error: 'Нельзя исключить владельца' });

    await query(`DELETE FROM ${guildsCfg.membersTable} WHERE guild_id = ? AND player_uuid = ?`,
      [myMember.guild_id, target.uuid]);

    await logAction(req.user.username, 'guild_kick', targetUsername, { guild_id: myMember.guild_id }, req.ip);
    res.json({ success: true, message: `${targetUsername} исключён из гильдии` });
  } catch (err) {
    console.error('guild kick error:', err);
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// POST /api/guild/promote — прямая DB операция
router.post('/promote', authMiddleware, async (req, res) => {
  try {
    if (!guildsCfg.enabled) return res.status(400).json({ error: 'Гильдии отключены' });
    const { targetUsername } = req.body;
    if (!targetUsername) return res.status(400).json({ error: 'Укажите игрока' });

    const myMember = await queryOne(
      `SELECT guild_id, role FROM ${guildsCfg.membersTable} WHERE player_uuid = ? LIMIT 1`,
      [req.user.uuid]
    );
    if (!myMember || !['OWNER', 'MASTER', 'LEADER'].includes(myMember.role)) {
      return res.status(403).json({ error: 'Только лидер может повышать участников' });
    }

    const target = await queryOne(
      `SELECT player_uuid AS uuid, role FROM ${guildsCfg.membersTable} WHERE guild_id = ? AND LOWER(player_name) = LOWER(?) LIMIT 1`,
      [myMember.guild_id, targetUsername]
    );
    if (!target) return res.status(404).json({ error: 'Игрок не найден в вашей гильдии' });
    const newRole = nextRole(target.role, 1);
    if (!newRole) return res.status(400).json({ error: 'Дальнейшее повышение невозможно' });

    await query(`UPDATE ${guildsCfg.membersTable} SET role = ? WHERE guild_id = ? AND player_uuid = ?`,
      [newRole, myMember.guild_id, target.uuid]);

    await logAction(req.user.username, 'guild_promote', targetUsername, { newRole }, req.ip);
    res.json({ success: true, message: `${targetUsername} повышен до ${newRole}` });
  } catch (err) {
    console.error('guild promote error:', err);
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// POST /api/guild/demote — прямая DB операция
router.post('/demote', authMiddleware, async (req, res) => {
  try {
    if (!guildsCfg.enabled) return res.status(400).json({ error: 'Гильдии отключены' });
    const { targetUsername } = req.body;
    if (!targetUsername) return res.status(400).json({ error: 'Укажите игрока' });

    const myMember = await queryOne(
      `SELECT guild_id, role FROM ${guildsCfg.membersTable} WHERE player_uuid = ? LIMIT 1`,
      [req.user.uuid]
    );
    if (!myMember || !['OWNER', 'MASTER', 'LEADER'].includes(myMember.role)) {
      return res.status(403).json({ error: 'Только лидер может понижать участников' });
    }

    const target = await queryOne(
      `SELECT player_uuid AS uuid, role FROM ${guildsCfg.membersTable} WHERE guild_id = ? AND LOWER(player_name) = LOWER(?) LIMIT 1`,
      [myMember.guild_id, targetUsername]
    );
    if (!target) return res.status(404).json({ error: 'Игрок не найден в вашей гильдии' });
    const newRole = nextRole(target.role, -1);
    if (!newRole) return res.status(400).json({ error: 'Дальнейшее понижение невозможно' });

    await query(`UPDATE ${guildsCfg.membersTable} SET role = ? WHERE guild_id = ? AND player_uuid = ?`,
      [newRole, myMember.guild_id, target.uuid]);

    await logAction(req.user.username, 'guild_demote', targetUsername, { newRole }, req.ip);
    res.json({ success: true, message: `${targetUsername} понижен до ${newRole}` });
  } catch (err) {
    console.error('guild demote error:', err);
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// POST /api/guild/disband — прямая DB операция (каскадное удаление)
router.post('/disband', authMiddleware, async (req, res) => {
  try {
    if (!guildsCfg.enabled) return res.status(400).json({ error: 'Гильдии отключены' });

    const myMember = await queryOne(
      `SELECT guild_id, role FROM ${guildsCfg.membersTable} WHERE player_uuid = ? LIMIT 1`,
      [req.user.uuid]
    );
    if (!myMember || myMember.role !== 'OWNER') {
      return res.status(403).json({ error: 'Только владелец может распустить гильдию' });
    }

    const guildId = myMember.guild_id;
    // Cascade delete all guild data
    for (const table of [
      'fx_guild_chest_items', 'fx_guild_members', 'fx_guild_roles',
      'fx_guild_stats', 'fx_guild_upgrades', 'fx_guild_level_rewards_claimed',
      'fx_guild_level_objective_progress', 'fx_guild_activity', 'fx_guild_war_matches',
      'fx_guilds'
    ]) {
      await query(`DELETE FROM \`${table}\` WHERE guild_id = ?`, [guildId]).catch(() => {});
    }

    await logAction(req.user.username, 'guild_disband', guildId, {}, req.ip);
    res.json({ success: true, message: 'Гильдия распущена' });
  } catch (err) {
    console.error('guild disband error:', err);
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// POST /api/guild/leave
router.post('/leave', authMiddleware, async (req, res) => {
  try {
    if (!guildsCfg.enabled) return res.status(400).json({ error: 'Гильдии отключены' });

    const myMember = await queryOne(
      `SELECT guild_id, role FROM ${guildsCfg.membersTable} WHERE player_uuid = ? LIMIT 1`,
      [req.user.uuid]
    );
    if (!myMember) return res.status(400).json({ error: 'Вы не состоите в гильдии' });
    if (myMember.role === 'OWNER') {
      return res.status(400).json({ error: 'Владелец не может покинуть гильдию. Сначала распустите её.' });
    }

    await query(`DELETE FROM ${guildsCfg.membersTable} WHERE guild_id = ? AND player_uuid = ?`,
      [myMember.guild_id, req.user.uuid]);

    await logAction(req.user.username, 'guild_leave', myMember.guild_id, {}, req.ip);
    res.json({ success: true, message: 'Вы покинули гильдию' });
  } catch (err) {
    console.error('guild leave error:', err);
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// POST /api/guild/invite — сохраняет приглашение в fx_guild_invites (плагин видит при /guild accept)
router.post('/invite', authMiddleware, async (req, res) => {
  try {
    if (!guildsCfg.enabled) return res.status(400).json({ error: 'Гильдии отключены' });
    const { targetUsername } = req.body;
    if (!targetUsername) return res.status(400).json({ error: 'Укажите игрока' });

    const myMember = await queryOne(
      `SELECT guild_id, role FROM ${guildsCfg.membersTable} WHERE player_uuid = ? LIMIT 1`,
      [req.user.uuid]
    );
    if (!myMember) return res.status(400).json({ error: 'Вы не состоите в гильдии' });
    if (!['OWNER', 'MASTER', 'LEADER', 'OFFICER', 'ADMIN'].includes(myMember.role)) {
      return res.status(403).json({ error: 'Недостаточно прав для приглашения' });
    }

    // Найти UUID цели по никнейму
    const targetPlayer = await queryOne(
      `SELECT player_uuid AS uuid FROM ${guildsCfg.membersTable} WHERE LOWER(player_name) = LOWER(?) LIMIT 1`,
      [targetUsername]
    ) || await queryOne(
      `SELECT uuid FROM luckperms_players WHERE LOWER(username) = LOWER(?) LIMIT 1`,
      [targetUsername]
    );
    if (!targetPlayer) return res.status(404).json({ error: 'Игрок не найден' });

    const alreadyMember = await queryOne(
      `SELECT 1 FROM ${guildsCfg.membersTable} WHERE player_uuid = ? LIMIT 1`,
      [targetPlayer.uuid]
    );
    if (alreadyMember) return res.status(400).json({ error: 'Игрок уже состоит в гильдии' });

    const expiresAt = Date.now() + 5 * 60 * 1000;
    await query(
      `INSERT INTO fx_guild_invites (guild_id, target_uuid, inviter_uuid, expires_at)
       VALUES (?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE inviter_uuid = VALUES(inviter_uuid), expires_at = VALUES(expires_at)`,
      [myMember.guild_id, targetPlayer.uuid, req.user.uuid, expiresAt]
    );

    await logAction(req.user.username, 'guild_invite', targetUsername, { guild_id: myMember.guild_id }, req.ip);
    res.json({ success: true, message: `Приглашение отправлено ${targetUsername}. Они могут принять его в игре (/guild accept)` });
  } catch (err) {
    console.error('guild invite error:', err);
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// GET /api/guild/list
router.get('/list', async (req, res) => {
  try {
    if (!guildsCfg.enabled) return res.json([]);
    const guilds = await query(
      `SELECT g.id, g.name, g.motd AS description, g.level AS tier, g.guild_points,
              g.bank_balance, g.friendly_fire, g.tag_color,
              COUNT(m.player_uuid) as member_count,
              gs.total_kills
       FROM ${guildsCfg.guildsTable} g
       LEFT JOIN ${guildsCfg.membersTable} m ON m.guild_id = g.id
       LEFT JOIN fx_guild_stats gs ON gs.guild_id = g.id
       GROUP BY g.id ORDER BY member_count DESC LIMIT 50`
    );
    res.json(guilds);
  } catch (err) {
    res.status(500).json({ error: 'Ошибка загрузки гильдий' });
  }
});

module.exports = router;
