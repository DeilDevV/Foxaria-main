package com.foxaria.modernfurnace;

import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;

public final class ModernFurnaceMenus {

    private ModernFurnaceMenus() {
    }

    public static ModernFurnaceHolder openMain(Location loc, PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        ModernFurnaceHolder holder = new ModernFurnaceHolder(loc, ModernFurnaceHolder.Kind.MAIN);
        String title = ChatColor.translateAlternateColorCodes('&', "&8▣ &6&lМодерн-печь &7│ &8Foxaria");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.attach(inv);
        refreshMain(inv, d, cfg);
        return holder;
    }

    public static void refreshMain(Inventory inv, PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        ModernFurnaceStateFactory.normalize(d);
        int lanes = cfg.parallelLanes(d.parallelLevel);

        ItemStack wall = fillerPane(Material.BLACK_STAINED_GLASS_PANE, "&8 ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, wall);
        }

        inv.setItem(9, fillerPane(Material.SPECTRAL_ARROW, "&a▶ &fВход",
            "&7Ресурсы для плавки"));
        inv.setItem(18, fillerPane(Material.GRAY_DYE, "&8│", "&7Процесс"));
        inv.setItem(27, fillerPane(Material.SPECTRAL_ARROW, "&e▶ &fВыход",
            "&7Готовые предметы"));

        for (int i = 0; i < ModernFurnaceMenuLayout.INPUT_SLOTS.length; i++) {
            int slot = ModernFurnaceMenuLayout.INPUT_SLOTS[i];
            if (i < lanes) {
                inv.setItem(slot, stackFromB64(d.lines.get(i).inputB64));
            } else {
                inv.setItem(slot, fillerPane(Material.GRAY_STAINED_GLASS_PANE,
                    "&8✖", "&7Канал &f" + (i + 1), "&7Открой в §eпрокачках §7(каналы)"));
            }
        }

        inv.setItem(ModernFurnaceMenuLayout.FUEL_SLOT, stackFromB64(d.fuelStackB64));

        double cookFactor = cfg.cookTimeFactor(d.speedLevel);
        for (int lane = 0; lane < 4; lane++) {
            int slot = ModernFurnaceMenuLayout.LANE_PROGRESS_SLOTS[lane];
            if (lane >= lanes) {
                inv.setItem(slot, fillerPane(Material.GRAY_STAINED_GLASS_PANE, "&8▱", "&7Канал закрыт"));
                continue;
            }
            ItemStack in = stackFromB64(d.lines.get(lane).inputB64);
            CookingRecipe<?> r = in != null && !in.getType().isAir() ? SmeltingUtil.findCookingRecipe(in) : null;
            if (r == null) {
                inv.setItem(slot, fillerPane(Material.RED_STAINED_GLASS_PANE,
                    "&8▱ &7Пусто", "&7Нет ресурса"));
                continue;
            }
            int need = Math.max(1, (int) Math.ceil(SmeltingUtil.baseCookTicks(r) * cookFactor));
            int prog = d.lines.get(lane).cookProgress;
            double frac = Math.min(1.0, (double) prog / (double) need);
            boolean hot = d.fuelTicksRemaining > 0;
            if (hot && frac >= 1.0 - 1e-6) {
                inv.setItem(slot, fillerPane(Material.LIME_STAINED_GLASS_PANE,
                    "&a▰ &fГотово", "&7Слот &f" + (lane + 1) + "&7: &f" + prog + " &8/ &f" + need));
            } else if (hot) {
                inv.setItem(slot, fillerPane(Material.LIME_STAINED_GLASS_PANE,
                    "&a▰ &fПлавка", "&7Канал &f" + (lane + 1),
                    "&7&f" + prog + " &8/ &f" + need + " &7тик.",
                    "&7≈ &f" + (int) Math.round(frac * 100.0) + "%"));
            } else {
                inv.setItem(slot, fillerPane(Material.ORANGE_STAINED_GLASS_PANE,
                    "&6▱ &7Ожидание", "&7Нужно топливо или тепло",
                    "&7Прогресс: &f" + prog + " &8/ &f" + need));
            }
        }

        int fuelHeat = ModernFurnaceMenuLayout.FUEL_HEAT_SLOT;
        if (d.fuelTicksRemaining > 0) {
            inv.setItem(fuelHeat, fillerPane(Material.LIME_STAINED_GLASS_PANE,
                "&a♨ Тепло", "&7Осталось &f" + d.fuelTicksRemaining + " &7тик. горения"));
        } else {
            inv.setItem(fuelHeat, fillerPane(Material.RED_STAINED_GLASS_PANE,
                "&c♨ Нет тепла", "&7Положите топливо"));
        }

        for (int i = 0; i < ModernFurnaceMenuLayout.OUTPUT_SLOTS.length; i++) {
            int slot = ModernFurnaceMenuLayout.OUTPUT_SLOTS[i];
            if (i < lanes) {
                inv.setItem(slot, stackFromB64(d.outputStacksB64.get(i)));
            } else {
                inv.setItem(slot, fillerPane(Material.GRAY_STAINED_GLASS_PANE,
                    "&8✖", "&7Выход канала &f" + (i + 1), "&7Открой канал в прокачках"));
            }
        }

        inv.setItem(ModernFurnaceMenuLayout.UPGRADE_BUTTON, fillerPane(Material.NETHER_STAR,
            "&e&l⚙ Прокачки",
            "&7Скорость · топливо · ×выход · каналы · трубы"));
        if (d.pipesUnlocked) {
            inv.setItem(ModernFurnaceMenuLayout.PIPE_BUTTON, fillerPane(Material.HOPPER,
                "&6&l⛁ Трубы",
                "&7ПКМ §fключом §7по печи — настройка"));
        } else {
            inv.setItem(ModernFurnaceMenuLayout.PIPE_BUTTON, fillerPane(Material.BARRIER,
                "&cТрубы закрыты",
                "&7Открой в прокачках"));
        }
        inv.setItem(ModernFurnaceMenuLayout.CLOSE_SLOT, fillerPane(Material.BARRIER, "&cЗакрыть", new String[0]));
    }

