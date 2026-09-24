package com.foxaria.itemtemplates.listener;

import com.foxaria.itemtemplates.ability.AbilityCodec;
import com.foxaria.itemtemplates.ability.AbilityInstance;
import com.foxaria.itemtemplates.ability.ItemAbility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Исполнение особых способностей Foxaria.
 *
 * Все эффекты сделаны «безопасно для гриферского сервера»:
 *  - взрывы не разрушают блоки (createExplosion с breakBlocks = false);
 *  - самонаведение не бьёт по своим и не цепляет стрелка;
 *  - у снарядов есть счётчик тиков, чтобы задачи не висели вечно.
 */
public final class ItemAbilityListener implements Listener {

    private static final String ARROW_TAG = "fox-arrow-ability";
    private static final int HOMING_MAX_TICKS = 100;

    private final JavaPlugin plugin;

    public ItemAbilityListener(JavaPlugin plugin) {
        this.plugin = plugin;
        startPassiveTask();
    }

    // ══════════ Ближний бой и попадания ══════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        Player attacker = resolveAttacker(event);
        boolean ranged = event.getDamager() instanceof Projectile;

        // ─── Защита цели: срабатывает даже без атакующего-игрока ───
        if (victim instanceof Player defender) {
            for (AbilityInstance instance : armorAbilities(defender)) {
                if (instance.ability().slot() != ItemAbility.Slot.DEFENSE) {
                    continue;
                }
                if (applyDefense(event, defender, instance)) {
                    return; // уклонение отменило урон
                }
            }
        }

        if (attacker == null) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        List<AbilityInstance> abilities = AbilityCodec.read(plugin, weapon);
        if (abilities.isEmpty()) {
            return;
        }

