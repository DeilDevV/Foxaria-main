package com.foxaria.moderation.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import com.foxaria.core.command.StaffCommandInput;
import com.foxaria.moderation.JdbcModerationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public final class StaffUtilityCommand implements CommandExecutor {

    private final JdbcModerationService moderationService;
    private final MessageService messages;

    public StaffUtilityCommand(JdbcModerationService moderationService, MessageService messages) {
        this.moderationService = moderationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }

        StaffCommandInput input = StaffCommandInput.parse(args);
        String[] parsedArgs = input.args();
        String name = command.getName().toLowerCase();
        String permission = switch (name) {
            case "staffchat" -> "foxaria.mod.staffchat";
            case "socialspy" -> "foxaria.mod.socialspy";
            case "commandspy" -> "foxaria.mod.commandspy";
            case "vanish" -> "foxaria.mod.vanish";
            case "invsee" -> "foxaria.mod.invsee";
            case "echest" -> "foxaria.mod.echest";
            default -> "";
        };
        if (!permission.isBlank() && !FoxariaStaffPermissions.has(player, permission)) {
            messages.send(player, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        switch (name) {
            case "staffchat" -> handleStaffChat(player, parsedArgs);
            case "socialspy" -> messages.send(player, "mod.toggle", toggle(moderationService.socialSpy(), player)
                ? "&aSocialSpy включён."
                : "&eSocialSpy выключен.");
            case "commandspy" -> messages.send(player, "mod.toggle", toggle(moderationService.commandSpy(), player)
                ? "&aCommandSpy включён."
                : "&eCommandSpy выключен.");
            case "vanish" -> handleVanish(player);
            case "invsee" -> openInventory(player, parsedArgs, true);
            case "echest" -> openInventory(player, parsedArgs, false);
            default -> {
            }
        }
        return true;
    }

    private void handleStaffChat(Player sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "mod.staffchat-usage", "&cИспользование: /staffchat <сообщение> [-s]");
            return;
        }
        Component message = Component.text("[Стафф] " + sender.getName() + ": " + String.join(" ", args));
        Bukkit.getOnlinePlayers().stream()
            .filter(target -> FoxariaStaffPermissions.has(target, "foxaria.mod.staffchat"))
            .forEach(target -> target.sendMessage(message));
    }

    private void handleVanish(Player player) {
        boolean enabled = moderationService.toggleVanish(player);
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(
            enabled ? "&aВаниш включён." : "&eВаниш выключен."
        ));
        messages.send(player, "mod.toggle", enabled
            ? "&aВаниш включён."
            : "&eВаниш выключен.");
    }

    private void openInventory(Player viewer, String[] args, boolean regularInventory) {
        if (args.length == 0) {
            messages.send(
                viewer,
                regularInventory ? "mod.invsee-usage" : "mod.echest-usage",
                regularInventory ? "&cИспользование: /invsee <игрок> [-s]" : "&cИспользование: /echest <игрок> [-s]"
            );
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(viewer, "general.player-not-found", "&cИгрок не найден.");
            return;
        }
        if (regularInventory) {
            viewer.openInventory(target.getInventory());
            return;
        }
        viewer.openInventory(target.getEnderChest());
    }

    private boolean toggle(Set<UUID> set, Player player) {
        if (set.add(player.getUniqueId())) {
            return true;
        }
        set.remove(player.getUniqueId());
        return false;
    }
}