    public static ModernFurnaceHolder openUpgrades(Location loc, PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        ModernFurnaceHolder holder = new ModernFurnaceHolder(loc, ModernFurnaceHolder.Kind.UPGRADES);
        String title = ChatColor.translateAlternateColorCodes('&', "&6Прокачки &8│ &fпечь");
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.attach(inv);
        refreshUpgrades(inv, d, cfg);
        return holder;
    }

    public static void refreshUpgrades(Inventory inv, PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        ModernFurnaceStateFactory.normalize(d);
        ItemStack bg = fillerPane(Material.BLACK_STAINED_GLASS_PANE, " ", new String[0]);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        inv.setItem(10, speedIcon(d, cfg));
        inv.setItem(12, fuelIcon(d, cfg));
        inv.setItem(14, outputIcon(d, cfg));
        inv.setItem(16, parallelIcon(d, cfg));
        inv.setItem(22, pipesIcon(d, cfg));
        inv.setItem(40, fillerPane(Material.ARROW, "&7← Назад", new String[0]));
        inv.setItem(49, fillerPane(Material.BARRIER, "&cЗакрыть", new String[0]));
    }

    private static ItemStack speedIcon(PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        int max = cfg.maxSpeedLevel();
        BigDecimal price = cfg.priceSpeedUpgrade(d.speedLevel);
        String[] lore = new String[]{
            "&7Уровень: &f" + d.speedLevel + " &7/ &f" + max,
            "&7Время плавки: &f" + cfg.speedPercentOfVanilla(d.speedLevel) + "% &7от ваниллы",
            "&7Быстрее базы на: &f" + cfg.speedBonusPercent(d.speedLevel) + "%",
            price == null ? "&7§mДальше некуда" : "&eЦена след.: &f" + cfg.formatMoney(price) + " &7монет"
        };
        return upgradeIcon(Material.GOLD_INGOT, "&eСкорость плавки", lore);
    }

    private static ItemStack fuelIcon(PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        int max = cfg.maxFuelLevel();
        BigDecimal price = cfg.priceFuelUpgrade(d.fuelLevel);
        String[] lore = new String[]{
            "&7Уровень: &f" + d.fuelLevel + " &7/ &f" + max,
            "&7Длительность горения: &fx" + trimDouble(cfg.fuelDurationMultiplier(d.fuelLevel)),
            "&7≈ экономия топлива: &f" + cfg.fuelSavingsApproxPercent(d.fuelLevel) + "%",
            price == null ? "&7§mДальше некуда" : "&eЦена след.: &f" + cfg.formatMoney(price) + " &7монет"
        };
        return upgradeIcon(Material.COAL, "&6Экономия топлива", lore);
    }

    private static ItemStack outputIcon(PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        int max = cfg.maxOutputLevel();
        BigDecimal price = cfg.priceOutputUpgrade(d.outputLevel);
        String[] lore = new String[]{
            "&7Уровень: &f" + d.outputLevel + " &7/ &f" + max,
            "&7Множитель выхода: &fx" + cfg.outputMultiplier(d.outputLevel),
            price == null ? "&7§mДальше некуда" : "&eЦена след.: &f" + cfg.formatMoney(price) + " &7монет"
        };
        return upgradeIcon(Material.NETHER_STAR, "&dМножитель выхода", lore);
    }

