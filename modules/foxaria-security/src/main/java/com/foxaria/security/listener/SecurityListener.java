package com.foxaria.security.listener;

import com.foxaria.api.service.MessageService;
import com.foxaria.security.JdbcSecurityService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SecurityListener implements Listener {

    private final JdbcSecurityService securityService;
    private final MessageService messages;
    private final FileConfiguration config;
    private final ConcurrentMap<String, Long> containerWindows = new ConcurrentHashMap<>();
    private final Set<String> blockedRoots;
    private final Set<String> blockedSubstrings;

    public SecurityListener(org.bukkit.plugin.java.JavaPlugin plugin, JdbcSecurityService securityService, MessageService messages, FileConfiguration config) {
        this.securityService = securityService;
        this.messages = messages;
        this.config = config;
        this.blockedRoots = new HashSet<>();
        for (String value : config.getStringList("exploit-blacklist.blocked-roots")) {
            String normalized = value.toLowerCase(Locale.ROOT).trim();
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (!normalized.isBlank()) {
                blockedRoots.add(normalized);
            }
        }
        this.blockedSubstrings = new HashSet<>();
        for (String value : config.getStringList("exploit-blacklist.blocked-substrings")) {
            String normalized = value.toLowerCase(Locale.ROOT).trim();
            if (!normalized.isBlank()) {
                blockedSubstrings.add(normalized);
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        String[] parts = message.split(" ");
        String key = parts[0].toLowerCase(Locale.ROOT);
        if (!securityService.allowCommand(event.getPlayer(), key)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "security.rate-limit", "&cSlow down. Command rate limit reached.");
            return;
        }
        if (!isBlockedCommand(message)) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "security.blocked-command", "&cThat command is blocked on this server.");
        securityService.recordEvent(event.getPlayer().getUniqueId(), "EXPLOIT_COMMAND_BLOCK", "Blocked blacklisted command: " + message);
    }

    @EventHandler
    public void onBook(PlayerEditBookEvent event) {
        if (!config.getBoolean("payload-protection.block-book-payloads", true)) {
            return;
        }
        if (event.getNewBookMeta().getPages().stream().mapToInt(String::length).sum() > 10000) {
            event.setCancelled(true);
            securityService.recordEvent(event.getPlayer().getUniqueId(), "BOOK_PAYLOAD_BLOCK", "Oversized book payload blocked");
        }
    }

    @EventHandler
    public void onSign(SignChangeEvent event) {
        if (!config.getBoolean("payload-protection.block-sign-payloads", true)) {
            return;
        }
        int total = 0;
        for (String line : event.getLines()) {
            total += line.length();
        }
        if (total > 400) {
            event.setCancelled(true);
            securityService.recordEvent(event.getPlayer().getUniqueId(), "SIGN_PAYLOAD_BLOCK", "Oversized sign payload blocked");
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !config.getBoolean("container-spam.enabled", true)) {
            return;
        }
        if (!isTrackableContainer(event.getInventory().getType())) {
            return;
        }
        long windowMillis = Math.max(250L, config.getLong("container-spam.window-millis", 2000L));
        int maxOpens = config.getInt("container-spam.max-opens-per-window", 12);
        long bucket = System.currentTimeMillis() / windowMillis;
        String key = player.getUniqueId() + ":" + bucket;
        long hits = containerWindows.merge(key, 1L, Long::sum);
        if (hits <= maxOpens) {
            return;
        }
        event.setCancelled(true);
        messages.send(player, "security.container-spam", "&cToo many container interactions. Slow down.");
        securityService.recordEvent(player.getUniqueId(), "CONTAINER_SPAM_BLOCK", "Blocked rapid container access");
    }

    private boolean isBlockedCommand(String rawMessage) {
        if (!config.getBoolean("exploit-blacklist.enabled", true)) {
            return false;
        }
        String normalized = rawMessage.toLowerCase(Locale.ROOT);
        String commandRoot = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        int spaceIndex = commandRoot.indexOf(' ');
        if (spaceIndex >= 0) {
            commandRoot = commandRoot.substring(0, spaceIndex);
        }
        if (blockedRoots.contains(commandRoot)) {
            return true;
        }
        for (String fragment : blockedSubstrings) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTrackableContainer(InventoryType type) {
        return switch (type) {
            case CHEST, BARREL, HOPPER, SHULKER_BOX, DISPENSER, DROPPER, FURNACE, BLAST_FURNACE, SMOKER,
                ENDER_CHEST, BREWING, ANVIL, SMITHING, CARTOGRAPHY, BEACON, MERCHANT -> true;
            default -> false;
        };
    }
}
