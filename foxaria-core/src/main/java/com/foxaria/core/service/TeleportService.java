package com.foxaria.core.service;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AsyncScheduler;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Iterator;

public final class TeleportService {

    private final JavaPlugin plugin;
    private final ConfigService configs;
    private final MessageService messages;
    private final AuditService audits;
    private final CoreRepository repository;
    private final CombatTagService combatTagService;
    private final AsyncScheduler scheduler;
    private final ServiceRegistry services;
    private final ConcurrentMap<UUID, PendingTeleport> warmups = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TpaRequest> requestsByTarget = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, List<String>> homeCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastRtpMs = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final ConcurrentLinkedQueue<Location> rtpPool = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean poolFilling = new AtomicBoolean(false);

    public TeleportService(
        JavaPlugin plugin,
        ConfigService configs,
        MessageService messages,
        AuditService audits,
        CoreRepository repository,
        CombatTagService combatTagService,
        AsyncScheduler scheduler,
        ServiceRegistry services
    ) {
        this.plugin = plugin;
        this.configs = configs;
        this.messages = messages;
        this.audits = audits;
        this.repository = repository;
        this.combatTagService = combatTagService;
        this.scheduler = scheduler;
        this.services = services;
    }

    public void teleportSpawn(Player player) {
        if (!canTeleport(player)) {
            return;
        }
        repository.loadSpawn().thenAccept(optionalSpawn -> optionalSpawn.ifPresentOrElse(
            location -> queueTeleport(player, location, "PLAYER_SPAWN_TELEPORT", "teleport.spawn-queued", "&aTeleporting to spawn...", false),
            () -> messages.send(player, "teleport.spawn-not-set", "&cSpawn has not been configured.")
        ));
    }

    public void setSpawn(Player player, Location location) {
        repository.saveSpawn(location).thenRun(() -> audits.append(new AuditEvent(
            "PLAYER_SET_SPAWN",
            player.getUniqueId(),
            null,
            player.getName(),
            null,
            "Spawn location updated",
            Map.of("world", location.getWorld().getName()),
            System.currentTimeMillis()
        )));
        messages.send(player, "teleport.spawn-set", "&aSpawn updated.");
    }

    public void randomTeleport(Player player) {
        randomTeleport(player, RtpReason.COMMAND);
    }

    public void randomTeleport(Player player, RtpReason reason) {
        if (!canTeleport(player)) {
            return;
        }
        long cooldownMs = Math.max(0L, configs.main().getLong("rtp.cooldown-seconds", 300L)) * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastRtpMs.get(player.getUniqueId());
        if (reason == RtpReason.COMMAND && cooldownMs > 0 && last != null && (now - last) < cooldownMs
            && !player.hasPermission("foxaria.rtp.bypass.cooldown")) {
            long left = Math.max(0L, (cooldownMs - (now - last)) / 1000L);
            showTitle(player,
                configs.main().getString("rtp.titles.cooldown.title", "&cRTP"),
                configs.main().getString("rtp.titles.cooldown.subtitle", "&eПерезарядка: &f<seconds>с").replace("<seconds>", String.valueOf(left)));
            return;
        }
        World world = plugin.getServer().getWorld(configs.main().getString("rtp.world", "world"));
        if (world == null) {
            messages.send(player, "teleport.rtp-world-missing", "&cRTP world is missing.");
            return;
        }
        if (reason == RtpReason.FIRST_JOIN) {
            showTitle(player,
                configs.main().getString("rtp.titles.first-join.title", "&6FOXARIA"),
                configs.main().getString("rtp.titles.first-join.subtitle", "&eРандомная точка спавна"));
        } else {
            showTitle(player,
                configs.main().getString("rtp.titles.queued.title", "&6RTP"),
                configs.main().getString("rtp.titles.queued.subtitle", "&eИщем безопасную точку..."));
        }
        // Fast path: use pooled safe locations (BetterRTP-like feel).
        Location pooled = rtpPool.poll();
        if (pooled != null) {
            boolean applyCooldownOnSuccess = (reason == RtpReason.COMMAND);
            queueTeleport(player, pooled, "PLAYER_RTP_REQUESTED", "teleport.rtp-queued", "&aRTP queued...", applyCooldownOnSuccess);
            showTitle(player,
                configs.main().getString("rtp.titles.success.title", "&aГотово"),
                configs.main().getString("rtp.titles.success.subtitle", "&fТелепортация выполнена"));
            return;
        }

        // IMPORTANT: Searching RTP in one tick can freeze the server (chunk loads/generation).
        // Spread attempts across ticks to avoid login timeouts and watchdog kills.
        findSafeRandomLocationBatched(world, reason).thenAccept(optional -> scheduler.runSync(() -> optional.ifPresentOrElse(
            location -> {
                boolean applyCooldownOnSuccess = (reason == RtpReason.COMMAND);
                queueTeleport(player, location, "PLAYER_RTP_REQUESTED", "teleport.rtp-queued", "&aRTP queued...", applyCooldownOnSuccess);
                showTitle(player,
                    configs.main().getString("rtp.titles.success.title", "&aГотово"),
                    configs.main().getString("rtp.titles.success.subtitle", "&fТелепортация выполнена"));
            },
            () -> showTitle(player,
                configs.main().getString("rtp.titles.failed.title", "&cОшибка"),
                configs.main().getString("rtp.titles.failed.subtitle", "&fНе удалось найти безопасную точку"))
        )));
    }

