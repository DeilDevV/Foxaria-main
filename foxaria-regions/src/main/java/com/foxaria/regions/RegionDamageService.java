package com.foxaria.regions;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.regions.RegionRepository.DamagedBlockRow;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionDamageService {

    private final JavaPlugin plugin;
    private final RegionConfig config;
    private final RegionRepository repo;
    private final RegionManager manager;
    private final MessageService messages;
    private final ConcurrentHashMap<UUID, Long> lastDismantleMs = new ConcurrentHashMap<>();

    public RegionDamageService(
        JavaPlugin plugin,
        RegionConfig config,
        RegionRepository repo,
        RegionManager manager,
        MessageService messages
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repo = repo;
        this.manager = manager;
        this.messages = messages;
    }

    /** Оставшееся время кулдауна сноса (мс), 0 если можно снести. */
    public long dismantleCooldownRemainingMs(Player player) {
        Long t = lastDismantleMs.get(player.getUniqueId());
        if (t == null) {
            return 0;
        }
        long left = t + config.dismantleCooldownMs - System.currentTimeMillis();
        return Math.max(0, left);
    }

    public void msgCannotBreakThis(Player player) {
        player.sendActionBar(FoxariaText.plain("Этим нельзя ломать блоки привата.").color(NamedTextColor.RED));
    }

    public void damageCore(Player attacker, RegionRecord region, int amount) {
        long now = System.currentTimeMillis();
        int hp = Math.max(0, region.coreHp() - amount);
        repo.updateCore(region.id(), hp, now).join();
        manager.patchCore(region.id(), hp, now);
        if (hp <= 0) {
            destroyRegion(region);
        }
    }

    public void damageBlockInRegion(RegionRecord region, Block block, int amount) {
        if (block.getType() == Material.BEDROCK || config.blockHp(block.getType()) <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Location loc = block.getLocation();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String matName = block.getType().name();

        Optional<DamagedBlockRow> existing = repo.findDamaged(region.id(), x, y, z).join();

        int maxHp = existing.map(DamagedBlockRow::maxHp).orElseGet(() -> config.blockHp(block.getType()));
        int curHp;
        if (existing.isPresent()) {
            curHp = Math.max(0, existing.get().currentHp() - amount);
        } else {
            curHp = Math.max(0, maxHp - amount);
        }

        if (curHp <= 0) {
            repo.deleteDamagedBlock(region.id(), x, y, z).join();
            dropContainerLoot(block);
            block.setType(Material.AIR, false);
            return;
        }

        DamagedBlockRow row = new DamagedBlockRow(x, y, z, matName, curHp, maxHp, now);
        repo.upsertDamagedBlock(region.id(), row).join();
    }

    public void damageCoreAtBlock(Player attacker, RegionRecord region, Block hitBlock, int amount) {
        damageCore(attacker, region, amount);
    }

    public void applyExplosion(Player source, Location center, int tier) {
        int dmg = config.dynamiteDamage(tier);
        int r = config.explosionXzRadius;
        int yh = config.explosionYHalf;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        org.bukkit.World world = center.getWorld();
        if (world == null) {
            return;
        }

        for (int dy = -yh; dy <= yh; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Location bl = b.getLocation();
                    Optional<RegionRecord> reg = manager.findContaining(bl);
                    if (reg.isEmpty()) {
                        continue;
                    }
                    RegionRecord rrec = reg.get();
                    if (rrec.isCoreBlock(b.getX(), b.getY(), b.getZ())) {
                        damageCore(source instanceof Player p ? p : null, rrec, dmg);
                    } else {
                        damageBlockInRegion(rrec, b, dmg);
                    }
                }
            }
        }
    }

    /** Снос владельцем: предмет ядра в инвентарь, регион удаляется. */
    public void dismantleCore(Player player, RegionRecord region) {
        if (dismantleCooldownRemainingMs(player) > 0) {
            return;
        }
        var left = player.getInventory().addItem(RegionItems.privatCabinet(plugin, region.level()));
        if (!left.isEmpty()) {
            left.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        lastDismantleMs.put(player.getUniqueId(), System.currentTimeMillis());
        destroyRegion(region);
    }

    private static void dropContainerLoot(Block block) {
        Material t = block.getType();
        if (t == Material.ENDER_CHEST) {
            return;
        }
        boolean storage = t == Material.CHEST || t == Material.TRAPPED_CHEST || t == Material.BARREL
            || Tag.SHULKER_BOXES.isTagged(t);
        if (!storage) {
            return;
        }
        if (!(block.getState() instanceof BlockInventoryHolder holder)) {
            return;
        }
        Location dropAt = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack it : holder.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) {
                block.getWorld().dropItemNaturally(dropAt, it.clone());
            }
        }
        holder.getInventory().clear();
    }

    private void destroyRegion(RegionRecord region) {
        playCoreDestroyedFxAndBroadcast(region);
        removeRegionBlocksAndDatabase(region);
    }

    private void playCoreDestroyedFxAndBroadcast(RegionRecord region) {
        World w = plugin.getServer().getWorld(region.world());
        if (w == null) {
            return;
        }
        Location loc = new Location(w, region.cabinetX() + 0.5, region.cabinetY() + 1.0, region.cabinetZ() + 0.5);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.85f);
        try {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        } catch (Throwable ignored) {
            w.spawnParticle(Particle.EXPLOSION, loc, 24, 0.35, 0.35, 0.35, 0.02);
        }
        String title = region.effectiveDisplayTitle();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) {
                continue;
            }
            if (manager.findContaining(p.getLocation()).filter(r -> r.id() == region.id()).isEmpty()) {
                continue;
            }
            messages.send(p, "region.core-destroyed",
                "&cЯдро привата региона &f<name>&c было уничтожено!",
                new MessageService.Placeholder("name", title));
        }
    }

    /** Админ: снести ядро в мире и строку в БД (без выдачи предмета). */
    public void adminDeleteRegion(RegionRecord region) {
        removeRegionBlocksAndDatabase(region);
    }

    private void removeRegionBlocksAndDatabase(RegionRecord region) {
        org.bukkit.World w = plugin.getServer().getWorld(region.world());
        if (w != null) {
            Block b = w.getBlockAt(region.cabinetX(), region.cabinetY(), region.cabinetZ());
            Block u = w.getBlockAt(region.cabinetX(), region.cabinetY() + 1, region.cabinetZ());
            RegionBlockMarkers.clear(plugin, b);
            RegionBlockMarkers.clear(plugin, u);
            b.setType(Material.AIR, false);
            u.setType(Material.AIR, false);
        }
        repo.deleteRegion(region.id()).join();
        manager.remove(region.id());
    }
}
