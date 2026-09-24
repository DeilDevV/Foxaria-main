package com.foxaria.itemtemplates.edit;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Несколько эффектов при ударе на цель и на себя. Старый формат (один эффект) подхватывается при чтении.
 */
public final class TemplateOnHitCodec {

    public static final int MAX_PER_SIDE = 12;

    /** Новый список: сегменты через ; поля через , */
    public static final String K_VICTIM_LIST = "tpl-onhit-vlist";
    public static final String K_SELF_LIST = "tpl-onhit-slist";
    public static final String K_PASSIVE_BENEFICIAL = "tpl-onhit-passive-beneficial";

    public static final String K_TYPE = "tpl-onhit-type";
    public static final String K_DURATION = "tpl-onhit-duration";
    public static final String K_AMPLIFIER = "tpl-onhit-amplifier";
    public static final String K_CHANCE = "tpl-onhit-chance";

    public static final String K_SELF_TYPE = "tpl-onhit-self-type";
    public static final String K_SELF_DURATION = "tpl-onhit-self-duration";
    public static final String K_SELF_AMPLIFIER = "tpl-onhit-self-amplifier";
    public static final String K_SELF_CHANCE = "tpl-onhit-self-chance";

    private TemplateOnHitCodec() {
    }

    private static NamespacedKey nk(JavaPlugin plugin, String key) {
        return new NamespacedKey(plugin, key);
    }

    /** @deprecated используйте {@link #appendVictim} */
    @Deprecated
    public static void set(JavaPlugin plugin, ItemStack stack, PotionEffectType type, int durationTicks, int amplifier, float chance01) {
        appendVictim(plugin, stack, type, durationTicks, amplifier, chance01);
    }

    public static void appendVictim(JavaPlugin plugin, ItemStack stack, PotionEffectType type, int durationTicks, int amplifier, float chance01) {
        if (HandItemAttributeUtil.isAirOrEmpty(stack) || type == null) {
            return;
        }
        List<OnHitData> all = new ArrayList<>(readAllVictim(plugin, stack));
        if (all.size() >= MAX_PER_SIDE) {
            return;
        }
        all.add(new OnHitData(type, Math.max(1, durationTicks), Math.max(0, amplifier), clamp01(chance01)));
        wipeVictimStorage(plugin, stack);
        writeVictimBlob(plugin, stack, all);
    }

    public static void appendSelf(JavaPlugin plugin, ItemStack stack, PotionEffectType type, int durationTicks, int amplifier, float chance01) {
        if (HandItemAttributeUtil.isAirOrEmpty(stack) || type == null) {
            return;
        }
        List<OnHitData> all = new ArrayList<>(readAllSelf(plugin, stack));
        if (all.size() >= MAX_PER_SIDE) {
            return;
        }
        all.add(new OnHitData(type, Math.max(1, durationTicks), Math.max(0, amplifier), clamp01(chance01)));
        wipeSelfStorage(plugin, stack);
        writeSelfBlob(plugin, stack, all);
    }

    public static void clearVictim(JavaPlugin plugin, ItemStack stack) {
        wipeVictimStorage(plugin, stack);
        stack.setItemMeta(stack.getItemMeta());
    }

    public static void clearSelf(JavaPlugin plugin, ItemStack stack) {
        wipeSelfStorage(plugin, stack);
        stack.setItemMeta(stack.getItemMeta());
    }

    public static void clear(JavaPlugin plugin, ItemStack stack) {
        clearVictim(plugin, stack);
    }

    public static void clearAllStrike(JavaPlugin plugin, ItemStack stack) {
        clearVictim(plugin, stack);
        clearSelf(plugin, stack);
    }

