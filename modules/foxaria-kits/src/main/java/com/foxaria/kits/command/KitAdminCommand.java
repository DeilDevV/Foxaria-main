package com.foxaria.kits.command;

import com.foxaria.api.model.KitContents;
import com.foxaria.api.model.KitDefinition;
import com.foxaria.api.service.MessageService;
import com.foxaria.kits.CompositeKitService;
import com.foxaria.kits.KitDefinitionRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class KitAdminCommand implements CommandExecutor, TabCompleter {

    private final CompositeKitService kitService;
    private final KitDefinitionRepository definitions;
    private final MessageService messages;

    public KitAdminCommand(CompositeKitService kitService, KitDefinitionRepository definitions, MessageService messages) {
        this.kitService = kitService;
        this.definitions = definitions;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.kits.admin")) {
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
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cТолько для игрока.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c/kitadmin create <id> [отображаемое имя…]");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9][a-z0-9_-]{0,48}")) {
                    sender.sendMessage("§cID: латиница, цифры, _ и -, до 50 символов.");
                    return true;
                }
                String display = args.length >= 3
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                    : id;
                KitContents contents = KitContents.fromPlayer(player);
                if (contents.nonEmptyCount() == 0) {
                    sender.sendMessage("§cИнвентарь и броня пусты — нечего сохранять.");
                    return true;
                }
                String permission = "foxaria.kits.use." + id;
                KitDefinition def = new KitDefinition(id, display, 0L, permission, 0L, contents);
                definitions.upsert(def).thenRun(() -> {
                    kitService.refreshDatabaseKits();
                    sender.sendMessage("§aНабор §f" + id + " §aсохранён из вашего инвентаря (§f" + contents.nonEmptyCount() + " §aпредметов). Право: §7" + permission);
                }).exceptionally(ex -> {
                    sender.sendMessage("§cОшибка: " + ex.getCause().getMessage());
                    return null;
                });
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c/kitadmin delete <id>");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                definitions.delete(id).thenRun(() -> {
                    kitService.refreshDatabaseKits();
                    sender.sendMessage("§eНабор §f" + id + " §eудалён из базы (если существовал). YAML-наборы удаляйте из kits.yml вручную.");
                });
            }
            case "list" -> {
                definitions.listAll().thenAccept(list ->
                    sender.sendMessage("§6Наборы в БД (§f" + list.size() + "§6): §f" +
                        list.stream().map(KitDefinition::id).collect(Collectors.joining(", ")))
                );
            }
            case "reload" -> {
                kitService.refreshDatabaseKits();
                sender.sendMessage("§aКеш наборов из базы обновлён.");
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/kitadmin create <id> [имя] §7— сохранить инвентарь+броню+оффхенд");
        sender.sendMessage("§e/kitadmin delete <id> §7— удалить из БД");
        sender.sendMessage("§e/kitadmin list §7— список БД-наборов");
        sender.sendMessage("§e/kitadmin reload §7— перечитать БД");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foxaria.kits.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("create", "delete", "list", "reload").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
