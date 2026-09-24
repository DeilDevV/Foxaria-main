package com.foxaria.itemtemplates.util;

import com.foxaria.core.text.FoxariaText;
import com.foxaria.itemtemplates.edit.HandPotionUtil;
import com.foxaria.itemtemplates.edit.TemplateOnHitCodec;
import com.foxaria.itemtemplates.edit.TemplateOnHitCodec.OnHitData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Дополнительные строки описания по эффектам удара и кастомным зельям (без брендированных блоков).
 */
public final class EditorLoreSync {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Границы авто-блока (plain), чтобы при refresh не дублировать строки. */
    private static final String MARK_OPEN_PLAIN = "⟨Foxaria⟩";
    private static final String MARK_CLOSE_PLAIN = "⟨/Foxaria⟩";

    private EditorLoreSync() {
    }

    public static List<Component> stripAutoBlock(List<Component> lore) {
        if (lore == null || lore.isEmpty()) {
            return new ArrayList<>();
        }
        int start = -1;
        int end = -1;
        for (int i = 0; i < lore.size(); i++) {
            String p = PLAIN.serialize(lore.get(i));
            if (p.contains(MARK_OPEN_PLAIN) && !p.contains(MARK_CLOSE_PLAIN)) {
                start = i;
            } else if (p.contains(MARK_CLOSE_PLAIN) && start >= 0) {
                end = i;
                break;
            }
        }
        if (start >= 0 && end >= 0) {
            List<Component> out = new ArrayList<>();
            for (int i = 0; i < lore.size(); i++) {
                if (i < start || i > end) {
                    out.add(lore.get(i));
                }
            }
            return out;
        }
        return stripLegacyUnmarkedStrikeBlock(lore);
    }

    /**
     * Старые предметы без ⟨Foxaria⟩: срезаем блоки «При ударе…» и «Зелье (кастом)».
     */
    private static List<Component> stripLegacyUnmarkedStrikeBlock(List<Component> lore) {
        List<Component> out = new ArrayList<>();
        int i = 0;
        while (i < lore.size()) {
            String p = PLAIN.serialize(lore.get(i));
            if (isStrikeHeader(p)) {
                i++;
                while (i < lore.size() && isStrikeEffectLine(PLAIN.serialize(lore.get(i)))) {
                    i++;
                }
                continue;
            }
            if (isPotionCustomHeader(p)) {
                i++;
                while (i < lore.size() && isPotionEffectLine(PLAIN.serialize(lore.get(i)))) {
                    i++;
                }
                continue;
            }
            out.add(lore.get(i));
            i++;
        }
        return out;
    }

    private static boolean isStrikeHeader(String plain) {
        return plain.contains("При ударе") && (plain.contains("цель") || plain.contains("себя"));
    }

    private static boolean isStrikeEffectLine(String plain) {
        return plain.contains("•") && (plain.contains("%") || plain.contains("ур."));
    }

    private static boolean isPotionCustomHeader(String plain) {
        return plain.contains("Зелье") && plain.contains("кастом");
    }

    private static boolean isPotionEffectLine(String plain) {
        return plain.contains("•") && plain.contains("ур.");
    }

    public static List<Component> buildAutoBlock(JavaPlugin plugin, ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        List<OnHitData> vic = TemplateOnHitCodec.readAllVictim(plugin, stack);
        if (!vic.isEmpty()) {
            lines.add(FoxariaText.legacy("&7При ударе &cна цель&7:"));
            for (OnHitData d : vic) {
                String t = EditorDisplayNames.potionEffect(d.type());
                lines.add(FoxariaText.legacy(String.format("&c  • &f%s &8(%s, ур.%d, %d%%)",
                    t, tickRu(d.durationTicks()), d.amplifier() + 1, Math.round(d.chance() * 100))));
            }
        }

        List<OnHitData> self = TemplateOnHitCodec.readAllSelf(plugin, stack);
        if (!self.isEmpty()) {
            lines.add(FoxariaText.legacy("&7При ударе &aна себя&7:"));
            for (OnHitData d : self) {
                String t = EditorDisplayNames.potionEffect(d.type());
                lines.add(FoxariaText.legacy(String.format("&a  • &f%s &8(%s, ур.%d, %d%%)",
                    t, tickRu(d.durationTicks()), d.amplifier() + 1, Math.round(d.chance() * 100))));
            }
        }

        if (HandPotionUtil.supportsPotionMeta(stack) && stack.getItemMeta() instanceof PotionMeta pm && !pm.getCustomEffects().isEmpty()) {
            lines.add(FoxariaText.legacy("&7Зелье &f(кастом)&7:"));
            for (PotionEffect pe : pm.getCustomEffects()) {
                String t = EditorDisplayNames.potionEffect(pe.getType());
                lines.add(FoxariaText.legacy(String.format("&d  • &f%s &8(%s, ур.%d)",
                    t, tickRu(pe.getDuration()), pe.getAmplifier() + 1)));
            }
        }

        if (lines.isEmpty()) {
            return lines;
        }
        List<Component> wrapped = new ArrayList<>();
        wrapped.add(FoxariaText.legacy("&8" + MARK_OPEN_PLAIN));
        wrapped.addAll(lines);
        wrapped.add(FoxariaText.legacy("&8" + MARK_CLOSE_PLAIN));
        return wrapped;
    }

    private static String tickRu(int ticks) {
        double sec = ticks / 20.0;
        if (sec >= 60) {
            return String.format("%.1f мин", sec / 60.0);
        }
        return String.format("%.1f с", sec);
    }

    public static void refresh(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> cur = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<Component> base = stripAutoBlock(cur);
        List<Component> auto = buildAutoBlock(plugin, stack);
        List<Component> merged = new ArrayList<>(base);
        merged.addAll(auto);
        meta.lore(FoxariaText.itemLoreNoItalic(merged));
        if (meta.hasDisplayName()) {
            meta.displayName(FoxariaText.itemNameNoItalic(meta.displayName()));
        }
        stack.setItemMeta(meta);
    }

    public static void appendUserLoreLine(JavaPlugin plugin, ItemStack stack, String ampersandLine) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> cur = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<Component> base = stripAutoBlock(cur);
        base.add(FoxariaText.legacy(ampersandLine));
        List<Component> merged = new ArrayList<>(base);
        merged.addAll(buildAutoBlock(plugin, stack));
        meta.lore(FoxariaText.itemLoreNoItalic(merged));
        if (meta.hasDisplayName()) {
            meta.displayName(FoxariaText.itemNameNoItalic(meta.displayName()));
        }
        stack.setItemMeta(meta);
    }

    public static void setColoredDisplayName(ItemStack stack, String ampersandText) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.displayName(FoxariaText.itemNameNoItalic(FoxariaText.legacy(ampersandText)));
        stack.setItemMeta(meta);
    }
}