    public static boolean passiveBeneficialEnabled(JavaPlugin plugin, ItemStack stack) {
        if (HandItemAttributeUtil.isAirOrEmpty(stack)) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte v = meta.getPersistentDataContainer().get(nk(plugin, K_PASSIVE_BENEFICIAL), PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    public static void setPassiveBeneficialEnabled(JavaPlugin plugin, ItemStack stack, boolean enabled) {
        if (HandItemAttributeUtil.isAirOrEmpty(stack)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        if (enabled) {
            pdc.set(nk(plugin, K_PASSIVE_BENEFICIAL), PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(nk(plugin, K_PASSIVE_BENEFICIAL));
        }
        stack.setItemMeta(meta);
    }

    public static boolean has(JavaPlugin plugin, ItemStack stack) {
        return !readAllVictim(plugin, stack).isEmpty() || !readAllSelf(plugin, stack).isEmpty();
    }

    public record OnHitData(PotionEffectType type, int durationTicks, int amplifier, float chance) {}

    public static List<OnHitData> readAllVictim(JavaPlugin plugin, ItemStack stack) {
        if (HandItemAttributeUtil.isAirOrEmpty(stack)) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        var pdc = meta.getPersistentDataContainer();
        String blob = pdc.get(nk(plugin, K_VICTIM_LIST), PersistentDataType.STRING);
        if (blob != null && !blob.isEmpty()) {
            return parseBlob(blob);
        }
        return readLegacySingleVictim(plugin, stack).map(List::of).orElseGet(List::of);
    }

    public static List<OnHitData> readAllSelf(JavaPlugin plugin, ItemStack stack) {
        if (HandItemAttributeUtil.isAirOrEmpty(stack)) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        var pdc = meta.getPersistentDataContainer();
        String blob = pdc.get(nk(plugin, K_SELF_LIST), PersistentDataType.STRING);
        if (blob != null && !blob.isEmpty()) {
            return parseBlob(blob);
        }
        return readLegacySingleSelf(plugin, stack).map(List::of).orElseGet(List::of);
    }

    public static Optional<OnHitData> read(JavaPlugin plugin, ItemStack stack) {
        List<OnHitData> all = readAllVictim(plugin, stack);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst());
    }

    public static Optional<OnHitData> readSelf(JavaPlugin plugin, ItemStack stack) {
        List<OnHitData> all = readAllSelf(plugin, stack);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst());
    }

    /** @deprecated используйте {@link #appendSelf} */
    @Deprecated
    public static void setSelf(JavaPlugin plugin, ItemStack stack, PotionEffectType type, int durationTicks, int amplifier, float chance01) {
        appendSelf(plugin, stack, type, durationTicks, amplifier, chance01);
    }

    private static Optional<OnHitData> readLegacySingleVictim(JavaPlugin plugin, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        var pdc = meta.getPersistentDataContainer();
        String typeKey = pdc.get(nk(plugin, K_TYPE), PersistentDataType.STRING);
        if (typeKey == null || typeKey.isEmpty()) {
            return Optional.empty();
        }
        return readDataFromPdc(typeKey, pdc, plugin, K_DURATION, K_AMPLIFIER, K_CHANCE);
    }

    private static Optional<OnHitData> readLegacySingleSelf(JavaPlugin plugin, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        var pdc = meta.getPersistentDataContainer();
        String typeKey = pdc.get(nk(plugin, K_SELF_TYPE), PersistentDataType.STRING);
        if (typeKey == null || typeKey.isEmpty()) {
            return Optional.empty();
        }
        return readDataFromPdc(typeKey, pdc, plugin, K_SELF_DURATION, K_SELF_AMPLIFIER, K_SELF_CHANCE);
    }

    private static Optional<OnHitData> readDataFromPdc(
        String typeKey,
        org.bukkit.persistence.PersistentDataContainer pdc,
        JavaPlugin plugin,
        String kDur,
        String kAmp,
        String kCh
    ) {
        Integer dur = pdc.get(nk(plugin, kDur), PersistentDataType.INTEGER);
        Integer amp = pdc.get(nk(plugin, kAmp), PersistentDataType.INTEGER);
        Float ch = pdc.get(nk(plugin, kCh), PersistentDataType.FLOAT);
        if (dur == null || amp == null || ch == null) {
            return Optional.empty();
        }
        PotionEffectType pet = Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.fromString(typeKey));
        if (pet == null) {
            return Optional.empty();
        }
        return Optional.of(new OnHitData(pet, dur, amp, clamp01(ch)));
    }

    private static List<OnHitData> parseBlob(String blob) {
        List<OnHitData> out = new ArrayList<>();
        for (String seg : blob.split(";")) {
            if (seg.isEmpty()) {
                continue;
            }
            String[] p = seg.split(",", 4);
            if (p.length < 4) {
                continue;
            }
            PotionEffectType t = Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.fromString(p[0]));
            if (t == null) {
                continue;
            }
            try {
                int dur = Integer.parseInt(p[1]);
                int amp = Integer.parseInt(p[2]);
                float ch = Float.parseFloat(p[3]);
                out.add(new OnHitData(t, dur, amp, clamp01(ch)));
            } catch (NumberFormatException ignored) {
                // skip bad segment
            }
        }
        return out;
    }

    private static String toBlob(List<OnHitData> list) {
        StringBuilder sb = new StringBuilder();
        for (OnHitData d : list) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(d.type().getKey().toString()).append(',')
                .append(d.durationTicks()).append(',')
                .append(d.amplifier()).append(',')
                .append(d.chance());
        }
        return sb.toString();
    }

    private static void writeVictimBlob(JavaPlugin plugin, ItemStack stack, List<OnHitData> data) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        if (data.isEmpty()) {
            pdc.remove(nk(plugin, K_VICTIM_LIST));
        } else {
            pdc.set(nk(plugin, K_VICTIM_LIST), PersistentDataType.STRING, toBlob(data));
        }
        stack.setItemMeta(meta);
    }

    private static void writeSelfBlob(JavaPlugin plugin, ItemStack stack, List<OnHitData> data) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        if (data.isEmpty()) {
            pdc.remove(nk(plugin, K_SELF_LIST));
        } else {
            pdc.set(nk(plugin, K_SELF_LIST), PersistentDataType.STRING, toBlob(data));
        }
        stack.setItemMeta(meta);
    }

    private static void wipeVictimStorage(JavaPlugin plugin, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        pdc.remove(nk(plugin, K_VICTIM_LIST));
        pdc.remove(nk(plugin, K_TYPE));
        pdc.remove(nk(plugin, K_DURATION));
        pdc.remove(nk(plugin, K_AMPLIFIER));
        pdc.remove(nk(plugin, K_CHANCE));
        stack.setItemMeta(meta);
    }

    private static void wipeSelfStorage(JavaPlugin plugin, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        pdc.remove(nk(plugin, K_SELF_LIST));
        pdc.remove(nk(plugin, K_SELF_TYPE));
        pdc.remove(nk(plugin, K_SELF_DURATION));
        pdc.remove(nk(plugin, K_SELF_AMPLIFIER));
        pdc.remove(nk(plugin, K_SELF_CHANCE));
        stack.setItemMeta(meta);
    }

    private static float clamp01(float f) {
        if (f < 0f) {
            return 0f;
        }
        if (f > 1f) {
            return 1f;
        }
        return f;
    }

}
