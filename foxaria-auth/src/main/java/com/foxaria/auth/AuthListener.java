package com.foxaria.auth;

import com.foxaria.api.service.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AuthListener implements Listener {

    private final FoxariaAuthService authService;
    private final MessageService messages;

    public AuthListener(FoxariaAuthService authService, MessageService messages) {
        this.authService = authService;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        authService.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        authService.handleQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!authService.requiresAuth(event.getPlayer().getUniqueId()) || event.getTo() == null) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
            authService.remind(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        if (!authService.requiresAuth(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "auth.chat-blocked", "&cСначала войдите в аккаунт или зарегистрируйтесь.");
        authService.remind(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!authService.requiresAuth(event.getPlayer().getUniqueId())) {
            return;
        }
        if (authService.isAllowedCommand(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "auth.command-blocked", "&cДо авторизации доступны только /register и /login.");
        authService.remind(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (block(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (block(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (block(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && block(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && block(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (block(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && block(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && block(player)) {
            event.setCancelled(true);
        }
    }

    private boolean block(Player player) {
        if (!authService.requiresAuth(player.getUniqueId())) {
            return false;
        }
        authService.remind(player);
        return true;
    }
}
