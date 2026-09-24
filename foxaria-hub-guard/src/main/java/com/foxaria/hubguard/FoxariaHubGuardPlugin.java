package com.foxaria.hubguard;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

public final class FoxariaHubGuardPlugin extends JavaPlugin implements Listener {

    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    private HubRankBridge hubRank;
    private BungeePlayerCountHandler bungeePlayerCount;
    private FoxariaProxyQueueHandler foxariaProxyQueue;
    private ServerStateTracker serverStateTracker;
    private WipeLockRegistry wipeLockRegistry;
    private LobbyMenuListener lobbyMenuListener;

    public HubRankBridge hubRank() {
        return hubRank;
    }

    @Override
    public void onLoad() {
        sanitizeLocalConfigYaml();
    }

    /**
     * SnakeYAML 2 отклоняет U+0098; битая перекодировка в config.yml ломала загрузку. Убираем BOM и этот символ до {@link #onEnable()}.
     */
    private void sanitizeLocalConfigYaml() {
        File f = new File(getDataFolder(), "config.yml");
        if (!f.isFile()) {
            return;
        }
        try {
            byte[] raw = Files.readAllBytes(f.toPath());
            int start = 0;
            if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
                start = 3;
            }
            String text = new String(raw, start, raw.length - start, StandardCharsets.UTF_8);
            String fixed = text.replace("\u0098", "");
            if (!fixed.equals(text) || start > 0) {
                Files.writeString(f.toPath(), fixed, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.hubRank = new HubRankBridge(this);
        this.wipeLockRegistry = new WipeLockRegistry();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new SimpleChatFormatListener(hubRank), this);
        LobbyBungeeConnect.register(this);
        boolean rankBridge = getConfig().getBoolean("rank-bridge.enabled", false);
        boolean lobbyMenu = "lobby".equalsIgnoreCase(getConfig().getString("mode", "lobby"))
            && getConfig().getBoolean("lobby-menu.enabled", true);
        this.foxariaProxyQueue = new FoxariaProxyQueueHandler(this, wipeLockRegistry);
        getServer().getMessenger().registerIncomingPluginChannel(this, "foxaria:proxy", foxariaProxyQueue);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "foxaria:proxy");
        if (lobbyMenu) {
            this.bungeePlayerCount = new BungeePlayerCountHandler(this);
            this.serverStateTracker = new ServerStateTracker(this);
            serverStateTracker.start();
            getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", bungeePlayerCount);
            this.lobbyMenuListener = new LobbyMenuListener(
                this, bungeePlayerCount, foxariaProxyQueue, serverStateTracker, wipeLockRegistry);
            getServer().getPluginManager().registerEvents(lobbyMenuListener, this);
            lobbyMenuListener.startRefreshTask();
        }
        WipeCommand wipeCommand = new WipeCommand(this, wipeLockRegistry);
        getServer().getPluginManager().registerEvents(wipeCommand, this);
        var wipeCmd = getCommand("wipe");
        if (wipeCmd != null) {
            wipeCmd.setExecutor(wipeCommand);
            wipeCmd.setTabCompleter(wipeCommand);
        }
    }

