package com.foxaria.auth;

import com.foxaria.api.event.FoxariaPlayerAuthenticatedEvent;
import com.foxaria.api.service.ConfigService;
import com.foxaria.api.service.MessageService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class FoxariaAuthService {

    private final JavaPlugin plugin;
    private final AuthRepository repository;
    private final ConfigService configs;
    private final MessageService messages;
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingAuthSession> pendingSessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> reminderTasks = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public FoxariaAuthService(JavaPlugin plugin, AuthRepository repository, ConfigService configs, MessageService messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.configs = configs;
        this.messages = messages;
    }

    public void handleJoin(Player player) {
        if (!config().getBoolean("enabled", true)) {
            authenticated.add(player.getUniqueId());
            return;
        }

        UUID uuid = player.getUniqueId();
        String username = normalize(player.getName());
        authenticated.remove(uuid);
        pendingSessions.put(uuid, new PendingAuthSession(username, false, true));

        showTitle(
            player,
            config().getString("titles.welcome.title", "&6FOXARIA"),
            config().getString("titles.welcome.subtitle", "&fДобро пожаловать на сервер")
        );
        messages.send(player, "auth.welcome", "&eДобро пожаловать на &6Foxaria&e. Сначала войдите или зарегистрируйтесь.");

        repository.findByUuid(uuid).thenCompose(account ->
            account != null
                ? CompletableFuture.completedFuture(account)
                : repository.findByUsername(username)
        ).whenComplete((account, throwable) ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                boolean registered = throwable == null && account != null;
                pendingSessions.put(uuid, new PendingAuthSession(username, registered, false));
                sendPrompt(player, registered);
                startReminder(player, registered);
            })
        );
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        pendingSessions.remove(uuid);
        cancelReminder(uuid);
    }

    public boolean isAuthenticated(UUID playerUuid) {
        return authenticated.contains(playerUuid);
    }

    public boolean requiresAuth(UUID playerUuid) {
        return config().getBoolean("enabled", true) && pendingSessions.containsKey(playerUuid) && !authenticated.contains(playerUuid);
    }

    public boolean isAllowedCommand(String rawMessage) {
        String command = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int spaceIndex = command.indexOf(' ');
        if (spaceIndex >= 0) {
            command = command.substring(0, spaceIndex);
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        return config().getStringList("allowed-commands").stream().anyMatch(value -> normalized.equalsIgnoreCase(value));
    }

    public CompletableFuture<AuthResult> register(Player player, String password, String confirmation) {
        if (isAuthenticated(player.getUniqueId())) {
            return CompletableFuture.completedFuture(AuthResult.ALREADY_AUTHENTICATED);
        }
        if (!password.equals(confirmation)) {
            return CompletableFuture.completedFuture(AuthResult.PASSWORD_MISMATCH);
        }

        int minLength = config().getInt("password.min-length", 6);
        int maxLength = config().getInt("password.max-length", 64);
        if (password.length() < minLength) {
            return CompletableFuture.completedFuture(AuthResult.PASSWORD_TOO_SHORT);
        }
        if (password.length() > maxLength) {
            return CompletableFuture.completedFuture(AuthResult.PASSWORD_TOO_LONG);
        }

        String username = normalize(player.getName());
        return repository.findByUuid(player.getUniqueId()).thenCompose(existingByUuid -> {
            if (existingByUuid != null) {
                return CompletableFuture.completedFuture(AuthResult.ALREADY_REGISTERED);
            }
            return repository.findByUsername(username).thenCompose(existingByName -> {
                if (existingByName != null) {
                    return CompletableFuture.completedFuture(AuthResult.ALREADY_REGISTERED);
                }
                PasswordHasher.HashPayload payload = passwordHasher.hash(password, config().getInt("password.pbkdf2-iterations", 120_000));
                return repository.createAccount(username, player.getUniqueId(), payload)
                    .thenApply(ignored -> AuthResult.SUCCESS);
            });
        }).exceptionally(ignored -> AuthResult.ERROR).thenApply(result -> {
            if (result == AuthResult.SUCCESS) {
                completeAuthentication(player, false);
            }
            return result;
        });
    }

    public CompletableFuture<AuthResult> login(Player player, String password) {
        if (isAuthenticated(player.getUniqueId())) {
            return CompletableFuture.completedFuture(AuthResult.ALREADY_AUTHENTICATED);
        }

        String username = normalize(player.getName());
        return repository.findByUuid(player.getUniqueId()).thenCompose(account ->
            account != null
                ? CompletableFuture.completedFuture(account)
                : repository.findByUsername(username)
        ).thenCompose(account -> {
            if (account == null) {
                return CompletableFuture.completedFuture(AuthResult.NOT_REGISTERED);
            }
            boolean valid = passwordHasher.verify(password, account.passwordHash(), account.salt(), account.iterations());
            if (!valid) {
                return CompletableFuture.completedFuture(AuthResult.INVALID_PASSWORD);
            }
            return repository.recordLogin(username, player.getUniqueId()).thenApply(ignored -> AuthResult.SUCCESS);
        }).exceptionally(ignored -> AuthResult.ERROR).thenApply(result -> {
            if (result == AuthResult.SUCCESS) {
                completeAuthentication(player, true);
            }
            return result;
        });
    }

    public void remind(Player player) {
        if (!requiresAuth(player.getUniqueId())) {
            return;
        }
        PendingAuthSession session = pendingSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.loading()) {
            player.sendActionBar(serializer.deserialize(config().getString("actionbar.loading", "&eПроверяем ваш аккаунт...")));
            return;
        }
        player.sendActionBar(serializer.deserialize(session.registered()
            ? config().getString("actionbar.login", "&e/login <пароль> &7- войдите в аккаунт")
            : config().getString("actionbar.register", "&e/register <пароль> [повтор] &7- создайте аккаунт")));
    }

    public void shutdown() {
        for (BukkitTask task : reminderTasks.values()) {
            task.cancel();
        }
        reminderTasks.clear();
        pendingSessions.clear();
        authenticated.clear();
    }

    private void completeAuthentication(Player player, boolean returningPlayer) {
        UUID uuid = player.getUniqueId();
        authenticated.add(uuid);
        pendingSessions.remove(uuid);
        cancelReminder(uuid);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (returningPlayer) {
                showTitle(
                    player,
                    config().getString("titles.login-success.title", "&aВход выполнен"),
                    config().getString("titles.login-success.subtitle", "&fПриятной игры")
                );
            } else {
                showTitle(
                    player,
                    config().getString("titles.register-success.title", "&aРегистрация завершена"),
                    config().getString("titles.register-success.subtitle", "&fАккаунт создан")
                );
            }
            player.sendActionBar(serializer.deserialize(config().getString("actionbar.success", "&aАвторизация завершена.")));
            plugin.getServer().getPluginManager().callEvent(new FoxariaPlayerAuthenticatedEvent(player, returningPlayer));
        });
    }

    private void sendPrompt(Player player, boolean registered) {
        if (registered) {
            showTitle(
                player,
                config().getString("titles.login.title", "&e/login"),
                config().getString("titles.login.subtitle", "&fВведите пароль для входа")
            );
            messages.send(player, "auth.login-required", "&eВы уже зарегистрированы. Используйте: /login <пароль>");
            return;
        }

        showTitle(
            player,
            config().getString("titles.register.title", "&e/register"),
            config().getString("titles.register.subtitle", "&fСоздайте пароль для входа")
        );
        messages.send(player, "auth.register-required", "&eВы новый игрок. Используйте: /register <пароль> [повтор]");
    }

    private void startReminder(Player player, boolean registered) {
        cancelReminder(player.getUniqueId());
        long interval = Math.max(40L, config().getLong("reminder-interval-ticks", 80L));
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || isAuthenticated(player.getUniqueId())) {
                cancelReminder(player.getUniqueId());
                return;
            }
            player.sendActionBar(serializer.deserialize(registered
                ? config().getString("actionbar.login", "&e/login <пароль> &7- войдите в аккаунт")
                : config().getString("actionbar.register", "&e/register <пароль> [повтор] &7- создайте аккаунт")));
        }, interval, interval);
        reminderTasks.put(player.getUniqueId(), task);
    }

    private void cancelReminder(UUID playerUuid) {
        BukkitTask task = reminderTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void showTitle(Player player, String title, String subtitle) {
        Title.Times times = Title.Times.times(Duration.ofMillis(250L), Duration.ofMillis(1800L), Duration.ofMillis(400L));
        player.showTitle(Title.title(serializer.deserialize(title), serializer.deserialize(subtitle), times));
    }

    private FileConfiguration config() {
        return configs.module("modules/auth.yml");
    }

    private String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private record PendingAuthSession(String username, boolean registered, boolean loading) {
    }
}
