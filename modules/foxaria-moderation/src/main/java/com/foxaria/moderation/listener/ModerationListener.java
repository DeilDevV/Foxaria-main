package com.foxaria.moderation.listener;

import com.foxaria.api.service.MessageService;
import com.foxaria.api.service.SecurityService;
import com.foxaria.moderation.JdbcModerationService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModerationListener implements Listener {

    private final JavaPlugin plugin;
    private final JdbcModerationService moderationService;
    private final MessageService messages;
    private final FileConfiguration config;
    private final SecurityService securityService;
    private final Map<UUID, ChatState> chatStates = new ConcurrentHashMap<>();
    private final Map<String, Long> joinWindows = new ConcurrentHashMap<>();

    public ModerationListener(JavaPlugin plugin, JdbcModerationService moderationService, MessageService messages, FileConfiguration config, SecurityService securityService) {
        this.plugin = plugin;
        this.moderationService = moderationService;
        this.messages = messages;
        this.config = config;
        this.securityService = securityService;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        moderationService.refresh(event.getUniqueId()).join();
        var activeBan = moderationService.activeBanRecord(event.getUniqueId());
        if (activeBan != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, moderationService.renderPunishmentPlain(activeBan));
            return;
        }
        if (!config.getBoolean("anti-bot.enabled", true) || event.getAddress() == null) {
            return;
        }
        String address = event.getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        long minInterval = config.getLong("anti-bot.min-join-interval-millis", 500L);
        long burstWindow = Math.max(1000L, config.getLong("anti-bot.burst-window-millis", 10_000L));
        int maxJoins = config.getInt("anti-bot.max-joins-per-window", 3);
        long lastJoin = joinWindows.getOrDefault(address, 0L);
        if (lastJoin > 0 && now - lastJoin < minInterval) {
            blockJoin(event, address, "ANTI_BOT_INTERVAL_BLOCK", "blocked by min join interval");
            return;
        }
        joinWindows.put(address, now);
        String burstKey = address + ":" + (now / burstWindow);
        long hits = joinWindows.merge(burstKey, 1L, Long::sum);
        if (hits > maxJoins) {
            blockJoin(event, address, "ANTI_BOT_BURST_BLOCK", "blocked by burst window");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        moderationService.refresh(joining.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, moderationService::refreshVanishVisibility);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        moderationService.handleQuit(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, moderationService::refreshVanishVisibility);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (moderationService.isMuted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "mod.muted", "&cВаш чат ограничен мутом.");
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (plain.isBlank()) {
            event.setCancelled(true);
            return;
        }
        if (isFilteredMessage(plain)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "mod.chat-filtered", "&cЭто сообщение заблокировано фильтром чата.");
            if (securityService != null) {
                securityService.recordEvent(event.getPlayer().getUniqueId(), "CHAT_FILTER_BLOCK", plain);
            }
            return;
        }
        if (!allowChat(event.getPlayer(), plain)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "mod.spam", "&cСлишком быстро. Замедлитесь в чате.");
            if (securityService != null) {
                securityService.recordEvent(event.getPlayer().getUniqueId(), "CHAT_SPAM_BLOCK", plain);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!moderationService.isFrozen(event.getPlayer().getUniqueId()) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (moderationService.isFrozen(event.getPlayer().getUniqueId()) && !isFrozenAllowed(event.getMessage())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "mod.freeze", "&cВы заморожены администрацией.");
            return;
        }
        if (moderationService.commandSpy().isEmpty() && moderationService.socialSpy().isEmpty()) {
            return;
        }
        String message = event.getMessage();
        for (UUID uuid : moderationService.commandSpy()) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null && !viewer.equals(event.getPlayer())) {
                viewer.sendMessage("[CommandSpy] " + event.getPlayer().getName() + ": " + message);
            }
        }
        if (message.startsWith("/msg") || message.startsWith("/tell") || message.startsWith("/w") || message.startsWith("/r")) {
            for (UUID uuid : moderationService.socialSpy()) {
                Player viewer = Bukkit.getPlayer(uuid);
                if (viewer != null && !viewer.equals(event.getPlayer())) {
                    viewer.sendMessage("[SocialSpy] " + event.getPlayer().getName() + ": " + message);
                }
            }
        }
    }

    private void blockJoin(AsyncPlayerPreLoginEvent event, String address, String eventType, String reason) {
        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            config.getString("anti-bot.kick-message", "&cСлишком много подключений. Попробуйте снова через несколько секунд.")
        );
        if (securityService != null) {
            securityService.recordEvent(null, eventType, address + " " + reason);
        }
    }

    private boolean allowChat(Player player, String plain) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1000L, config.getLong("anti-spam.message-window-seconds", 4L) * 1000L);
        int maxIdentical = config.getInt("anti-spam.max-identical-messages", 3);
        int maxWindowMessages = config.getInt("anti-spam.max-messages-per-window", 6);
        String normalized = normalize(plain);
        UUID uuid = player.getUniqueId();
        ChatState previous = chatStates.getOrDefault(uuid, new ChatState("", 0L, 0, 0L, 0));
        int identicalMessages = normalized.equals(previous.lastMessage()) && now - previous.lastAt() <= windowMillis
            ? previous.identicalMessages() + 1
            : 1;
        long bucket = now / windowMillis;
        int windowMessages = bucket == previous.windowBucket() ? previous.windowMessages() + 1 : 1;
        chatStates.put(uuid, new ChatState(normalized, now, identicalMessages, bucket, windowMessages));
        return identicalMessages <= maxIdentical && windowMessages <= maxWindowMessages;
    }

    private boolean isFilteredMessage(String plain) {
        if (!config.getBoolean("chat-filter.enabled", true)) {
            return false;
        }
        String normalized = normalize(plain);
        for (String phrase : config.getStringList("chat-filter.blocked-phrases")) {
            if (!phrase.isBlank() && normalized.contains(phrase.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        int minCapsLength = config.getInt("chat-filter.min-caps-length", 12);
        double maxCapsRatio = config.getDouble("chat-filter.max-caps-ratio", 0.75D);
        if (plain.length() >= minCapsLength) {
            long letters = plain.chars().filter(Character::isLetter).count();
            long upper = plain.chars().filter(Character::isUpperCase).count();
            if (letters >= minCapsLength && upper > 0 && (double) upper / (double) letters > maxCapsRatio) {
                return true;
            }
        }
        return false;
    }

    private boolean isFrozenAllowed(String rawCommand) {
        String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        int spaceIndex = command.indexOf(' ');
        if (spaceIndex >= 0) {
            command = command.substring(0, spaceIndex);
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        for (String value : config.getStringList("frozen-allowed-commands")) {
            if (normalized.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private record ChatState(String lastMessage, long lastAt, int identicalMessages, long windowBucket, int windowMessages) {
    }
}
