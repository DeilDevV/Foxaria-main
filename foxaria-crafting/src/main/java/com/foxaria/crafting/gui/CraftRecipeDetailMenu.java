package com.foxaria.crafting.gui;

import com.foxaria.api.service.KnowledgeService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuHolder;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.crafting.CraftBuiltinIds;
import com.foxaria.crafting.CraftJson;
import com.foxaria.crafting.CraftingService;
import com.foxaria.crafting.CustomCraftDefinition;
import com.foxaria.crafting.model.CraftRecipeJson;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Просмотр рецепта: верстак 3×3 или схема «печь» с анимацией топлива/входа.
 */
public final class CraftRecipeDetailMenu extends BaseMenu {

    private static final int[] TABLE_MATRIX_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int TABLE_RESULT_SLOT = 24;

    private static final int FU_PREVIEW_INPUT = 10;
    private static final int FU_PREVIEW_GLASS = 19;
    private static final int FU_PREVIEW_FUEL = 28;
    private static final int FU_ARROW_IN = 9;
    private static final int FU_ARROW_MID = 18;
    /** Декор слева от превью топлива (не «выход» — результат в слоте {@link #FU_RESULT_SLOT}). */
    private static final int FU_GLASS_BESIDE_FUEL = 27;
    private static final int FU_RESULT_SLOT = 16;

    private static final Material[] FUEL_DEMO = {
        Material.COAL, Material.CHARCOAL, Material.BLAZE_ROD, Material.LAVA_BUCKET,
        Material.COAL_BLOCK, Material.OAK_PLANKS, Material.DRIED_KELP_BLOCK,
        Material.STICK, Material.BAMBOO, Material.WARPED_STEM
    };

    private static final Material[] SULFUR_INPUT_DEMO = {
        Material.SAND, Material.RED_SAND,
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE
    };

    private static final ConcurrentHashMap<UUID, BukkitTask> FURNACE_ANIM = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> FURNACE_ANIM_SESSION = new ConcurrentHashMap<>();
    private static final AtomicInteger FURNACE_SESSION_GEN = new AtomicInteger(1);

    private final MenuManager menus;
    private final CraftingService crafting;
    private final com.foxaria.regions.RegionConfig regionConfig;
    private final KnowledgeService knowledge;
    private final CustomCraftDefinition definition;

    private boolean furnaceView;
    private boolean animSulfur;
    private CraftRecipeJson animRecipe;
    private volatile int animFrame;

    public CraftRecipeDetailMenu(
        MenuManager menus,
        CraftingService crafting,
        com.foxaria.regions.RegionConfig regionConfig,
        KnowledgeService knowledge,
        CustomCraftDefinition definition
    ) {
        super("&6&lРецепт &8| &fFoxaria", 54);
        this.menus = menus;
        this.crafting = crafting;
        this.regionConfig = regionConfig;
        this.knowledge = knowledge;
        this.definition = definition;
    }

    private static void cancelAnim(UUID id) {
        BukkitTask t = FURNACE_ANIM.remove(id);
        if (t != null) {
            t.cancel();
        }
        FURNACE_ANIM_SESSION.remove(id);
    }

    private static boolean furnaceAnimSessionOk(UUID id, int sid) {
        return Integer.valueOf(sid).equals(FURNACE_ANIM_SESSION.get(id));
    }

    /** Вызывать при закрытии инвентаря, чтобы остановить таймер анимации. */
    public static void cancelAnimOnClose(Player player) {
        cancelAnim(player.getUniqueId());
    }

