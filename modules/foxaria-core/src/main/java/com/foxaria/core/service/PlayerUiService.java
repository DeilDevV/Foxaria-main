package com.foxaria.core.service;

import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ServiceRegistry;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.gui.PlayerMainMenu;
import com.foxaria.core.text.FoxariaText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class PlayerUiService implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final ServiceRegistry services;
    private final MessageService messages;
    private final MenuManager menuManager;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private final NamespacedKey menuItemKey;

    public PlayerUiService(JavaPlugin plugin, ConfigService configs, ServiceRegistry services, MessageService messages, MenuManager menuManager) {
        this.plugin = plugin;
        this.configs = configs;
        this.services = services;
        this.messages = messages;
        this.menuManager = menuManager;
        this.menuItemKey = new NamespacedKey(plugin, "menu-item");
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!config().getBoolean("hotbar.lock-item", true)) {
            return;
        }
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        while (iterator.hasNext()) {
            ItemStack next = iterator.next();
            if (isMenuItem(next) || (playerFlowService != null && playerFlowService.isSelectorItem(next))) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        if (playerFlowService != null && playerFlowService.isServerSelectorEnabled()
            && playerFlowService.isSelectorItem(event.getItem())) {
            event.setCancelled(true);
            playerFlowService.openSelectorMenu(event.getPlayer());
            return;
        }

        if (!config().getBoolean("hotbar.enabled", true) || !isMenuItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        openMenu(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (isMenuItem(stack) && config().getBoolean("hotbar.vanish-on-drop", true)) {
            event.getItemDrop().remove();
            return;
        }
        if (!config().getBoolean("hotbar.lock-item", true)) {
            return;
        }
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        if (isMenuItem(stack) || (playerFlowService != null && playerFlowService.isSelectorItem(stack))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config().getBoolean("hotbar.lock-item", true)) {
            return;
        }
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        boolean selectorCurrent = playerFlowService != null && playerFlowService.isSelectorItem(event.getCurrentItem());
        boolean selectorCursor = playerFlowService != null && playerFlowService.isSelectorItem(event.getCursor());
        if (isMenuItem(event.getCurrentItem()) || isMenuItem(event.getCursor()) || selectorCurrent || selectorCursor) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!config().getBoolean("hotbar.lock-item", true)) {
            return;
        }
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        if (isMenuItem(event.getOldCursor()) || (playerFlowService != null && playerFlowService.isSelectorItem(event.getOldCursor()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!config().getBoolean("hotbar.lock-item", true)) {
            return;
        }
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        boolean selectorMain = playerFlowService != null && playerFlowService.isSelectorItem(event.getMainHandItem());
        boolean selectorOff = playerFlowService != null && playerFlowService.isSelectorItem(event.getOffHandItem());
        if (isMenuItem(event.getMainHandItem()) || isMenuItem(event.getOffHandItem()) || selectorMain || selectorOff) {
            event.setCancelled(true);
        }
    }

    public void refresh(Player player) {
        if (!player.isOnline()) {
            return;
        }

        removeFlowItems(player);
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        if (playerFlowService != null) {
            if (playerFlowService.isAuthStage(player)) {
                return;
            }
            if (playerFlowService.isSelectorStage(player)) {
                playerFlowService.syncItems(player);
                return;
            }
        }

        ensureMenuItem(player);
        showJoinPresentation(player);
    }

    public void openMenu(Player player) {
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        if (playerFlowService != null) {
            if (playerFlowService.isAuthStage(player)) {
                messages.send(player, "flow.must-auth-first", "&cСначала завершите авторизацию через /register или /login.");
                return;
            }
            if (playerFlowService.isSelectorStage(player)) {
                if (playerFlowService.isServerSelectorEnabled()) {
                    playerFlowService.openSelectorMenu(player);
                } else {
                    menuManager.open(player, new PlayerMainMenu(plugin, services, configs));
                }
                return;
            }
        }

        menuManager.open(player, new PlayerMainMenu(plugin, services, configs));
        sendActionBarIfPresent(player, config().getString("actionbar.menu-open", "&6Меню"));
    }

    private void ensureMenuItem(Player player) {
        if (!config().getBoolean("hotbar.enabled", true)) {
            return;
        }
        Inventory inventory = player.getInventory();
        int configuredSlot = Math.max(0, Math.min(8, config().getInt("hotbar.slot", 4)));
        ItemStack atSlot = inventory.getItem(configuredSlot);
        if (isMenuItem(atSlot)) {
            return;
        }
        if (hasMenuItem(inventory)) {
            for (int i = 0; i < inventory.getSize(); i++) {
                if (isMenuItem(inventory.getItem(i))) {
                    inventory.setItem(i, null);
                    break;
                }
            }
        }

        ItemStack menuItem = createMenuItem();
        if (atSlot != null && !atSlot.getType().isAir()) {
            int empty = inventory.firstEmpty();
            if (empty >= 0) {
                inventory.setItem(empty, atSlot);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), atSlot);
            }
        }
        inventory.setItem(configuredSlot, menuItem);
        sendActionBarIfPresent(player, config().getString("actionbar.menu-hotbar", ""));
    }

    private boolean hasMenuItem(Inventory inventory) {
        for (ItemStack itemStack : inventory.getContents()) {
            if (isMenuItem(itemStack)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createMenuItem() {
        Material material = Material.matchMaterial(config().getString("hotbar.material", "COMPASS"));
        ItemStack itemStack = new ItemStack(material == null ? Material.COMPASS : material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(FoxariaText.legacy(config().getString("hotbar.name", "&6&lМеню сервера")));
        List<Component> lore = new ArrayList<>();
        for (String line : config().getStringList("hotbar.lore")) {
            lore.add(FoxariaText.legacy(line));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(menuItemKey, PersistentDataType.BYTE, (byte) 1);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private boolean isMenuItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer data = itemStack.getItemMeta().getPersistentDataContainer();
        Byte marker = data.get(menuItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void removeFlowItems(Player player) {
        Inventory inventory = player.getInventory();
        PlayerFlowService playerFlowService = services.optional(PlayerFlowService.class);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (isMenuItem(current) || (playerFlowService != null && playerFlowService.isSelectorItem(current))) {
                inventory.setItem(slot, null);
            }
        }
    }

    private void showJoinPresentation(Player player) {
        if (config().getBoolean("titles.join.enabled", true)) {
            Title.Times times = Title.Times.times(
                Duration.ofMillis(config().getLong("titles.join.fade-in-millis", 300L)),
                Duration.ofMillis(config().getLong("titles.join.stay-millis", 1800L)),
                Duration.ofMillis(config().getLong("titles.join.fade-out-millis", 500L))
            );
            player.showTitle(Title.title(
                FoxariaText.noItalicDeep(serializer.deserialize(config().getString("titles.join.title", "&6FOXARIA"))),
                FoxariaText.noItalicDeep(serializer.deserialize(config().getString("titles.join.subtitle", "&fПолуанархия"))),
                times
            ));
        }
        if (config().getBoolean("actionbar.join.enabled", false)) {
            sendActionBarIfPresent(player, config().getString("actionbar.join.message", ""));
        }
    }

    private void sendActionBarIfPresent(Player player, String legacy) {
        if (legacy == null || legacy.isBlank()) {
            return;
        }
        player.sendActionBar(FoxariaText.noItalicDeep(serializer.deserialize(legacy)));
    }

    private FileConfiguration config() {
        return configs.module("modules/player-ui.yml");
    }
}
