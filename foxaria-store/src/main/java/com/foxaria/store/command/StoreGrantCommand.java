package com.foxaria.store.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.store.TebexFulfillmentService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public final class StoreGrantCommand implements CommandExecutor {

    private final TebexFulfillmentService service;
    private final MessageService messages;

    public StoreGrantCommand(TebexFulfillmentService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.store.manage")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length < 3) {
            messages.send(sender, "store.usage", "&cИспользование: /storegrant [grant|revoke] <транзакция> <игрок> <пакет>");
            return true;
        }
        boolean revoke = args[0].equalsIgnoreCase("revoke");
        int offset = revoke || args[0].equalsIgnoreCase("grant") ? 1 : 0;
        if (args.length < 3 + offset) {
            messages.send(sender, "store.usage", "&cИспользование: /storegrant [grant|revoke] <транзакция> <игрок> <пакет>");
            return true;
        }
        UUID playerUuid = Bukkit.getOfflinePlayer(args[1 + offset]).getUniqueId();
        String transactionId = args[offset];
        String packageId = args[2 + offset];
        if (revoke) {
            service.enqueueRevoke(transactionId, playerUuid, packageId);
        } else {
            service.enqueue(transactionId, playerUuid, packageId);
        }
        messages.send(
            sender,
            revoke ? "store.queued-revoke" : "store.queued",
            revoke ? "&eОткат пакета <package> поставлен в очередь для игрока <player>." : "&aПакет <package> поставлен в очередь для игрока <player>.",
            new MessageService.Placeholder("package", packageId),
            new MessageService.Placeholder("player", args[1 + offset])
        );
        return true;
    }
}
