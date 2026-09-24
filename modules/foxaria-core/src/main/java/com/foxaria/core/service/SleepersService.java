package com.foxaria.core.service;

import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.DatabaseGateway;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sleepers for anarchy: when player disconnects, their body stays as a killable entity with full loot.
 * Anti-dupe invariant: items exist in exactly one place:
 * - stored in DB while sleeper is ACTIVE, then either restored to player OR dropped on death.
 */
public final class SleepersService implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final SleepersRepository repo;
    private final TeleportService teleport;
    private final NamespacedKey sleeperKey;
    private final NamespacedKey sleeperKindKey;
    private final FakePlayerSleeper fakePlayers;

    public SleepersService(JavaPlugin plugin, ConfigService configs, DatabaseGateway db, TeleportService teleport) {
        this.plugin = plugin;
        this.configs = configs;
        this.repo = new SleepersRepository(db);
        this.teleport = teleport;
        this.sleeperKey = new NamespacedKey(plugin, "sleeper-owner");
        this.sleeperKindKey = new NamespacedKey(plugin, "sleeper-kind");
        this.fakePlayers = new FakePlayerSleeper(plugin);
    }

    private final Map<UUID, Long> healthSyncThrottle = new ConcurrentHashMap<>();
    private final Set<String> lootClaims = ConcurrentHashMap.newKeySet();

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        fakePlayers.setDebug(configs.main().getBoolean("sleepers.debug", false));
        plugin.getServer().getScheduler().runTask(plugin, this::spawnAllActive);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    private boolean enabled() {
        return configs.main().getBoolean("sleepers.enabled", true);
    }

    private String worldName() {
        return configs.main().getString("sleepers.world", configs.main().getString("rtp.world", "world"));
    }

    private void spawnAllActive() {
        if (!enabled()) return;
        repo.activeSleepersAsync().thenAccept(rows -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (SleepersRepository.SleeperRow row : rows) {
                World w = Bukkit.getWorld(row.world());
                if (w == null) continue;
                spawnEntityIfMissing(row, w);
            }
        }));
    }

    private void spawnEntityIfMissing(SleepersRepository.SleeperRow row, World w) {
        UUID zombieId = row.zombieUuid() != null ? row.zombieUuid() : row.entityUuid();
        if (zombieId != null) {
            Entity existing = Bukkit.getEntity(zombieId);
            if (existing != null && !existing.isDead()) {
                // ensure visual exists
                if (row.armorUuid() == null || Bukkit.getEntity(row.armorUuid()) == null) {
                    ArmorStand a = spawnCorpseVisual(row.toLocation(w), row.playerUuid(), row.nameLineLegacy(), row.health());
                    repo.upsertSleeper(row.playerUuid(), row.playerName(), row.toLocation(w), zombieId, a.getUniqueId(), row.health(), row.nameLineLegacy());
                }
                fakePlayers.spawn(row.toLocation(w), row.playerUuid(), row.playerName(), null);
                return;
            }
        }
        Location loc = snapToGround(row.toLocation(w));
        Zombie z = (Zombie) w.spawnEntity(loc, EntityType.ZOMBIE);
        configureSleeperZombie(z, row.playerUuid(), row.playerName(), row.health());
        if (!fakePlayers.spawn(loc, row.playerUuid(), row.playerName(), null)) {
            z.setInvisible(false);
            applySkullToZombie(z, row.playerUuid(), null);
        }
        ArmorStand a = spawnCorpseVisual(loc, row.playerUuid(), row.nameLineLegacy(), row.health());
        repo.upsertSleeper(row.playerUuid(), row.playerName(), loc, z.getUniqueId(), a.getUniqueId(), row.health(), row.nameLineLegacy());
        applyVisualEquipmentAsync(row.playerUuid(), z);
    }

    private void configureSleeperZombie(Zombie z, UUID owner, String name, double health) {
        z.setAI(false);
        z.setSilent(true);
        z.setAdult();
        z.setRemoveWhenFarAway(false);
        z.setPersistent(true);
        z.setCanPickupItems(false);
        z.setFireTicks(0);
        z.setBaby(false);
        // Prevent burning in sunlight.
        try {
            Zombie.class.getMethod("setShouldBurnInDay", boolean.class).invoke(z, false);
        } catch (Exception ignored) {}
        keepExtinguished(z);
        z.getPersistentDataContainer().set(sleeperKey, PersistentDataType.STRING, owner.toString());
        z.getPersistentDataContainer().set(sleeperKindKey, PersistentDataType.STRING, "hitbox");
        // Invisible hitbox — the FakePlayerSleeper handles the visual layer
        z.setInvisible(true);
        z.setCollidable(true);
        z.setHealth(Math.max(1.0, Math.min(z.getMaxHealth(), health)));
    }

    private void keepExtinguished(Zombie z) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (z == null || z.isDead() || !z.isValid()) {
                task.cancel();
                return;
            }
            if (z.getFireTicks() > 0) {
                z.setFireTicks(0);
            }
        }, 1L, 20L);
    }

    /**
     * Spawns the name-tag ArmorStand for a sleeper.
     * The stand is invisible+marker — it only shows the floating health+name tag above the zombie.
     */
    private ArmorStand spawnCorpseVisual(Location base, UUID owner, String nameLineLegacy, double health) {
        return spawnCorpseVisual(base, owner, nameLineLegacy, health, null);
    }

    private ArmorStand spawnCorpseVisual(Location base, UUID owner, String nameLineLegacy, double health, PlayerProfile ignored) {
        // Name tag floats ~1.6 blocks above the lying zombie (zombie height when swimming ≈ 0.6)
        Location tagLoc = base.clone().add(0, 1.6, 0);
        ArmorStand a = (ArmorStand) base.getWorld().spawnEntity(tagLoc, EntityType.ARMOR_STAND);
        a.setGravity(false);
        a.setSmall(true);
        a.setBasePlate(false);
        a.setArms(false);
        a.setInvulnerable(true);
        a.setPersistent(true);
        a.setRemoveWhenFarAway(false);
        a.setCanPickupItems(false);
        a.setMarker(true);       // No hitbox, no interactions
        a.setVisible(false);     // Invisible body — only custom name shows
        a.getPersistentDataContainer().set(sleeperKey, PersistentDataType.STRING, owner.toString());
        a.getPersistentDataContainer().set(sleeperKindKey, PersistentDataType.STRING, "visual");
        updateVisualName(a, nameLineLegacy, health);
        a.setCustomNameVisible(true);
        return a;
    }

    /** Apply player skull (with skin) to the zombie's helmet slot. */
    private void applySkullToZombie(Zombie z, UUID owner, PlayerProfile profile) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            if (profile != null) {
                meta.setOwnerProfile(profile);
            } else {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            }
            skull.setItemMeta(meta);
        }
        z.getEquipment().setHelmet(skull, true);
    }

    private void updateVisualName(ArmorStand a, String nameLineLegacy, double health) {
        String base = (nameLineLegacy == null || nameLineLegacy.isBlank())
            ? (configs.main().getString("sleepers.name-format", "&cСлиппер &8| &f") + "Игрок")
            : nameLineLegacy;
        int hp = (int) Math.ceil(Math.max(0, health));
        String line = ChatColor.translateAlternateColorCodes('&', base + " &8| &c" + hp + "❤");
        a.setCustomName(line);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled()) return;
        Player player = event.getPlayer();
        if (configs.main().getBoolean("sleepers.only-main-world", true)) {
            if (player.getWorld() == null || !player.getWorld().getName().equalsIgnoreCase(worldName())) {
                return;
            }
        }

        // Если игрок уже "умирал" как sleeper и мы ещё не восстановили — не создаём новый.
        if (repo.activeSleeper(player.getUniqueId()).isPresent()) return;

        Location loc = snapToGround(player.getLocation());

        FoxariaPermissionService perms = servicesOptionalPerms();
        String prefix = perms != null ? perms.rankPrefixForChat(player) : "";
        // Start with name without guild suffix — guild lookup is async, will update the visual after.
        String baseNameLine = (prefix == null || prefix.isBlank())
            ? ("&f" + player.getName())
            : (prefix + " &7" + player.getName());

        // Capture skin profile while player is still online (ensures skin texture is available).
        PlayerProfile profile = player.getPlayerProfile();

        final UUID playerUuid = player.getUniqueId();
        final String playerName = player.getName();
        final double health = player.getHealth();

        // Снимок всего инвентаря (0..40 как Bukkit отдаёт).
        ItemStack[] snapshot = player.getInventory().getContents();

        // Invisible Zombie — hitbox only (damage, death, interaction detection)
        Zombie z = (Zombie) player.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        configureSleeperZombie(z, playerUuid, playerName, health);

        // Fake Player entity (NMS) — full player skin + SWIMMING pose (lying flat, RustMe style)
        boolean fakeSpawned = fakePlayers.spawn(loc, playerUuid, playerName, profile);
        if (!fakeSpawned) {
            // Fallback: visible zombie with skull + armor
            z.setInvisible(false);
            applySkullToZombie(z, playerUuid, profile);
            applyVisualEquipmentFromSnapshot(z, snapshot);
        }

        // Floating name-tag ArmorStand (marker, invisible body)
        ArmorStand a = spawnCorpseVisual(loc, playerUuid, baseNameLine, health);

        // Clear old items then save fresh snapshot (anti-dupe: items are in DB only).
        repo.clearItems(playerUuid);
        repo.upsertSleeper(playerUuid, playerName, loc, z.getUniqueId(), a.getUniqueId(), health, baseNameLine);
        repo.saveItems(playerUuid, snapshot);

        // Anti-dupe: чистим инвентарь игрока на выходе.
        player.getInventory().clear();
        player.updateInventory();

        // Async guild suffix update — ONLY updates name_line in DB, does NOT touch items.
        resolveGuildSuffixAsync(playerUuid).thenAccept(guildSuffix -> {
            if (guildSuffix.isEmpty()) return;
            String fullName = baseNameLine + guildSuffix.get();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!a.isValid() || a.isDead()) return;
                updateVisualName(a, fullName, health);
                // Only update name_line, never touches fx_sleeper_items
                repo.upsertSleeper(playerUuid, playerName, loc, z.getUniqueId(), a.getUniqueId(), health, fullName);
            });
        });
    }

    private FoxariaPermissionService servicesOptionalPerms() {
        try {
            // Foxaria uses its own ServiceRegistry, not Bukkit ServicesManager.
            // TeleportService already has registry access; we reuse that through reflection here to avoid extra wiring.
            java.lang.reflect.Field f = TeleportService.class.getDeclaredField("services");
            f.setAccessible(true);
            Object registry = f.get(teleport);
            if (registry instanceof com.foxaria.api.service.ServiceRegistry sr) {
                return sr.optional(FoxariaPermissionService.class);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private CompletableFuture<Optional<String>> resolveGuildSuffixAsync(UUID playerUuid) {
        try {
            java.lang.reflect.Field f = TeleportService.class.getDeclaredField("services");
            f.setAccessible(true);
            Object registry = f.get(teleport);
            if (!(registry instanceof com.foxaria.api.service.ServiceRegistry sr)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            Class<?> guildServiceClz = Class.forName("com.foxaria.guilds.GuildService");
            Object guildService = sr.optional(guildServiceClz);
            if (guildService == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            Object rawFuture = guildServiceClz.getMethod("guildOf", UUID.class).invoke(guildService, playerUuid);
            if (!(rawFuture instanceof CompletableFuture<?> cf)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            final Class<?> gsClz = guildServiceClz;
            final Object gs = guildService;
            @SuppressWarnings("unchecked")
            CompletableFuture<Optional<String>> result = ((CompletableFuture<Object>) cf).thenApply(opt -> {
                if (!(opt instanceof Optional<?> o) || o.isEmpty()) return Optional.<String>empty();
                try {
                    Object guildRecord = o.get();
                    String name = (String) guildRecord.getClass().getMethod("name").invoke(guildRecord);
                    Object tagColor = guildRecord.getClass().getMethod("tagColor").invoke(guildRecord);
                    String colorLegacy = (String) gsClz.getMethod("legacyColorForGuildTag", tagColor.getClass()).invoke(gs, tagColor);
                    String c = (colorLegacy == null || colorLegacy.isBlank()) ? "&f" : colorLegacy;
                    return Optional.of("&8[" + c + name + "&8]");
                } catch (Exception ignored) {
                    return Optional.<String>empty();
                }
            });
            return result;
        } catch (Exception ignored) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) return;
        Player player = event.getPlayer();
        // Send all active fake player sleepers to this newly connected player.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            repo.activeSleepersAsync().thenAccept(rows -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (SleepersRepository.SleeperRow row : rows) {
                    if (!player.isOnline() || row.playerUuid().equals(player.getUniqueId())) continue;
                    World w = Bukkit.getWorld(row.world());
                    if (w == null) continue;
                    fakePlayers.sendToPlayer(player, row.playerUuid(), row.toLocation(w), row.playerName(), null);
                }
            }));
        }, 20L);
        // If sleeper was killed while offline: player must join "dead".
        repo.sleeperAnyAsync(player.getUniqueId()).thenAccept(any -> {
            if (any.isPresent() && "KILLED".equalsIgnoreCase(any.get().state())) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.setHealth(0.0);
                    }
                }, 1L);
                return;
            }
            repo.activeSleeperAsync(player.getUniqueId()).thenAccept(active -> {
                if (active.isEmpty()) return;
                SleepersRepository.SleeperRow row = active.get();
                repo.loadItemsAsync(player.getUniqueId(), 41).thenAccept(items -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        // Remove entities if exist
                        UUID zid = row.zombieUuid() != null ? row.zombieUuid() : row.entityUuid();
                        if (zid != null) {
                            Entity e = Bukkit.getEntity(zid);
                            if (e != null) e.remove();
                        }
                        if (row.armorUuid() != null) {
                            Entity e = Bukkit.getEntity(row.armorUuid());
                            if (e != null) e.remove();
                        }
                        // Remove the fake player visual entity (NMS)
                        fakePlayers.remove(player.getUniqueId());

                        // Restore inventory
                        player.getInventory().clear();
                        player.getInventory().setContents(items);
                        player.updateInventory();
                        // health sync: player joins with sleeper HP
                        double hp = Math.max(1.0, Math.min(player.getMaxHealth(), row.health()));
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline()) {
                                player.setHealth(hp);
                            }
                        }, 1L);
                        repo.markRestored(player.getUniqueId());
                        healthSyncThrottle.remove(player.getUniqueId());
                    });
                });
            });
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSleeperDamage(EntityDamageEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof Zombie z)) return;
        String owner = z.getPersistentDataContainer().get(sleeperKey, PersistentDataType.STRING);
        String kind = z.getPersistentDataContainer().get(sleeperKindKey, PersistentDataType.STRING);
        if (owner == null || !"hitbox".equals(kind)) return;
        UUID ownerUuid;
        try { ownerUuid = UUID.fromString(owner); } catch (Exception ex) { return; }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (z.isDead()) return;
            long now = System.currentTimeMillis();
            Long prev = healthSyncThrottle.get(ownerUuid);
            if (prev != null && now - prev < 2000L) return;
            healthSyncThrottle.put(ownerUuid, now);
            double hp = Math.max(0, z.getHealth());
            repo.updateHealth(ownerUuid, hp);
            // update visual name if present
            repo.activeSleeperAsync(ownerUuid).thenAccept(active -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (active.isPresent() && active.get().armorUuid() != null) {
                    Entity e = Bukkit.getEntity(active.get().armorUuid());
                    if (e instanceof ArmorStand a) {
                        updateVisualName(a, active.get().nameLineLegacy(), hp);
                    }
                }
            }));
        }, 1L);
    }

    @EventHandler
    public void onSleeperDeath(EntityDeathEvent event) {
        if (!enabled()) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie z)) return;
        String owner = z.getPersistentDataContainer().get(sleeperKey, PersistentDataType.STRING);
        String kind = z.getPersistentDataContainer().get(sleeperKindKey, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) {
            return;
        }
        if (!"hitbox".equals(kind)) {
            return;
        }
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(owner);
        } catch (Exception ex) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);

        repo.activeSleeperAsync(ownerUuid).thenAccept(rowOpt ->
            repo.loadItemsAsync(ownerUuid, 41).thenAccept(items -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (ItemStack it : items) {
                    if (it == null || it.getType().isAir()) continue;
                    entity.getWorld().dropItemNaturally(entity.getLocation(), it);
                }
                if (rowOpt.isPresent() && rowOpt.get().armorUuid() != null) {
                    Entity e = Bukkit.getEntity(rowOpt.get().armorUuid());
                    if (e != null) e.remove();
                }
                fakePlayers.remove(ownerUuid);
                repo.markKilled(ownerUuid);
                healthSyncThrottle.remove(ownerUuid);
            }))
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!enabled()) return;
        Entity e = event.getRightClicked();
        if (e == null) return;
        String owner = e.getPersistentDataContainer().get(sleeperKey, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) return;
        UUID ownerUuid;
        try { ownerUuid = UUID.fromString(owner); } catch (Exception ex) { return; }
        event.setCancelled(true);
        openSleeperInventory(event.getPlayer(), ownerUuid);
    }

    private void openSleeperInventory(Player viewer, UUID ownerUuid) {
        int size = 54;
        String title = ChatColor.translateAlternateColorCodes('&', "&0Слиппер");
        repo.loadItemsAsync(ownerUuid, 41).thenAccept(items -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) return;
            SleeperHolder holder = new SleeperHolder(ownerUuid);
            Inventory inv = Bukkit.createInventory(holder, size, title);

            // Main inventory 0..35 in the top 4 rows.
            for (int i = 0; i < 36 && i < items.length; i++) {
                if (items[i] != null) {
                    inv.setItem(i, items[i]);
                    holder.map(i, i);
                }
            }
            // Armor + offhand displayed separately for better UX.
            // PlayerInventory contents mapping: 36 boots, 37 leggings, 38 chest, 39 helmet, 40 offhand.
            Map<Integer, Integer> display = Map.of(
                45, 39, // helmet
                46, 38, // chest
                47, 37, // legs
                48, 36, // boots
                49, 40  // offhand
            );
            for (var entry : display.entrySet()) {
                int viewSlot = entry.getKey();
                int dbSlot = entry.getValue();
                if (dbSlot >= 0 && dbSlot < items.length && items[dbSlot] != null) {
                    inv.setItem(viewSlot, items[dbSlot]);
                    holder.map(viewSlot, dbSlot);
                }
            }
            viewer.openInventory(inv);
        }));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof SleeperHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player viewer)) {
                return;
            }
            if (!(event.getInventory().getHolder() instanceof SleeperHolder holder)) {
                return;
            }
            int raw = event.getRawSlot();
            if (raw < 0 || raw >= event.getInventory().getSize()) {
                return;
            }
            Integer dbSlot = holder.viewToDb.get(raw);
            if (dbSlot == null) {
                return;
            }
            ItemStack clicked = event.getInventory().getItem(raw);
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }
            String claim = holder.owner() + ":" + dbSlot;
            if (!lootClaims.add(claim)) {
                return; // этот слот прямо сейчас забирает другой игрок
            }
            var leftover = viewer.getInventory().addItem(clicked.clone());
            if (!leftover.isEmpty()) {
                lootClaims.remove(claim);
                return;
            }
            event.getInventory().setItem(raw, null);
            repo.clearSlot(holder.owner(), dbSlot);
            viewer.updateInventory();
            lootClaims.remove(claim);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SleeperHolder) {
            event.setCancelled(true);
        }
    }

    private static final class SleeperHolder implements InventoryHolder {
        private final UUID owner;
        private final Map<Integer, Integer> viewToDb = new HashMap<>();

        private SleeperHolder(UUID owner) {
            this.owner = owner;
        }

        private void map(int viewSlot, int dbSlot) {
            viewToDb.put(viewSlot, dbSlot);
        }

        public UUID owner() {
            return owner;
        }

        @Override public Inventory getInventory() { return Bukkit.createInventory(this, 9); }
    }

    private Location snapToGround(Location loc) {
        if (loc == null || loc.getWorld() == null) return loc;
        World w = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int startY = Math.min(w.getMaxHeight() - 1, Math.max(w.getMinHeight() + 1, loc.getBlockY()));
        for (int y = startY; y >= w.getMinHeight(); y--) {
            var block = w.getBlockAt(x, y, z);
            if (!block.getType().isAir() && block.getType().isSolid()) {
                Location out = new Location(w, x + 0.5, y + 1.0, z + 0.5, loc.getYaw(), loc.getPitch());
                return out;
            }
        }
        // fallback: world's highest
        int top = w.getHighestBlockYAt(x, z);
        return new Location(w, x + 0.5, top + 1.0, z + 0.5, loc.getYaw(), loc.getPitch());
    }

    private void applyVisualEquipmentAsync(UUID ownerUuid, Zombie z) {
        repo.loadItemsAsync(ownerUuid, 41).thenAccept(items -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (z == null || z.isDead() || !z.isValid()) return;
            applyVisualEquipmentFromSnapshot(z, items);
        }));
    }

    /**
     * Apply the player's armor and weapon to the zombie body.
     * Slots: 36=boots, 37=leggings, 38=chestplate, 39=helmet, 40=offhand, 0=mainhand.
     * Player skull (set in applySkullToZombie) is replaced by real helmet if player had one.
     */
    private void applyVisualEquipmentFromSnapshot(Zombie z, ItemStack[] snapshot) {
        if (z == null || snapshot == null) return;
        var equip = z.getEquipment();
        if (snapshot.length > 36 && snapshot[36] != null && !snapshot[36].getType().isAir())
            equip.setBoots(snapshot[36], true);
        if (snapshot.length > 37 && snapshot[37] != null && !snapshot[37].getType().isAir())
            equip.setLeggings(snapshot[37], true);
        if (snapshot.length > 38 && snapshot[38] != null && !snapshot[38].getType().isAir())
            equip.setChestplate(snapshot[38], true);
        // If player had a helmet, it replaces the skull (shows actual helmet).
        // If no helmet, applySkullToZombie already placed the player skull there.
        if (snapshot.length > 39 && snapshot[39] != null && !snapshot[39].getType().isAir())
            equip.setHelmet(snapshot[39], true);
        if (snapshot.length > 40 && snapshot[40] != null && !snapshot[40].getType().isAir())
            equip.setItemInOffHand(snapshot[40], true);
        if (snapshot.length > 0 && snapshot[0] != null && !snapshot[0].getType().isAir())
            equip.setItemInMainHand(snapshot[0], true);
        // Drop chance 0 — equipment doesn't drop when zombie dies (items drop via onSleeperDeath)
        equip.setBootsDropChance(0f);
        equip.setLeggingsDropChance(0f);
        equip.setChestplateDropChance(0f);
        equip.setHelmetDropChance(0f);
        equip.setItemInMainHandDropChance(0f);
        equip.setItemInOffHandDropChance(0f);
    }
}

