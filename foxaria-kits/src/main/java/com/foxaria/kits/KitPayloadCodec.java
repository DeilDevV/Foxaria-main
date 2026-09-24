package com.foxaria.kits;

import com.foxaria.api.model.KitContents;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class KitPayloadCodec {

    private static final int LINES = KitContents.STORAGE_SLOTS + KitContents.ARMOR_SLOTS + 1;

    private KitPayloadCodec() {
    }

    static String encode(KitContents contents) {
        List<String> lines = new ArrayList<>(LINES);
        for (int i = 0; i < KitContents.STORAGE_SLOTS; i++) {
            lines.add(encodeOne(contents.storageSlot(i)));
        }
        for (int i = 0; i < KitContents.ARMOR_SLOTS; i++) {
            lines.add(encodeOne(contents.armorSlot(i)));
        }
        lines.add(encodeOne(contents.offhand()));
        return String.join("\n", lines);
    }

    static KitContents decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return KitContents.empty();
        }
        String[] parts = payload.split("\n", -1);
        ItemStack[] storage = new ItemStack[KitContents.STORAGE_SLOTS];
        ItemStack[] armor = new ItemStack[KitContents.ARMOR_SLOTS];
        ItemStack off = null;
        for (int i = 0; i < KitContents.STORAGE_SLOTS && i < parts.length; i++) {
            storage[i] = decodeOne(parts[i]);
        }
        int base = KitContents.STORAGE_SLOTS;
        for (int j = 0; j < KitContents.ARMOR_SLOTS && base + j < parts.length; j++) {
            armor[j] = decodeOne(parts[base + j]);
        }
        int offIdx = base + KitContents.ARMOR_SLOTS;
        if (offIdx < parts.length) {
            off = decodeOne(parts[offIdx]);
        }
        return new KitContents(storage, armor, off);
    }

    private static String encodeOne(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "-";
        }
        return ItemStackSerializer.serialize(stack);
    }

    private static ItemStack decodeOne(String line) {
        if (line == null || line.isBlank() || "-".equals(line)) {
            return null;
        }
        return ItemStackSerializer.deserialize(line.trim());
    }
}
