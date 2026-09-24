package com.foxaria.hubguard;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class WipeCommand implements CommandExecutor, TabCompleter, Listener {

    private final FoxariaHubGuardPlugin plugin;
    private final WipeLockRegistry lockRegistry;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public WipeCommand(FoxariaHubGuardPlugin plugin, WipeLockRegistry lockRegistry) {
        this.plugin = plugin;
        this.lockRegistry = lockRegistry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только игроки могут использовать эту команду.");
            return true;
        }
        if (!isAdmin(player, plugin.hubRank(), plugin)) {
            player.sendMessage(legacy.deserialize("&cНедостаточно прав."));
            return true;
        }
        if (args.length >= 1) {
            String bungeeServer = args[0];
            boolean nowLocked = lockRegistry.toggle(bungeeServer);
            sendWipeSetToProxy(player, bungeeServer, nowLocked);
            player.sendMessage(legacy.deserialize(nowLocked
                ? "&cСервер &f" + bungeeServer + " &cзаблокирован для игроков &8(режим вайпа)&c. Вы как Админ можете войти."
                : "&aСервер &f" + bungeeServer + " &aразблокирован."));
            return true;
        }
        openGui(player);
        return true;
    }

    private void openGui(Player player) {
        Component title = legacy.deserialize("&4&lВайп &8\u00bb &cуправление серверами");
        Inventory inv = Bukkit.createInventory(new WipeMenuHolder(), 27, title);

        ConfigurationSection servers = plugin.getConfig().getConfigurationSection("lobby-menu.servers");
        if (servers != null) {
            for (String key : servers.getKeys(false)) {
                ConfigurationSection s = servers.getConfigurationSection(key);
                if (s == null || s.getBoolean("coming-soon", false)) {
                    continue;
                }
                String bungeeServer = s.getString("bungee-server", key);
                int slot = Math.max(0, Math.min(26, s.getInt("slot", 0)));
                boolean locked = lockRegistry.isLocked(bungeeServer);
                ItemStack icon = new ItemStack(locked ? Material.RED_CONCRETE : Material.LIME_CONCRETE);
                ItemMeta meta = icon.getItemMeta();
                String name = s.getString("name-gradient-red", s.getString("name", "&f" + key));
                meta.displayName(legacy.deserialize(name));
                List<Component> lore = new ArrayList<>();
                lore.add(legacy.deserialize("&7Сервер: &f" + bungeeServer));
                lore.add(legacy.deserialize(locked
                    ? "&cСтатус: &l\u2718 ВАЙП &8(игроки заблокированы)"
                    : "&aСтатус: &l\u2714 Открыт"));
                lore.add(Component.empty());
                lore.add(legacy.deserialize(locked
                    ? "&eНажми \u2192 &aРазблокировать"
                    : "&eНажми \u2192 &cЗаблокировать &e(вайп)"));
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                    ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
                icon.setItemMeta(meta);
                inv.setItem(slot, icon);
            }
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.empty());
        fm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        filler.setItemMeta(fm);
        for (int i = 0; i < 27; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur == null || cur.getType().isAir()) {
                inv.setItem(i, filler.clone());
            }
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onWipeMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof WipeMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()
            || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        int slot = event.getRawSlot();
        ConfigurationSection servers = plugin.getConfig().getConfigurationSection("lobby-menu.servers");
        if (servers == null) {
            return;
        }
        for (String key : servers.getKeys(false)) {
            ConfigurationSection s = servers.getConfigurationSection(key);
            if (s == null || s.getBoolean("coming-soon", false)) {
                continue;
            }
            if (Math.max(0, Math.min(26, s.getInt("slot", 0))) != slot) {
                continue;
            }
            String bungeeServer = s.getString("bungee-server", key);
            boolean nowLocked = lockRegistry.toggle(bungeeServer);
            sendWipeSetToProxy(player, bungeeServer, nowLocked);
            player.sendMessage(legacy.deserialize(nowLocked
                ? "&cСервер &f" + bungeeServer + " &cзаблокирован &8(вайп)&c. Вы как Админ можете войти."
                : "&aСервер &f" + bungeeServer + " &aразблокирован."));
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    openGui(player);
                }
            }, 1L);
            return;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            ConfigurationSection servers = plugin.getConfig().getConfigurationSection("lobby-menu.servers");
            if (servers != null) {
                for (String key : servers.getKeys(false)) {
                    ConfigurationSection s = servers.getConfigurationSection(key);
                    if (s != null && !s.getBoolean("coming-soon", false)) {
                        String bungee = s.getString("bungee-server", key);
                        if (bungee.toLowerCase().startsWith(args[0].toLowerCase())) {
                            result.add(bungee);
                        }
                    }
                }
            }
            return result;
        }
        return List.of();
    }

    private void sendWipeSetToProxy(Player player, String bungeeServerName, boolean locked) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("WipeSet");
        out.writeUTF(bungeeServerName);
        out.writeBoolean(locked);
        player.sendPluginMessage(plugin, "foxaria:proxy", out.toByteArray());
    }

    /**
     * Admin check: OP, explicit permission, OR rank is in rank-bridge.admin-groups config list.
     */
    static boolean isAdmin(Player player, HubRankBridge hubRank) {
        return isAdmin(player, hubRank, null);
    }

    static boolean isAdmin(Player player, HubRankBridge hubRank, FoxariaHubGuardPlugin plugin) {
        if (player.isOp()) return true;
        if (player.hasPermission("foxaria.admin.wipe")) return true;
        if (player.hasPermission("foxaria.admin")) return true;
        if (player.hasPermission("foxaria.proxy.admin")) return true;
        if (player.hasPermission("foxaria.admin.panel")) return true;
        String group = hubRank.group(player);
        if (group == null || group.isBlank()) return false;
        java.util.List<String> adminGroups = DEFAULT_ADMIN_GROUPS;
        if (plugin != null) {
            java.util.List<String> cfgGroups = plugin.getConfig().getStringList("rank-bridge.admin-groups");
            if (!cfgGroups.isEmpty()) adminGroups = cfgGroups;
        }
        final String g = group;
        return adminGroups.stream().anyMatch(a -> a.equalsIgnoreCase(g));
    }

    private static final java.util.List<String> DEFAULT_ADMIN_GROUPS = java.util.List.of(
        "admin", "headadmin", "co-owner", "coadmin", "owner"
    );
}
