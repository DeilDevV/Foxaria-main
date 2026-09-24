package com.foxaria.core.service;

import com.foxaria.api.event.FoxariaPlayerAuthenticatedEvent;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ServiceRegistry;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.gui.ServerSelectorMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerFlowService implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final ServiceRegistry services;
    private final MenuManager menuManager;
    private final CoreRepository repository;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private final ConcurrentMap<UUID, PlayerStage> stages = new ConcurrentHashMap<>();
    private final EmptyWorldGenerator emptyWorldGenerator = new EmptyWorldGenerator();
    private final NamespacedKey selectorItemKey;
    private volatile World authWorld;
    private volatile World selectorWorld;
    private volatile World mainWorld;
    private volatile boolean proxyAuthHandled;

    public PlayerFlowService(
        JavaPlugin plugin,
        ConfigService configs,
        MessageService messages,
        ServiceRegistry services,
        MenuManager menuManager,
        CoreRepository repository
    ) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.services = services;
        this.menuManager = menuManager;
        this.repository = repository;
        this.selectorItemKey = new NamespacedKey(plugin, "selector-item");
    }

    public void start() {
        proxyAuthHandled = config().getBoolean("proxy-auth-handled", false);
        authWorld = ensureVoidWorld(config().getString("auth.world", "auth_void"));
        selectorWorld = ensureVoidWorld(config().getString("selector.world", "selector_void"));
        mainWorld = ensureMainWorld(config().getString("main.world", "world"));
        prepareSelectorWorld();
        plugin.getLogger().info("Player flow worlds ready: auth=" + authWorld.getName() + ", selector=" + selectorWorld.getName()
            + ", main=" + mainWorld.getName() + ", proxy-auth-handled=" + proxyAuthHandled);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        stages.clear();
    }

    public PlayerStage stage(Player player) {
        return stage(player.getUniqueId());
    }

    public PlayerStage stage(UUID playerUuid) {
        return stages.getOrDefault(playerUuid, PlayerStage.AUTH);
    }

    public boolean isRestrictedStage(Player player) {
        PlayerStage stage = stage(player);
        return stage == PlayerStage.AUTH || stage == PlayerStage.SELECTOR;
    }

    public boolean isMainStage(Player player) {
        return stage(player) == PlayerStage.MAIN;
    }

    public boolean isSelectorStage(Player player) {
        return stage(player) == PlayerStage.SELECTOR;
    }

    public boolean isAuthStage(Player player) {
        return stage(player) == PlayerStage.AUTH;
    }

    public boolean isServerSelectorEnabled() {
        return config().getBoolean("server-selector-enabled", false);
    }

    public void openOrRouteSelector(Player player) {
        if (!isServerSelectorEnabled()) {
            return;
        }
        if (isAuthStage(player)) {
            messages.send(player, "flow.must-auth-first", "&cСначала завершите авторизацию через /register или /login.");
            return;
        }
        if (!isSelectorStage(player)) {
            sendToSelector(player, true, true);
            return;
        }
        openSelectorMenu(player);
    }

    public void openSelectorMenu(Player player) {
        if (!isServerSelectorEnabled()) {
            return;
        }
        menuManager.open(player, new ServerSelectorMenu(this));
        player.sendActionBar(serializer.deserialize(config().getString("selector.actionbar-open", "&6Открыт выбор сервера.")));
    }

    public String selectorMenuTitle() {
        return config().getString("selector.menu-title", "&6Выбор сервера");
    }

    public List<ServerDestination> destinations() {
        ConfigurationSection section = config().getConfigurationSection("destinations");
        if (section == null) {
            return List.of();
        }
        List<ServerDestination> destinations = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null || !entry.getBoolean("enabled", true)) {
                continue;
            }
            String materialName = entry.getString("material", "NETHER_STAR");
            Material material = Material.matchMaterial(materialName);
            int slot = Math.max(0, Math.min(26, entry.getInt("slot", 13)));
            List<String> description = new ArrayList<>(entry.getStringList("description"));
            destinations.add(new ServerDestination(
                id,
                entry.getString("name", "&6" + id),
                material == null ? Material.NETHER_STAR : material,
                slot,
                description,
                entry.getString("world", config().getString("main.world", "world")),
                entry.getBoolean("use-last-location", true),
                resolveLocation("destinations." + id + ".spawn", ensureMainWorld(entry.getString("world", config().getString("main.world", "world"))))
            ));
        }
        destinations.sort(Comparator.comparingInt(ServerDestination::slot));
        return destinations;
    }

    public void sendToMain(Player player, String destinationId) {
        ServerDestination destination = destinations().stream()
            .filter(entry -> entry.id().equalsIgnoreCase(destinationId))
            .findFirst()
            .orElse(null);
        if (destination == null) {
            messages.send(player, "flow.destination-missing", "&cЭтот сервер сейчас недоступен.");
            return;
        }

        World destinationWorld = ensureMainWorld(destination.worldName());
        repository.loadEntryPoint(player.getUniqueId()).exceptionally(ignored -> Optional.empty())
            .thenAccept(optionalEntry -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Location target = resolveDestinationLocation(destination, destinationWorld, optionalEntry);
                stages.put(player.getUniqueId(), PlayerStage.MAIN);
                player.closeInventory();
                removeSelectorItems(player.getInventory());
                player.setGameMode(GameMode.SURVIVAL);
                player.teleportAsync(target);
                showTitle(
                    player,
                    config().getString("main.title.title", "&aВход на сервер"),
                    config().getString("main.title.subtitle", "&fДобро пожаловать в основной мир")
                );
                player.sendActionBar(serializer.deserialize(config().getString("main.actionbar", "&aВы вошли на основной сервер.")));
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        refreshUi(player);
                        refreshSidebar(player);
                    }
                }, 5L);
            }));
    }

    public ItemStack createSelectorItem() {
        Material material = Material.matchMaterial(config().getString("selector.item.material", "NETHER_STAR"));
        ItemStack itemStack = new ItemStack(material == null ? Material.NETHER_STAR : material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(serializer.deserialize(config().getString("selector.item.name", "&6Выбор сервера")));
        List<Component> lore = new ArrayList<>();
        for (String line : config().getStringList("selector.item.lore")) {
            lore.add(serializer.deserialize(line));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(selectorItemKey, PersistentDataType.BYTE, (byte) 1);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public boolean isSelectorItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer data = itemStack.getItemMeta().getPersistentDataContainer();
        Byte marker = data.get(selectorItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public void syncItems(Player player) {
        if (!isServerSelectorEnabled()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        removeSelectorItems(inventory);
        if (isMainStage(player)) {
            return;
        }
        if (!isSelectorStage(player) || !config().getBoolean("selector.item.enabled", true)) {
            return;
        }
        if (hasSelectorItem(inventory)) {
            return;
        }

        int slot = Math.max(0, Math.min(8, config().getInt("selector.item.slot", 4)));
        ItemStack selectorItem = createSelectorItem();
        ItemStack slotItem = inventory.getItem(slot);
        if (slotItem == null || slotItem.getType().isAir()) {
            inventory.setItem(slot, selectorItem);
            return;
        }
        int emptySlot = inventory.firstEmpty();
        if (emptySlot >= 0) {
            inventory.setItem(emptySlot, selectorItem);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (proxyAuthHandled) {
            stages.put(player.getUniqueId(), PlayerStage.MAIN);
            if (!canKeepGamemode(player)) {
                player.setGameMode(GameMode.SURVIVAL);
            }
            refreshUi(player);
            refreshSidebar(player);
            return;
        }
        stages.put(player.getUniqueId(), PlayerStage.AUTH);
        player.closeInventory();
        player.teleportAsync(resolveLocation("auth.spawn", ensureAuthWorld()));
        player.setGameMode(GameMode.SPECTATOR);
        player.setFallDistance(0.0F);
        player.setFlying(true);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        refreshUi(player);
        refreshSidebar(player);
    }

    @EventHandler
    public void onAuthenticated(FoxariaPlayerAuthenticatedEvent event) {
        if (proxyAuthHandled) {
            return;
        }
        sendToSelector(event.player(), event.returningPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isMainStage(player) && !isFlowWorld(player.getWorld())) {
            repository.saveEntryPoint(player.getUniqueId(), player.getLocation());
        }
        stages.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isSelectorStage(event.getPlayer())) {
            return;
        }
        if (isAllowedSelectorCommand(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "flow.selector-command-blocked", "&cСначала выберите сервер через /server или меню.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isRestrictedStage(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isRestrictedStage(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isRestrictedStage(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isRestrictedStage(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isRestrictedStage(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isRestrictedStage(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player player = resolvePlayer(event.getDamager());
        if (player != null && isRestrictedStage(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isRestrictedStage(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        }
    }

    private void sendToSelector(Player player, boolean returningPlayer, boolean saveCurrentLocation) {
        if (!isServerSelectorEnabled()) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        if (saveCurrentLocation && isMainStage(player) && !isFlowWorld(player.getWorld())) {
            repository.saveEntryPoint(player.getUniqueId(), player.getLocation());
        }

        stages.put(player.getUniqueId(), PlayerStage.SELECTOR);
        player.closeInventory();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0.0F);
        player.teleportAsync(resolveLocation("selector.spawn", ensureSelectorWorld()));
        showTitle(
            player,
            config().getString("selector.title.title", "&6Выбор сервера"),
            config().getString("selector.title.subtitle", returningPlayer ? "&fАвторизация завершена, выберите сервер" : "&fРегистрация завершена, выберите сервер")
        );
        player.sendActionBar(serializer.deserialize(config().getString("selector.actionbar", "&eВыберите сервер для входа.")));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isSelectorStage(player)) {
                return;
            }
            syncItems(player);
            openSelectorMenu(player);
            refreshUi(player);
            refreshSidebar(player);
        }, Math.max(1L, config().getLong("selector.auto-open-delay-ticks", 15L)));
    }

    private Location resolveDestinationLocation(ServerDestination destination, World destinationWorld, Optional<Location> savedEntryPoint) {
        if (destination.useLastLocation() && savedEntryPoint.isPresent()) {
            Location stored = savedEntryPoint.get();
            if (stored.getWorld() != null && stored.getWorld().getName().equalsIgnoreCase(destinationWorld.getName())) {
                return stored;
            }
        }
        if (destination.configuredSpawn() != null && destination.configuredSpawn().getWorld() != null) {
            return destination.configuredSpawn();
        }
        return destinationWorld.getSpawnLocation().clone().add(0.5D, 0.0D, 0.5D);
    }

    private World ensureAuthWorld() {
        if (authWorld == null) {
            authWorld = ensureVoidWorld(config().getString("auth.world", "auth_void"));
        }
        return authWorld;
    }

    private World ensureSelectorWorld() {
        if (selectorWorld == null) {
            selectorWorld = ensureVoidWorld(config().getString("selector.world", "selector_void"));
            prepareSelectorWorld();
        }
        return selectorWorld;
    }

    private World ensureMainWorld(String worldName) {
        if (mainWorld != null && mainWorld.getName().equalsIgnoreCase(worldName)) {
            return mainWorld;
        }
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            if (worldName.equalsIgnoreCase(config().getString("main.world", "world"))) {
                mainWorld = existing;
            }
            return existing;
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(true);
        World created = creator.createWorld();
        if (worldName.equalsIgnoreCase(config().getString("main.world", "world"))) {
            mainWorld = created;
        }
        return created == null ? Bukkit.getWorlds().get(0) : created;
    }

    private World ensureVoidWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            configureVoidWorld(world);
            return world;
        }
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.generator(emptyWorldGenerator);
        World created = creator.createWorld();
        if (created == null) {
            throw new IllegalStateException("Не удалось создать служебный мир: " + worldName);
        }
        configureVoidWorld(created);
        return created;
    }

    private void configureVoidWorld(World world) {
        world.setAutoSave(true);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
        world.setTime(config().getLong("void-world.time", 6000L));
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
    }

    private void prepareSelectorWorld() {
        World world = ensureSelectorWorld();
        Location spawn = resolveLocation("selector.spawn", world);
        int centerX = spawn.getBlockX();
        int centerY = config().getInt("selector.platform.y", Math.max(64, spawn.getBlockY() - 1));
        int centerZ = spawn.getBlockZ();
        int radius = Math.max(2, config().getInt("selector.platform.radius", 4));
        Material floor = Material.matchMaterial(config().getString("selector.platform.material", "SMOOTH_STONE"));
        Material border = Material.matchMaterial(config().getString("selector.platform.border-material", "GLASS"));
        Material floorMaterial = floor == null ? Material.SMOOTH_STONE : floor;
        Material borderMaterial = border == null ? Material.GLASS : border;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                boolean edge = x == centerX - radius || x == centerX + radius || z == centerZ - radius || z == centerZ + radius;
                world.getBlockAt(x, centerY, z).setType(edge ? borderMaterial : floorMaterial, false);
                world.getBlockAt(x, centerY + 1, z).setType(Material.AIR, false);
                world.getBlockAt(x, centerY + 2, z).setType(Material.AIR, false);
            }
        }
    }

    private Location resolveLocation(String path, World fallbackWorld) {
        String worldName = config().getString(path + ".world", fallbackWorld.getName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = fallbackWorld;
        }
        double x = config().getDouble(path + ".x", 0.5D);
        double y = config().getDouble(path + ".y", path.startsWith("selector") ? 65.0D : 100.0D);
        double z = config().getDouble(path + ".z", 0.5D);
        float yaw = (float) config().getDouble(path + ".yaw", 0.0D);
        float pitch = (float) config().getDouble(path + ".pitch", 0.0D);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private boolean hasSelectorItem(PlayerInventory inventory) {
        for (ItemStack itemStack : inventory.getContents()) {
            if (isSelectorItem(itemStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedSelectorCommand(String rawMessage) {
        String command = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int separator = command.indexOf(' ');
        if (separator >= 0) {
            command = command.substring(0, separator);
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        return config().getStringList("selector.allowed-commands").stream()
            .anyMatch(entry -> normalized.equalsIgnoreCase(entry));
    }

    private boolean isFlowWorld(World world) {
        if (world == null) {
            return false;
        }
        String authWorldName = config().getString("auth.world", "auth_void");
        String selectorWorldName = config().getString("selector.world", "selector_void");
        return world.getName().equalsIgnoreCase(authWorldName) || world.getName().equalsIgnoreCase(selectorWorldName);
    }

    private void refreshUi(Player player) {
        PlayerUiService playerUiService = services.optional(PlayerUiService.class);
        if (playerUiService != null) {
            playerUiService.refresh(player);
        }
    }

    private void refreshSidebar(Player player) {
        SidebarService sidebarService = services.optional(SidebarService.class);
        if (sidebarService != null) {
            sidebarService.refresh(player);
        }
    }

    private void removeSelectorItems(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (isSelectorItem(current)) {
                inventory.setItem(slot, null);
            }
        }
    }

    private void showTitle(Player player, String title, String subtitle) {
        Title.Times times = Title.Times.times(Duration.ofMillis(250L), Duration.ofMillis(1800L), Duration.ofMillis(400L));
        player.showTitle(Title.title(serializer.deserialize(title), serializer.deserialize(subtitle), times));
    }

    private Player resolvePlayer(Entity entity) {
        return entity instanceof Player player ? player : null;
    }

    private FileConfiguration config() {
        return configs.module("modules/player-flow.yml");
    }

    private boolean canKeepGamemode(Player player) {
        return player.isOp() || player.hasPermission("foxaria.gamemode.keep");
    }

    public record ServerDestination(
        String id,
        String displayName,
        Material material,
        int slot,
        List<String> description,
        String worldName,
        boolean useLastLocation,
        Location configuredSpawn
    ) {
    }
}
