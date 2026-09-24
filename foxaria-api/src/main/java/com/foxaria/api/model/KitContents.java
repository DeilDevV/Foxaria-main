package com.foxaria.api.model;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимок инвентаря набора: 36 слотов хранилища (как {@link PlayerInventory#getStorageContents()}),
 * 4 слота брони (сапоги, поножи, нагрудник, шлем), оффхенд.
 */
public final class KitContents {

    public static final int STORAGE_SLOTS = 36;
    public static final int ARMOR_SLOTS = 4;

    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack offhand;

    public KitContents(ItemStack[] storage36, ItemStack[] armor4, ItemStack offhand) {
        this.storage = storage36 == null ? new ItemStack[STORAGE_SLOTS] : copyFixed(storage36, STORAGE_SLOTS);
        this.armor = armor4 == null ? new ItemStack[ARMOR_SLOTS] : copyFixed(armor4, ARMOR_SLOTS);
        this.offhand = cloneOrNull(offhand);
    }

    public static KitContents empty() {
        return new KitContents(new ItemStack[STORAGE_SLOTS], new ItemStack[ARMOR_SLOTS], null);
    }

    public static KitContents fromPlayer(Player player) {
        PlayerInventory inv = player.getInventory();
        return new KitContents(inv.getStorageContents(), inv.getArmorContents(), inv.getItemInOffHand());
    }

    /**
     * Старый формат YAML: все предметы по порядку заполняют слоты хранилища, броня пустая.
     */
    public static KitContents fromFlatItems(List<ItemStack> items) {
        ItemStack[] st = new ItemStack[STORAGE_SLOTS];
        if (items != null) {
            for (int i = 0; i < STORAGE_SLOTS && i < items.size(); i++) {
                st[i] = cloneOrNull(items.get(i));
            }
        }
        return new KitContents(st, new ItemStack[ARMOR_SLOTS], null);
    }

    public ItemStack storageSlot(int index) {
        return cloneOrNull(storage[index]);
    }

    public ItemStack armorSlot(int index) {
        return cloneOrNull(armor[index]);
    }

    public ItemStack offhand() {
        return cloneOrNull(offhand);
    }

    public int nonEmptyCount() {
        int n = 0;
        for (ItemStack s : storage) {
            if (s != null && !s.getType().isAir()) {
                n++;
            }
        }
        for (ItemStack a : armor) {
            if (a != null && !a.getType().isAir()) {
                n++;
            }
        }
        if (offhand != null && !offhand.getType().isAir()) {
            n++;
        }
        return n;
    }

    /** Первый непустой предмет — для иконки в списке наборов. */
    public ItemStack firstIcon() {
        for (ItemStack s : storage) {
            if (s != null && !s.getType().isAir()) {
                return s.clone();
            }
        }
        for (ItemStack a : armor) {
            if (a != null && !a.getType().isAir()) {
                return a.clone();
            }
        }
        if (offhand != null && !offhand.getType().isAir()) {
            return offhand.clone();
        }
        return null;
    }

    /** Все стеки для выдачи игроку (клоны). */
    public List<ItemStack> stacksForGive() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : storage) {
            if (s != null && !s.getType().isAir()) {
                out.add(s.clone());
            }
        }
        for (ItemStack a : armor) {
            if (a != null && !a.getType().isAir()) {
                out.add(a.clone());
            }
        }
        if (offhand != null && !offhand.getType().isAir()) {
            out.add(offhand.clone());
        }
        return out;
    }

    private static ItemStack[] copyFixed(ItemStack[] src, int len) {
        ItemStack[] out = new ItemStack[len];
        for (int i = 0; i < len; i++) {
            out[i] = i < src.length ? cloneOrNull(src[i]) : null;
        }
        return out;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }
}
