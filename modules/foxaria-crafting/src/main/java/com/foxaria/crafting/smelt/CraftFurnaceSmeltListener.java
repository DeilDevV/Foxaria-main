package com.foxaria.crafting.smelt;

import com.foxaria.api.service.SmeltBonusHook;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Плавка в ванильных печах: запоминаем последнего открывшего игрока, после переплава — бонус.
 */
public final class CraftFurnaceSmeltListener implements Listener {

    private final JavaPlugin plugin;
    private final SmeltBonusHook hook;
    private final NamespacedKey lastSmelterKey;

    public CraftFurnaceSmeltListener(JavaPlugin plugin, SmeltBonusHook hook) {
        this.plugin = plugin;
        this.hook = hook;
        this.lastSmelterKey = new NamespacedKey(plugin, "last_smelter_uuid");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        org.bukkit.inventory.Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof Furnace furnace) {
            try {
                furnace.getPersistentDataContainer().set(
                    lastSmelterKey,
                    PersistentDataType.STRING,
                    player.getUniqueId().toString()
                );
                furnace.update();
            } catch (Exception ignored) {
            }
            return;
        }
        if (inv.getHolder() instanceof BlastFurnace blast) {
            try {
                blast.getPersistentDataContainer().set(
                    lastSmelterKey,
                    PersistentDataType.STRING,
                    player.getUniqueId().toString()
                );
                blast.update();
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        ItemStack source = event.getSource();
        if (source == null || source.getType().isAir()) {
            return;
        }
        Material inputMat = source.getType();
        Location loc = event.getBlock().getLocation();
        boolean blast = event.getBlock().getType() == Material.BLAST_FURNACE;
        UUID smelter = readLastSmelter(event.getBlock().getState());
        Consumer<ItemStack> deliver = bonus -> deliverToFurnaceOutput(event.getBlock().getState(), bonus, loc);
        hook.afterOneSmelted(smelter, loc, inputMat, blast, false, deliver);
    }

    private UUID readLastSmelter(BlockState state) {
        try {
            if (state instanceof Furnace furnace) {
                return readUuid(furnace.getPersistentDataContainer());
            }
            if (state instanceof BlastFurnace blast) {
                return readUuid(blast.getPersistentDataContainer());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private UUID readUuid(org.bukkit.persistence.PersistentDataContainer pdc) {
        String s = pdc.get(lastSmelterKey, PersistentDataType.STRING);
        if (s == null || s.isBlank()) {
            return null;
        }
        return UUID.fromString(s);
    }

    private void deliverToFurnaceOutput(BlockState state, ItemStack bonus, Location furnaceLoc) {
        if (state instanceof Furnace furnace) {
            deliverToFurnaceLikeInventory(furnace.getInventory(), furnace, bonus, furnaceLoc);
            return;
        }
        if (state instanceof BlastFurnace blast) {
            deliverToFurnaceLikeInventory(blast.getInventory(), blast, bonus, furnaceLoc);
            return;
        }
        if (furnaceLoc.getWorld() != null) {
            furnaceLoc.getWorld().dropItemNaturally(furnaceLoc.clone().add(0.5, 0.5, 0.5), bonus);
        }
    }

    private void deliverToFurnaceLikeInventory(
        org.bukkit.inventory.FurnaceInventory inv,
        BlockState tile,
        ItemStack bonus,
        Location furnaceLoc
    ) {
        ItemStack out = inv.getResult();
        if (out == null || out.getType().isAir()) {
            inv.setResult(bonus.clone());
            tile.update();
            return;
        }
        if (out.isSimilar(bonus) && out.getAmount() + bonus.getAmount() <= out.getMaxStackSize()) {
            out.setAmount(out.getAmount() + bonus.getAmount());
            inv.setResult(out);
            tile.update();
            return;
        }
        if (tryInsertIntoHopperBelow(furnaceLoc, bonus)) {
            tile.update();
            return;
        }
        if (furnaceLoc.getWorld() != null) {
            furnaceLoc.getWorld().dropItemNaturally(furnaceLoc.clone().add(0.5, 0.5, 0.5), bonus);
        }
        tile.update();
    }

    private static boolean tryInsertIntoHopperBelow(Location furnaceLoc, ItemStack bonus) {
        if (furnaceLoc.getWorld() == null) {
            return false;
        }
        Block under = furnaceLoc.getWorld().getBlockAt(
            furnaceLoc.getBlockX(), furnaceLoc.getBlockY() - 1, furnaceLoc.getBlockZ());
        if (under.getType() != Material.HOPPER) {
            return false;
        }
        BlockState st = under.getState();
        if (!(st instanceof Hopper hopper)) {
            return false;
        }
        ItemStack add = bonus.clone();
        Map<Integer, ItemStack> left = hopper.getInventory().addItem(add);
        return left.isEmpty();
    }
}