        for (AbilityInstance instance : abilities) {
            ItemAbility ability = instance.ability();
            boolean fitsMelee = ability.slot() == ItemAbility.Slot.MELEE && !ranged;
            boolean fitsAny = ability.slot() == ItemAbility.Slot.ANY_ATTACK;
            if (!fitsMelee && !fitsAny) {
                continue;
            }
            if (!roll(instance.chance())) {
                continue;
            }
            applyOffense(event, attacker, victim, instance);
        }
    }

    private void applyOffense(EntityDamageByEntityEvent event, Player attacker, LivingEntity victim, AbilityInstance instance) {
        Location at = victim.getLocation();
        switch (instance.ability()) {
            case EXPLOSIVE_STRIKE -> {
                // breakBlocks = false: постройки не страдают, урон по существам остаётся.
                victim.getWorld().createExplosion(at, instance.power(), false, false, attacker);
                victim.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
            }
            case KNOCKBACK_BLAST -> {
                Vector push = victim.getLocation().toVector()
                    .subtract(attacker.getLocation().toVector())
                    .normalize().multiply(Math.max(1, instance.power()) * 0.6D).setY(0.45D);
                victim.setVelocity(push);
                for (Entity nearby : victim.getNearbyEntities(3, 2, 3)) {
                    if (nearby instanceof LivingEntity other && !other.equals(attacker)) {
                        other.setVelocity(push.clone().multiply(0.6D));
                    }
                }
                playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 1.4F);
            }
            case FROST_BITE -> {
                addEffect(victim, PotionEffectType.SLOWNESS, instance.durationTicks(), instance.amplifier());
                addEffect(victim, PotionEffectType.MINING_FATIGUE, instance.durationTicks(), 0);
                victim.setFreezeTicks(Math.min(victim.getMaxFreezeTicks(), instance.durationTicks()));
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0.02);
            }
            case LIFE_STEAL -> {
                double healed = event.getFinalDamage() * (instance.power() / 100.0D);
                double max = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                    ? attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                    : 20.0D;
                attacker.setHealth(Math.min(max, attacker.getHealth() + Math.max(0.5D, healed)));
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0), 3);
            }
            case LIGHTNING_STRIKE -> victim.getWorld().strikeLightning(at);
            case CHAIN_LIGHTNING -> {
                int jumps = Math.max(1, instance.power());
                int done = 0;
                for (Entity nearby : victim.getNearbyEntities(6, 4, 6)) {
                    if (done >= jumps) {
                        break;
                    }
                    if (nearby instanceof LivingEntity other && !other.equals(attacker)) {
                        other.getWorld().strikeLightning(other.getLocation());
                        done++;
                    }
                }
            }
            case EXECUTE -> {
                double maxHealth = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                    ? victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                    : 20.0D;
                double threshold = maxHealth * (instance.power() / 100.0D);
                if (victim.getHealth() - event.getFinalDamage() <= threshold) {
                    event.setDamage(event.getDamage() + maxHealth);
                    playSound(at, Sound.ENTITY_WITHER_DEATH, 0.6F, 1.6F);
                }
            }
            case WITHER_TOUCH -> addEffect(victim, PotionEffectType.WITHER, instance.durationTicks(), instance.amplifier());
            case POISON_BLADE -> addEffect(victim, PotionEffectType.POISON, instance.durationTicks(), instance.amplifier());
            case BLIND_STRIKE -> addEffect(victim, PotionEffectType.BLINDNESS, instance.durationTicks(), 0);
            case FIRE_TRAIL -> victim.setFireTicks(instance.durationTicks());
            default -> {
                // остальные слоты обрабатываются в своих обработчиках
            }
        }
    }

    /** @return true, если урон полностью отменён (уклонение). */
    private boolean applyDefense(EntityDamageByEntityEvent event, Player defender, AbilityInstance instance) {
        switch (instance.ability()) {
            case DODGE -> {
                if (roll(instance.chance())) {
                    event.setCancelled(true);
                    defender.getWorld().spawnParticle(Particle.CLOUD, defender.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.01);
                    playSound(defender.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 0.7F, 1.8F);
                    return true;
                }
            }
            case THORNS_AURA -> {
                if (roll(instance.chance()) && event.getDamager() instanceof LivingEntity source) {
                    double reflected = event.getFinalDamage() * (instance.power() / 100.0D);
                    if (reflected > 0) {
                        source.damage(reflected, defender);
                    }
                }
            }
            case SECOND_WIND -> {
                double left = defender.getHealth() - event.getFinalDamage();
                if (left <= 2.0D && roll(instance.chance())) {
                    addEffect(defender, PotionEffectType.REGENERATION, instance.durationTicks(), instance.amplifier());
                    addEffect(defender, PotionEffectType.ABSORPTION, instance.durationTicks(), instance.amplifier());
                    playSound(defender.getLocation(), Sound.ITEM_TOTEM_USE, 0.8F, 1.2F);
                }
            }
            default -> {
            }
        }
        return false;
    }

    // ══════════ Лук ══════════

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        ItemStack bow = event.getBow();
        List<AbilityInstance> abilities = AbilityCodec.read(plugin, bow);
        if (abilities.isEmpty() || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        for (AbilityInstance instance : abilities) {
            if (instance.ability().slot() != ItemAbility.Slot.BOW
                && instance.ability().slot() != ItemAbility.Slot.ANY_ATTACK) {
                continue;
            }
            if (!roll(instance.chance())) {
                continue;
            }
            switch (instance.ability()) {
                case HOMING_ARROW -> {
                    tagArrow(arrow, "homing", instance.power());
                    startHoming(arrow, shooter, instance.power());
                }
                case EXPLOSIVE_ARROW -> tagArrow(arrow, "explosive", instance.power());
                case TELEPORT_ARROW -> tagArrow(arrow, "teleport", 1);
                case MULTI_SHOT -> spawnExtraArrows(shooter, arrow, instance.power());
                default -> {
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }
        String tag = arrow.getPersistentDataContainer()
            .get(new org.bukkit.NamespacedKey(plugin, ARROW_TAG), PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        String[] parts = tag.split(":");
        String kind = parts[0];
        int power = parts.length > 1 ? parseInt(parts[1], 2) : 2;
        Location at = event.getHitBlock() != null
            ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
            : arrow.getLocation();

        switch (kind) {
            case "explosive" -> {
                arrow.getWorld().createExplosion(at, power, false, false,
                    arrow.getShooter() instanceof Player p ? p : null);
                arrow.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
            }
            case "teleport" -> {
                if (arrow.getShooter() instanceof Player shooter && shooter.isOnline()) {
                    Location target = at.clone();
                    target.setYaw(shooter.getLocation().getYaw());
                    target.setPitch(shooter.getLocation().getPitch());
                    shooter.teleportAsync(target);
                    playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.0F);
                }
            }
            default -> {
            }
        }
    }

    private void spawnExtraArrows(Player shooter, AbstractArrow original, int extra) {
        Vector base = original.getVelocity();
        for (int i = 1; i <= Math.max(1, extra); i++) {
            double angle = Math.toRadians(i * 8.0D * (i % 2 == 0 ? 1 : -1));
            Vector rotated = base.clone().rotateAroundY(angle);
            Arrow clone = shooter.getWorld().spawn(original.getLocation(), Arrow.class);
            clone.setShooter(shooter);
            clone.setVelocity(rotated);
            clone.setCritical(original.isCritical());
            clone.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }

    /** Плавный доворот стрелы к ближайшей цели. */
    private void startHoming(AbstractArrow arrow, Player shooter, int radius) {
        final int searchRadius = Math.max(4, radius);
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks;

            @Override
            public void run() {
                ticks++;
                // Жёсткий лимит: задача не должна жить дольше полёта стрелы.
                if (ticks > HOMING_MAX_TICKS || arrow.isDead() || !arrow.isValid() || arrow.isOnGround()) {
                    holder[0].cancel();
                    return;
                }
                LivingEntity target = nearestTarget(arrow, shooter, searchRadius);
                if (target == null) {
                    return;
                }
                Vector desired = target.getLocation().add(0, 1, 0).toVector()
                    .subtract(arrow.getLocation().toVector()).normalize();
                Vector velocity = arrow.getVelocity();
                double speed = velocity.length();
                // Смешиваем текущее направление с нужным — получается дуга, а не «телепорт».
                Vector blended = velocity.normalize().multiply(0.72D).add(desired.multiply(0.28D));
                arrow.setVelocity(blended.normalize().multiply(speed));
                arrow.getWorld().spawnParticle(Particle.CRIT, arrow.getLocation(), 1);
            }
        }, 2L, 1L);
    }

    private LivingEntity nearestTarget(AbstractArrow arrow, Player shooter, int radius) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : arrow.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(shooter) || living.isDead()) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(arrow.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private void tagArrow(AbstractArrow arrow, String kind, int power) {
        arrow.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(plugin, ARROW_TAG), PersistentDataType.STRING, kind + ":" + power);
    }

    // ══════════ Пассивные ══════════

    private void startPassiveTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                List<AbilityInstance> abilities = AbilityCodec.read(plugin, player.getInventory().getItemInMainHand());
                for (AbilityInstance instance : abilities) {
                    if (instance.ability().slot() != ItemAbility.Slot.PASSIVE) {
                        continue;
                    }
                    switch (instance.ability()) {
                        case SWIFTNESS -> addEffect(player, PotionEffectType.SPEED, 80, instance.amplifier());
                        case NIGHT_HUNTER -> addEffect(player, PotionEffectType.NIGHT_VISION, 300, 0);
                        case STRENGTH_AURA -> addEffect(player, PotionEffectType.STRENGTH, 80, instance.amplifier());
                        default -> {
                        }
                    }
                }
            }
        }, 40L, 40L);
    }

    // ══════════ Утилиты ══════════

    private List<AbilityInstance> armorAbilities(Player player) {
        List<AbilityInstance> all = new java.util.ArrayList<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && !piece.getType().isAir()) {
                all.addAll(AbilityCodec.read(plugin, piece));
            }
        }
        all.addAll(AbilityCodec.read(plugin, player.getInventory().getItemInOffHand()));
        return all;
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private void addEffect(LivingEntity target, PotionEffectType type, int ticks, int amplifier) {
        target.addPotionEffect(new PotionEffect(type, Math.max(1, ticks), Math.max(0, amplifier), true, true));
    }

    private void playSound(Location at, Sound sound, float volume, float pitch) {
        if (at.getWorld() != null) {
            at.getWorld().playSound(at, sound, volume, pitch);
        }
    }

    private boolean roll(int chancePercent) {
        if (chancePercent >= 100) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) < Math.max(0, chancePercent);
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
