package com.foxaria.regions.gui;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.regions.RegionFacade;
import com.foxaria.regions.RegionItems;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RaidManualMenu extends BaseMenu {

    private final RegionFacade f;

    public RaidManualMenu(RegionFacade f) {
        super("&8⟨ &c&lРейды &8│ &fдинамит &8⟩", 54);
        this.f = f;
    }

    @Override
    protected void draw(Player player) {
        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        ConfigurationSection sec = f.config().raidManualSection;
        int slot = 10;
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                if (slot > 43) {
                    break;
                }
                String name = sec.getString(key + ".name", "&f" + key);
                java.util.List<String> lore = sec.getStringList(key + ".lore");
                setItem(slot, MenuItems.item(Material.TNT, name, lore.toArray(new String[0])), null);
                slot++;
                if ((slot + 1) % 9 == 0) {
                    slot += 2;
                }
            }
        }
        if (player.hasPermission("foxaria.region.dynamite.give")) {
            for (int tier = 1; tier <= 4; tier++) {
                int t = tier;
                int dmg = f.config().dynamiteDamage(t);
                ItemStack icon = RegionItems.raidDynamite(f.plugin(), t, dmg);
                setItem(44 + tier, icon, e -> {
                    Player p = (Player) e.getWhoClicked();
                    ItemStack give = RegionItems.raidDynamite(f.plugin(), t, dmg);
                    var left = p.getInventory().addItem(give);
                    left.values().forEach(s -> p.getWorld().dropItemNaturally(p.getLocation(), s));
                    f.messages().send(p, "region.raid-give-done", "&aВыдан динамит рейда &f<tier>&a × &f<amt>",
                        new MessageService.Placeholder("tier", String.valueOf(t)),
                        new MessageService.Placeholder("amt", "1"));
                });
            }
        }
        setItem(49, MenuItems.item(Material.ARROW, "&7Закрыть"), e -> player.closeInventory());
    }
}