    private void scheduleFurnaceAnim(Player player) {
        UUID id = player.getUniqueId();
        cancelAnim(id);
        int sid = FURNACE_SESSION_GEN.incrementAndGet();
        FURNACE_ANIM_SESSION.put(id, sid);
        JavaPlugin plug = crafting.plugin();
        // 1 тик — после openInventory; далее каждые 20 тиков (~1 с), слоты через InventoryView (Paper 1.21).
        BukkitTask task = plug.getServer().getScheduler().runTaskTimer(plug, () -> {
            if (!player.isOnline()) {
                cancelAnim(id);
                return;
            }
            if (!furnaceAnimSessionOk(id, sid)) {
                cancelAnim(id);
                return;
            }
            animFrame++;
            paintFurnaceAnimLayer(player);
        }, 1L, 20L);
        FURNACE_ANIM.put(id, task);
    }

    @Override
    protected void draw(Player player) {
        cancelAnim(player.getUniqueId());

        for (int i = 0; i < 54; i++) {
            setItem(i, MenuItems.filler(), null);
        }
        for (int i = 36; i < 45; i++) {
            setItem(i, MenuItems.item(Material.PURPLE_STAINED_GLASS_PANE, "&8 "), null);
        }

        boolean builtinSulfur = CraftBuiltinIds.SULFUR.equals(definition.craftId());
        CraftRecipeJson j = builtinSulfur ? null : definition.parsed();
        furnaceView = builtinSulfur || CraftJson.isSmeltingKind(j);

        if (furnaceView) {
            animSulfur = builtinSulfur;
            animRecipe = j;
            animFrame = 0;
            drawFurnaceShell(player, j, builtinSulfur);
            paintFurnaceAnimLayer(player);
            scheduleFurnaceAnim(player);
            return;
        }

        drawTableRecipe(player, j);
    }