    @Override
    public void onDisable() {
        if (lobbyMenuListener != null) {
            lobbyMenuListener.stopRefreshTask();
            lobbyMenuListener = null;
        }
        if (serverStateTracker != null) {
            serverStateTracker.stop();
            serverStateTracker = null;
        }
        if (bungeePlayerCount != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, "BungeeCord", bungeePlayerCount);
            bungeePlayerCount = null;
        }
        if (foxariaProxyQueue != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, "foxaria:proxy", foxariaProxyQueue);
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, "foxaria:proxy");
            foxariaProxyQueue = null;
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        if (cfg().getBoolean("messages.hide-vanilla-join-quit", true)) {
            event.setJoinMessage(null);
        }
        Player player = event.getPlayer();
        if (cfg().getBoolean("rank-bridge.enabled", false)) {
            hubRank.warmCache(player);
            if (foxariaProxyQueue != null) {
                Bukkit.getScheduler().runTaskLater(this, () -> requestChatPrefixFromProxy(player), 1L);
            }
        }
        if (foxariaProxyQueue != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> foxariaProxyQueue.requestWipeLockSync(player), 2L);
        }
        if (!isProtected(player)) {
            return;
        }
        if (isAuthMode()) {
            player.setGameMode(GameMode.SPECTATOR);
            scheduleAuthSpawn(player);
            return;
        }
        if (cfg().getBoolean("restrictions.force-gamemode-adventure", true)) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        if (cfg().getBoolean("messages.hide-vanilla-join-quit", true)) {
            event.setQuitMessage(null);
        }
        hubRank.forget(event.getPlayer());
        if (bungeePlayerCount != null) {
            bungeePlayerCount.clearPlayer(event.getPlayer());
        }
        if (foxariaProxyQueue != null) {
            foxariaProxyQueue.clearPlayer(event.getPlayer());
        }
    }

    private void scheduleAuthSpawn(Player player) {
        if (!cfg().getBoolean("auth.spawn.enabled", true)) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> teleportAuthSpawn(player));
    }

    private void teleportAuthSpawn(Player player) {
        if (!player.isOnline()) {
            return;
        }
        String worldName = cfg().getString("auth.spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("auth.spawn: мир не найден: " + worldName);
            return;
        }
        double x = cfg().getDouble("auth.spawn.x", 0.5);
        double z = cfg().getDouble("auth.spawn.z", 0.5);
        double y = cfg().getDouble("auth.spawn.y", -1.0);
        if (y < 0) {
            int max = world.getMaxHeight() - 2;
            y = Math.min(cfg().getDouble("auth.spawn.y-max-fallback", 320.0), max);
        }
        float yaw = (float) cfg().getDouble("auth.spawn.yaw", 0.0);
        float pitch = (float) cfg().getDouble("auth.spawn.pitch", 0.0);
        Location loc = new Location(world, x, y, z, yaw, pitch);
        player.teleport(loc);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (cfg().getBoolean("restrictions.block-place-break", true) && isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (cfg().getBoolean("restrictions.block-place-break", true) && isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!cfg().getBoolean("restrictions.block-interact", true) || !isProtected(event.getPlayer())) {
            return;
        }
        if (allowLobbyCompassMenu(event)) {
            return;
        }
        event.setCancelled(true);
    }

    /** Компас в лобби обрабатывает {@link LobbyMenuListener} — здесь не отменяем взаимодействие. */
    private boolean allowLobbyCompassMenu(PlayerInteractEvent event) {
        if (!"lobby".equalsIgnoreCase(cfg().getString("mode", "lobby")) || !cfg().getBoolean("lobby-menu.enabled", true)) {
            return false;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.COMPASS) {
            return false;
        }
        Action a = event.getAction();
        return a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
            && cfg().getBoolean("restrictions.inventory-interact", true)
            && isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
            && cfg().getBoolean("restrictions.inventory-interact", true)
            && isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (cfg().getBoolean("restrictions.drop-pickup", true) && isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
            && cfg().getBoolean("restrictions.drop-pickup", true)
            && isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
            && cfg().getBoolean("restrictions.pvp", true)
            && isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!cfg().getBoolean("restrictions.pvp", true)) {
            return;
        }
        Player damager = asPlayer(event.getDamager());
        if (damager != null && isProtected(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isProtected(event.getPlayer())) {
            return;
        }
        String raw = event.getMessage().toLowerCase(Locale.ROOT);
        if (isAuthMode()) {
            if (raw.startsWith("/help") || raw.startsWith("/lobbyhelp")
                || raw.startsWith("/login") || raw.startsWith("/l")
                || raw.startsWith("/register") || raw.startsWith("/r")) {
                return;
            }
            event.setCancelled(true);
            return;
        }
        if (raw.startsWith("/help") || raw.startsWith("/lobbyhelp") || raw.startsWith("/server") || raw.startsWith("/menu")) {
            return;
        }
        if (raw.startsWith("/wipe") && WipeCommand.isAdmin(event.getPlayer(), hubRank, this)) {
            return;
        }
        event.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("help") && !command.getName().equalsIgnoreCase("lobbyhelp")) {
            return false;
        }
        send(sender, cfg().getString("help.header", "&6&lFOXARIA &8» &fКоманды"));
        List<String> lines = isAuthMode()
            ? List.of("&e/register <пароль> <повтор> &7| &e/r", "&e/login <пароль> &7| &e/l")
            : cfg().getStringList("help.player-lines");
        for (String line : lines) {
            send(sender, line);
        }
        if (sender.hasPermission("minecraft.command.op") || sender.isOp()) {
            for (String line : cfg().getStringList("help.admin-lines")) {
                send(sender, line);
            }
        }
        return true;
    }

    private Player asPlayer(Entity entity) {
        return entity instanceof Player p ? p : null;
    }

    /** Прокси отвечает {@code ChatPrefixSync}; без общего sqlite на лобби/auth префикс всё равно совпадает с табом. */
    private void requestChatPrefixFromProxy(Player player) {
        if (foxariaProxyQueue == null || player == null || !player.isOnline()) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("RequestChatPrefix");
        player.sendPluginMessage(this, "foxaria:proxy", out.toByteArray());
    }

    private boolean isProtected(Player player) {
        return !player.isOp();
    }

    private FileConfiguration cfg() {
        return getConfig();
    }

    private boolean isAuthMode() {
        return "auth".equalsIgnoreCase(cfg().getString("mode", "lobby"));
    }

    private void send(CommandSender sender, String legacy) {
        sender.sendMessage(serializer.deserialize(legacy));
    }
}
