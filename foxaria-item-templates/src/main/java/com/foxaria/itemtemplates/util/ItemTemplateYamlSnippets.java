package com.foxaria.itemtemplates.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Готовые фрагменты YAML для вставки в shop.yml и guilds-shop.yml.
 */
public final class ItemTemplateYamlSnippets {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int BOOK_PAGE_CHARS = 220;

    private ItemTemplateYamlSnippets() {
    }

    public static String displayLabel(ItemStack stack, String fallbackId) {
        if (stack == null || stack.getType().isAir()) {
            return fallbackId;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return PLAIN.serialize(meta.displayName());
        }
        return fallbackId;
    }

    /**
     * Блок для {@code modules/guilds-shop.yml} → секция {@code offers}.
     */
    public static String guildShopOfferBlock(String offerKey, String templateId, ItemStack sample) {
        String mat = sample != null && !sample.getType().isAir() ? sample.getType().name() : "PAPER";
        String disp = displayLabel(sample, offerKey);
        return offerKey + ":\n"
            + "  item-template: \"" + escapeYamlDoubleQuoted(templateId) + "\"\n"
            + "  display: \"" + escapeYamlDoubleQuoted(disp) + "\"\n"
            + "  material: " + mat + "\n"
            + "  amount: 1\n"
            + "  price-coins: 100\n"
            + "  required-tier: 1\n";
    }

    /**
     * Блок для {@code modules/shop.yml} → {@code categories.&lt;категория&gt;.offers}.
     */
    public static String serverShopOfferBlock(String offerKey, String templateId, ItemStack sample) {
        String mat = sample != null && !sample.getType().isAir() ? sample.getType().name() : "PAPER";
        String disp = displayLabel(sample, offerKey);
        return offerKey + ":\n"
            + "  item-template: \"" + escapeYamlDoubleQuoted(templateId) + "\"\n"
            + "  display-name: \"" + escapeYamlDoubleQuoted(disp) + "\"\n"
            + "  material: " + mat + "\n"
            + "  amount: 1\n"
            + "  buy-price: 100.0\n"
            + "  sell-price: 0.0\n";
    }

    /**
     * Полный текст для книги: два готовых блока YAML + подсказка.
     */
    public static String fullSnippetBook(String templateId, ItemStack sample) {
        String key = templateId;
        return templateId + "\n\n=== guilds-shop.yml (offers) ===\n"
            + guildShopOfferBlock(key, templateId, sample)
            + "\n=== shop.yml (categories.*.offers) ===\n"
            + serverShopOfferBlock(key, templateId, sample)
            + "\nПоменяй ключ лота, цены, tier и категорию.";
    }

    public static List<String> pagesForBook(String text) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + BOOK_PAGE_CHARS);
            if (end < text.length()) {
                int breakAt = text.lastIndexOf('\n', end);
                if (breakAt > i + BOOK_PAGE_CHARS / 2) {
                    end = breakAt + 1;
                }
            }
            out.add(text.substring(i, end));
            i = end;
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    private static String escapeYamlDoubleQuoted(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
