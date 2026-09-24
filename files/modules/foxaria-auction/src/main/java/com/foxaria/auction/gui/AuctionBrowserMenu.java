package com.foxaria.auction.gui;

import com.foxaria.api.model.AuctionListing;
import com.foxaria.api.service.AuctionService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class AuctionBrowserMenu extends BaseMenu {

    private static final int PAGE_SIZE = 36;
    private static final int SEPARATOR_START = 36;

    private final JavaPlugin plugin;
    private final AuctionService auctionService;
    private final MessageService messages;
    private final MenuManager menuManager;
    private final int page;
    private final AuctionFilter filter;

    public AuctionBrowserMenu(
        JavaPlugin plugin,
        AuctionService auctionService,
        MessageService messages,
        MenuManager menuManager,
        int page,
        AuctionFilter filter
    ) {
        super("&8⟨ &6&lАукцион &8│ &fлоты &8⟩", 54);
        this.plugin = plugin;
        this.auctionService = auctionService;
        this.messages = messages;
        this.menuManager = menuManager;
        this.page = Math.max(0, page);
        this.filter = filter == null ? AuctionFilter.ALL : filter;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = SEPARATOR_START; i < SEPARATOR_START + 9; i++) {
            setItem(i, MenuItems.item(Material.GRAY_STAINED_GLASS_PANE, "&8 "), null);
        }

        auctionService.activeListings().thenAccept(all ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                List<AuctionListing> filtered = all.stream()
                    .filter(l -> filter.matches(l.item().getType()))
                    .collect(Collectors.toList());
                int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                int safePage = Math.min(page, totalPages - 1);
                if (safePage != page) {
                    menuManager.open(player, new AuctionBrowserMenu(plugin, auctionService, messages, menuManager, safePage, filter));
                    return;
                }
                int from = safePage * PAGE_SIZE;
                List<AuctionListing> slice = filtered.subList(from, Math.min(from + PAGE_SIZE, filtered.size()));

                int slot = 0;
                for (AuctionListing listing : slice) {
                    setItem(slot++, listingIcon(player, listing), e ->
                        auctionService.buy(player, listing.id()).thenRun(() ->
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (player.isOnline()) {
                                    menuManager.open(player, new AuctionBrowserMenu(plugin, auctionService, messages, menuManager, safePage, filter));
                                }
                            })
                        )
                    );
                }

                drawNav(player, filtered.size(), safePage, totalPages);
            })
        );
    }

    private ItemStack listingIcon(Player viewer, AuctionListing listing) {
        ItemStack icon = listing.item().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) {
            return icon;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerUuid());
        String sellerName = seller.getName() == null ? "—" : seller.getName();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            for (Component line : meta.lore()) {
                lore.add(FoxariaText.noItalic(line));
            }
            lore.add(FoxariaText.legacy("&8 "));
        }
        lore.add(FoxariaText.legacy("&8▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪"));
        lore.add(FoxariaText.legacy("&7Продавец: &f&l" + sellerName));
        lore.add(FoxariaText.legacy("&7Цена: &e&l" + listing.price().toPlainString() + " &7монет"));
        lore.add(FoxariaText.legacy("&7Комиссия с продавца: &c" + listing.fee().toPlainString()));
        lore.add(FoxariaText.legacy("&8 "));
        lore.add(FoxariaText.legacy("&a▶ &lКупить &7(клик)"));
        meta.lore(lore);
        Component baseName = meta.hasDisplayName() ? meta.displayName() : FoxariaText.plain(icon.getType().name());
        meta.displayName(FoxariaText.noItalic(baseName));
        icon.setItemMeta(meta);
        return icon;
    }

    private void drawNav(Player player, int totalFiltered, int safePage, int totalPages) {
        int bottom = 45;
        if (safePage > 0) {
            setItem(bottom, MenuItems.item(Material.ARROW, "&e&lНазад", "&7Страница &f" + (safePage) + "&7/&f" + totalPages),
                e -> menuManager.open(player, new AuctionBrowserMenu(plugin, auctionService, messages, menuManager, safePage - 1, filter)));
        } else {
            setItem(bottom, MenuItems.item(Material.GRAY_DYE, "&8Первая страница", "&7Это начало списка"), null);
        }

        setItem(bottom + 1, MenuItems.item(Material.HOPPER, filter.display(), List.of(
            "&7Фильтр по типу предмета.",
            "&7Клик — следующий фильтр.",
            "&8Всего под фильтр: &f" + totalFiltered
        ).toArray(new String[0])),
            e -> menuManager.open(player, new AuctionBrowserMenu(plugin, auctionService, messages, menuManager, 0, filter.next())));

        setItem(bottom + 2, MenuItems.item(Material.BOOK, "&f&lСправка", List.of(
            "&7/ah &f— открыть это меню",
            "&7/ah sell <цена> &f— выставить предмет из руки",
            "&7/ah expired &f— забрать всё из почты в инвентарь",
            "&7Почта нужна, если при покупке не было места."
        ).toArray(new String[0])), null);

        setItem(bottom + 3, MenuItems.item(Material.CHEST, "&6&lПочта аукциона", List.of(
            "&7Предметы, которые не влезли в инвентарь.",
            "&7Клик — открыть окно почты.",
            "&a▶ Открыть"
        ).toArray(new String[0])),
            e -> menuManager.open(player, new AuctionMailboxMenu(plugin, auctionService, messages, menuManager, 0)));

        setItem(bottom + 4, MenuItems.item(Material.GOLD_NUGGET, "&e&lПродать", List.of(
            "&7Возьми предмет в главную руку:",
            "&f/ah sell <цена>",
            "&7Списание комиссии — по настройкам сервера."
        ).toArray(new String[0])), null);

        if (safePage < totalPages - 1) {
            setItem(bottom + 5, MenuItems.item(Material.ARROW, "&e&lДалее", "&7Страница &f" + (safePage + 2) + "&7/&f" + totalPages),
                e -> menuManager.open(player, new AuctionBrowserMenu(plugin, auctionService, messages, menuManager, safePage + 1, filter)));
        } else {
            setItem(bottom + 5, MenuItems.item(Material.GRAY_DYE, "&8Последняя страница", "&7Дальше лотов нет"), null);
        }

        setItem(bottom + 7, MenuItems.item(Material.EMERALD, "&a&lОбновить", "&7Перечитать лоты с сервера"),
            e -> menuManager.open(player, new AuctionBrowserMenu(plugin, auctionService, messages, menuManager, safePage, filter)));

        setItem(bottom + 8, MenuItems.item(Material.BARRIER, "&c&lЗакрыть", "&7Закрыть меню"), e -> player.closeInventory());
    }
}
