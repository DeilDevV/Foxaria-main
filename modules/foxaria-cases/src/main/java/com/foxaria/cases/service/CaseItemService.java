package com.foxaria.cases.service;

import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.text.FoxariaText;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

public final class CaseItemService {

    private final NamespacedKey blockItemKey;
    private final NamespacedKey placedBlockKey;

    public CaseItemService(JavaPlugin plugin) {
        this.blockItemKey = new NamespacedKey(plugin, "case-block-item");
        this.placedBlockKey = new NamespacedKey(plugin, "case-placed");
    }

    public ItemStack createBlockItem(String caseId, com.foxaria.cases.model.CaseBlockItemConfig config, int amount) {
        ItemStack stack = MenuItems.item(
            config.material(),
            config.name(),
            config.lore().toArray(new String[0])
        );
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(blockItemKey, PersistentDataType.STRING, caseId.toLowerCase(Locale.ROOT));
        stack.setItemMeta(meta);
        return stack;
    }

    public String readBlockItemCaseId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(blockItemKey, PersistentDataType.STRING);
    }

    public void markPlacedBlock(Block block, String caseId) {
        TileState state = tileState(block);
        if (state == null) {
            return;
        }
        state.getPersistentDataContainer().set(placedBlockKey, PersistentDataType.STRING, caseId.toLowerCase(Locale.ROOT));
        state.update(true, false);
    }

    public String readPlacedBlockCaseId(Block block) {
        TileState state = tileState(block);
        if (state == null) {
            return null;
        }
        return state.getPersistentDataContainer().get(placedBlockKey, PersistentDataType.STRING);
    }

    public void clearPlacedBlock(Block block) {
        TileState state = tileState(block);
        if (state == null) {
            return;
        }
        state.getPersistentDataContainer().remove(placedBlockKey);
        state.update(true, false);
    }

    private TileState tileState(Block block) {
        if (block.getState() instanceof TileState state) {
            return state;
        }
        return null;
    }

    public void deliverItem(org.bukkit.entity.Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover)
            );
        }
        player.updateInventory();
    }

    public ItemStack rewardIcon(com.foxaria.cases.model.CaseReward reward) {
        ItemStack icon = new ItemStack(reward.icon());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(FoxariaText.legacy(reward.displayName()));
        if (!reward.menuLore().isEmpty()) {
            meta.lore(FoxariaText.legacyLore(reward.menuLore().toArray(new String[0])));
        }
        icon.setItemMeta(meta);
        return icon;
    }
}
