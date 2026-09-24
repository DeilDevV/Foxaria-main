package com.foxaria.shop.progression;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.util.ItemStackSerializer;
import com.foxaria.shop.ProgressionShopService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AdminProgressionShopCommand implements CommandExecutor, TabCompleter {

    private final ProgressionShopService shop;
    private final MessageService messages;

    public AdminProgressionShopCommand(ProgressionShopService shop, MessageService messages) {
        this.shop = shop;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foxaria.questshop.admin")) {
            messages.send(sender, "general.no-permission", "&cНет прав.");
            return true;
        }
        if (args.length < 1) {
            usage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("add")) {
            if (args.length < 4) {
                sender.sendMessage("§c/adminquestshop add <раздел> <уровень_знаний> <цена_монет> [template <id>]");
                sender.sendMessage("§7Без template: держите предмет в главной руке (игрок).");
                return true;
            }
            ProgressionCategory cat = ProgressionCategory.parse(args[1]).orElse(null);
            if (cat == null) {
                sender.sendMessage("§cРазделы: food, resources, equipment, blocks, special");
                return true;
            }
            int level;
            BigDecimal price;
            try {
                level = Integer.parseInt(args[2]);
                price = new BigDecimal(args[3]).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception e) {
                sender.sendMessage("§cУровень — целое число, цена — число (монеты).");
                return true;
            }
            if (level < 1 || price.compareTo(BigDecimal.ZERO) <= 0) {
                sender.sendMessage("§cУровень ≥ 1, цена > 0.");
                return true;
            }
            boolean templateMode = args.length >= 6 && args[4].equalsIgnoreCase("template");
            String blob;
            String tpl;
            if (templateMode) {
                tpl = args[5];
                if (shop.itemTemplates() == null || !shop.itemTemplates().exists(tpl)) {
                    sender.sendMessage("§cШаблон не найден или модуль шаблонов выключен: §f" + tpl);
                    return true;
                }
                blob = null;
            } else {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cБез template нужен предмет в руке — только игрок.");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    sender.sendMessage("§cДержите предмет в главной руке или укажите §ftemplate <id>");
                    return true;
                }
                tpl = "";
                blob = ItemStackSerializer.serialize(hand.clone().asOne());
            }
            ProgressionShopOffer offer = new ProgressionShopOffer(
                ProgressionRepository.newId(),
                cat.id(),
                level,
                price,
                blob,
                tpl,
                (int) (System.currentTimeMillis() % 1_000_000)
            );
            shop.shopRepository().insertShopOffer(offer).thenRun(() ->
                sender.sendMessage("§aТовар §f" + offer.id() + " §a→ §f" + cat.id() + " §aур.§f" + level + " §aза §e" + price
                    + (templateMode ? " §7(template)" : ""))
            );
            return true;
        }
        if (sub.equals("remove")) {
            if (args.length < 2) {
                sender.sendMessage("§c/adminquestshop remove <id>");
                return true;
            }
            shop.shopRepository().deleteShopOffer(args[1]).thenRun(() ->
                sender.sendMessage("§eУдалено (если было): §f" + args[1])
            );
            return true;
        }
        if (sub.equals("list")) {
            shop.shopRepository().listAllShopOffers().thenAccept(list -> {
                sender.sendMessage("§6Товары магазина знаний: §f" + list.size());
                for (ProgressionShopOffer o : list) {
                    sender.sendMessage("§e" + o.id() + " §7| §f" + o.category() + " §7| ур.§c" + o.requiredKnowledge() + " §7| §a" + o.price());
                }
            });
            return true;
        }
        usage(sender);
        return true;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage("§e/adminquestshop add <раздел> <уровень> <цена> §7| §e... template <id>");
        sender.sendMessage("§7(без template — предмет в руке)");
        sender.sendMessage("§e/adminquestshop remove <id>  §7|  §elist");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foxaria.questshop.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Stream.of("add", "remove", "list")
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return Stream.of(ProgressionCategory.values())
                .map(ProgressionCategory::id)
                .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        return List.of();
    }
}