    private static ItemStack parallelIcon(PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        int max = cfg.maxParallelLevel();
        BigDecimal price = cfg.priceParallelUpgrade(d.parallelLevel);
        String[] lore = new String[]{
            "&7Уровень: &f" + d.parallelLevel + " &7/ &f" + max,
            "&7Каналов (вход+выход): &f" + cfg.parallelLanes(d.parallelLevel),
            price == null ? "&7§mДальше некуда" : "&eЦена след.: &f" + cfg.formatMoney(price) + " &7монет"
        };
        return upgradeIcon(Material.IRON_BLOCK, "&bКаналы плавки", lore);
    }

    private static ItemStack pipesIcon(PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        BigDecimal price = cfg.pricePipesUnlock();
        if (d.pipesUnlocked) {
            return upgradeIcon(Material.HOPPER, "&6Трубы к сундуку",
                "&aРазблокировано",
                "&7Входной сундук: руда/топливо",
                "&7Выходной сундук: готовая продукция");
        }
        return upgradeIcon(Material.HOPPER, "&6Трубы к сундуку",
            "&7Нажми, чтобы открыть",
            "&eЦена: &f" + cfg.formatMoney(price) + " &7монет",
            "&7Входной сундук: руда/топливо",
            "&7Выходной сундук: готовая продукция");
    }

    private static String trimDouble(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    public static ModernFurnaceHolder openPipes(Location loc, PersistedFurnaceJson d) {
        ModernFurnaceHolder holder = new ModernFurnaceHolder(loc, ModernFurnaceHolder.Kind.PIPES);
        String title = ChatColor.translateAlternateColorCodes('&', "&cТрубы &8│ &fвход / выход");
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.attach(inv);
        ItemStack bg = fillerPane(Material.BLACK_STAINED_GLASS_PANE, " ", new String[0]);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }
        inv.setItem(11, fillerPane(Material.RED_STAINED_GLASS_PANE, "&cВход (сундук)",
            inputLore(d, true)));
        inv.setItem(15, fillerPane(Material.LIME_STAINED_GLASS_PANE, "&aВыход (сундук)",
            inputLore(d, false)));
        inv.setItem(13, fillerPane(Material.TRIPWIRE_HOOK, "&eКлюч",
            "&7Сначала кнопка входа/выхода, затем §fключ в руке §7и ПКМ по сундуку"));
        inv.setItem(22, fillerPane(Material.BARRIER, "&cСбросить связи", "&7Отключить трубы"));
        inv.setItem(26, fillerPane(Material.ARROW, "&7← Назад", new String[0]));
        return holder;
    }

    private static String[] inputLore(PersistedFurnaceJson d, boolean input) {
        if (input && d.inputChestX != null) {
            return new String[]{"&7XYZ: &f" + d.inputChestX + " " + d.inputChestY + " " + d.inputChestZ};
        }
        if (!input && d.outputChestX != null) {
            return new String[]{"&7XYZ: &f" + d.outputChestX + " " + d.outputChestY + " " + d.outputChestZ};
        }
        return new String[]{"&7Не подключено", "&7Кнопка, затем §fключ Foxaria §7в руке и ПКМ по сундуку"};
    }

    private static ItemStack fillerPane(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null && lore.length > 0) {
                java.util.List<String> list = new java.util.ArrayList<>();
                for (String s : lore) {
                    list.add(ChatColor.translateAlternateColorCodes('&', s));
                }
                meta.setLore(list);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack upgradeIcon(Material m, String name, String... lore) {
        return fillerPane(m, name, lore);
    }

    private static ItemStack stackFromB64(String b64) {
        if (b64 == null || b64.isBlank()) {
            return null;
        }
        try {
            return ItemStackSerializer.deserialize(b64);
        } catch (Exception e) {
            return null;
        }
    }

    public static void readMainIntoState(Inventory inv, PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        ModernFurnaceStateFactory.normalize(d);
        int lanes = cfg.parallelLanes(d.parallelLevel);
        for (int i = 0; i < lanes; i++) {
            ItemStack s = inv.getItem(ModernFurnaceMenuLayout.INPUT_SLOTS[i]);
            d.lines.get(i).inputB64 = stackToB64(s);
        }
        d.fuelStackB64 = stackToB64(inv.getItem(ModernFurnaceMenuLayout.FUEL_SLOT));
        for (int i = 0; i < lanes; i++) {
            ItemStack s = inv.getItem(ModernFurnaceMenuLayout.OUTPUT_SLOTS[i]);
            d.outputStacksB64.set(i, stackToB64(s));
        }
    }

    private static String stackToB64(ItemStack s) {
        if (s == null || s.getType().isAir()) {
            return null;
        }
        return ItemStackSerializer.serialize(s);
    }
}
