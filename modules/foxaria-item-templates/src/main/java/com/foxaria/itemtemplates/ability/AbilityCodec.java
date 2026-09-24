package com.foxaria.itemtemplates.ability;

import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Хранение способностей, редкости и пользовательского описания на предмете.
 *
 * Почему описание тоже здесь: раньше авто-строки помечались в тексте маркерами
 * ⟨Foxaria⟩…⟨/Foxaria⟩ и вырезались при каждом обновлении. Если закрывающий
 * маркер терялся (обрезка лора, ручная правка, старый предмет) или блоков
 * оказывалось несколько, вырезался только один — остальные копились, и строки
 * дублировались с каждым действием.
 *
 * Здесь пользовательские строки хранятся отдельно в PDC, а видимое описание
 * всегда собирается заново целиком. Дубли невозможны в принципе.
 */
public final class AbilityCodec {

    private static final String K_ABILITIES = "fox-abilities";
    private static final String K_RARITY = "fox-rarity";
    private static final String K_USER_LORE = "fox-user-lore";
    private static final String LORE_SEPARATOR = "\u0001";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private AbilityCodec() {
    }

    private static NamespacedKey key(JavaPlugin plugin, String name) {
        return new NamespacedKey(plugin, name);
    }

    // ── Способности ──────────────────────────────────────────────────

    public static List<AbilityInstance> read(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        String raw = meta.getPersistentDataContainer().get(key(plugin, K_ABILITIES), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<AbilityInstance> out = new ArrayList<>();
        for (String chunk : raw.split(";")) {
            if (chunk.isBlank()) {
                continue;
            }
            String[] parts = chunk.split(",");
            if (parts.length < 4) {
                continue;
            }
            ItemAbility.byId(parts[0]).ifPresent(ability -> out.add(new AbilityInstance(
                ability,
                parseInt(parts[1], ability.defaultChance()),
                parseInt(parts[2], ability.defaultPower()),
                parseInt(parts[3], ability.defaultSeconds())
            )));
        }
        return out;
    }

    public static void write(JavaPlugin plugin, ItemStack stack, List<AbilityInstance> abilities) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        // Дедупликация: одна способность не может стоять на предмете дважды.
        Map<ItemAbility, AbilityInstance> unique = new LinkedHashMap<>();
        for (AbilityInstance instance : abilities) {
            unique.put(instance.ability(), instance);
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (unique.isEmpty()) {
            pdc.remove(key(plugin, K_ABILITIES));
        } else {
            StringBuilder sb = new StringBuilder();
            for (AbilityInstance instance : unique.values()) {
                if (sb.length() > 0) {
                    sb.append(';');
                }
                sb.append(instance.ability().id()).append(',')
                    .append(instance.chance()).append(',')
                    .append(instance.power()).append(',')
                    .append(instance.seconds());
            }
            pdc.set(key(plugin, K_ABILITIES), PersistentDataType.STRING, sb.toString());
        }
        stack.setItemMeta(meta);
    }

    public static void toggle(JavaPlugin plugin, ItemStack stack, ItemAbility ability) {
        List<AbilityInstance> current = new ArrayList<>(read(plugin, stack));
        boolean removed = current.removeIf(instance -> instance.ability() == ability);
        if (!removed) {
            current.add(AbilityInstance.defaults(ability));
        }
        write(plugin, stack, current);
    }

    public static void update(JavaPlugin plugin, ItemStack stack, AbilityInstance updated) {
        List<AbilityInstance> current = new ArrayList<>(read(plugin, stack));
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).ability() == updated.ability()) {
                current.set(i, updated);
                write(plugin, stack, current);
                return;
            }
        }
        current.add(updated);
        write(plugin, stack, current);
    }

    public static void clearAbilities(JavaPlugin plugin, ItemStack stack) {
        write(plugin, stack, List.of());
    }

    public static boolean has(JavaPlugin plugin, ItemStack stack, ItemAbility ability) {
        return read(plugin, stack).stream().anyMatch(instance -> instance.ability() == ability);
    }

    // ── Редкость ─────────────────────────────────────────────────────

    public static ItemRarity rarity(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return ItemRarity.COMMON;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return ItemRarity.COMMON;
        }
        return ItemRarity.byId(meta.getPersistentDataContainer()
            .get(key(plugin, K_RARITY), PersistentDataType.STRING));
    }

    public static void setRarity(JavaPlugin plugin, ItemStack stack, ItemRarity rarity) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key(plugin, K_RARITY), PersistentDataType.STRING, rarity.id());
        stack.setItemMeta(meta);
    }

    // ── Пользовательское описание ────────────────────────────────────

    public static List<String> userLore(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return List.of();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return List.of();
        }
        String raw = meta.getPersistentDataContainer().get(key(plugin, K_USER_LORE), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return List.of(raw.split(LORE_SEPARATOR, -1));
    }

    public static void setUserLore(JavaPlugin plugin, ItemStack stack, List<String> lines) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        if (lines.isEmpty()) {
            meta.getPersistentDataContainer().remove(key(plugin, K_USER_LORE));
        } else {
            meta.getPersistentDataContainer().set(key(plugin, K_USER_LORE), PersistentDataType.STRING,
                String.join(LORE_SEPARATOR, lines));
        }
        stack.setItemMeta(meta);
    }

    public static void addUserLoreLine(JavaPlugin plugin, ItemStack stack, String line) {
        List<String> lines = new ArrayList<>(userLore(plugin, stack));
        lines.add(line);
        setUserLore(plugin, stack, lines);
    }

    public static void removeLastUserLoreLine(JavaPlugin plugin, ItemStack stack) {
        List<String> lines = new ArrayList<>(userLore(plugin, stack));
        if (!lines.isEmpty()) {
            lines.remove(lines.size() - 1);
            setUserLore(plugin, stack, lines);
        }
    }

    /**
     * Переносит ранее набитое описание в PDC — для предметов, созданных
     * старым редактором. Вызывается один раз при открытии меню.
     */
    public static void migrateLegacyLore(JavaPlugin plugin, ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.getPersistentDataContainer().has(key(plugin, K_USER_LORE), PersistentDataType.STRING)) {
            return;
        }
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return;
        }
        List<String> keep = new ArrayList<>();
        boolean insideAutoBlock = false;
        for (Component line : lore) {
            String legacy = LEGACY.serialize(line);
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(line);
            if (plain.contains("⟨Foxaria⟩")) {
                insideAutoBlock = true;
                continue;
            }
            if (plain.contains("⟨/Foxaria⟩")) {
                insideAutoBlock = false;
                continue;
            }
            if (insideAutoBlock) {
                continue;
            }
            // Старые авто-строки без маркеров — тоже выбрасываем, иначе
            // после миграции они останутся навсегда как «пользовательские».
            if (plain.contains("При ударе") || plain.trim().startsWith("•") || plain.contains("✦")) {
                continue;
            }
            keep.add(legacy);
        }
        if (!keep.isEmpty()) {
            setUserLore(plugin, stack, keep);
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static Component legacy(String text) {
        return FoxariaText.legacy(text);
    }
}
