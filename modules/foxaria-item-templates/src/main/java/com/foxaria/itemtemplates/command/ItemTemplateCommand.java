package com.foxaria.itemtemplates.command;

import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.itemtemplates.ItemTemplateEditorConfig;
import com.foxaria.itemtemplates.gui.ItemForgeMenu;
import com.foxaria.itemtemplates.gui.ItemTemplateWorkbenchMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemTemplateCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ItemTemplateService templates;
    private final MessageService messages;
    private final MenuManager menuManager;
    private final ItemTemplateEditorConfig editorConfig;

    public ItemTemplateCommand(
        JavaPlugin plugin,
        ItemTemplateService templates,
        MessageService messages,
        MenuManager menuManager,
        ItemTemplateEditorConfig editorConfig
    ) {
        this.plugin = plugin;
        this.templates = templates;
        this.messages = messages;
        this.menuManager = menuManager;
        this.editorConfig = editorConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.itemtemplate.admin")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "itemtemplate.usage", "&e/itpl forge &7— кузница способностей &8| &e/itpl workbench &7— полный редактор &8| &e/itpl save <id>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui", "forge" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cТолько для игроков.");
                    return true;
                }
                menuManager.open(player, new ItemForgeMenu(plugin));
                return true;
            }
            case "edit", "workbench" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cТолько для игроков.");
                    return true;
                }
                // Старый полный верстак остаётся для тонких настроек:
                // атрибуты, ванильные чары, зелья, YAML и шаблоны в БД.
                menuManager.open(player, new ItemTemplateWorkbenchMenu(plugin, menuManager, templates, messages, editorConfig, ItemTemplateWorkbenchMenu.Panel.EDIT_HUB));
                return true;
            }
            case "reload" -> {
                templates.reload().thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(sender, "itemtemplate.reloaded", "&aКеш шаблонов обновлён.")));
                return true;
            }
            case "list" -> {
                List<String> ids = templates.listTemplateIds().stream().sorted().toList();
                if (ids.isEmpty()) {
                    messages.send(sender, "itemtemplate.list-empty", "&7Шаблонов пока нет.");
                    return true;
                }
                messages.send(sender, "itemtemplate.list", "&6Шаблоны: &f<ids>",
                    new MessageService.Placeholder("ids", String.join(", ", ids)));
                return true;
            }
            case "export" -> {
                if (args.length < 2) {
                    messages.send(sender, "itemtemplate.export-usage", "&c/itemtemplate export <id>");
                    return true;
                }
                String id = args[1];
                if (!templates.exists(id)) {
                    messages.send(sender, "itemtemplate.missing", "&cШаблон не найден: <id>", new MessageService.Placeholder("id", id));
                    return true;
                }
                messages.send(sender, "itemtemplate.yaml-snippet", "&7[YAML] &f<text>",
                    new MessageService.Placeholder("text", templates.yamlReference(id)));
                return true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    messages.send(sender, "itemtemplate.delete-usage", "&c/itemtemplate delete <id>");
                    return true;
                }
                String id = args[1];
                templates.deleteTemplate(id).thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (Boolean.TRUE.equals(ok)) {
                        messages.send(sender, "itemtemplate.deleted", "&eШаблон удалён: <id>", new MessageService.Placeholder("id", id));
                    } else {
                        messages.send(sender, "itemtemplate.missing", "&cШаблон не найден: <id>", new MessageService.Placeholder("id", id));
                    }
                }));
                return true;
            }
            case "give" -> {
                if (args.length < 2) {
                    messages.send(sender, "itemtemplate.give-usage", "&c/itemtemplate give <id> [игрок]");
                    return true;
                }
                String id = args[1];
                Player target = args.length >= 3 ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    messages.send(sender, "general.player-not-found", "&cИгрок не найден.");
                    return true;
                }
                ItemStack stack = templates.cloneTemplate(id).orElse(null);
                if (stack == null) {
                    messages.send(sender, "itemtemplate.missing", "&cШаблон не найден: <id>", new MessageService.Placeholder("id", id));
                    return true;
                }
                target.getInventory().addItem(stack.clone());
                messages.send(sender, "itemtemplate.given-other", "&aВыдан шаблон &f<id>&a игроку &f<player>",
                    new MessageService.Placeholder("id", id),
                    new MessageService.Placeholder("player", target.getName()));
                return true;
            }
            case "save" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cТолько для игроков.");
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "itemtemplate.save-usage", "&c/itemtemplate save <id>");
                    return true;
                }
                String id = args[1];
                if (!id.matches("[A-Za-z0-9_-]+")) {
                    messages.send(sender, "itemtemplate.id-invalid", "&cID: только латиница, цифры, _ и -");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    messages.send(sender, "itemtemplate.save-empty-hand", "&cДержите предмет в главной руке.");
                    return true;
                }
                templates.saveTemplate(id, hand.clone(), "").whenComplete((v, ex) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ex != null) {
                        messages.send(player, "itemtemplate.save-failed", "&cНе удалось сохранить шаблон.");
                    } else {
                        messages.send(player, "itemtemplate.saved", "&aШаблон сохранён: &f<id>",
                            new MessageService.Placeholder("id", id));
                    }
                }));
                return true;
            }
            case "clearlore" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cТолько для игроков.");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    messages.send(sender, "itemtemplate.save-empty-hand", "&cДержите предмет в главной руке.");
                    return true;
                }
                ItemMeta meta = hand.getItemMeta();
                if (meta == null) {
                    messages.send(sender, "itemtemplate.clearlore-fail", "&cУ предмета нет метаданных.");
                    return true;
                }
                meta.lore(null);
                hand.setItemMeta(meta);
                player.getInventory().setItemInMainHand(hand);
                messages.send(player, "itemtemplate.clearlore-done", "&aВсё описание (lore) снято. Имя предмета не менялось.");
                return true;
            }
            case "glint" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cТолько для игроков.");
                    return true;
                }
                if (args.length < 2) {
                    messages.send(sender, "itemtemplate.glint-usage", "&c/itemtemplate glint on|off");
                    return true;
                }
                String mode = args[1].toLowerCase(Locale.ROOT);
                boolean on = mode.equals("on") || mode.equals("1") || mode.equals("true") || mode.equals("да");
                boolean off = mode.equals("off") || mode.equals("0") || mode.equals("false") || mode.equals("нет");
                if (!on && !off) {
                    messages.send(sender, "itemtemplate.glint-usage", "&c/itemtemplate glint on|off");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    messages.send(sender, "itemtemplate.save-empty-hand", "&cДержите предмет в главной руке.");
                    return true;
                }
                ItemMeta meta = hand.getItemMeta();
                if (meta == null) {
                    messages.send(sender, "itemtemplate.clearlore-fail", "&cУ предмета нет метаданных.");
                    return true;
                }
                meta.setEnchantmentGlintOverride(on);
                hand.setItemMeta(meta);
                player.getInventory().setItemInMainHand(hand);
                if (on) {
                    messages.send(player, "itemtemplate.glint-on", "&aВключён пустой блеск (без чар).");
                } else {
                    messages.send(player, "itemtemplate.glint-off", "&eБлеск выключен.");
                }
                return true;
            }
            default -> messages.send(sender, "itemtemplate.usage", "&e/itpl gui | edit | save | give | clearlore | glint on|off | …");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foxaria.itemtemplate.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("gui", "forge", "edit", "workbench", "save", "delete", "give", "list", "reload", "export", "clearlore", "glint").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("give")
            || args[0].equalsIgnoreCase("export"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return templates.listTemplateIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return null;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("glint")) {
            return List.of("on", "off").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .toList();
        }
        return new ArrayList<>();
    }
}
