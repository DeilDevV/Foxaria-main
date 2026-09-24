package com.foxaria.regions;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RegionInvUtil {

    private RegionInvUtil() {
    }

    public static int countLogs(Player player, Material exactOrNull) {
        int n = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) {
                continue;
            }
            if (!Tag.LOGS.isTagged(it.getType())) {
                continue;
            }
            if (exactOrNull != null && it.getType() != exactOrNull) {
                continue;
            }
            n += it.getAmount();
        }
        return n;
    }

    public static Material findLogTypeWithCount(Player player, int atLeast) {
        for (Material m : Material.values()) {
            if (!Tag.LOGS.isTagged(m)) {
                continue;
            }
            if (countLogs(player, m) >= atLeast) {
                return m;
            }
        }
        return null;
    }

    public static int totalLogs(Player player) {
        return countLogs(player, null);
    }

    public static int takeLogs(Player player, int amount) {
        int left = amount;
        ItemStack[] cont = player.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack it = cont[i];
            if (it == null || !Tag.LOGS.isTagged(it.getType())) {
                continue;
            }
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) {
                cont[i] = null;
            }
            left -= take;
        }
        player.getInventory().setStorageContents(cont);
        return amount - left;
    }

    public static int takeLogsOf(Player player, Material logType, int amount) {
        int left = amount;
        ItemStack[] cont = player.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack it = cont[i];
            if (it == null || it.getType() != logType) {
                continue;
            }
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) {
                cont[i] = null;
            }
            left -= take;
        }
        player.getInventory().setStorageContents(cont);
        return amount - left;
    }

    public static int countIron(Player player) {
        int n = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it != null && it.getType() == Material.IRON_INGOT) {
                n += it.getAmount();
            }
        }
        return n;
    }

    public static int takeIron(Player player, int amount) {
        int left = amount;
        ItemStack[] cont = player.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack it = cont[i];
            if (it == null || it.getType() != Material.IRON_INGOT) {
                continue;
            }
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) {
                cont[i] = null;
            }
            left -= take;
        }
        player.getInventory().setStorageContents(cont);
        return amount - left;
    }
}
