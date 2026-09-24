package com.foxaria.itemtemplates.ability;

import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборка видимого описания предмета.
 *
 * Описание ВСЕГДА собирается с нуля из PDC: редкость → способности →
 * пользовательские строки. Поэтому повторный вызов не плодит дубли —
 * старая система с текстовыми маркерами этим страдала.
 */
public final class AbilityLoreRenderer {

    private AbilityLoreRenderer() {
    }

    public static void apply(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }

        List<AbilityInstance> abilities = AbilityCodec.read(plugin, stack);
        ItemRarity rarity = AbilityCodec.rarity(plugin, stack);
        List<String> userLore = AbilityCodec.userLore(plugin, stack);

        List<Component> lore = new ArrayList<>();

        if (rarity != ItemRarity.COMMON || !abilities.isEmpty()) {
            lore.add(FoxariaText.legacy(rarity.badge()));
        }

        if (!userLore.isEmpty()) {
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            userLore.forEach(line -> lore.add(FoxariaText.legacy(line)));
        }

        if (!abilities.isEmpty()) {
            lore.add(Component.empty());
            lore.add(FoxariaText.legacy(rarity.accent() + "✧ &fОсобые свойства"));
            for (AbilityInstance instance : abilities) {
                lore.add(FoxariaText.legacy(describe(instance)));
                String detail = details(instance);
                if (!detail.isEmpty()) {
                    lore.add(FoxariaText.legacy("   &8" + detail));
                }
            }
        }

        if (!lore.isEmpty()) {
            lore.add(FoxariaText.legacy(rarity.frame()));
        }

        meta.lore(FoxariaText.itemLoreNoItalic(lore));
        if (meta.hasDisplayName()) {
            meta.displayName(FoxariaText.itemNameNoItalic(meta.displayName()));
        }
        stack.setItemMeta(meta);
    }

    /** Строка способности: «▸ Взрывной удар» цветом слота. */
    public static String describe(AbilityInstance instance) {
        ItemAbility ability = instance.ability();
        return ability.slot().color() + "▸ &f" + ability.title();
    }

    /** Параметры: шанс, сила, длительность — только значимые. */
    public static String details(AbilityInstance instance) {
        ItemAbility ability = instance.ability();
        List<String> parts = new ArrayList<>();
        if (ability.usesChance() && instance.chance() < 100) {
            parts.add("шанс " + instance.chance() + "%");
        }
        if (ability.usesPower()) {
            parts.add(ability.powerLabel() + " " + instance.power());
        }
        if (ability.usesSeconds()) {
            parts.add(instance.seconds() + " сек");
        }
        return String.join(" · ", parts);
    }
}
