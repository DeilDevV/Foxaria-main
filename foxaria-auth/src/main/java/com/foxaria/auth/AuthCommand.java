package com.foxaria.auth;

import com.foxaria.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuthCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final FoxariaAuthService authService;
    private final MessageService messages;

    public AuthCommand(JavaPlugin plugin, FoxariaAuthService authService, MessageService messages) {
        this.plugin = plugin;
        this.authService = authService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("register")) {
            if (args.length < 1) {
                messages.send(player, "auth.register-usage", "&cИспользование: /register <пароль> [повтор]");
                return true;
            }
            String confirmation = args.length > 1 ? args[1] : args[0];
            authService.register(player, args[0], confirmation).thenAccept(result ->
                plugin.getServer().getScheduler().runTask(plugin, () -> handleRegisterResult(player, result))
            );
            return true;
        }

        if (args.length < 1) {
            messages.send(player, "auth.login-usage", "&cИспользование: /login <пароль>");
            return true;
        }
        authService.login(player, args[0]).thenAccept(result ->
            plugin.getServer().getScheduler().runTask(plugin, () -> handleLoginResult(player, result))
        );
        return true;
    }

    private void handleRegisterResult(Player player, AuthResult result) {
        if (!player.isOnline()) {
            return;
        }
        switch (result) {
            case SUCCESS -> messages.send(player, "auth.register-success", "&aРегистрация успешно завершена.");
            case ALREADY_REGISTERED -> messages.send(player, "auth.already-registered", "&cЭтот аккаунт уже зарегистрирован. Используйте /login <пароль>.");
            case ALREADY_AUTHENTICATED -> messages.send(player, "auth.already-authenticated", "&eВы уже вошли в аккаунт.");
            case PASSWORD_MISMATCH -> messages.send(player, "auth.password-mismatch", "&cПароли не совпадают.");
            case PASSWORD_TOO_SHORT -> messages.send(player, "auth.password-too-short", "&cПароль слишком короткий.");
            case PASSWORD_TOO_LONG -> messages.send(player, "auth.password-too-long", "&cПароль слишком длинный.");
            default -> messages.send(player, "auth.error", "&cНе удалось завершить регистрацию. Попробуйте снова.");
        }
    }

    private void handleLoginResult(Player player, AuthResult result) {
        if (!player.isOnline()) {
            return;
        }
        switch (result) {
            case SUCCESS -> messages.send(player, "auth.login-success", "&aВход выполнен.");
            case NOT_REGISTERED -> messages.send(player, "auth.not-registered", "&cАккаунт не найден. Используйте /register <пароль> [повтор].");
            case INVALID_PASSWORD -> messages.send(player, "auth.invalid-password", "&cНеверный пароль.");
            case ALREADY_AUTHENTICATED -> messages.send(player, "auth.already-authenticated", "&eВы уже вошли в аккаунт.");
            default -> messages.send(player, "auth.error", "&cНе удалось выполнить вход. Попробуйте снова.");
        }
    }
}
