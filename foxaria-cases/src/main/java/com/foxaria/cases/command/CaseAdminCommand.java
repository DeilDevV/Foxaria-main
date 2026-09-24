package com.foxaria.cases.command;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import com.foxaria.api.service.MessageService;
import com.foxaria.cases.model.CaseLocation;
import com.foxaria.cases.service.CaseService;
import com.foxaria.cases.service.ConfigCaseService;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CaseAdminCommand implements CommandExecutor, TabCompleter {

    private final CaseService caseService;
    private final ConfigCaseService config;
    private final JavaPlugin plugin;
    private final MessageService messages;

    public CaseAdminCommand(
        CaseService caseService,
        ConfigCaseService config,
        JavaPlugin plugin,
        MessageService messages
    ) {
        this.caseService = caseService;
        this.config = config;
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.cases.admin")) {
            messages.send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c/caseadmin create <id>");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!config.isValidId(id)) {
                    sender.sendMessage("§cId: латиница, цифры, _ и - (до 49 символов).");
                    return true;
                }
                sender.sendMessage("§aId §f" + id + " §aпринят. Добавьте секцию в §fplugins/FoxariaCases/cases.yml §aи выполните §f/caseadmin reload§a.");
            }
            case "giveblock" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c/caseadmin giveblock <игрок> <caseId> [кол-во]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок не в сети.");
                    return true;
                }
                int amount = args.length > 3 ? parseAmount(args[3]) : 1;
                if (config.definition(args[2]).isEmpty()) {
                    sender.sendMessage("§cКейс не найден в конфиге.");
                    return true;
                }
                caseService.giveBlock(target, args[2], amount);
                sender.sendMessage("§aВыдан блок кейса §f" + args[2] + " §ax" + amount + " → §f" + target.getName());
            }
            case "givekey" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c/caseadmin givekey <игрок> <caseId> [кол-во]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок не в сети.");
                    return true;
                }
                int amount = args.length > 3 ? parseAmount(args[3]) : 1;
                if (config.definition(args[2]).isEmpty()) {
                    sender.sendMessage("§cКейс не найден в конфиге.");
                    return true;
                }
                caseService.giveKeys(target.getUniqueId(), args[2], amount, sender instanceof Player p ? p.getUniqueId() : null);
                sender.sendMessage("§aВыдано §f" + amount + " §aключ(ей) §f" + args[2] + " → §f" + target.getName());
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cТолько для игрока.");
                    return true;
                }
                Block targetBlock = player.getTargetBlockExact(6);
                if (targetBlock == null || caseService.items().readPlacedBlockCaseId(targetBlock) == null) {
                    sender.sendMessage("§cСмотрите на установленный кейс (до 6 блоков).");
                    return true;
                }
                caseService.placedCases().remove(CaseLocation.from(targetBlock, config.serverId()), true);
                sender.sendMessage("§aКейс удалён.");
            }
            case "reload" -> {
                File file = casesConfigFile();
                FileConfiguration reloaded = YamlConfiguration.loadConfiguration(file);
                config.reload(reloaded);
                caseService.reload();
                sender.sendMessage("§aКейсы перезагружены.");
            }
            case "list" -> {
                sender.sendMessage("§6§lКейсы Foxaria");
                for (var definition : config.definitions()) {
                    sender.sendMessage("§7- §f" + definition.id() + " §8» " + definition.displayName().replace('&', '§'));
                }
                caseService.repository().listPlacedCases(config.serverId()).whenComplete((records, throwable) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        int count = records == null ? 0 : records.size();
                        sender.sendMessage("§7Установлено на этом сервере: §f" + count);
                    })
                );
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private File casesConfigFile() {
        File standalone = new File(plugin.getDataFolder(), "cases.yml");
        if (standalone.isFile()) {
            return standalone;
        }
        return new File(plugin.getDataFolder(), "modules/cases.yml");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/caseadmin create|giveblock|givekey|remove|reload|list");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foxaria.cases.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("create", "giveblock", "givekey", "remove", "reload", "list"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("giveblock") || args[0].equalsIgnoreCase("givekey"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("giveblock") || args[0].equalsIgnoreCase("givekey"))) {
            return filter(config.definitions().stream().map(d -> d.id()).toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return List.of("<id>");
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toCollection(ArrayList::new));
    }
}
