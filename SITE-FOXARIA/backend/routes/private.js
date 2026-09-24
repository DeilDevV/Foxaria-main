const express = require('express');
const router = express.Router();
const { query, queryOne, logAction } = require('../services/database');
const { authMiddleware } = require('../middleware/auth');
const { executeCommand } = require('../services/rcon');
const config = require('../../config');

const ANARCHY = 'anarchy';

// GET /api/private/my — регионы/приваты текущего игрока
router.get('/my', authMiddleware, async (req, res) => {
  try {
    const regions = await query(
      `SELECT id, world, display_name, center_x, center_z, half_size,
              level, core_hp, core_max_hp, deposited_wood, deposited_iron, flags
       FROM fx_regions_view
       WHERE owner_uuid = ?`,
      [req.user.uuid]
    );

    const result = await Promise.all(regions.map(async r => {
      const members = await query(
        `SELECT m.member_uuid, m.role,
                p.username
         FROM fx_region_members_view m
         LEFT JOIN luckperms_players p ON p.uuid = m.member_uuid
         WHERE m.region_id = ?`,
        [r.id]
      ).catch(() => []);

      return {
        id: r.id,
        displayName: r.display_name || `Регион #${r.id}`,
        world: r.world || 'world',
        centerX: r.center_x,
        centerZ: r.center_z,
        halfSize: r.half_size,
        level: r.level || 1,
        coreHp: r.core_hp || 0,
        coreMaxHp: r.core_max_hp || 100,
        depositedWood: r.deposited_wood || 0,
        depositedIron: r.deposited_iron || 0,
        size: r.half_size ? Math.pow(r.half_size * 2 + 1, 2) : 0,
        members: members.map(m => ({
          uuid: m.member_uuid,
          username: m.username || 'Неизвестно',
          role: m.role || 'MEMBER',
        })),
      };
    }));

    res.json(result);
  } catch (err) {
    console.error('Private/region error:', err);
    res.status(500).json({ error: 'Ошибка загрузки регионов' });
  }
});

// GET /api/private/:id — детали одного региона
router.get('/:id', authMiddleware, async (req, res) => {
  try {
    const region = await queryOne(
      'SELECT * FROM fx_regions_view WHERE id = ? LIMIT 1',
      [req.params.id]
    );
    if (!region) return res.status(404).json({ error: 'Регион не найден' });
    if (region.owner_uuid !== req.user.uuid && !req.user.isAdmin) {
      return res.status(403).json({ error: 'Нет доступа' });
    }

    const members = await query(
      `SELECT m.member_uuid, m.role, p.username
       FROM fx_region_members_view m
       LEFT JOIN luckperms_players p ON p.uuid = m.member_uuid
       WHERE m.region_id = ?`,
      [region.id]
    ).catch(() => []);

    res.json({
      id: region.id,
      displayName: region.display_name || `Регион #${region.id}`,
      world: region.world,
      centerX: region.center_x,
      centerZ: region.center_z,
      halfSize: region.half_size,
      level: region.level || 1,
      coreHp: region.core_hp,
      coreMaxHp: region.core_max_hp,
      depositedWood: region.deposited_wood,
      depositedIron: region.deposited_iron,
      flags: region.flags,
      members,
    });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// POST /api/private/add-member — добавить участника (через RCON)
router.post('/add-member', authMiddleware, async (req, res) => {
  try {
    const { targetUsername, regionName } = req.body;
    if (!targetUsername) return res.status(400).json({ error: 'Укажите игрока' });

    // Foxaria region command — /region member add <region> <player>
    const cmd = regionName
      ? `region member add ${regionName} ${targetUsername}`
      : `region member add ${targetUsername}`;

    const result = await executeCommand(ANARCHY, cmd);
    if (!result.success) return res.status(500).json({ error: 'Ошибка RCON: ' + result.error });

    await logAction(req.user.username, 'region_add_member', targetUsername, { region: regionName }, req.ip);
    res.json({ success: true, message: `${targetUsername} добавлен в регион` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// POST /api/private/remove-member — убрать участника
router.post('/remove-member', authMiddleware, async (req, res) => {
  try {
    const { targetUsername, regionName } = req.body;
    if (!targetUsername) return res.status(400).json({ error: 'Укажите игрока' });

    const cmd = regionName
      ? `region member remove ${regionName} ${targetUsername}`
      : `region member remove ${targetUsername}`;

    const result = await executeCommand(ANARCHY, cmd);
    if (!result.success) return res.status(500).json({ error: 'Ошибка RCON: ' + result.error });

    await logAction(req.user.username, 'region_remove_member', targetUsername, { region: regionName }, req.ip);
    res.json({ success: true, message: `${targetUsername} убран из региона` });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

module.exports = router;