    private void drawFurnaceShell(Player player, CraftRecipeJson j, boolean builtinSulfur) {
        int need = builtinSulfur ? 1 : j.requiredKnowledge;
        int have = 1;
        if (knowledge != null) {
            try {
                have = Math.max(1, knowledge.knowledgeLevel(player.getUniqueId()).join());
            } catch (Exception ignored) {
                have = 1;
            }
        }
        boolean ok = have >= need;
        ItemStack headPreview;
        if (builtinSulfur) {
            headPreview = crafting.templates().cloneTemplate("sulfur")
                .orElseGet(() -> MenuItems.item(Material.BARRIER, "&cНет шаблона sulfur"));
            headPreview.setAmount(1);
        } else {
            try {
                headPreview = CraftJson.resolveResult(crafting.templates(), j).clone();
                headPreview.setAmount(1);
            } catch (Exception ex) {
                headPreview = MenuItems.item(Material.BARRIER, "&cНет результата");
            }
        }
        ItemMeta headMeta = headPreview.getItemMeta();
        if (headMeta != null) {
            List<Component> headLore = new ArrayList<>();
            if (headMeta.lore() != null && !headMeta.lore().isEmpty()) {
                headLore.addAll(headMeta.lore());
                headLore.add(FoxariaText.legacy("&8 "));
            }
            headLore.add(FoxariaText.legacy("&7Минимум знаний: &e" + need));
            headLore.add(FoxariaText.legacy("&7У тебя: &f" + have + (ok ? " &a✓" : " &c— мало для крафта")));
            headLore.add(FoxariaText.legacy("&8 "));
            if (builtinSulfur) {
                headLore.add(FoxariaText.legacy("&7Создание: в печи"));
                headLore.add(FoxariaText.legacy("&8(&7Все печи: обычная, плавильная, модерн&8)"));
                headLore.add(FoxariaText.legacy("&7Доп. дроп при переплавке руд и песка."));
            } else {
                for (String ln : CraftJson.creationLoreLines(j)) {
                    headLore.add(FoxariaText.legacy(ln));
                }
                if (j.bonusChancePercent > 0 && j.bonusTemplateId != null && !j.bonusTemplateId.isBlank()) {
                    headLore.add(FoxariaText.legacy("&7Доп. дроп: &f" + j.bonusTemplateId + " &7— &e"
                        + (int) Math.round(j.bonusChancePercent) + "%"));
                }
            }
            headLore.add(FoxariaText.legacy("&8 "));
            headLore.add(FoxariaText.legacy("&7Слева: &fвход &7→ топливо внизу."));
            headLore.add(FoxariaText.legacy("&7Справа: &fрезультат."));
            headMeta.lore(headLore);
            headPreview.setItemMeta(headMeta);
        }
        setItem(4, headPreview, null);

        setItem(FU_ARROW_IN, MenuItems.item(Material.SPECTRAL_ARROW, "&a▶ &fВход",
            "&7Сюда кладут руду / ресурс"), null);
        setItem(FU_ARROW_MID, MenuItems.item(Material.GRAY_DYE, "&8│", "&7Процесс плавки"), null);
        setItem(FU_GLASS_BESIDE_FUEL, MenuItems.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&8 "), null);

        setItem(FU_PREVIEW_GLASS, MenuItems.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&8 "), null);

        if (!builtinSulfur && j != null) {
            ItemStack inPreview = firstGridItemStack(j);
            if (inPreview != null) {
                ItemStack show = inPreview.clone();
                show.setAmount(Math.min(64, Math.max(1, show.getAmount())));
                setItem(FU_PREVIEW_INPUT, show, null);
            } else {
                setItem(FU_PREVIEW_INPUT, MenuItems.item(Material.BARRIER, "&cНет входа"), null);
            }
        }

        ItemStack result;
        if (builtinSulfur) {
            result = crafting.templates().cloneTemplate("sulfur").map(ItemStack::clone).orElseGet(
                () -> MenuItems.item(Material.BARRIER, "&cНет шаблона"));
        } else {
            try {
                result = CraftJson.resolveResult(crafting.templates(), j).clone();
                result.setAmount(Math.min(64, Math.max(1, result.getAmount())));
            } catch (Exception ex) {
                result = MenuItems.item(Material.BARRIER, "&cНет результата");
            }
        }
        setItem(FU_RESULT_SLOT, result, null);

        setItem(31, MenuItems.item(Material.BOOK, "&7Печь",
            "&7Один тип ресурса в слоте входа.",
            "&7Работает в обычной печи, плавильной и модерн-печи."), null);

        setItem(49, MenuItems.item(Material.ARROW, "&7Назад к списку"), e ->
            menus.open(player, new CraftPlayerMenu(menus, crafting, regionConfig, knowledge)));
        setItem(53, MenuItems.item(Material.BARRIER, "&cЗакрыть"), e -> player.closeInventory());
    }

    @Override
    protected void onOpened(Player player) {
        if (furnaceView) {
            paintFurnaceAnimLayer(player);
        }
    }

    /**
     * Обновляет слоты превью печи. Пока GUI закрыт — только {@link #inventory()}.
     * Когда открыт этот же {@link BaseMenu}, дублирует в {@link InventoryView#setItem} —
     * иначе на Paper 1.21+ клиент часто не получает пакеты и картинка «замирает».
     */
    private void paintFurnaceAnimLayer(Player viewer) {
        int fi = animFrame % FUEL_DEMO.length;
        ItemStack fuelShow = new ItemStack(FUEL_DEMO[fi]);
        fuelShow.setAmount(1);

        ItemStack inputShow = null;
        if (animSulfur) {
            Material inputMat = SULFUR_INPUT_DEMO[animFrame % SULFUR_INPUT_DEMO.length];
            inputShow = new ItemStack(inputMat);
            inputShow.setAmount(1);
        }

        inventory().setItem(FU_PREVIEW_FUEL, fuelShow.clone());
        if (inputShow != null) {
            inventory().setItem(FU_PREVIEW_INPUT, inputShow.clone());
        }

        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        InventoryView view = viewer.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (top.getSize() != 54 || !(top.getHolder() instanceof MenuHolder mh) || mh.menu() != this) {
            return;
        }
        view.setItem(FU_PREVIEW_FUEL, fuelShow.clone());
        if (inputShow != null) {
            view.setItem(FU_PREVIEW_INPUT, inputShow.clone());
        }
    }

    private ItemStack firstGridItemStack(CraftRecipeJson j) {
        ItemStack[] cells = CraftJson.resolveGrid(crafting.templates(), j);
        for (ItemStack s : cells) {
            if (s != null && !s.getType().isAir()) {
                return s;
            }
        }
        return null;
    }

    private void drawTableRecipe(Player player, CraftRecipeJson j) {
        int need = j.requiredKnowledge;
        int have = 1;
        if (knowledge != null) {
            try {
                have = Math.max(1, knowledge.knowledgeLevel(player.getUniqueId()).join());
            } catch (Exception ignored) {
                have = 1;
            }
        }
        boolean ok = have >= need;
        ItemStack headPreview;
        try {
            headPreview = CraftJson.resolveResult(crafting.templates(), j).clone();
            headPreview.setAmount(1);
        } catch (Exception ex) {
            headPreview = MenuItems.item(Material.BARRIER, "&cНет результата");
        }
        ItemMeta headMeta = headPreview.getItemMeta();
        if (headMeta != null) {
            List<Component> headLore = new ArrayList<>();
            if (headMeta.lore() != null && !headMeta.lore().isEmpty()) {
                headLore.addAll(headMeta.lore());
                headLore.add(FoxariaText.legacy("&8 "));
            }
            headLore.add(FoxariaText.legacy("&7Минимум знаний: &e" + need));
            headLore.add(FoxariaText.legacy("&7У тебя: &f" + have + (ok ? " &a✓" : " &c— мало для крафта")));

            headLore.add(FoxariaText.legacy("&8 "));
            for (String ln : CraftJson.creationLoreLines(j)) {
                headLore.add(FoxariaText.legacy(ln));
            }
            headLore.add(FoxariaText.legacy("&8 "));
            if (CraftJson.isTable(j)) {
                headLore.add(FoxariaText.legacy("&7Расположи в верстаке &fточно так же&7:"));
                headLore.add(FoxariaText.legacy("&7пустые клетки = пустые слоты."));
            }
            headMeta.lore(headLore);
            headPreview.setItemMeta(headMeta);
        }
        setItem(4, headPreview, null);

        ItemStack[] cells = CraftJson.resolveGrid(crafting.templates(), j);
        for (int i = 0; i < TABLE_MATRIX_SLOTS.length; i++) {
            ItemStack show = cells[i];
            if (show == null || show.getType().isAir()) {
                setItem(TABLE_MATRIX_SLOTS[i], MenuItems.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&8 "), null);
            } else {
                ItemStack ic = show.clone();
                ic.setAmount(Math.min(64, Math.max(1, ic.getAmount())));
                setItem(TABLE_MATRIX_SLOTS[i], ic, null);
            }
        }

        ItemStack result;
        try {
            result = CraftJson.resolveResult(crafting.templates(), j).clone();
            result.setAmount(Math.min(64, Math.max(1, result.getAmount())));
        } catch (Exception ex) {
            result = MenuItems.item(Material.BARRIER, "&cНет результата");
        }
        setItem(TABLE_RESULT_SLOT, result, null);

        if (CraftJson.isTable(j)) {
            setItem(31, MenuItems.item(Material.BOOK, "&7Важно",
                "&7Порядок и пустые места &fсовпадают &7с верстаком.",
                "&7Иначе рецепт не сработает."), null);
        } else {
            setItem(31, MenuItems.item(Material.BOOK, "&7Печь", CraftJson.creationLoreLines(j)), null);
        }

        setItem(49, MenuItems.item(Material.ARROW, "&7Назад к списку"), e ->
            menus.open(player, new CraftPlayerMenu(menus, crafting, regionConfig, knowledge)));
        setItem(53, MenuItems.item(Material.BARRIER, "&cЗакрыть"), e -> player.closeInventory());
    }
}
