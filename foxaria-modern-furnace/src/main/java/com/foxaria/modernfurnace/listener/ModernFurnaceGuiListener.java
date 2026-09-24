package com.foxaria.modernfurnace.listener;

import com.foxaria.api.service.EconomyService;
import com.foxaria.modernfurnace.ModernFurnaceConfig;
import com.foxaria.modernfurnace.ModernFurnaceHolder;
import com.foxaria.modernfurnace.ModernFurnaceMenuLayout;
import com.foxaria.modernfurnace.ModernFurnaceMenus;
import com.foxaria.modernfurnace.ModernFurnacePending;
import com.foxaria.modernfurnace.ModernFurnaceService;
import com.foxaria.modernfurnace.PersistedFurnaceJson;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.function.Consumer;

public final class ModernFurnaceGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final ModernFurnaceService service;
    private final ModernFurnaceConfig config;
    private final EconomyService economy;
    private final ModernFurnacePending pipePending;

    public ModernFurnaceGuiListener(
        JavaPlugin plugin,
        ModernFurnaceService service,
        ModernFurnaceConfig config,
        EconomyService economy,
        ModernFurnacePending pipePending
    ) {
        this.plugin = plugin;
        this.service = service;
        this.config = config;
        this.economy = economy;
        this.pipePending = pipePending;
    }

    /**
     * Главное меню: почти всё отменяем; разрешаем только слоты плавки (по числу каналов).
     * Низ инвентаря — без отмены (кроме shift в верх).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMainClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder h = top.getHolder();
        if (!(h instanceof ModernFurnaceHolder holder) || holder.kind() != ModernFurnaceHolder.Kind.MAIN) {
            return;
        }
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }
        Player player = (Player) event.getWhoClicked();
        PersistedFurnaceJson d = service.findOurFurnace(holder.location()).orElse(null);
        if (d != null) {
            markLastSmelter(d, player);
            service.putMemory(holder.location(), d);
        }
        int lanes = d == null ? 1 : config.parallelLanes(d.parallelLevel);

        boolean topClick = event.getClickedInventory() == top;
        boolean bottomClick = event.getClickedInventory() == event.getView().getBottomInventory();

        if (bottomClick) {
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                event.setCancelled(true);
                player.sendMessage("§7Перетащи предметы вручную в слоты плавки §8(§7shift отключён§8)");
                return;
            }
            return;
        }

        if (!topClick) {
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) {
            return;
        }

        if (ModernFurnaceMenuLayout.isDecorOrButton(raw) || ModernFurnaceMenuLayout.isLockedLaneSlot(raw, lanes)) {
            event.setCancelled(true);
            handleButtons(event, holder, player, raw);
            return;
        }

        if (ModernFurnaceMenuLayout.isOutputSlot(raw, lanes)) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                return;
            }
        }

        if (ModernFurnaceMenuLayout.isSmeltingSlot(raw, lanes)) {
            if (event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(false);
            scheduleSync(player, holder);
            return;
        }

        event.setCancelled(true);
    }

    private void handleButtons(InventoryClickEvent event, ModernFurnaceHolder holder, Player player, int raw) {
        if (raw == ModernFurnaceMenuLayout.UPGRADE_BUTTON) {
            openUpgrades(player, holder.location());
            return;
        }
        if (raw == ModernFurnaceMenuLayout.PIPE_BUTTON) {
            PersistedFurnaceJson d = service.findOurFurnace(holder.location()).orElse(null);
            if (d != null && d.pipesUnlocked) {
                ModernFurnaceHolder p = ModernFurnaceMenus.openPipes(holder.location(), d);
                player.openInventory(p.getInventory());
            }
            return;
        }
        if (raw == ModernFurnaceMenuLayout.CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void scheduleSync(Player player, ModernFurnaceHolder holder) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof ModernFurnaceHolder mh)
                || mh.kind() != ModernFurnaceHolder.Kind.MAIN) {
                return;
            }
            PersistedFurnaceJson d = service.findOurFurnace(holder.location()).orElse(null);
            if (d == null) {
                return;
            }
            markLastSmelter(d, player);
            ModernFurnaceMenus.readMainIntoState(player.getOpenInventory().getTopInventory(), d, config);
            service.putMemory(holder.location(), d);
            service.saveAsync(holder.location());
        });
    }

    private static void markLastSmelter(PersistedFurnaceJson d, Player player) {
        d.lastSmelterUuid = player.getUniqueId().toString();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder h = event.getInventory().getHolder();
        if (!(h instanceof ModernFurnaceHolder mh) || mh.kind() != ModernFurnaceHolder.Kind.MAIN) {
            return;
        }
        PersistedFurnaceJson d = service.findOurFurnace(mh.location()).orElse(null);
        int lanes = d == null ? 1 : config.parallelLanes(d.parallelLevel);
        boolean fromBottom = event.getRawSlots().stream().anyMatch(r -> r >= 54);
        boolean toOutput = event.getRawSlots().stream().anyMatch(r -> r < 54 && ModernFurnaceMenuLayout.isOutputSlot(r, lanes));
        if (fromBottom && toOutput) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw >= 54) {
                continue;
            }
            if (!ModernFurnaceMenuLayout.isSmeltingSlot(raw, lanes)) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> scheduleSync(player, (ModernFurnaceHolder) mh));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onOtherMenusClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder h = top.getHolder();
        if (!(h instanceof ModernFurnaceHolder holder)) {
            return;
        }
        if (holder.kind() == ModernFurnaceHolder.Kind.MAIN) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        if (holder.kind() == ModernFurnaceHolder.Kind.UPGRADES) {
            PersistedFurnaceJson d = service.findOurFurnace(holder.location()).orElse(null);
            if (d == null) {
                return;
            }
            if (raw == 40) {
                ModernFurnaceHolder m = ModernFurnaceMenus.openMain(holder.location(), d, config);
                player.openInventory(m.getInventory());
                return;
            }
            if (raw == 49) {
                player.closeInventory();
                return;
            }
            if (raw == 10) {
                tryBuyUpgrade(player, holder, config.priceSpeedUpgrade(d.speedLevel), j -> j.speedLevel++);
                return;
            }
            if (raw == 12) {
                tryBuyUpgrade(player, holder, config.priceFuelUpgrade(d.fuelLevel), j -> j.fuelLevel++);
                return;
            }
            if (raw == 14) {
                tryBuyUpgrade(player, holder, config.priceOutputUpgrade(d.outputLevel), j -> j.outputLevel++);
                return;
            }
            if (raw == 16) {
                tryBuyUpgrade(player, holder, config.priceParallelUpgrade(d.parallelLevel), j -> j.parallelLevel++);
                return;
            }
            if (raw == 22 && !d.pipesUnlocked) {
                tryBuyUpgrade(player, holder, config.pricePipesUnlock(), j -> j.pipesUnlocked = true);
            }
            return;
        }
        if (holder.kind() == ModernFurnaceHolder.Kind.PIPES) {
            PersistedFurnaceJson d = service.findOurFurnace(holder.location()).orElse(null);
            if (d == null) {
                return;
            }
            if (raw == 26) {
                ModernFurnaceHolder m = ModernFurnaceMenus.openMain(holder.location(), d, config);
                player.openInventory(m.getInventory());
                return;
            }
            if (raw == 22) {
                d.inputChestX = null;
                d.inputChestY = null;
                d.inputChestZ = null;
                d.inputChestWorld = null;
                d.outputChestX = null;
                d.outputChestY = null;
                d.outputChestZ = null;
                d.outputChestWorld = null;
                service.putMemory(holder.location(), d);
                service.saveAsync(holder.location());
                player.sendMessage("§eСвязи труб сброшены.");
                ModernFurnaceHolder p = ModernFurnaceMenus.openPipes(holder.location(), d);
                player.openInventory(p.getInventory());
                return;
            }
            if (raw == 11) {
                pipePending.set(player.getUniqueId(), holder.location(), ModernFurnacePending.Mode.INPUT_CHEST);
                player.closeInventory();
                player.sendMessage("§7ПКМ по сундуку или бочке §c(вход)§7 — до §f5 §7блоков.");
                return;
            }
            if (raw == 15) {
                pipePending.set(player.getUniqueId(), holder.location(), ModernFurnacePending.Mode.OUTPUT_CHEST);
                player.closeInventory();
                player.sendMessage("§7ПКМ по сундуку §a(выход)§7.");
            }
        }
    }

    private void tryBuyUpgrade(Player player, ModernFurnaceHolder holder, BigDecimal price, Consumer<PersistedFurnaceJson> apply) {
        PersistedFurnaceJson snapshot = service.findOurFurnace(holder.location()).orElse(null);
        if (price == null) {
            player.sendMessage("§7Уже максимальный уровень.");
            if (snapshot != null) {
                reopenUpgrades(player, holder.location(), snapshot);
            }
            return;
        }
        economy.withdraw(player.getUniqueId(), price, "modern_furnace:upgrade", null).whenComplete((v, ex) ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                PersistedFurnaceJson fresh = service.findOurFurnace(holder.location()).orElse(null);
                if (fresh == null) {
                    return;
                }
                if (ex != null) {
                    player.sendMessage("§cНедостаточно монет. Нужно: §f" + config.formatMoney(price));
                    reopenUpgrades(player, holder.location(), fresh);
                    return;
                }
                apply.accept(fresh);
                service.putMemory(holder.location(), fresh);
                service.saveAsync(holder.location());
                reopenUpgrades(player, holder.location(), fresh);
            }));
    }

    private void reopenUpgrades(Player player, org.bukkit.Location loc, PersistedFurnaceJson d) {
        ModernFurnaceHolder up = ModernFurnaceMenus.openUpgrades(loc, d, config);
        player.openInventory(up.getInventory());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder h = top.getHolder();
        if (!(h instanceof ModernFurnaceHolder holder) || holder.kind() != ModernFurnaceHolder.Kind.MAIN) {
            return;
        }
        PersistedFurnaceJson d = service.findOurFurnace(holder.location()).orElse(null);
        if (d == null) {
            return;
        }
        if (event.getPlayer() instanceof Player p) {
            markLastSmelter(d, p);
        }
        ModernFurnaceMenus.readMainIntoState(top, d, config);
        service.putMemory(holder.location(), d);
        service.saveAsync(holder.location());
    }

    private void openUpgrades(Player player, org.bukkit.Location loc) {
        PersistedFurnaceJson d = service.findOurFurnace(loc).orElse(null);
        if (d == null) {
            return;
        }
        ModernFurnaceHolder up = ModernFurnaceMenus.openUpgrades(loc, d, config);
        player.openInventory(up.getInventory());
    }
}
