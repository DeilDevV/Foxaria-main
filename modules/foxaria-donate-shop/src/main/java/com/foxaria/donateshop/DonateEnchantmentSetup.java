package com.foxaria.donateshop;

import com.foxaria.itemtemplates.ability.AbilityCodec;
import com.foxaria.itemtemplates.ability.AbilityInstance;
import com.foxaria.itemtemplates.ability.ItemAbility;
import com.foxaria.itemtemplates.ability.ItemRarity;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Утилита для создания книг зачарований Foxaria в донат-магазине.
 * 
 * Выполни setupAllEnchantments() один раз (например из /fdonate enchants),
 * и все книги будут добавлены в БД в категории ENCHANTMENTS.
 * Покупатель получает книгу, применяет её через наковальню к своему предмету.
 *
 * Путь к файлу: modules/foxaria-donate-shop/src/main/java/com/foxaria/donateshop/DonateEnchantmentSetup.java
 */
public final class DonateEnchantmentSetup {

    private final JavaPlugin plugin;
    private final DonateShopRepository repository;

    public DonateEnchantmentSetup(JavaPlugin plugin, DonateShopRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /** Добавляет все книги зачарований в БД если их ещё нет. */
    public void setupAllEnchantments() {
        for (EnchantBook book : EnchantBook.values()) {
            repository.listByCategory(DonateCategory.ENCHANTMENTS).thenAccept(existing -> {
                boolean alreadyExists = existing.stream().anyMatch(o -> o.id().equals(book.offerId));
                if (alreadyExists) return;
                repository.addOffer(new DonateOffer(
                    book.offerId,
                    DonateCategory.ENCHANTMENTS,
                    book.priceTokens,
                    createBook(book),
                    book.sortOrder
                ));
            });
        }
    }

    private ItemStack createBook(EnchantBook book) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        Component displayName = FoxariaText.legacy("&6&l" + book.displayName);
        meta.displayName(displayName);

        meta.lore(List.of(
            FoxariaText.legacy("&7" + book.description),
            FoxariaText.legacy("&8───────────────"),
            FoxariaText.legacy("&7Редкость: " + book.rarity.name()),
            FoxariaText.legacy("&7Шанс: &f" + book.defaultChance + "%"),
            book.defaultPower > 0 ? FoxariaText.legacy("&7Сила: &f" + book.defaultPower) : null,
            book.defaultSeconds > 0 ? FoxariaText.legacy("&7Длительность: &f" + book.defaultSeconds + "с") : null,
            FoxariaText.legacy("&8───────────────"),
            FoxariaText.legacy("&eПримените через наковальню к предмету!"),
            FoxariaText.legacy("&8Зачарование: &f/itpl")
        ).stream().filter(c -> c != null).toList());

        item.setItemMeta(meta);

        // Записываем способность в PDC книги — при применении через наковальню передастся на предмет
        AbilityCodec.toggle(plugin, item, book.ability);
        AbilityCodec.setRarity(plugin, item, book.rarity);

        return item;
    }

    /** Все доступные книги зачарований для донат-магазина. */
    public enum EnchantBook {
        EXPLOSIVE_STRIKE("enchant_explosive_strike", "Взрывной удар",
            "Взрыв при ударе (без разрушения блоков)", ItemAbility.EXPLOSIVE_STRIKE,
            ItemRarity.RARE, 20, 1, 0, 80, 1),
        KNOCKBACK_BLAST("enchant_knockback_blast", "Ударная волна",
            "Отбрасывает всех рядом с целью", ItemAbility.KNOCKBACK_BLAST,
            ItemRarity.UNCOMMON, 25, 2, 0, 50, 2),
        LIFE_STEAL("enchant_life_steal", "Вампиризм",
            "Восстанавливает 15% нанесённого урона", ItemAbility.LIFE_STEAL,
            ItemRarity.RARE, 100, 15, 0, 100, 3),
        LIGHTNING_STRIKE("enchant_lightning_strike", "Удар молнии",
            "Призывает молнию при ударе", ItemAbility.LIGHTNING_STRIKE,
            ItemRarity.EPIC, 12, 1, 0, 150, 4),
        CHAIN_LIGHTNING("enchant_chain_lightning", "Цепная молния",
            "Разряд перепрыгивает на 2 ближних врага", ItemAbility.CHAIN_LIGHTNING,
            ItemRarity.EPIC, 15, 2, 0, 200, 5),
        FROST_BITE("enchant_frost_bite", "Обморожение",
            "Замедляет цель на 2 секунды", ItemAbility.FROST_BITE,
            ItemRarity.UNCOMMON, 30, 1, 2, 60, 6),
        HOMING_ARROW("enchant_homing_arrow", "Самонаведение",
            "Стрела сама доворачивает к цели", ItemAbility.HOMING_ARROW,
            ItemRarity.RARE, 100, 10, 0, 120, 7),
        EXPLOSIVE_ARROW("enchant_explosive_arrow", "Разрывная стрела",
            "Взрыв при попадании стрелы", ItemAbility.EXPLOSIVE_ARROW,
            ItemRarity.EPIC, 25, 1, 0, 180, 8),
        WITHER_TOUCH("enchant_wither_touch", "Иссушение",
            "Иссушение на 3 секунды при ударе", ItemAbility.WITHER_TOUCH,
            ItemRarity.UNCOMMON, 20, 1, 3, 55, 9),
        POISON_BLADE("enchant_poison_blade", "Ядовитый клинок",
            "Отравляет цель на 4 секунды", ItemAbility.POISON_BLADE,
            ItemRarity.COMMON, 25, 1, 4, 40, 10),
        EXECUTE("enchant_execute", "Добивание",
            "Убивает цель при HP ниже 15%", ItemAbility.EXECUTE,
            ItemRarity.LEGENDARY, 100, 15, 0, 300, 11),
        THORNS_AURA("enchant_thorns_aura", "Шипы возмездия",
            "Возвращает 20% урона атакующему", ItemAbility.THORNS_AURA,
            ItemRarity.RARE, 35, 20, 0, 90, 12),
        SECOND_WIND("enchant_second_wind", "Второе дыхание",
            "При смертельном ударе — щит регенерации", ItemAbility.SECOND_WIND,
            ItemRarity.LEGENDARY, 100, 1, 4, 350, 13),
        DODGE("enchant_dodge", "Уклонение",
            "Шанс уклониться от удара 12%", ItemAbility.DODGE,
            ItemRarity.RARE, 12, 0, 0, 110, 14),
        SWIFTNESS("enchant_swiftness", "Лёгкость",
            "Ускорение, пока предмет в руке", ItemAbility.SWIFTNESS,
            ItemRarity.COMMON, 100, 1, 0, 45, 15);

        public final String offerId;
        public final String displayName;
        public final String description;
        public final ItemAbility ability;
        public final ItemRarity rarity;
        public final int defaultChance;
        public final int defaultPower;
        public final int defaultSeconds;
        public final long priceTokens;
        public final int sortOrder;

        EnchantBook(String offerId, String displayName, String description, ItemAbility ability,
                    ItemRarity rarity, int defaultChance, int defaultPower, int defaultSeconds,
                    long priceTokens, int sortOrder) {
            this.offerId = offerId;
            this.displayName = displayName;
            this.description = description;
            this.ability = ability;
            this.rarity = rarity;
            this.defaultChance = defaultChance;
            this.defaultPower = defaultPower;
            this.defaultSeconds = defaultSeconds;
            this.priceTokens = priceTokens;
            this.sortOrder = sortOrder;
        }
    }
}
