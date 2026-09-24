package com.foxaria.itemtemplates.listener;

import com.foxaria.itemtemplates.edit.StrikeEffectCategories;
import com.foxaria.itemtemplates.edit.TemplateOnHitCodec;
import com.foxaria.itemtemplates.edit.TemplateOnHitCodec.OnHitData;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.foxaria.itemtemplates.session.ItemTemplateWorkbenchSession;

public final class ItemTemplateOnHitListener implements Listener {

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final Map<UUID, Set<PotionEffectType>> passiveApplied = new ConcurrentHashMap<>();

    public ItemTemplateOnHitListener(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
        if (enabled) {
            // Короткий бафф-пульс: пока предмет в руке, эффект поддерживается; убрал — быстро снимется.
            Bukkit.getScheduler().runTaskTimer(plugin, this::tickPassiveBeneficial, 20L, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ItemTemplateWorkbenchSession.forget(event.getPlayer());
        clearPassive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        List<ItemStack> sources = strikeSources(attacker, event.getDamager());
        for (ItemStack source : sources) {
            applyFromSource(victim, attacker, source);
        }
    }

    private void applyFromSource(LivingEntity victim, Player attacker, ItemStack source) {
        for (OnHitData data : TemplateOnHitCodec.readAllVictim(plugin, source)) {
            if (!roll(data.chance())) {
                continue;
            }
            LivingEntity target = StrikeEffectCategories.misplacedBeneficialInVictimSlot(data.type()) ? attacker : victim;
            applyEffect(target, data);
        }
        for (OnHitData data : TemplateOnHitCodec.readAllSelf(plugin, source)) {
            if (!roll(data.chance())) {
                continue;
            }
            LivingEntity target = StrikeEffectCategories.misplacedHarmfulInSelfSlot(data.type()) ? victim : attacker;
            applyEffect(target, data);
        }
    }

    private static boolean roll(float chance01) {
        return ThreadLocalRandom.current().nextDouble() < chance01;
    }

    private static void applyEffect(LivingEntity target, OnHitData data) {
        target.addPotionEffect(new PotionEffect(
            data.type(),
            data.durationTicks(),
            data.amplifier(),
            false,
            true,
            true
        ));
    }

    /**
     * Главная и левая рука, вся броня, плюс копия предмета с трезубца при ударе трезубцем.
     */
    private List<ItemStack> strikeSources(Player attacker, Entity damager) {
        List<ItemStack> list = new ArrayList<>();
        addIdentity(list, attacker.getInventory().getItemInMainHand());
        addIdentity(list, attacker.getInventory().getItemInOffHand());
        EntityEquipment eq = attacker.getEquipment();
        if (eq != null) {
            addIdentity(list, eq.getHelmet());
            addIdentity(list, eq.getChestplate());
            addIdentity(list, eq.getLeggings());
            addIdentity(list, eq.getBoots());
        }
        if (damager instanceof Trident trident) {
            ItemStack embedded = trident.getItem();
            if (embedded != null && !embedded.getType().isAir()) {
                addIdentity(list, embedded);
            }
        }
        if (damager instanceof AbstractArrow) {
            ItemStack main = attacker.getInventory().getItemInMainHand();
            Material mt = main.getType();
            if (mt == Material.BOW || mt == Material.CROSSBOW) {
                addIdentity(list, main);
            }
        }
        return list;
    }

    private static void addIdentity(List<ItemStack> list, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        for (ItemStack x : list) {
            if (x == stack) {
                return;
            }
        }
        list.add(stack);
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof AbstractArrow arrow) {
            ProjectileSource src = arrow.getShooter();
            if (src instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    private void tickPassiveBeneficial() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            Set<PotionEffectType> now = collectPassiveFromEquipment(player);
            Set<PotionEffectType> prev = passiveApplied.getOrDefault(player.getUniqueId(), Set.of());
            for (PotionEffectType type : prev) {
                if (!now.contains(type)) {
                    player.removePotionEffect(type);
                }
            }
            if (now.isEmpty()) {
                passiveApplied.remove(player.getUniqueId());
                continue;
            }
            passiveApplied.put(player.getUniqueId(), now);
            for (PotionEffectType type : now) {
                int amp = maxPassiveAmplifier(player, type);
                player.addPotionEffect(new PotionEffect(type, 50, amp, false, true, true));
            }
        }
    }

    /** Обе руки и все слоты брони (нагрудник = нагрудник или элитра). */
    private Set<PotionEffectType> collectPassiveFromEquipment(Player player) {
        Set<PotionEffectType> out = new HashSet<>();
        collectPassiveFromItem(player.getInventory().getItemInMainHand(), out);
        collectPassiveFromItem(player.getInventory().getItemInOffHand(), out);
        EntityEquipment eq = player.getEquipment();
        if (eq != null) {
            collectPassiveFromItem(eq.getHelmet(), out);
            collectPassiveFromItem(eq.getChestplate(), out);
            collectPassiveFromItem(eq.getLeggings(), out);
            collectPassiveFromItem(eq.getBoots(), out);
        }
        return out;
    }

    private void collectPassiveFromItem(ItemStack stack, Set<PotionEffectType> out) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        if (!TemplateOnHitCodec.passiveBeneficialEnabled(plugin, stack)) {
            return;
        }
        for (OnHitData data : TemplateOnHitCodec.readAllVictim(plugin, stack)) {
            if (StrikeEffectCategories.isBeneficial(data.type())) {
                out.add(data.type());
            }
        }
        for (OnHitData data : TemplateOnHitCodec.readAllSelf(plugin, stack)) {
            if (StrikeEffectCategories.isBeneficial(data.type())) {
                out.add(data.type());
            }
        }
    }

    private int maxPassiveAmplifier(Player player, PotionEffectType type) {
        int max = 0;
        max = Math.max(max, maxFromItem(player.getInventory().getItemInMainHand(), type));
        max = Math.max(max, maxFromItem(player.getInventory().getItemInOffHand(), type));
        EntityEquipment eq = player.getEquipment();
        if (eq != null) {
            max = Math.max(max, maxFromItem(eq.getHelmet(), type));
            max = Math.max(max, maxFromItem(eq.getChestplate(), type));
            max = Math.max(max, maxFromItem(eq.getLeggings(), type));
            max = Math.max(max, maxFromItem(eq.getBoots(), type));
        }
        return max;
    }

    private int maxFromItem(ItemStack stack, PotionEffectType type) {
        if (stack == null || stack.getType().isAir()) {
            return 0;
        }
        if (!TemplateOnHitCodec.passiveBeneficialEnabled(plugin, stack)) {
            return 0;
        }
        int max = 0;
        for (OnHitData data : TemplateOnHitCodec.readAllVictim(plugin, stack)) {
            if (data.type().equals(type) && StrikeEffectCategories.isBeneficial(type)) {
                max = Math.max(max, data.amplifier());
            }
        }
        for (OnHitData data : TemplateOnHitCodec.readAllSelf(plugin, stack)) {
            if (data.type().equals(type) && StrikeEffectCategories.isBeneficial(type)) {
                max = Math.max(max, data.amplifier());
            }
        }
        return max;
    }

    private void clearPassive(Player player) {
        Set<PotionEffectType> prev = passiveApplied.remove(player.getUniqueId());
        if (prev == null || prev.isEmpty()) {
            return;
        }
        for (PotionEffectType type : prev) {
            player.removePotionEffect(type);
        }
    }
}