    public void startRtpPool() {
        if (!configs.main().getBoolean("rtp.pool.enabled", true)) {
            return;
        }
        World world = plugin.getServer().getWorld(configs.main().getString("rtp.world", "world"));
        if (world == null) {
            return;
        }
        int targetSize = Math.max(0, configs.main().getInt("rtp.pool.size", 24));
        if (targetSize <= 0) return;
        // Periodic check (every 3 s) + self-trigger after each fill to drain the pool fast on startup.
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> fillPool(world, targetSize), 20L, 60L);
    }

    private void fillPool(World world, int targetSize) {
        if (rtpPool.size() >= targetSize) return;
        if (!poolFilling.compareAndSet(false, true)) return;
        findSafeRandomLocationBatched(world, RtpReason.COMMAND).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(Optional.<Location>empty());
            Location loc = opt.get();
            return preloadChunkAsync(world, loc).handle((ok, ex) -> Optional.of(loc));
        }).thenAccept(opt -> {
            try {
                opt.ifPresent(rtpPool::add);
            } finally {
                poolFilling.set(false);
                if (rtpPool.size() < targetSize) {
                    fillPool(world, targetSize);
                }
            }
        });
    }

    /**
     * Returns and removes one prepared safe RTP location for the given world.
     * Used by first-join spawn event to place player immediately at random safe spot.
     */
    public Optional<Location> consumePreparedRtpLocation(World world) {
        if (world == null) {
            return Optional.empty();
        }
        // Fast path: head is for the needed world.
        Location head = rtpPool.peek();
        if (head != null && head.getWorld() != null && head.getWorld().getName().equalsIgnoreCase(world.getName())) {
            return Optional.ofNullable(rtpPool.poll());
        }
        // Fallback: scan queue for matching world.
        for (Iterator<Location> it = rtpPool.iterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (loc == null || loc.getWorld() == null) {
                continue;
            }
            if (loc.getWorld().getName().equalsIgnoreCase(world.getName())) {
                if (rtpPool.remove(loc)) {
                    return Optional.of(loc);
                }
            }
        }
        return Optional.empty();
    }

    private CompletableFuture<Void> preloadChunkAsync(World world, Location loc) {
        if (world == null || loc == null) return CompletableFuture.completedFuture(null);
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        return world.getChunkAtAsync(cx, cz).thenApply(chunk -> null);
    }

    public void respawnToHomeOrRandom(Player player) {
        if (player == null) return;
        if (!configs.main().getBoolean("rtp.respawn.enabled", true)) {
            return;
        }
        repository.listHomes(player.getUniqueId()).thenAccept(homes -> {
            if (homes != null && !homes.isEmpty()) {
                String first = homes.getFirst();
                repository.loadHome(player.getUniqueId(), first).thenAccept(opt -> opt.ifPresentOrElse(
                    loc -> scheduler.runSync(() -> player.teleportAsync(loc)),
                    () -> scheduler.runSync(() -> respawnRandomTeleport(player))
                ));
            } else {
                scheduler.runSync(() -> respawnRandomTeleport(player));
            }
        });
    }

    /**
     * Respawn RTP — fully async via Paper chunk loading, never blocks the main thread.
     */
    private void respawnRandomTeleport(Player player) {
        if (player == null || !player.isOnline()) return;
        World world = plugin.getServer().getWorld(configs.main().getString("rtp.world", "world"));
        if (world == null) return;
        // Fast path: pool has a ready location.
        Location pooled = rtpPool.poll();
        if (pooled != null) {
            if (player.isOnline()) player.teleportAsync(pooled);
            fillPool(world, configs.main().getInt("rtp.pool.size", 24));
            return;
        }
        findSafeRandomLocationBatched(world, RtpReason.RESPAWN).thenAccept(opt ->
            opt.ifPresent(location -> {
                if (player.isOnline()) player.teleportAsync(location);
            })
        );
    }

    public void setHome(Player player, String homeName) {
        repository.listHomes(player.getUniqueId()).thenAccept(existing -> {
            int limit = configs.main().getInt("homes.default-limit", 2);
            if (!existing.contains(homeName) && existing.size() >= limit && !player.hasPermission("foxaria.core.home.multiple.unlimited")) {
                messages.send(player, "teleport.home-limit", "&cYou reached your home limit.");
                return;
            }
            repository.saveHome(player.getUniqueId(), homeName, player.getLocation()).thenRun(() -> {
                homeCache.remove(player.getUniqueId());
                audits.append(new AuditEvent(
                    "PLAYER_HOME_SET",
                    player.getUniqueId(),
                    null,
                    player.getName(),
                    null,
                    "Home set",
                    Map.of("home", homeName),
                    System.currentTimeMillis()
                ));
                messages.send(player, "teleport.home-set", "&aHome <home> saved.", new MessageService.Placeholder("home", homeName));
            });
        });
    }

    public void teleportHome(Player player, String homeName) {
        if (!canTeleport(player)) {
            return;
        }
        repository.loadHome(player.getUniqueId(), homeName).thenAccept(optionalLocation -> optionalLocation.ifPresentOrElse(
            location -> queueTeleport(player, location, "PLAYER_HOME_TELEPORT", "teleport.home-queued", "&aTeleporting to home <home>...", false, new MessageService.Placeholder("home", homeName)),
            () -> messages.send(player, "teleport.home-missing", "&cHome <home> not found.", new MessageService.Placeholder("home", homeName))
        ));
    }

    public void deleteHome(Player player, String homeName) {
        repository.deleteHome(player.getUniqueId(), homeName).thenRun(() -> {
            homeCache.remove(player.getUniqueId());
            audits.append(new AuditEvent(
                "PLAYER_HOME_DELETE",
                player.getUniqueId(),
                null,
                player.getName(),
                null,
                "Home deleted",
                Map.of("home", homeName),
                System.currentTimeMillis()
            ));
            messages.send(player, "teleport.home-deleted", "&aHome <home> deleted.", new MessageService.Placeholder("home", homeName));
        });
    }

    public void listHomes(Player player) {
        repository.listHomes(player.getUniqueId()).thenAccept(homes -> {
            homeCache.put(player.getUniqueId(), homes);
            messages.send(
                player,
                "teleport.homes-list",
                "&eHomes: <homes>",
                new MessageService.Placeholder("homes", homes.isEmpty() ? "none" : String.join(", ", homes))
            );
        });
    }

    public List<String> cachedHomes(UUID playerUuid) {
        return homeCache.getOrDefault(playerUuid, List.of());
    }

    public void sendTeleportRequest(Player requester, Player target, boolean here) {
        if (!canTeleport(requester)) {
            return;
        }
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            messages.send(requester, "teleport.tpa-self", "&cYou cannot send a request to yourself.");
            return;
        }
        requestsByTarget.put(target.getUniqueId(), new TpaRequest(requester.getUniqueId(), target.getUniqueId(), here, System.currentTimeMillis()));
        messages.send(requester, "teleport.tpa-sent", "&aTeleport request sent to <target>.", new MessageService.Placeholder("target", target.getName()));
        messages.send(
            target,
            here ? "teleport.tpahere-received" : "teleport.tpa-received",
            here ? "&e<player> wants you to teleport to them." : "&e<player> wants to teleport to you.",
            new MessageService.Placeholder("player", requester.getName())
        );
        audits.append(new AuditEvent(
            "PLAYER_TPA_SENT",
            requester.getUniqueId(),
            target.getUniqueId(),
            requester.getName(),
            target.getName(),
            "Teleport request sent",
            Map.of("here", String.valueOf(here)),
            System.currentTimeMillis()
        ));
    }

    public void acceptTeleportRequest(Player target) {
        TpaRequest request = requestsByTarget.remove(target.getUniqueId());
        if (!validateRequest(target, request)) {
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            messages.send(target, "general.player-not-found", "&cPlayer not found.");
            return;
        }
        if (request.here()) {
            queueTeleport(target, requester.getLocation(), "PLAYER_TPA_ACCEPTED", "teleport.tpaccept-success", "&aTeleport accepted.", false);
        } else {
            queueTeleport(requester, target.getLocation(), "PLAYER_TPA_ACCEPTED", "teleport.tpaccept-success", "&aTeleport accepted.", false);
        }
        messages.send(target, "teleport.tpaccept-notify", "&aTeleport request accepted.");
    }

    public void denyTeleportRequest(Player target) {
        TpaRequest request = requestsByTarget.remove(target.getUniqueId());
        if (!validateRequest(target, request)) {
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) {
            messages.send(requester, "teleport.tpdeny-notify", "&cYour teleport request was denied.");
        }
        messages.send(target, "teleport.tpdeny-success", "&aTeleport request denied.");
    }

    public void handleMove(Player player, Location from, Location to) {
        PendingTeleport pending = warmups.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            warmups.remove(player.getUniqueId());
            messages.send(player, "teleport.warmup-cancelled", "&cTeleport cancelled because you moved.");
        }
    }

    private boolean validateRequest(Player target, TpaRequest request) {
        if (request == null) {
            messages.send(target, "teleport.tpa-none", "&cNo pending teleport request.");
            return false;
        }
        long expirySeconds = configs.main().getLong("tpa.request-expiry-seconds", 60L);
        if ((System.currentTimeMillis() - request.createdAt()) / 1000L > expirySeconds) {
            messages.send(target, "teleport.tpa-expired", "&cThe teleport request has expired.");
            return false;
        }
        return true;
    }

    private boolean canTeleport(Player player) {
        if (combatTagService.isTagged(player.getUniqueId()) && !player.hasPermission("foxaria.core.bypass.combat")) {
            messages.send(
                player,
                "teleport.blocked-combat",
                "&cYou cannot teleport while combat tagged. Remaining: <seconds>s",
                new MessageService.Placeholder("seconds", String.valueOf(combatTagService.remainingSeconds(player.getUniqueId())))
            );
            return false;
        }
        return true;
    }

    private void queueTeleport(Player player, Location location, String auditType, String messagePath, String fallback, boolean applyRtpCooldownOnSuccess, MessageService.Placeholder... placeholders) {
        long warmupTicks = configs.main().getLong("teleport-warmup.ticks", 60L);
        if (warmupTicks <= 0 || player.hasPermission("foxaria.core.bypass.warmup")) {
            performTeleport(player, location, auditType, applyRtpCooldownOnSuccess);
            return;
        }

        warmups.put(player.getUniqueId(), new PendingTeleport(location, auditType, applyRtpCooldownOnSuccess));
        messages.send(player, messagePath, fallback, placeholders);
        scheduler.runLaterSync(() -> {
            PendingTeleport pending = warmups.remove(player.getUniqueId());
            if (pending == null) {
                return;
            }
            performTeleport(player, pending.target(), pending.auditType(), pending.applyRtpCooldownOnSuccess());
        }, warmupTicks);
    }

    private void performTeleport(Player player, Location location, String auditType, boolean applyRtpCooldownOnSuccess) {
        scheduler.runSync(() -> {
            if (!player.isOnline()) {
                return;
            }
            player.teleportAsync(location);
            if (applyRtpCooldownOnSuccess) {
                lastRtpMs.put(player.getUniqueId(), System.currentTimeMillis());
            }
            audits.append(new AuditEvent(
                auditType,
                player.getUniqueId(),
                null,
                player.getName(),
                null,
                "Player teleported",
                Map.of(
                    "world", location.getWorld().getName(),
                    "x", String.valueOf(location.getBlockX()),
                    "z", String.valueOf(location.getBlockZ())
                ),
                System.currentTimeMillis()
            ));
        });
    }

    /**
     * Finds a safe random RTP location fully asynchronously.
     * Uses Paper's getChunkAtAsync so chunk loading/generation never blocks the main thread.
     * Callbacks always fire on the main thread, so block-access in findSafeSurface is safe.
     */
    private CompletableFuture<Optional<Location>> findSafeRandomLocationBatched(World world, RtpReason reason) {
        int radius = Math.max(50, configs.main().getInt("rtp.radius", 2000));
        int attempts = Math.max(10, configs.main().getInt("rtp.max-attempts", 40));
        int avoidRegions = Math.max(0, configs.main().getInt("rtp.avoid-regions-distance", 50));
        int minSurfaceY = configs.main().getInt("rtp.min-surface-y", 62);
        int maxSurfaceScan = Math.max(64, configs.main().getInt("rtp.surface-scan-down", 96));

        if (reason == RtpReason.FIRST_JOIN || reason == RtpReason.RESPAWN) {
            attempts = Math.max(attempts, 100);
            maxSurfaceScan = Math.max(maxSurfaceScan, 140);
            minSurfaceY = Math.min(minSurfaceY, 58);
        } else {
            attempts = Math.max(attempts, 50);
        }

        CompletableFuture<Optional<Location>> out = new CompletableFuture<>();
        findSafeAsync(world, 0, attempts, radius, avoidRegions, minSurfaceY, maxSurfaceScan, out);
        return out;
    }

    /**
     * Recursive async RTP search.
     * Each iteration loads one chunk via Paper's async API (no main-thread blocking),
     * then checks safety on main thread in the callback.
     * "Recursion" is tail-recursive through CompletableFuture callbacks — no stack growth.
     */
    private void findSafeAsync(World world, int attempt, int maxAttempts, int radius,
                                int avoidRegions, int minSurfaceY, int maxSurfaceScan,
                                CompletableFuture<Optional<Location>> out) {
        if (out.isDone()) return;
        if (attempt >= maxAttempts) {
            out.complete(Optional.empty());
            return;
        }
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double r = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
        int x = (int) Math.round(r * Math.cos(angle));
        int z = (int) Math.round(r * Math.sin(angle));

        // Paper's getChunkAtAsync: chunk loading/generation happens on worker threads,
        // callback fires on the main thread with chunk already loaded.
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (out.isDone()) return;
            Optional<Location> safe = findSafeSurface(world, x, z, minSurfaceY, maxSurfaceScan);
            if (safe.isPresent()) {
                Location candidate = safe.get();
                if (avoidRegions <= 0 || !isNearAnyRegionReflective(candidate, avoidRegions)) {
                    out.complete(Optional.of(candidate));
                    return;
                }
            }
            findSafeAsync(world, attempt + 1, maxAttempts, radius, avoidRegions, minSurfaceY, maxSurfaceScan, out);
        });
    }

    private Optional<Location> findSafeSurface(World world, int x, int z, int minSurfaceY, int scanDown) {
        int maxY = world.getMaxHeight() - 2;
        int minY = Math.max(world.getMinHeight() + 2, minSurfaceY);
        int y = Math.min(maxY, world.getHighestBlockYAt(x, z) + 1);
        if (y < minY) {
            y = minY;
        }
        // Спускаемся вниз, пока не найдём "поверхность" с воздухом над головой.
        for (int tries = 0; tries < scanDown && y >= minY; tries++, y--) {
            var feet = world.getBlockAt(x, y, z);
            var head = world.getBlockAt(x, y + 1, z);
            var below = world.getBlockAt(x, y - 1, z);

            // Оба блока для игрока должны быть свободны.
            boolean feetOk = feet.isPassable();
            boolean headOk = head.isPassable();

            // Разрешаем "мелкую реку": ноги в воде, но голова в воздухе.
            if (!feetOk && feet.getType() == Material.WATER) {
                feetOk = true;
            }
            if (!feetOk || !headOk) {
                continue;
            }

            // Под ногами должна быть твёрдая поверхность. Если ноги в воде — требуем твёрдое дно.
            if (!below.isSolid()) {
                continue;
            }
            Material belowType = below.getType();
            if (belowType == Material.LAVA || belowType.name().contains("LEAVES")) {
                continue;
            }

            // Если мы в воде — допускаем только 1 блок воды (голова уже воздух), т.е. не озёра/океан.
            if (feet.getType() == Material.WATER) {
                var below2 = world.getBlockAt(x, y - 2, z);
                if (!below2.isSolid()) {
                    continue;
                }
            }

            Location candidate = new Location(world, x + 0.5D, y, z + 0.5D);
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private void showTitle(Player player, String titleLegacy, String subtitleLegacy) {
        if (player == null || !player.isOnline()) return;
        Duration fadeIn = Duration.ofMillis(configs.main().getLong("rtp.titles.fade-in-ms", 300));
        Duration stay = Duration.ofMillis(configs.main().getLong("rtp.titles.stay-ms", 1400));
        Duration fadeOut = Duration.ofMillis(configs.main().getLong("rtp.titles.fade-out-ms", 300));
        Component title = legacy.deserialize(titleLegacy == null ? "" : titleLegacy);
        Component sub = legacy.deserialize(subtitleLegacy == null ? "" : subtitleLegacy);
        player.showTitle(Title.title(title, sub, Title.Times.times(fadeIn, stay, fadeOut)));
    }

    public enum RtpReason {
        COMMAND,
        FIRST_JOIN,
        RESPAWN
    }

    /**
     * Проверка "не рядом с приватом" без compile-time зависимости от foxaria-regions.
     *
     * Если модуль regions не загружен — считаем, что ограничений по приватам нет.
     */
    private boolean isNearAnyRegionReflective(Location loc, int minDistance) {
        if (loc.getWorld() == null) {
            return false;
        }
        try {
            Class<?> facadeClz = Class.forName("com.foxaria.regions.RegionFacade");
            Object facade = services.optional(facadeClz);
            if (facade == null) {
                return false;
            }
            Object manager = facadeClz.getMethod("manager").invoke(facade);
            if (manager == null) {
                return false;
            }
            @SuppressWarnings("unchecked")
            java.util.List<Object> regions = (java.util.List<Object>) manager.getClass().getMethod("snapshot").invoke(manager);
            if (regions == null || regions.isEmpty()) {
                return false;
            }
            int x = loc.getBlockX();
            int z = loc.getBlockZ();
            String world = loc.getWorld().getName();
            for (Object r : regions) {
                if (r == null) continue;
                String rw = (String) r.getClass().getMethod("world").invoke(r);
                if (!world.equals(rw)) continue;
                int cx = (int) r.getClass().getMethod("centerX").invoke(r);
                int cz = (int) r.getClass().getMethod("centerZ").invoke(r);
                int half = (int) r.getClass().getMethod("halfSize").invoke(r);
                int dx = Math.abs(x - cx);
                int dz = Math.abs(z - cz);
                if (dx <= (half + minDistance) && dz <= (half + minDistance)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int randomSigned(int min, int max) {
        int absolute = ThreadLocalRandom.current().nextInt(min, max + 1);
        return ThreadLocalRandom.current().nextBoolean() ? absolute : -absolute;
    }

    private record PendingTeleport(Location target, String auditType, boolean applyRtpCooldownOnSuccess) {
    }

    private record TpaRequest(UUID requester, UUID target, boolean here, long createdAt) {
    }
}
