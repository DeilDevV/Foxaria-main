package com.foxaria.auction.gui;

import com.foxaria.api.model.AuctionMailboxItem;
import com.foxaria.api.service.AuctionService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class AuctionMailboxMenu extends BaseMenu {

    private static final int PAGE_SIZE = 36;
    private static final int SEPARATOR_START = 36;

    private final JavaPlugin plugin;
    private final AuctionService auctionService;
    private final MessageService messages;
    private final MenuManager menuManager;
    private final int page;

    public AuctionMailboxMenu(
        JavaPlugin plugin,
        AuctionService auctionService,
        MessageService messages,
        MenuManager menuManager,
        int page
    ) {
        super("&6&lПочта аукциона", 54);
        this.plugin = plugin;
        this.auctionService = auctionService;
        this.messages = messages;
        this.menuManager = menuManager;
        this.page = Math.max(0, page);
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = SEPARATOR_START; i < SEPARATOR_START + 9; i++) {
            setItem(i, MenuItems.item(Material.GRAY_STAINED_GLASS_PANE, "&8 "), null);
        }

        auctionService.pendingMailboxItems(player.getUniqueId()).thenAccept(items ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                int totalPages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                int safePage = Math.min(page, totalPages - 1);
                int from = safePage * PAGE_SIZE;
                if (items.isEmpty()) {
                    setItem(22, MenuItems.item(Material.CHEST_MINECART, "&a&lПочта пуста", List.of(
                        "&7Когда купишь лот при полном инвентаре,",
                        "&7предмет окажется здесь.",
                        "&7Или используй: &f/ah expired"
                    ).toArray(new String[0])), null);
                } else {
                    List<AuctionMailboxItem> slice = items.subList(from, Math.min(from + PAGE_SIZE, items.size()));
                    int slot = 0;
                    for (AuctionMailboxItem row : slice) {
                        ItemStack icon = wrapMailboxItem(row);
                        String id = row.deliveryId();
                        setItem(slot++, icon, e ->
                            auctionService.claimMailboxDelivery(player, id).thenAccept(ok ->
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    if (!player.isOnline()) {
                                        return;
                                    }
                                    if (Boolean.TRUE.equals(ok)) {
                                        messages.send(player, "auction.mailbox-one-claimed", "&aПредмет забран из почты.");
                                    }
                                    menuManager.open(player, new AuctionMailboxMenu(plugin, auctionService, messages, menuManager, safePage));
                                })
                            )
                        );
                    }
                }
                drawBottom(player, items.size(), safePage, totalPages);
            })
        );
    }

    private static ItemStack wrapMailboxItem(AuctionMailboxItem row) {
        ItemStack icon = row.item().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore() && meta.lore() != null) {
                for (Component line : meta.lore()) {
                    lore.add(FoxariaText.noItalic(line));
                }
                lore.add(FoxariaText.legacy("&8 "));
            }
            lore.add(FoxariaText.legacy("&6&lПочта аукциона"));
            lore.add(FoxariaText.legacy("&7Клик — забрать в инвентарь"));
            meta.lore(lore);
            Component base = meta.hasDisplayName() ? meta.displayName() : FoxariaText.plain(icon.getType().name());
            meta.displayName(FoxariaText.noItalic(base));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void drawBottom(Player player, int total, int safePage, int totalPages) {
        int b = 45;
        if (safePage > 0) {
            setItem(b, MenuItems.item(Material.ARROW, "&e&lНазад", "&7Страница почты"),
                e -> menuManager.open(player, new AuctionMailboxMenu(plugin, auctionService, messages, menuManager, safePage - 1)));
        } else {
            setItem(b, MenuItems.item(Material.GRAY_DYE, "&8Начало", "&7Первая страница"), null);
        }

        setItem(b + 2, MenuItems.item(Material.HOPPER_MINECART, "&e&lЗабрать всё", List.of(
            "&7Выдаст в инвентарь всё, что поместится.",
            "&7Команда: &f/ah expired"
        ).toArray(new String[0])),
            e -> {
                auctionService.openMailbox(player).thenRun(() ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            menuManager.open(player, new AuctionMailboxMenu(plugin, auctionService, messages, menuManager, 0));
                        }
                    })
                );
            });

        setItem(b + 4, MenuItems.item(Material.BOOK, "&f&lВсего в почте: &a" + total, "&7Постранично по &f36 &7слотов"), null);

        if (safePage < totalPages - 1) {
            setItem(b + 5, MenuItems.item(Material.ARROW, "&e&lДалее", "&7Следующая страница"),
                e -> menuManager.open(player, new AuctionMailboxMenu(plugin, auctionService, messages, menuManager, safePage + 1)));
        } else {
            setItem(b + 5, MenuItems.item(Material.GRAY_DYE, "&8Конец", "&7Последняя страница"), null);
        }

        setItem(b + 6, MenuItems.item(Material.COMPASS, "&6&lК лотам", "&7Вернуться к аукциону"),
            e -> menuManager.open(player, new AuctionBrowserMenu(plugin, auctionService, messages, menuManager, 0, AuctionFilter.ALL)));

        setItem(b + 8, MenuItems.item(Material.BARRIER, "&c&lЗакрыть", "&7Закрыть"), e -> player.closeInventory());
    }
}
