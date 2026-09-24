package com.foxaria.api.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Общие ключи PDC для предметов (шаблоны, крафт).
 */
public final class FoxariaItemPdc {

    private FoxariaItemPdc() {
    }

    /** Предмет-«ядро» крафта: не участвует в ванильных рецептах верстака (кроме наших). */
    public static NamespacedKey craftingCoreIngredient(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "crafting_core_ingredient");
    }

    public static boolean isCraftingCoreIngredient(JavaPlugin plugin, org.bukkit.inventory.ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Byte v = stack.getItemMeta().getPersistentDataContainer().get(craftingCoreIngredient(plugin), org.bukkit.persistence.PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }
}
