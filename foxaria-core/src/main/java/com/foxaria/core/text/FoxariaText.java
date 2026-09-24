package com.foxaria.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Единый стиль текста в GUI: без курсива (ванильный курсив предметов отключаем явно).
 */
public final class FoxariaText {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private FoxariaText() {
    }

    public static Component legacy(String ampersandLine) {
        return noItalic(LEGACY.deserialize(ampersandLine));
    }

    public static List<Component> legacyLore(List<String> ampersandLines) {
        List<Component> out = new ArrayList<>(ampersandLines.size());
        for (String line : ampersandLines) {
            out.add(legacy(line));
        }
        return out;
    }

    public static List<Component> legacyLore(String... ampersandLines) {
        List<Component> out = new ArrayList<>(ampersandLines.length);
        for (String line : ampersandLines) {
            out.add(legacy(line));
        }
        return out;
    }

    public static Component plain(String text) {
        return Component.text(text).decoration(TextDecoration.ITALIC, false);
    }

    public static Component noItalic(Component component) {
        if (component == null) {
            return Component.empty();
        }
        return component.style(style -> style.decoration(TextDecoration.ITALIC, false));
    }

    /** Рекурсивно снимает курсив с текста и вложенных фрагментов (для сообщений из YAML и т.п.). */
    public static Component noItalicDeep(Component input) {
        if (input == null) {
            return Component.empty();
        }
        if (input instanceof TextComponent tc) {
            Style st = tc.style().decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
            TextComponent.Builder b = Component.text().content(tc.content()).style(st);
            for (Component child : tc.children()) {
                b.append(noItalicDeep(child));
            }
            return b.build();
        }
        return noItalic(input);
    }

    /** Lore предметов: без курсива (ваниль по умолчанию italic для custom items). */
    public static List<Component> itemLoreNoItalic(List<Component> lore) {
        if (lore == null || lore.isEmpty()) {
            return lore == null ? List.of() : lore;
        }
        List<Component> out = new ArrayList<>(lore.size());
        for (Component c : lore) {
            out.add(noItalicDeep(c));
        }
        return out;
    }

    /** Имя предмета без курсива. */
    public static Component itemNameNoItalic(Component name) {
        return name == null ? Component.empty() : noItalicDeep(name);
    }
}
