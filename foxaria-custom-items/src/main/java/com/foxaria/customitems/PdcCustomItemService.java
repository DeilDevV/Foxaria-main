package com.foxaria.customitems;

import com.foxaria.api.service.CustomItemService;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class PdcCustomItemService implements CustomItemService {

    private final NamespacedKey idKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey claimKey;
    private final NamespacedKey rarityKey;

    public PdcCustomItemService(JavaPlugin plugin) {
        this.idKey = new NamespacedKey(plugin, "custom_item_id");
        this.typeKey = new NamespacedKey(plugin, "custom_item_type");
        this.claimKey = new NamespacedKey(plugin, "custom_item_claim");
        this.rarityKey = new NamespacedKey(plugin, "custom_item_rarity");
    }

    @Override
    public ItemStack stampIdentity(ItemStack itemStack, String type) {
        ItemStack clone = itemStack.clone();
        ItemMeta meta = clone.getItemMeta();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        clone.setItemMeta(meta);
        return clone;
    }

    @Override
    public boolean hasIdentity(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    @Override
    public UUID readIdentity(ItemStack itemStack) {
        if (!hasIdentity(itemStack)) {
            return null;
        }
        return UUID.fromString(itemStack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    public String readType(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public ItemStack stampClaimKey(ItemStack itemStack, String claimKeyValue) {
        ItemStack clone = itemStack.clone();
        ItemMeta meta = clone.getItemMeta();
        meta.getPersistentDataContainer().set(claimKey, PersistentDataType.STRING, claimKeyValue);
        clone.setItemMeta(meta);
        return clone;
    }

    public String readClaimKey(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(claimKey, PersistentDataType.STRING);
    }

    public ItemStack stampRarity(ItemStack itemStack, String rarity) {
        ItemStack clone = itemStack.clone();
        ItemMeta meta = clone.getItemMeta();
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity);
        clone.setItemMeta(meta);
        return clone;
    }

    public String readRarity(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
    }
}
