package com.foxaria.shop.progression;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public final class QuestProgressListener implements Listener {

    private final ProgressionService progression;
    private final PlayerPlacedBlockTracker placedBlocks;

    public QuestProgressListener(ProgressionService progression, PlayerPlacedBlockTracker placedBlocks) {
        this.progression = progression;
        this.placedBlocks = placedBlocks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player killer)) {
            return;
        }
        EntityType t = event.getEntityType();
        progression.addProgress(killer, QuestType.ENTITY_KILL, t.name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            progression.addProgress(killer, QuestType.PLAYER_KILL, "PLAYER", 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (placedBlocks.pollPlayerPlacedAndClear(event.getBlock().getLocation())) {
            return;
        }
        progression.addProgress(p, QuestType.BLOCK_BREAK, event.getBlock().getType().name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        placedBlocks.markPlaced(event.getBlock().getLocation());
        progression.addProgress(p, QuestType.BLOCK_PLACE, event.getBlock().getType().name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item != null && !item.getType().isAir()) {
            progression.addProgress(event.getPlayer(), QuestType.CONSUME_ITEM, item.getType().name(), 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() != null) {
            progression.addProgress(event.getPlayer(), QuestType.FISH, "", 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!(event.getEnchanter() instanceof Player p)) {
            return;
        }
        int levels = event.getEnchantsToAdd().values().stream().mapToInt(Integer::intValue).sum();
        progression.addProgress(p, QuestType.ENCHANT_ITEM, "", Math.max(1, levels));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (result != null && !result.getType().isAir()) {
            int amt = result.getAmount();
            progression.addProgress(p, QuestType.CRAFT_ITEM, result.getType().name(), amt);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player p = event.getPlayer();
        progression.addProgress(p, QuestType.INTERACT_BLOCK, event.getClickedBlock().getType().name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        long dmg = Math.max(1L, Math.round(event.getFinalDamage()));
        progression.addProgress(p, QuestType.DAMAGE_TAKEN, "", dmg);
    }
}
