package com.foxaria.modernfurnace;

import com.foxaria.api.service.SmeltBonusHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModernFurnaceService {

    private final JavaPlugin plugin;
    private final ModernFurnaceRepository repository;
    private final ModernFurnaceConfig config;
    @SuppressWarnings("unused")
    private final ModernFurnaceKeys keys;
    private final SmeltBonusHook smeltBonusHook;

    private final Map<String, PersistedFurnaceJson> cache = new ConcurrentHashMap<>();
    private int tickCounter;

    public ModernFurnaceService(
        JavaPlugin plugin,
        ModernFurnaceRepository repository,
        ModernFurnaceKeys keys,
        ModernFurnaceConfig config,
        SmeltBonusHook smeltBonusHook
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.keys = keys;
        this.config = config;
        this.smeltBonusHook = smeltBonusHook;
    }

    public ModernFurnaceConfig config() {
        return config;
    }

    public static String key(Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            return "";
        }
        return w.getUID().toString() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public static Location parseKey(String k) {
        int first = k.indexOf(':');
        if (first < 0) {
            return null;
        }
        String uuidPart = k.substring(0, first);
        String rest = k.substring(first + 1);
        String[] c = rest.split(":");
        if (c.length != 3) {
            return null;
        }
        try {
            UUID u = UUID.fromString(uuidPart);
            World world = Bukkit.getWorld(u);
            if (world == null) {
                return null;
            }
            int x = Integer.parseInt(c[0]);
            int y = Integer.parseInt(c[1]);
            int z = Integer.parseInt(c[2]);
            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    public void loadChunk(World world, int cx, int cz) {
        repository.loadInChunk(world, cx, cz).thenAccept(map ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (Map.Entry<Location, PersistedFurnaceJson> e : map.entrySet()) {
                    putMemory(e.getKey(), e.getValue());
                }
            }));
    }

    public void saveAllInChunk(World w, int cx, int cz) {
        for (String k : java.util.Set.copyOf(cache.keySet())) {
            Location l = parseKey(k);
            if (l == null || !w.equals(l.getWorld())) {
                continue;
            }
            if ((l.getBlockX() >> 4) != cx || (l.getBlockZ() >> 4) != cz) {
                continue;
            }
            saveAsync(l);
        }
    }

    /** Только наши печи (есть в кэше или в БД). Без записи пустого шаблона. */
    public Optional<PersistedFurnaceJson> findOurFurnace(Location loc) {
        String k = key(loc);
        PersistedFurnaceJson mem = cache.get(k);
        if (mem != null) {
            ModernFurnaceStateFactory.normalize(mem);
            config.clampLevels(mem);
            return Optional.of(mem);
        }
        try {
            Optional<PersistedFurnaceJson> opt = repository.load(loc).join();
            if (opt.isEmpty()) {
                return Optional.empty();
            }
            putMemory(loc, opt.get());
            return Optional.ofNullable(cache.get(k));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putMemory(Location loc, PersistedFurnaceJson j) {
        PersistedFurnaceJson n = ModernFurnaceStateFactory.normalize(j);
        config.clampLevels(n);
        cache.put(key(loc), n);
    }

    public void saveAsync(Location loc) {
        PersistedFurnaceJson j = cache.get(key(loc));
        if (j != null) {
            repository.save(loc, j);
        }
    }

    public void removeBlock(Location loc) {
        cache.remove(key(loc));
        repository.delete(loc);
    }

    public Map<String, PersistedFurnaceJson> snapshotCache() {
        return Map.copyOf(cache);
    }

    public void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void tick() {
        Iterator<Map.Entry<String, PersistedFurnaceJson>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PersistedFurnaceJson> e = it.next();
            Location loc = parseKey(e.getKey());
            if (loc == null) {
                it.remove();
                continue;
            }
            World w = loc.getWorld();
            if (w == null || !w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            Block b = loc.getBlock();
            if (b.getType() != Material.FURNACE) {
                continue;
            }
            PersistedFurnaceJson d = e.getValue();
            ModernFurnaceEngine.processTick(loc, d, config, smeltBonusHook);
            if (b.getBlockData() instanceof Furnace fd) {
                fd.setLit(d.fuelTicksRemaining > 0);
                b.setBlockData(fd, false);
            }
        }
        maybeRefreshOpenGuis();
    }

    /**
     * Обновляет открытые меню печи без перезахода (интервал из gui.refresh-interval-ticks).
     * Тикер печи вызывается каждые 2 тика сервера.
     */
    private void maybeRefreshOpenGuis() {
        tickCounter++;
        int interval = config.guiRefreshIntervalTicks();
        int taskPeriod = 2;
        int everyNthRun = Math.max(1, (interval + taskPeriod - 1) / taskPeriod);
        if (tickCounter % everyNthRun != 0) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            InventoryHolder h = top.getHolder();
            if (!(h instanceof ModernFurnaceHolder mh)) {
                continue;
            }
            Location loc = mh.location();
            PersistedFurnaceJson d = cache.get(key(loc));
            if (d == null) {
                continue;
            }
            if (mh.kind() == ModernFurnaceHolder.Kind.MAIN) {
                ModernFurnaceMenus.refreshMain(top, d, config);
            } else if (mh.kind() == ModernFurnaceHolder.Kind.UPGRADES) {
                ModernFurnaceMenus.refreshUpgrades(top, d, config);
            }
        }
    }
}
