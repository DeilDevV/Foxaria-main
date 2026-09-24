package com.foxaria.hubguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LobbyMenuListener implements Listener {

    private final FoxariaHubGuardPlugin plugin;
    private final BungeePlayerCountHandler playerCount;
    private final FoxariaProxyQueueHandler proxyQueue;
    private final ServerStateTracker stateTracker;
    private final WipeLockRegistry wipeLock;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    /** Обход вайп-блокировки: явные права + группы из rank-bridge (кэш ранга может ещё не прогреться). */
    private boolean isWipeAdmin(Player player) {
        return player.isOp()
            || player.hasPermission("foxaria.proxy.admin")
            || player.hasPermission("foxaria.admin")
            || player.hasPermission("foxaria.admin.panel")
            || player.hasPermission("foxaria.admin.wipe")
            || WipeCommand.isAdmin(player, plugin.hubRank(), plugin);
    }

    /** Last known player counts from BungeeCord (bungee-server → online). */
    private final Map<String, Integer> cachedOnline = new ConcurrentHashMap<>();
    /** Last known queue sizes (bungee-server → queue). */
    private final Map<String, Integer> cachedQueue = new ConcurrentHashMap<>();
    /** Players currently viewing the lobby menu. */
    private final Set<UUID> menuViewers = ConcurrentHashMap.newKeySet();

    private BukkitTask refreshTask;

    public LobbyMenuListener(
        FoxariaHubGuardPlugin plugin,
        BungeePlayerCountHandler playerCount,
        FoxariaProxyQueueHandler proxyQueue,
        ServerStateTracker stateTracker,
        WipeLockRegistry wipeLock
    ) {
        this.plugin = plugin;
        this.playerCount = playerCount;
        this.proxyQueue = proxyQueue;
        this.stateTracker = stateTracker;
        this.wipeLock = wipeLock;
    }

    public void startRefreshTask() {
        // Refresh open menus every 20 ticks (1 sec); re-request counts every 60 ticks (3 sec).
        final int[] tick = {0};
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0]++;
            if (menuViewers.isEmpty()) return;
            if (tick[0] % 3 == 0) {
                requestCounts();
            }
            refreshAllOpen();
        }, 20L, 20L);
    }

    public void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void requestCounts() {
        // Send count requests via any online viewer (BungeeCord plugin messaging needs a player).
        Player messenger = null;
        for (UUID id : menuViewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) { messenger = p; break; }
        }
        if (messenger == null) return;
        ConfigurationSection servers = cfg().getConfigurationSection("lobby-menu.servers");
        if (servers == null) return;
        final Player m = messenger;
        for (String key : servers.getKeys(false)) {
            ConfigurationSection s = servers.getConfigurationSection(key);
            if (s == null || s.getBoolean("coming-soon", false)) continue;
            String bungee = s.getString("bungee-server", "");
            if (bungee.isBlank() || !stateTracker.isOnline(bungee)) continue;
            playerCount.request(m, bungee, count -> cachedOnline.put(bungee, count));
            proxyQueue.request(m, bungee, queue -> cachedQueue.put(bungee, queue));
        }
    }

    private void refreshAllOpen() {
        for (UUID id : menuViewers) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { menuViewers.remove(id); continue; }
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof LobbyMenuHolder)) {
                menuViewers.remove(id);
                continue;
            }
            refreshMenuLore(p);
        }
    }

    private void refreshMenuLore(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        boolean admin = isWipeAdmin(player);
        ConfigurationSection servers = cfg().getConfigurationSection("lobby-menu.servers");
        if (servers == null) return;
        for (String key : servers.getKeys(false)) {
            ConfigurationSection s = servers.getConfigurationSection(key);
            if (s == null || s.getBoolean("coming-soon", false)) continue;
            String bungee = s.getString("bungee-server", "");
            if (bungee.isBlank()) continue;
            int slot = Math.max(0, Math.min(26, s.getInt("slot", 0)));
            ItemStack icon = inv.getItem(slot);
            if (icon == null || icon.getType().isAir()) continue;
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) continue;
            boolean serverOnline = stateTracker.isOnline(bungee);
            boolean locked = wipeLock.isLocked(bungee);
            Integer online = serverOnline ? cachedOnline.get(bungee) : -1;
            Integer queue = serverOnline ? cachedQueue.get(bungee) : null;
            List<Component> lc = new ArrayList<>();
            for (String line : loreLinesForServer(s, false, online, queue, serverOnline, locked, admin)) {
                lc.add(legacy.deserialize(line));
            }
            meta.lore(lc);
            hideTooltipNoise(meta);
            icon.setItemMeta(meta);
        }
    }

    // ── Core menu ────────────────────────────────────────────────────────────

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private boolean lobbyFeatures() {
        return "lobby".equalsIgnoreCase(cfg().getString("mode", "lobby"))
            && cfg().getBoolean("lobby-menu.enabled", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!lobbyFeatures()) return;
        Player player = event.getPlayer();
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        giveCompass(player);
    }

    private void giveCompass(Player player) {
        int slot = cfg().getInt("lobby-menu.compass-slot", 4);
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(legacy.deserialize(cfg().getString("lobby-menu.compass-name", "&#FF9900&lСерверы Foxaria")));
        List<String> loreLines = cfg().getStringList("lobby-menu.compass-lore");
        if (!loreLines.isEmpty()) {
            List<Component> cl = new ArrayList<>();
            for (String line : loreLines) cl.add(legacy.deserialize(line));
            meta.lore(cl);
        }
        hideTooltipNoise(meta);
        compass.setItemMeta(meta);
        player.getInventory().setItem(Math.max(0, Math.min(8, slot)), compass);
    }

    private static void hideTooltipNoise(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
            ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!lobbyFeatures()) return;
        if (event.getItem() == null || event.getItem().getType() != Material.COMPASS) return;
        event.setCancelled(true);
        openMenu(event.getPlayer());
    }

    private void openMenu(Player player) {
        boolean admin = isWipeAdmin(player);
        Component title = legacy.deserialize(cfg().getString("lobby-menu.gui-title", "&#FF8800&lСерверы"));
        Inventory inv = Bukkit.createInventory(new LobbyMenuHolder(), 27, title);

        ConfigurationSection servers = cfg().getConfigurationSection("lobby-menu.servers");
        if (servers != null) {
            for (String key : servers.getKeys(false)) {
                ConfigurationSection s = servers.getConfigurationSection(key);
                if (s == null) continue;
                int slot = Math.max(0, Math.min(26, s.getInt("slot", 0)));
                boolean soon = s.getBoolean("coming-soon", false);
                String matName = soon ? s.getString("soon-material", "BARRIER") : s.getString("material", "GRASS_BLOCK");
                Material mat = Material.matchMaterial(matName != null ? matName : "STONE");
                if (mat == null) mat = Material.STONE;

                ItemStack icon = new ItemStack(mat);
                ItemMeta meta = icon.getItemMeta();
                String gradientPlain = s.getString("name-gradient-red", "");
                String displayRaw = (gradientPlain != null && !gradientPlain.isBlank())
                    ? HubGradientUtil.lightRedGradient(gradientPlain.trim())
                    : s.getString("name", "&f" + key);
                meta.displayName(legacy.deserialize(displayRaw));

                String bungee = s.getString("bungee-server", "");
                boolean serverOnline = !soon && !bungee.isBlank() && stateTracker.isOnline(bungee);
                boolean locked = !bungee.isBlank() && wipeLock.isLocked(bungee);

                List<Component> lc = new ArrayList<>();
                for (String line : loreLinesForServer(s, soon, cachedOnline.get(bungee), cachedQueue.get(bungee),
                    serverOnline, locked, admin)) {
                    lc.add(legacy.deserialize(line));
                }
                meta.lore(lc);
                hideTooltipNoise(meta);
                icon.setItemMeta(meta);
                inv.setItem(slot, icon);

                // Initial async count request if server is accessible
                if (!soon && !bungee.isBlank() && serverOnline) {
                    PendingSlotStats pending = new PendingSlotStats();
                    AtomicBoolean applied = new AtomicBoolean(false);
                    String bungeeCapture = bungee;
                    Runnable applyOnce = () -> {
                        if (!applied.compareAndSet(false, true)) return;
                        int o = pending.online != null ? pending.online : -1;
                        int q = pending.queue != null ? pending.queue : -1;
                        cachedOnline.put(bungeeCapture, o);
                        cachedQueue.put(bungeeCapture, q);
                        applyStatsToSlot(player, slot, s, o, q, stateTracker.isOnline(bungeeCapture),
                            wipeLock.isLocked(bungeeCapture), isWipeAdmin(player));
                    };
                    playerCount.request(player, bungee, online -> {
                        pending.online = online;
                        if (pending.queue != null) applyOnce.run();
                    });
                    proxyQueue.request(player, bungee, queue -> {
                        pending.queue = queue;
                        if (pending.online != null) applyOnce.run();
                    });
                    long delay = cfg().getLong("lobby-menu.player-count-timeout-ticks", 45L);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!applied.get()) {
                            if (pending.online == null) pending.online = -1;
                            if (pending.queue == null) pending.queue = -1;
                            applyOnce.run();
                        }
                    }, delay);
                }
            }
        }

        fillMenuBackground(inv);
        placeFooterHelp(inv);
        player.openInventory(inv);
        menuViewers.add(player.getUniqueId());
    }

    private void fillMenuBackground(Inventory inv) {
        String matName = cfg().getString("lobby-menu.filler-material", "GRAY_STAINED_GLASS_PANE");
        Material fillerMat = Material.matchMaterial(matName != null ? matName : "GRAY_STAINED_GLASS_PANE");
        if (fillerMat == null) fillerMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack filler = new ItemStack(fillerMat);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.displayName(Component.empty());
            hideTooltipNoise(fm);
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.getType().isAir()) inv.setItem(i, filler.clone());
        }
    }

    private void placeFooterHelp(Inventory inv) {
        if (!cfg().getBoolean("lobby-menu.footer-help.enabled", true)) return;
        int slot = Math.max(0, Math.min(inv.getSize() - 1, cfg().getInt("lobby-menu.footer-help.slot", 22)));
        String matName = cfg().getString("lobby-menu.footer-help.material", "BOOK");
        Material mat = Material.matchMaterial(matName != null ? matName : "BOOK");
        if (mat == null) mat = Material.BOOK;
        ItemStack book = new ItemStack(mat);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return;
        meta.displayName(legacy.deserialize(cfg().getString("lobby-menu.footer-help.name", "&6&lПодсказки лобби")));
        List<Component> lore = new ArrayList<>();
        for (String line : cfg().getStringList("lobby-menu.footer-help.lore")) lore.add(legacy.deserialize(line));
        meta.lore(lore);
        hideTooltipNoise(meta);
        book.setItemMeta(meta);
        inv.setItem(slot, book);
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof LobbyMenuHolder) {
            menuViewers.remove(event.getPlayer().getUniqueId());
        }
    }

    // ── Lore builders ────────────────────────────────────────────────────────

    private List<String> loreLinesForServer(
        ConfigurationSection s, boolean soon,
        Integer online, Integer queue,
        boolean serverOnline, boolean locked, boolean admin
    ) {
        List<String> lore = new ArrayList<>(s.getStringList("lore"));
        int cap = s.getInt("max-slots", 200);
        String queueNone = cfg().getString("lobby-menu.queue-none-text", "нет");
        lore.add("&8────────────");

        if (soon) {
            lore.add("&7&oСкоро");
            return lore;
        }
        if (!serverOnline) {
            lore.add("&c&lВыключен");
            return lore;
        }
        if (locked) {
            if (admin) {
                String onlinePart = (online == null) ? "&7…" : (online < 0 ? "&8?" : "&a" + online);
                lore.add("&7Онлайн: " + onlinePart + " &8/ &f" + cap);
                lore.add(queueLine(queue, queueNone));
                lore.add("&6\u26a0 &eВайп &8| &fВы можете войти");
            } else {
                lore.add("&c&lВыключен");
            }
            return lore;
        }

        String onlinePart = (online == null) ? "&7…" : (online < 0 ? "&8?" : "&a" + online);
        lore.add("&7Онлайн: " + onlinePart + " &8/ &f" + cap);
        lore.add(queueLine(queue, queueNone));
        lore.add("&aНажми, чтобы подключиться");
        return lore;
    }

    private String queueLine(Integer queue, String queueNone) {
        if (queue == null) return "&7Очередь: &7…";
        if (queue < 0) return "&7Очередь: &8?";
        if (queue == 0) return "&7Очередь: &f" + queueNone;
        return "&7Очередь: &e" + queue;
    }

    private void applyStatsToSlot(
        Player player, int slot, ConfigurationSection s,
        int online, int queue,
        boolean serverOnline, boolean locked, boolean admin
    ) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof LobbyMenuHolder)) return;
        ItemStack icon = player.getOpenInventory().getTopInventory().getItem(slot);
        if (icon == null || icon.getType().isAir()) return;
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return;
        List<Component> lc = new ArrayList<>();
        for (String line : loreLinesForServer(s, false, online, queue, serverOnline, locked, admin)) {
            lc.add(legacy.deserialize(line));
        }
        meta.lore(lc);
        hideTooltipNoise(meta);
        icon.setItemMeta(meta);
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof LobbyMenuHolder)) return;
        // Только клик по верхнему меню — иначе rawSlot совпадает с хотбаром и срабатывает «вайп» несколько раз
        if (event.getClickedInventory() == null
            || !(event.getClickedInventory().getHolder() instanceof LobbyMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        int slot = event.getRawSlot();
        ConfigurationSection servers = cfg().getConfigurationSection("lobby-menu.servers");
        if (servers == null) return;

        for (String key : servers.getKeys(false)) {
            ConfigurationSection s = servers.getConfigurationSection(key);
            if (s == null) continue;
            if (Math.max(0, Math.min(26, s.getInt("slot", 0))) != slot) continue;

            if (s.getBoolean("coming-soon", false)) {
                player.sendMessage(Component.text("Сервер ещё закрыт.", NamedTextColor.YELLOW));
                player.closeInventory();
                return;
            }
            String bungee = s.getString("bungee-server", key);
            boolean admin = isWipeAdmin(player);

            if (!stateTracker.isOnline(bungee)) {
                player.sendMessage(legacy.deserialize("&cСервер сейчас выключен."));
                player.closeInventory();
                return;
            }
            if (wipeLock.isLocked(bungee) && !admin) {
                player.sendMessage(legacy.deserialize("&cСервер заблокирован &8(идёт вайп)&c."));
                player.closeInventory();
                return;
            }
            LobbyBungeeConnect.connect(player, plugin, bungee);
            player.closeInventory();
            return;
        }
    }

    // ── Protection listeners ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!lobbyFeatures() || !cfg().getBoolean("lobby-menu.no-damage", true)) return;
        if (event.getEntity() instanceof Player) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!lobbyFeatures() || !cfg().getBoolean("lobby-menu.no-food-loss", true)) return;
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!lobbyFeatures() || !cfg().getBoolean("lobby-menu.lock-compass", true)) return;
        if (event.getItemDrop().getItemStack().getType() == Material.COMPASS) event.setCancelled(true);
    }

    private static final class PendingSlotStats {
        volatile Integer online;
        volatile Integer queue;
    }
}
