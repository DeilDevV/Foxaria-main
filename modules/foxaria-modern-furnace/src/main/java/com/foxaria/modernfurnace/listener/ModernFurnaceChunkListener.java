package com.foxaria.modernfurnace.listener;

import com.foxaria.modernfurnace.ModernFurnaceService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public final class ModernFurnaceChunkListener implements Listener {

    private final ModernFurnaceService service;

    public ModernFurnaceChunkListener(ModernFurnaceService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoad(ChunkLoadEvent event) {
        service.loadChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        service.saveAllInChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }
}
