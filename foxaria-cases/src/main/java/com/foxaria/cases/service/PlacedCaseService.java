package com.foxaria.cases.service;

import com.foxaria.cases.CaseRepository;
import com.foxaria.cases.model.CaseDefinition;
import com.foxaria.cases.model.CaseLocation;
import com.foxaria.cases.model.PlacedCaseRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlacedCaseService {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ConfigCaseService config;
    private final CaseRepository repository;
    private final CaseItemService items;
    private final CaseHologramService holograms;
    private final Map<String, ActiveCase> activeCases = new ConcurrentHashMap<>();

    public PlacedCaseService(
        org.bukkit.plugin.java.JavaPlugin plugin,
        ConfigCaseService config,
        CaseRepository repository,
        CaseItemService items,
        CaseHologramService holograms
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.items = items;
        this.holograms = holograms;
    }

    public void restoreAll() {
        repository.listPlacedCases(config.serverId()).whenComplete((records, throwable) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || records == null) {
                    return;
                }
                for (PlacedCaseRecord record : records) {
                    activate(record.location(), record.caseId(), false);
                }
            })
        );
    }

    public void reloadVisuals() {
        for (ActiveCase active : new ArrayList<>(activeCases.values())) {
            deactivate(active.location(), false);
            activate(active.location(), active.caseId(), false);
        }
    }

    public Optional<ActiveCase> activeCase(CaseLocation location) {
        return Optional.ofNullable(activeCases.get(location.key()));
    }

    public Optional<CaseDefinition> definitionAt(CaseLocation location) {
        ActiveCase active = activeCases.get(location.key());
        if (active == null) {
            return Optional.empty();
        }
        return config.definition(active.caseId());
    }

    public void place(Block block, String caseId) {
        CaseLocation location = CaseLocation.from(block, config.serverId());
        items.markPlacedBlock(block, caseId);
        repository.savePlacedCase(location, caseId);
        activate(location, caseId, true);
    }

    public void remove(CaseLocation location, boolean breakBlock) {
        deactivate(location, breakBlock);
        repository.removePlacedCase(location);
    }

    public void hideTemporarily(CaseLocation location) {
        ActiveCase active = activeCases.get(location.key());
        if (active == null) {
            return;
        }
        stopIdle(active);
        World world = Bukkit.getWorld(location.world());
        if (world != null) {
            holograms.despawn(world, active.hologramEntityIds());
        }
        Block block = blockAt(location);
        if (block != null) {
            block.setType(Material.AIR);
        }
    }

    public void restoreAfterAnimation(CaseLocation location) {
        ActiveCase active = activeCases.get(location.key());
        if (active == null) {
            return;
        }
        Block block = blockAt(location);
        if (block != null) {
            config.definition(active.caseId()).ifPresent(definition -> block.setType(definition.blockItem().material()));
            items.markPlacedBlock(block, active.caseId());
        }
        World world = Bukkit.getWorld(location.world());
        if (world != null) {
            config.definition(active.caseId()).ifPresent(definition -> {
                List<UUID> ids = holograms.spawn(location, definition, world);
                active.setHologramEntityIds(ids);
            });
        }
        startIdle(active);
    }

    private void activate(CaseLocation location, String caseId, boolean ensureBlock) {
        Optional<CaseDefinition> definitionOpt = config.definition(caseId);
        if (definitionOpt.isEmpty()) {
            return;
        }
        CaseDefinition definition = definitionOpt.get();
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            return;
        }
        Block block = blockAt(location);
        if (block == null) {
            return;
        }
        if (ensureBlock || block.getType().isAir()) {
            block.setType(definition.blockItem().material());
        }
        items.markPlacedBlock(block, caseId);
        List<UUID> hologramIds = holograms.spawn(location, definition, world);
        ActiveCase active = new ActiveCase(location, caseId, hologramIds);
        activeCases.put(location.key(), active);
        startIdle(active);
    }

    private void deactivate(CaseLocation location, boolean breakBlock) {
        ActiveCase active = activeCases.remove(location.key());
        if (active == null) {
            return;
        }
        stopIdle(active);
        World world = Bukkit.getWorld(location.world());
        if (world != null) {
            holograms.despawn(world, active.hologramEntityIds());
        }
        Block block = blockAt(location);
        if (block != null) {
            items.clearPlacedBlock(block);
            if (breakBlock) {
                block.setType(Material.AIR);
            }
        }
    }

    private void startIdle(ActiveCase active) {
        Optional<CaseDefinition> definitionOpt = config.definition(active.caseId());
        World world = Bukkit.getWorld(active.location().world());
        if (definitionOpt.isEmpty() || world == null) {
            return;
        }
        stopIdle(active);
        BukkitTask task = CaseIdleEffectTask.start(plugin, active.location(), definitionOpt.get(), config, world);
        active.setIdleTask(task);
    }

    private void stopIdle(ActiveCase active) {
        BukkitTask task = active.idleTask();
        if (task != null) {
            task.cancel();
            active.setIdleTask(null);
        }
    }

    private Block blockAt(CaseLocation location) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(location.x(), location.y(), location.z());
    }

    public Location centerLocation(CaseLocation location) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            return null;
        }
        return new Location(world, location.x() + 0.5D, location.y() + 1.0D, location.z() + 0.5D);
    }

    public static final class ActiveCase {
        private final CaseLocation location;
        private final String caseId;
        private List<UUID> hologramEntityIds;
        private BukkitTask idleTask;

        public ActiveCase(CaseLocation location, String caseId, List<UUID> hologramEntityIds) {
            this.location = location;
            this.caseId = caseId;
            this.hologramEntityIds = hologramEntityIds;
        }

        public CaseLocation location() {
            return location;
        }

        public String caseId() {
            return caseId;
        }

        public List<UUID> hologramEntityIds() {
            return hologramEntityIds;
        }

        public void setHologramEntityIds(List<UUID> hologramEntityIds) {
            this.hologramEntityIds = hologramEntityIds;
        }

        public BukkitTask idleTask() {
            return idleTask;
        }

        public void setIdleTask(BukkitTask idleTask) {
            this.idleTask = idleTask;
        }
    }
}
