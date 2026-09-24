package com.foxaria.donateshop.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.donateshop.DonateCategory;
import com.foxaria.donateshop.DonateOffer;
import com.foxaria.donateshop.DonateShopRepository;
import com.foxaria.donateshop.DonateShopService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DonateShopCommand implements CommandExecutor, TabCompleter {

    private final DonateShopService service;
    private final MessageService messages;

    public DonateShopCommand(DonateShopService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add") || sub.equals("remove") || sub.equals("list")) {
                if (!sender.hasPermission("foxaria.donateshop.admin")) {
                    messages.send(sender, "general.no-permission", "&cНет прав.");
                    return true;
                }
            }
            if (sub.equals("add")) {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only", "&cТолько игрок (нужен предмет в руке).");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§c/donateshop add <раздел> <цена_токенов>");
                    sender.sendMessage("§7Разделы: armor, weapons, totems, runes, potions, other");
                    return true;
                }
                DonateCategory cat = parseCategory(args[1]);
                if (cat == null) {
                    sender.sendMessage("§cНеизвестный раздел. Используйте: armor, weapons, totems, runes, potions, other");
                    return true;
                }
                long price;
                try {
                    price = Long.parseLong(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cЦена — целое число токенов.");
                    return true;
                }
                if (price <= 0) {
                    sender.sendMessage("§cЦена должна быть больше нуля.");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType().isAir()) {
                    sender.sendMessage("§cДержите предмет в главной руке.");
                    return true;
                }
                DonateOffer offer = new DonateOffer(
                    DonateShopRepository.newId(),
                    cat,
                    price,
                    hand.clone().asOne(),
                    (int) (System.currentTimeMillis() % 1_000_000)
                );
                service.repository().insert(offer).thenRun(() ->
                    sender.sendMessage("§aТовар добавлен: §f" + offer.id() + " §aв §f" + cat.id() + " §aза §b" + price + " §aток.")
                ).exceptionally(ex -> {
                    sender.sendMessage("§cОшибка: " + ex.getCause().getMessage());
                    return null;
                });
                return true;
            }
            if (sub.equals("remove")) {
                if (args.length < 2) {
                    sender.sendMessage("§c/donateshop remove <id>");
                    return true;
                }
                String id = args[1];
                service.repository().delete(id).thenRun(() ->
                    sender.sendMessage("§eУдалено (если было): §f" + id)
                );
                return true;
            }
            if (sub.equals("list")) {
                service.repository().listAll().thenAccept(list -> {
                    if (list.isEmpty()) {
                        sender.sendMessage("§7Донат-магазин пуст.");
                        return;
                    }
                    sender.sendMessage("§6Товары (§f" + list.size() + "§6):");
                    for (DonateOffer o : list) {
                        sender.sendMessage("§e" + o.id() + " §7| §f" + o.category().id() + " §7| §b" + o.priceTokens() + " §7ток.");
                    }
                });
                return true;
            }
            sender.sendMessage("§cНеизвестная подкоманда. §7Используйте §f/donateshop §7или админ: §fadd§7, §fremove§7, §flist§7.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only", "&cЭту команду могут использовать только игроки.");
            return true;
        }
        if (!player.hasPermission("foxaria.donateshop.use")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }
        service.openRoot(player);
        return true;
    }

    private DonateCategory parseCategory(String raw) {
        return DonateCategory.parse(raw).orElse(null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foxaria.donateshop.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return Stream.of("add", "remove", "list")
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            List<String> ids = new ArrayList<>();
            for (DonateCategory c : DonateCategory.values()) {
                if (c.id().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    ids.add(c.id());
                }
            }
            return ids;
        }
        return List.of();
    }
}
