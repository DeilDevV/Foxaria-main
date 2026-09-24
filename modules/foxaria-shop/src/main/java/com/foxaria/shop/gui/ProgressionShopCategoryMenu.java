package com.foxaria.shop.gui;

import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.shop.ProgressionShopService;
import com.foxaria.shop.progression.ProgressionCategory;
import com.foxaria.shop.progression.ProgressionRepository;
import com.foxaria.shop.progression.ProgressionService;
import com.foxaria.shop.progression.ProgressionShopOffer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ProgressionShopCategoryMenu extends BaseMenu {

    private static final int AREA = 36;

    private final ProgressionShopService shop;
    private final ProgressionService progression;
    private final ProgressionCategory category;
    private final FileConfiguration cfg;

    public ProgressionShopCategoryMenu(
        ProgressionShopService shop,
        ProgressionService progression,
        ProgressionCategory category,
        FileConfiguration cfg
    ) {
        super(category.titleLegacy() + " &8| &7/shop", 54);
        this.shop = shop;
        this.progression = progression;
        this.category = category;
        this.cfg = cfg;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = 36; i < 45; i++) {
            setItem(i, MenuItems.item(Material.GRAY_STAINED_GLASS_PANE, "&8 "), null);
        }

        setItem(40, MenuItems.item(Material.BOOK, "&f&lКак открыть товары?", new String[]{
            "&7Прокачивайте уровень знаний в &f/quest",
            "&7и выполняйте цепочку квестов.",
            "&8 ",
            "&7Оплата только &eмонетами&7 (не токены)."
        }), null);

        progression.knowledgeLevel(player.getUniqueId()).thenAccept(kl ->
            shop.shopRepository().listShopOffers(category.id()).thenAccept(list ->
                shop.plugin().getServer().getScheduler().runTask(shop.plugin(), () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    int slot = 0;
                    for (ProgressionShopOffer offer : list) {
                        if (slot >= AREA) {
                            break;
                        }
                        boolean locked = kl < offer.requiredKnowledge();
                        if (locked) {
                            setItem(slot++, lockedIcon(offer), null);
                        } else {
                            setItem(slot++, unlockedIcon(offer), e -> shop.buy(player, offer, 1));
                        }
                    }
                })
            )
        );

        setItem(45, MenuItems.item(Material.ARROW, "&e&lРазделы", "&7Назад"),
            e -> shop.open(player));
        setItem(49, MenuItems.item(Material.EMERALD, "&a&lОбновить", "&7Перечитать"),
            e -> shop.openCategory(player, category));
        setItem(53, MenuItems.item(Material.BARRIER, "&cЗакрыть", "&7Закрыть"), e -> player.closeInventory());
    }

    private ItemStack lockedIcon(ProgressionShopOffer offer) {
        return MenuItems.item(Material.IRON_BARS, "&8&lЗакрыто", List.of(
            "&7Недостаточно знаний.",
            "&7Нужен уровень: &c" + offer.requiredKnowledge(),
            "&7Ваш уровень слишком низок.",
            "&8 ",
            "&7Откройте в &f/quest"
        ).toArray(new String[0]));
    }

    private ItemStack unlockedIcon(ProgressionShopOffer offer) {
        ItemStack icon;
        if (offer.usesTemplate() && shop.itemTemplates() != null) {
            icon = shop.itemTemplates().cloneTemplate(offer.itemTemplate()).orElseGet(() -> new ItemStack(Material.PAPER));
        } else {
            ItemStack d = ProgressionRepository.deserializeItem(offer);
            icon = d != null ? d.clone() : new ItemStack(Material.PAPER);
        }
        ItemMeta meta = icon.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() != null
            ? new ArrayList<>(meta.lore())
            : new ArrayList<>();
        lore.add(FoxariaText.legacy("&8▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪"));
        lore.add(FoxariaText.legacy("&7Цена: &e" + offer.price().toPlainString() + " &7монет"));
        lore.add(FoxariaText.legacy("&7Нужен уровень знаний: &a" + offer.requiredKnowledge() + " &7(есть)"));
        lore.add(FoxariaText.legacy("&a▶ ЛКМ — купить x1"));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }
}
