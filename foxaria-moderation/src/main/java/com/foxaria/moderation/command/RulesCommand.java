package com.foxaria.moderation.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.moderation.PunishmentCatalog;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class RulesCommand implements CommandExecutor {

    private final MessageService messages;
    private final PunishmentCatalog catalog;

    public RulesCommand(MessageService messages, PunishmentCatalog catalog) {
        this.messages = messages;
        this.catalog = catalog;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        messages.send(sender, "mod.rules-header", "&6&lFOXARIA &8» &fПричины наказаний");
        for (PunishmentCatalog.Entry entry : catalog.all()) {
            String duration = entry.durationSeconds() <= 0L ? "перманентно" : human(entry.durationSeconds());
            messages.send(sender, "mod.rules-line",
                "&e<code> &8• &f<title> &8• &7<type> &8• &f<duration>\n&8  <text>",
                new MessageService.Placeholder("code", entry.code()),
                new MessageService.Placeholder("title", entry.title()),
                new MessageService.Placeholder("type", entry.type()),
                new MessageService.Placeholder("duration", duration),
                new MessageService.Placeholder("text", entry.rulesText()));
        }
        return true;
    }

    private String human(long seconds) {
        if (seconds % 86400L == 0L) {
            return (seconds / 86400L) + "д";
        }
        if (seconds % 3600L == 0L) {
            return (seconds / 3600L) + "ч";
        }
        if (seconds % 60L == 0L) {
            return (seconds / 60L) + "м";
        }
        return seconds + "с";
    }
}
