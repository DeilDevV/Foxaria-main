package com.foxaria.core.service;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AsyncScheduler;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.ServiceRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final java.util.Set<Material> HAZARD_BLOCKS = java.util.Set.of(
        Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE, Material.CACTUS,
        Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.WITHER_ROSE,
        Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY, Material.MAGMA_BLOCK,
        Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE, Material.BUBBLE_COLUMN
    );

    private static final java.util.List<String> STRUCTURE_MARKERS = java.util.List.of(
        "PLANKS", "CHEST", "FURNACE", "CRAFTING_TABLE", "GLASS", "CONCRETE", "WOOL", "TERRACOTTA",
        "BRICKS", "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "NETHERITE_BLOCK", "HOPPER", "ANVIL",
        "BEACON", "ENCHANTING_TABLE", "_BED", "_DOOR", "LADDER", "RAIL", "TORCH", "BARREL",
        "SHULKER_BOX", "SCAFFOLDING", "GLAZED", "COPPER_BLOCK", "SPAWNER", "BOOKSHELF"
    );

    private volatile java.lang.reflect.Method regionWorldMethod;
    private volatile java.lang.reflect.Method regionCenterXMethod;
    private volatile java.lang.reflect.Method regionCenterZMethod;
    private volatile java.lang.reflect.Method regionHalfSizeMethod;

    public TeleportService(
        JavaPlugin plugin, ConfigService configs, MessageService messages, AuditService audits,
        CoreRepository repository, CombatTagService combatTagService, AsyncScheduler scheduler, ServiceRegistry services
    ) {
        this.plugin = plugin; this.configs = configs; this.messages = messages;
        this.audits = audits; this.repository = repository;
        this.combatTagService = combatTagService; this.scheduler = scheduler; this.services = services;
    }

    private static Component foxariaGradient() {
        return Component.text()
            .append(Component.text("F", TextColor.color(0xFF6600), TextDecoration.BOLD))
            .append(Component.text("O", TextColor.color(0xFF8800), TextDecoration.BOLD))
            .append(Component.text("X", TextColor.color(0xFFAA00), TextDecoration.BOLD))
            .append(Component.text("A", TextColor.color(0xFFCC00), TextDecoration.BOLD))
            .append(Component.text("R", TextColor.color(0xFFDD44), TextDecoration.BOLD))
            .append(Component.text("I", TextColor.color(0xFFEE66), TextDecoration.BOLD))
            .append(Component.text("A", TextColor.color(0xFFFF88), TextDecoration.BOLD))
            .append(Component.text(" "))
            .build();
    }

    public void randomTeleport(Player player) { randomTeleport(player, RtpReason.COMMAND); }

    public void randomTeleport(Player player, RtpReason reason) {
        if (!canTeleport(player)) return;
        long cooldownMs = Math.max(0L, configs.main().getLong("rtp.cooldown-seconds", 300L)) * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastRtpMs.get(player.getUniqueId());
        if (reason == RtpReason.COMMAND && cooldownMs > 0 && last != null && (now - last) < cooldownMs) {
            long left = (cooldownMs - (now - last)) / 1000L;
            messages.send(player, "teleport.rtp-cooldown", "&cRTP cooldown: <left>с",
                new MessageService.Placeholder("left", String.valueOf(left)));
            return;
        }
        World world = plugin.getServer().getWorld(configs.main().getString("rtp.world", "world"));
        if (world == null) { messages.send(player, "teleport.rtp-world-missing", "&cRTP world is missing."); return; }
        if (reason == RtpReason.FIRST_JOIN) {
            showTitle(player, configs.main().getString("rtp.titles.first-join.title", "&6FOXARIA"),
                configs.main().getString("rtp.titles.first-join.subtitle", "&eРандомная точка спавна"));
        } else {
            showTitle(player, configs.main().getString("rtp.titles.queued.title", "&6RTP"),
                configs.main().getString("rtp.titles.queued.subtitle", "&eИщем безопасную точку..."));
        }
        Location pooled = rtpPool.poll();
        if (pooled != null) {
            queueTeleport(player, pooled, "PLAYER_RTP_REQUESTED", "teleport.rtp-queued", "&aRTP queued...", reason == RtpReason.COMMAND);
            showTitle(player, configs.main().getString("rtp.titles.success.title", "&aГотово"),
                configs.main().getString("rtp.titles.success.subtitle", "&fТелепортация выполнена"));
            return;
        }
        findSafeRandomLocationBatched(world, reason).thenAccept(optional -> scheduler.runSync(() ->
            optional.ifPresentOrElse(
                location -> {
                    queueTeleport(player, location, "PLAYER_RTP_REQUESTED", "teleport.rtp-queued", "&aRTP queued...", reason == RtpReason.COMMAND);
                    showTitle(player, configs.main().getString("rtp.titles.success.title", "&aГотово"),
                        configs.main().getString("rtp.titles.success.subtitle", "&fТелепортация выполнена"));
                },
                () -> showTitle(player, configs.main().getString("rtp.titles.failed.title", "&cОшибка"),
                    configs.main().getString("rtp.titles.failed.subtitle", "&fНе удалось найти безопасную точку"))
            )
        ));
    }

    public void startRtpPool() {
        if (!configs.main().getBoolean("rtp.pool.enabled", true)) return;
        World world = plugin.getServer().getWorld(configs.main().getString("rtp.world", "world"));
        if (world == null) return;
        int targetSize = Math.max(0, configs.main().getInt("rtp.pool.size", 24));
        if (targetSize <= 0) return;
        long period = Math.max(20L, configs.main().getLong("rtp.pool.refill-interval-ticks", 100L));
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> fillPool(world, targetSize), 100L, period);
    }

    private void fillPool(World world, int targetSize) {
        if (rtpPool.size() >= targetSize) return;
        if (!poolFilling.compareAndSet(false, true)) return;
        findSafeRandomLocationBatched(world, RtpReason.POOL).whenComplete((opt, error) -> {
            try { if (error == null && opt != null) opt.ifPresent(rtpPool::add); }
            finally {
                poolFilling.set(false);
                if (rtpPool.size() < targetSize) {
                    long delay = Math.max(5L, configs.main().getLong("rtp.pool.refill-interval-ticks", 100L));
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> fillPool(world, targetSize), delay);
                }
            }
        });
    }

    public Optional<Location> consumePreparedRtpLocation(World world) {
        if (world == null) return Optional.empty();
        Location head = rtpPool.peek();
        if (head != null && head.getWorld() != null && head.getWorld().getName().equalsIgnoreCase(world.getName())) {
            return Optional.ofNullable(rtpPool.poll());
        }
        for (Iterator<Location> it = rtpPool.iterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (loc == null || loc.getWorld() == null) continue;
            if (loc.getWorld().getName().equalsIgnoreCase(world.getName())) {
                if (rtpPool.remove(loc)) return Optional.of(loc);
            }
        }
        return Optional.empty();
    }

    public void respawnToHomeOrRandom(Player player) {
        if (player == null) return;
        if (!configs.main().getBoolean("rtp.respawn.enabled", true)) return;
        repository.listHomes(player.getUniqueId()).thenAccept(homes -> {
            if (homes != null && !homes.isEmpty()) {
                String first = homes.getFirst();
                repository.loadHome(player.getUniqueId(), first).thenAccept(opt ->
                    opt.ifPresentOrElse(loc -> scheduler.runSync(() -> player.teleportAsync(loc)),
                        () -> scheduler.runSync(() -> respawnRandomTeleport(player)))
                );
            } else {
                scheduler.runSync(() -> respawnRandomTeleport(player));
            }
        });
    }

    private void respawnRandomTeleport(Player player) {
        if (player == null || !player.isOnline()) return;
        World world = plugin.getServer().getWorld(configs.main().getString("rtp.world", "world"));
        if (world == null) return;
        Location pooled = rtpPool.poll();
        if (pooled != null) {
            if (player.isOnline()) player.teleportAsync(pooled);
            fillPool(world, configs.main().getInt("rtp.pool.size", 24));
            return;
        }
        findSafeRandomLocationBatched(world, RtpReason.RESPAWN).thenAccept(opt ->
            opt.ifPresent(location -> { if (player.isOnline()) player.teleportAsync(location); })
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
                audits.append(new AuditEvent("PLAYER_HOME_SET", player.getUniqueId(), null, player.getName(), null, "Home set", Map.of("home", homeName), System.currentTimeMillis()));
                messages.send(player, "teleport.home-set", "&aHome saved.", new MessageService.Placeholder("home", homeName));
            });
        });
    }

    public void teleportHome(Player player, String homeName) {
        if (!canTeleport(player)) return;
        repository.loadHome(player.getUniqueId(), homeName).thenAccept(optionalLocation ->
            optionalLocation.ifPresentOrElse(
                location -> queueTeleport(player, location, "PLAYER_HOME_TELEPORT", "teleport.home-queued", "&aTeleporting to home ...", false, new MessageService.Placeholder("home", homeName)),
                () -> messages.send(player, "teleport.home-missing", "&cHome not found.", new MessageService.Placeholder("home", homeName))
            )
        );
    }

    public void deleteHome(Player player, String homeName) {
        repository.deleteHome(player.getUniqueId(), homeName).thenRun(() -> {
            homeCache.remove(player.getUniqueId());
            audits.append(new AuditEvent("PLAYER_HOME_DELETE", player.getUniqueId(), null, player.getName(), null, "Home deleted", Map.of("home", homeName), System.currentTimeMillis()));
            messages.send(player, "teleport.home-deleted", "&aHome deleted.", new MessageService.Placeholder("home", homeName));
        });
    }

    public void listHomes(Player player) {
        repository.listHomes(player.getUniqueId()).thenAccept(homes -> {
            homeCache.put(player.getUniqueId(), homes);
            messages.send(player, "teleport.homes-list", "&eHomes: <homes>",
                new MessageService.Placeholder("homes", homes.isEmpty() ? "none" : String.join(", ", homes)));
        });
    }

    public List<String> cachedHomes(UUID playerUuid) { return homeCache.getOrDefault(playerUuid, List.of()); }

    public void sendTeleportRequest(Player requester, Player target, boolean here) {
        if (!canTeleport(requester)) return;
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            messages.send(requester, "teleport.tpa-self", "&cYou cannot send a request to yourself.");
            return;
        }
        requestsByTarget.put(target.getUniqueId(), new TpaRequest(requester.getUniqueId(), target.getUniqueId(), here, System.currentTimeMillis()));
        messages.send(requester, "teleport.tpa-sent", "&aTeleport request sent to <target>.",
            new MessageService.Placeholder("target", target.getName()));

        // Кликабельное сообщение с градиентом FOXARIA
        Component tpaMsg = Component.text()
            .append(foxariaGradient())
            .append(Component.text(
                here ? requester.getName() + " хочет, чтобы вы телепортировались к нему. "
                     : requester.getName() + " хочет телепортироваться к вам. ",
                NamedTextColor.YELLOW))
            .append(Component.text("[ПРИНЯТЬ]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Принять телепортацию", NamedTextColor.GREEN)))
                .clickEvent(ClickEvent.runCommand("/tpaccept")))
            .append(Component.text(" "))
            .append(Component.text("[ОТКЛОНИТЬ]", NamedTextColor.RED, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Отклонить телепортацию", NamedTextColor.RED)))
                .clickEvent(ClickEvent.runCommand("/tpdeny")))
            .build();
        target.sendMessage(tpaMsg);

        audits.append(new AuditEvent("PLAYER_TPA_SENT", requester.getUniqueId(), target.getUniqueId(),
            requester.getName(), target.getName(), "Teleport request sent",
            Map.of("here", String.valueOf(here)), System.currentTimeMillis()));
    }

    public void acceptTeleportRequest(Player target) {
        TpaRequest request = requestsByTarget.remove(target.getUniqueId());
        if (!validateRequest(target, request)) return;
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) { messages.send(target, "general.player-not-found", "&cPlayer not found."); return; }
        if (request.here()) {
            queueTeleport(target, requester.getLocation(), "PLAYER_TPA_ACCEPTED", "teleport.tpaccept-success", "&aTeleport accepted.", false);
        } else {
            queueTeleport(requester, target.getLocation(), "PLAYER_TPA_ACCEPTED", "teleport.tpaccept-success", "&aTeleport accepted.", false);
        }
        messages.send(target, "teleport.tpaccept-notify", "&aTeleport request accepted.");
    }

    public void denyTeleportRequest(Player target) {
        TpaRequest request = requestsByTarget.remove(target.getUniqueId());
        if (!validateRequest(target, request)) return;
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) messages.send(requester, "teleport.tpdeny-notify", "&cYour teleport request was denied.");
        messages.send(target, "teleport.tpdeny-success", "&aTeleport request denied.");
    }

    public void handleMove(Player player, Location from, Location to) {
        PendingTeleport pending = warmups.get(player.getUniqueId());
        if (pending == null) return;
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            warmups.remove(player.getUniqueId());
            messages.send(player, "teleport.warmup-cancelled", "&cTeleport cancelled because you moved.");
        }
    }

    private boolean validateRequest(Player target, TpaRequest request) {
        if (request == null) { messages.send(target, "teleport.tpa-none", "&cNo pending teleport request."); return false; }
        long expirySeconds = configs.main().getLong("tpa.request-expiry-seconds", 60L);
        if ((System.currentTimeMillis() - request.createdAt()) / 1000L > expirySeconds) {
            messages.send(target, "teleport.tpa-expired", "&cThe teleport request has expired.");
            return false;
        }
        return true;
    }

    private boolean canTeleport(Player player) {
        if (combatTagService.isTagged(player.getUniqueId()) && !player.hasPermission("foxaria.core.bypass.combat")) {
            messages.send(player, "teleport.blocked-combat", "&cYou cannot teleport while combat tagged. Remaining: <seconds>s",
                new MessageService.Placeholder("seconds", String.valueOf(combatTagService.remainingSeconds(player.getUniqueId()))));
            return false;
        }
        return true;
    }

    private void queueTeleport(Player player, Location location, String auditType, String messagePath, String fallback, boolean applyRtpCooldownOnSuccess, MessageService.Placeholder... placeholders) {
        long warmupTicks = configs.main().getLong("teleport-warmup.ticks", 60L);
        if (warmupTicks <= 0) {
            performTeleport(player, location, auditType, applyRtpCooldownOnSuccess);
            return;
        }
        warmups.put(player.getUniqueId(), new PendingTeleport(location, auditType, applyRtpCooldownOnSuccess));
        messages.send(player, messagePath, fallback, placeholders);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingTeleport pending = warmups.remove(player.getUniqueId());
            if (pending == null) return;
            performTeleport(player, pending.target(), pending.auditType(), pending.applyRtpCooldownOnSuccess());
        }, warmupTicks);
    }

    private void performTeleport(Player player, Location location, String auditType, boolean applyRtpCooldownOnSuccess) {
        scheduler.runSync(() -> {
            if (!player.isOnline()) return;
            player.teleportAsync(location);
            if (applyRtpCooldownOnSuccess) lastRtpMs.put(player.getUniqueId(), System.currentTimeMillis());
            audits.append(new AuditEvent(auditType, player.getUniqueId(), null, player.getName(), null, "Player teleported",
                Map.of("world", location.getWorld().getName(), "x", String.valueOf(location.getBlockX()), "z", String.valueOf(location.getBlockZ())),
                System.currentTimeMillis()));
        });
    }

    private CompletableFuture<Optional<Location>> findSafeRandomLocationBatched(World world, RtpReason reason) {
        int radius = Math.max(50, configs.main().getInt("rtp.radius", 2000));
        int minDistance = Math.max(0, configs.main().getInt("rtp.min-distance", 0));
        if (minDistance >= radius) minDistance = Math.max(0, radius / 4);
        int attempts = clamp(configs.main().getInt("rtp.max-attempts", 24), 5, 60);
        int avoidRegions = Math.max(0, configs.main().getInt("rtp.avoid-regions-distance", 50));
        int minSurfaceY = configs.main().getInt("rtp.min-surface-y", 50);
        int maxSurfaceScan = clamp(configs.main().getInt("rtp.surface-scan-down", 96), 16, 160);
        int generateBudget = Math.max(0, configs.main().getInt("rtp.max-chunk-generations", 4));
        CompletableFuture<Optional<Location>> out = new CompletableFuture<>();
        SearchContext ctx = new SearchContext(attempts, radius, minDistance, avoidRegions, minSurfaceY, maxSurfaceScan, new java.util.concurrent.atomic.AtomicInteger(generateBudget));
        long timeoutTicks = Math.max(40L, configs.main().getLong("rtp.search-timeout-ticks", 200L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (!out.isDone()) out.complete(Optional.empty()); }, timeoutTicks);
        findSafeAsync(world, 0, ctx, out);
        return out;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private void findSafeAsync(World world, int attempt, SearchContext ctx, CompletableFuture<Optional<Location>> out) {
        if (out.isDone()) return;
        if (attempt >= ctx.maxAttempts()) { out.complete(Optional.empty()); return; }
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double span = ctx.radius() - ctx.minDistance();
        double r = ctx.minDistance() + Math.sqrt(ThreadLocalRandom.current().nextDouble()) * span;
        int x = (int) Math.round(r * Math.cos(angle));
        int z = (int) Math.round(r * Math.sin(angle));
        boolean allowGenerate = ctx.generateBudget().get() > 0;
        if (allowGenerate) ctx.generateBudget().decrementAndGet();
        world.getChunkAtAsync(x >> 4, z >> 4, allowGenerate).exceptionally(ex -> null).thenAccept(chunk -> {
            if (out.isDone()) return;
            Runnable step = () -> {
                if (out.isDone()) return;
                if (chunk != null) {
                    Optional<Location> safe = findSafeSurface(world, x, z, ctx.minSurfaceY(), ctx.maxSurfaceScan());
                    if (safe.isPresent()) {
                        Location candidate = safe.get();
                        boolean nearRegion = ctx.avoidRegions() > 0 && isNearAnyRegionReflective(candidate, ctx.avoidRegions());
                        if (!nearRegion && !isNearPlayerStructure(candidate)) { out.complete(Optional.of(candidate)); return; }
                    }
                }
                findSafeAsync(world, attempt + 1, ctx, out);
            };
            if (plugin.getServer().isPrimaryThread()) step.run();
            else plugin.getServer().getScheduler().runTask(plugin, step);
        });
    }

    private Optional<Location> findSafeSurface(World world, int x, int z, int minSurfaceY, int scanDown) {
        int maxY = world.getMaxHeight() - 2;
        int minY = Math.max(world.getMinHeight() + 2, minSurfaceY);
        int y = Math.min(maxY, world.getHighestBlockYAt(x, z) + 1);
        if (y > maxY) y = maxY;
        for (int tries = 0; tries < scanDown && y >= minY; tries++, y--) {
            var feet = world.getBlockAt(x, y, z);
            var head = world.getBlockAt(x, y + 1, z);
            var below = world.getBlockAt(x, y - 1, z);
            if (!isFreeSpace(feet) || !isFreeSpace(head)) continue;
            if (!isSafeGround(below)) continue;
            if (hasHazardAround(world, x, y, z)) continue;
            return Optional.of(new Location(world, x + 0.5D, y, z + 0.5D));
        }
        return Optional.empty();
    }

    private boolean isFreeSpace(org.bukkit.block.Block block) {
        Material type = block.getType();
        if (type.isAir()) return true;
        if (!block.isPassable()) return false;
        return !HAZARD_BLOCKS.contains(type) && !block.isLiquid();
    }

    private boolean isSafeGround(org.bukkit.block.Block below) {
        if (!below.isSolid() || below.isLiquid()) return false;
        Material type = below.getType();
        if (HAZARD_BLOCKS.contains(type)) return false;
        String name = type.name();
        return !name.contains("LEAVES") && !name.contains("SLIME") && !name.contains("HONEY");
    }

    private boolean hasHazardAround(World world, int x, int y, int z) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            Material side = world.getBlockAt(x + off[0], y, z + off[1]).getType();
            if (side == Material.LAVA || side == Material.FIRE || side == Material.SOUL_FIRE) return true;
        }
        return world.getBlockAt(x, y + 2, z).getType() == Material.LAVA;
    }

    private boolean isNearPlayerStructure(Location candidate) {
        if (!configs.main().getBoolean("rtp.avoid-structures.enabled", true)) return false;
        World world = candidate.getWorld();
        if (world == null) return false;
        int radius = clamp(configs.main().getInt("rtp.avoid-structures.radius", 8), 2, 16);
        int step = Math.max(2, configs.main().getInt("rtp.avoid-structures.step", 3));
        int baseX = candidate.getBlockX(), baseY = candidate.getBlockY(), baseZ = candidate.getBlockZ();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int wx = baseX + dx, wz = baseZ + dz;
                if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                for (int dy = -2; dy <= 4; dy++) {
                    Material m = world.getBlockAt(wx, baseY + dy, wz).getType();
                    if (m.isAir()) continue;
                    String name = m.name();
                    for (String marker : STRUCTURE_MARKERS) {
                        if (name.contains(marker)) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNearAnyRegionReflective(Location loc, int minDistance) {
        try {
            Class<?> facadeClz = Class.forName("com.foxaria.regions.RegionFacade");
            Object facade = services.optional(facadeClz);
            if (facade == null) return false;
            Object manager = facadeClz.getMethod("manager").invoke(facade);
            if (manager == null) return false;
            @SuppressWarnings("unchecked")
            java.util.List<Object> regions = (java.util.List<Object>) manager.getClass().getMethod("snapshot").invoke(manager);
            if (regions == null || regions.isEmpty()) return false;
            int x = loc.getBlockX(), z = loc.getBlockZ();
            String world = loc.getWorld().getName();
            if (regionWorldMethod == null) {
                Class<?> regionClz = regions.get(0).getClass();
                regionWorldMethod   = regionClz.getMethod("world");
                regionCenterXMethod = regionClz.getMethod("centerX");
                regionCenterZMethod = regionClz.getMethod("centerZ");
                regionHalfSizeMethod = regionClz.getMethod("halfSize");
            }
            for (Object reg : regions) {
                if (reg == null) continue;
                String rw = (String) regionWorldMethod.invoke(reg);
                if (!world.equals(rw)) continue;
                int cx = (int) regionCenterXMethod.invoke(reg);
                int cz = (int) regionCenterZMethod.invoke(reg);
                int half = (int) regionHalfSizeMethod.invoke(reg);
                if (Math.abs(x - cx) <= (half + minDistance) && Math.abs(z - cz) <= (half + minDistance)) return true;
            }
            return false;
        } catch (Exception ignored) { return false; }
    }

    private void showTitle(Player player, String titleRaw, String subtitleRaw) {
        Component title = legacy.deserialize(titleRaw);
        Component subtitle = legacy.deserialize(subtitleRaw);
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500))));
    }

    public enum RtpReason { COMMAND, FIRST_JOIN, RESPAWN, POOL }

    private record SearchContext(int maxAttempts, int radius, int minDistance, int avoidRegions, int minSurfaceY, int maxSurfaceScan, java.util.concurrent.atomic.AtomicInteger generateBudget) {}
    private record PendingTeleport(Location target, String auditType, boolean applyRtpCooldownOnSuccess) {}
    private record TpaRequest(UUID requester, UUID target, boolean here, long createdAt) {}
}
