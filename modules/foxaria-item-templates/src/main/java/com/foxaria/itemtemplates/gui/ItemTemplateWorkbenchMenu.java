package com.foxaria.itemtemplates.gui;

import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.BaseMenu;
import com.foxaria.core.gui.MenuItems;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.core.text.FoxariaText;
import com.foxaria.itemtemplates.ItemTemplateEditorConfig;
import com.foxaria.itemtemplates.edit.HandEnchantUtil;
import com.foxaria.itemtemplates.edit.HandItemAttributeUtil;
import com.foxaria.itemtemplates.edit.HandPotionUtil;
import com.foxaria.itemtemplates.edit.TemplateOnHitCodec;
import com.foxaria.itemtemplates.session.ItemTemplateWorkbenchSession;
import com.foxaria.itemtemplates.session.ItemTemplateWorkbenchSession.StrikeFocus;
import com.foxaria.itemtemplates.util.EditorDisplayNames;
import com.foxaria.itemtemplates.util.EditorLoreSync;
import com.foxaria.itemtemplates.util.ItemTemplateYamlSnippets;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Одно окно: шаблоны, редактор руки, зачарования, зелья, дебафф при ударе, YAML.
 */
public final class ItemTemplateWorkbenchMenu extends BaseMenu {

    private static final int LIST_PER_PAGE = 28;
    private static final int ENCHANT_PER_PAGE = 21;

    /** Быстрые дебаффы для зелий и удара */
    private static final PotionEffectType[] DEBUFF_PRESETS = {
        PotionEffectType.POISON,
        PotionEffectType.WITHER,
        PotionEffectType.SLOWNESS,
        PotionEffectType.WEAKNESS,
        PotionEffectType.BLINDNESS,
        PotionEffectType.NAUSEA,
        PotionEffectType.DARKNESS,
        PotionEffectType.HUNGER
    };

    private static final PotionEffectType[] BUFF_STRIKE_PRESETS = {
        PotionEffectType.REGENERATION,
        PotionEffectType.SPEED,
        PotionEffectType.STRENGTH,
        PotionEffectType.RESISTANCE,
        PotionEffectType.FIRE_RESISTANCE,
        PotionEffectType.ABSORPTION,
        PotionEffectType.HASTE,
        PotionEffectType.JUMP_BOOST
    };

    public enum Panel {
        HOME,
        LIST,
        EDIT_HUB,
        EDIT_DURABILITY,
        EDIT_COMBAT,
        EDIT_ARMOR,
        EDIT_ENCHANT,
        EDIT_POTION,
        EDIT_ON_HIT,
        EDIT_LORE,
        EDIT_FLAGS
    }

    private final JavaPlugin plugin;
    private final MenuManager menuManager;
    private final ItemTemplateService templates;
    private final MessageService messages;
    private final ItemTemplateEditorConfig editorConfig;
    private final Panel panel;
    private final int listPage;
    private final int enchantPage;

    public ItemTemplateWorkbenchMenu(
        JavaPlugin plugin,
        MenuManager menuManager,
        ItemTemplateService templates,
        MessageService messages,
        ItemTemplateEditorConfig editorConfig,
        Panel panel
    ) {
        this(plugin, menuManager, templates, messages, editorConfig, panel, 0, 0);
    }

    public ItemTemplateWorkbenchMenu(
        JavaPlugin plugin,
        MenuManager menuManager,
        ItemTemplateService templates,
        MessageService messages,
        ItemTemplateEditorConfig editorConfig,
        Panel panel,
        int listPage
    ) {
        this(plugin, menuManager, templates, messages, editorConfig, panel, listPage, 0);
    }

    public ItemTemplateWorkbenchMenu(
        JavaPlugin plugin,
        MenuManager menuManager,
        ItemTemplateService templates,
        MessageService messages,
        ItemTemplateEditorConfig editorConfig,
        Panel panel,
        int listPage,
        int enchantPage
    ) {
        super(title(panel, listPage), 54);
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.templates = templates;
        this.messages = messages;
        this.editorConfig = editorConfig;
        this.panel = panel;
        this.listPage = Math.max(0, listPage);
        this.enchantPage = Math.max(0, enchantPage);
    }

    private static String title(Panel panel, int page) {
        return switch (panel) {
            case HOME -> "&5Шаблоны · лаборатория";
            case LIST -> "&5Шаблоны · список (стр. " + (page + 1) + ")";
            case EDIT_HUB -> "&5Шаблоны · редактор руки";
            case EDIT_DURABILITY -> "&5Шаблоны · прочность";
            case EDIT_COMBAT -> "&5Шаблоны · бой";
            case EDIT_ARMOR -> "&5Шаблоны · защита";
            case EDIT_ENCHANT -> "&5Шаблоны · зачарования";
            case EDIT_POTION -> "&5Шаблоны · зелья";
            case EDIT_ON_HIT -> "&5Шаблоны · эффекты удара";
            case EDIT_LORE -> "&5Шаблоны · имя и описание";
            case EDIT_FLAGS -> "&5Шаблоны · крафт и блеск";
        };
    }

    private boolean editorFamily() {
        return panel == Panel.EDIT_HUB || panel == Panel.EDIT_DURABILITY || panel == Panel.EDIT_COMBAT
            || panel == Panel.EDIT_ARMOR || panel == Panel.EDIT_ENCHANT || panel == Panel.EDIT_POTION || panel == Panel.EDIT_ON_HIT
            || panel == Panel.EDIT_LORE || panel == Panel.EDIT_FLAGS;
    }

    @Override
    protected void draw(Player player) {
        drawNav(player);
        switch (panel) {
            case HOME -> drawHome(player);
            case LIST -> drawList(player);
            case EDIT_HUB -> drawEditHub(player);
            case EDIT_DURABILITY -> drawDurability(player);
            case EDIT_COMBAT -> drawCombat(player);
            case EDIT_ARMOR -> drawArmor(player);
            case EDIT_ENCHANT -> drawEnchant(player);
            case EDIT_POTION -> drawPotion(player);
            case EDIT_ON_HIT -> drawOnHit(player);
            case EDIT_LORE -> drawLore(player);
            case EDIT_FLAGS -> drawFlags(player);
        }
    }

    private void drawNav(Player player) {
        setItem(45, navButton(Material.NETHER_STAR, "&fГлавная", "&7Сводка и подсказки", panel == Panel.HOME), e ->
            open(player, Panel.HOME, 0, 0));
        setItem(46, navButton(Material.WRITABLE_BOOK, "&fВсе шаблоны", "&7Выдача, YAML, книга для конфига", panel == Panel.LIST), e ->
            open(player, Panel.LIST, listPage, enchantPage));
        setItem(47, navButton(Material.ANVIL, "&fРедактор", "&7Статы, чары, зелья, эффекты удара", editorFamily()), e ->
            open(player, Panel.EDIT_HUB, listPage, enchantPage));
        setItem(48, MenuItems.item(Material.HOPPER, "&eОбновить кеш", "&7Перечитать шаблоны из БД"), e ->
            templates.reload().thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                messages.send(player, "itemtemplate.reloaded", "&aКеш шаблонов обновлён.");
                open(player, panel, listPage, enchantPage);
            })));
        setItem(52, MenuItems.item(Material.KNOWLEDGE_BOOK, "&bПодсказка", "&7ЛКМ в списке — выдать себе",
            "&7ПКМ — строка шаблона в чат",
            "&7Shift+ПКМ — &fкнига &7с блоками shop + guilds"), null);
        setItem(53, MenuItems.item(Material.BARRIER, "&cЗакрыть"), e -> player.closeInventory());
    }

    private ItemStack navButton(Material mat, String name, String lore, boolean current) {
        List<String> lines = new ArrayList<>();
        lines.add(lore);
        if (current) {
            lines.add("&a► открыто");
        }
        return MenuItems.item(mat, name, lines.toArray(new String[0]));
    }

    private void open(Player player, Panel p, int newListPage, int newEnchantPage) {
        menuManager.open(player, new ItemTemplateWorkbenchMenu(plugin, menuManager, templates, messages, editorConfig, p, newListPage, newEnchantPage));
    }

    private void open(Player player, Panel p) {
        open(player, p, listPage, enchantPage);
    }

    private ItemStack requireHand(Player player) {
        return player.getInventory().getItemInMainHand();
    }

    private void drawHome(Player player) {
        ItemStack hand = requireHand(player);
        if (hand.getType().isAir()) {
            setItem(13, MenuItems.item(Material.BARRIER, "&cГлавная рука пуста", "&7Возьми предмет — так он попадёт в шаблон"), null);
        } else {
            ItemStack show = hand.clone();
            show.setAmount(1);
            setItem(13, show, null);
        }
        setItem(20, MenuItems.item(Material.WRITABLE_BOOK, "&aШаблоны в базе", "&7Просмотр, тест, YAML, удаление"), e ->
            open(player, Panel.LIST, 0, 0));
        setItem(22, MenuItems.item(Material.ANVIL, "&dПравить предмет в руке", "&7Статы, чары, зелья, дебафф"), e ->
            open(player, Panel.EDIT_HUB, 0, 0));
        setItem(24, MenuItems.item(Material.EMERALD, "&eСохранить в БД", "&7Команда: &f/itemtemplate save <id>",
            "&7или &f/itpl save <id>",
            "&7Сохраняется полный предмет из главной руки"), null);
        setItem(29, MenuItems.item(Material.PAPER, "&7Строка для YAML", "&7В списке: ПКМ по шаблону"), null);
        setItem(31, MenuItems.item(Material.BOOK, "&fГотовый блок в конфиг", "&7В списке: Shift+ПКМ — книга",
            "&7с офферами для guilds-shop и shop"), null);
    }

    private void drawList(Player player) {
        List<String> ids = templates.listTemplateIds().stream().sorted().toList();
        int start = listPage * LIST_PER_PAGE;
        int end = Math.min(ids.size(), start + LIST_PER_PAGE);
        int slot = 10;
        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            ItemStack icon = templates.cloneTemplate(id).orElseGet(() -> new ItemStack(Material.BARRIER));
            ItemStack row = icon.clone();
            ItemMeta meta = row.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(FoxariaText.legacy("&8─────────────"));
                lore.add(FoxariaText.legacy("&7ID: &f" + id));
                lore.add(FoxariaText.legacy("&7ЛКМ &f— выдать себе (тест)"));
                lore.add(FoxariaText.legacy("&7ПКМ &f— строка YAML в чат"));
                lore.add(FoxariaText.legacy("&7Shift+ПКМ &f— &6книга &7для конфигов"));
                lore.add(FoxariaText.legacy("&7Shift+ЛКМ &f— &cудалить"));
                meta.lore(lore);
                row.setItemMeta(meta);
            }
            ItemStack sampleForBook = icon.clone();
            setItem(slot, row, e -> {
                if (e.isShiftClick() && e.isLeftClick()) {
                    templates.deleteTemplate(id).thenAccept(deleted ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (Boolean.TRUE.equals(deleted)) {
                                messages.send(player, "itemtemplate.deleted", "&eШаблон удалён: <id>", new MessageService.Placeholder("id", id));
                                open(player, Panel.LIST, listPage, enchantPage);
                            } else {
                                messages.send(player, "itemtemplate.missing", "&cШаблон не найден: <id>", new MessageService.Placeholder("id", id));
                            }
                        }));
                    return;
                }
                if (e.isShiftClick() && e.isRightClick()) {
                    giveConfigBook(player, id, sampleForBook);
                    messages.send(player, "itemtemplate.config-book", "&aВыдана книга с YAML для магазинов: &f<id>",
                        new MessageService.Placeholder("id", id));
                    return;
                }
                if (e.isRightClick()) {
                    String yaml = templates.yamlReference(id);
                    messages.send(player, "itemtemplate.yaml-snippet", "&7[YAML] &f<text>",
                        new MessageService.Placeholder("text", yaml));
                    return;
                }
                ItemStack give = templates.cloneTemplate(id).orElse(null);
                if (give == null) {
                    messages.send(player, "itemtemplate.missing", "&cШаблон не найден: <id>", new MessageService.Placeholder("id", id));
                    return;
                }
                player.getInventory().addItem(give.clone());
                messages.send(player, "itemtemplate.given", "&aВыдан шаблон: <id>", new MessageService.Placeholder("id", id));
            });
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
        if (start > 0) {
            setItem(36, MenuItems.item(Material.ARROW, "&7Страница назад"), e ->
                open(player, Panel.LIST, listPage - 1, enchantPage));
        }
        if (end < ids.size()) {
            setItem(44, MenuItems.item(Material.ARROW, "&7Страница вперёд"), e ->
                open(player, Panel.LIST, listPage + 1, enchantPage));
        }
    }

    private void giveConfigBook(Player player, String id, ItemStack sample) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bm = (BookMeta) book.getItemMeta();
        if (bm != null) {
            bm.setTitle("Foxaria " + id);
            bm.setAuthor("Foxaria");
            bm.setPages(ItemTemplateYamlSnippets.pagesForBook(ItemTemplateYamlSnippets.fullSnippetBook(id, sample)));
            book.setItemMeta(bm);
        }
        player.getInventory().addItem(book);
    }

    private void drawEditHub(Player player) {
        ItemStack hand = requireHand(player);
        if (hand.getType().isAir()) {
            setItem(13, MenuItems.item(Material.BARRIER, "&cПустая рука", "&7Возьми предмет в главную руку"), null);
        } else {
            ItemStack show = hand.clone();
            show.setAmount(1);
            setItem(13, show, null);
        }
        setItem(10, MenuItems.item(Material.IRON_SWORD, "&cБой", "&7Урон, скорость, отброс"), e ->
            open(player, Panel.EDIT_COMBAT));
        setItem(12, MenuItems.item(Material.IRON_CHESTPLATE, "&9Защита",
            "&7Броня, твёрдость, стойкость",
            "&7Элитра: те же бонусы, когда надета в нагрудник"), e ->
            open(player, Panel.EDIT_ARMOR));
        setItem(14, MenuItems.item(Material.ANVIL, "&eПрочность", "&7Повреждение, починка"), e ->
            open(player, Panel.EDIT_DURABILITY));
        setItem(16, MenuItems.item(Material.ENCHANTED_BOOK, "&dЗачарования", "&7Все чары реестра, уровни выше лимита"), e ->
            open(player, Panel.EDIT_ENCHANT, listPage, 0));
        setItem(19, MenuItems.item(Material.BREWING_STAND, "&5Зелья", "&7Кастомные эффекты в зелье/стреле"), e ->
            open(player, Panel.EDIT_POTION));
        setItem(21, MenuItems.item(Material.WITHER_ROSE, "&4Эффекты при ударе", "&7Дебаффы → цель, баффы → ты;",
            "&7Считаются: обе руки, вся броня, лук/арбалет, трезубец;",
            "&7Руны держи в левой руке — настрой через переключатель руки"), e ->
            open(player, Panel.EDIT_ON_HIT));
        setItem(25, MenuItems.item(Material.OAK_SIGN, "&fИмя и описание", "&7Переименование (& + код), строки lore;",
            "&7Блок &eFoxaria &7в конце — эффекты удара и зелья"), e ->
            open(player, Panel.EDIT_LORE));
        setItem(22, MenuItems.item(Material.ENCHANTING_TABLE, "&dКрафт и «пустой» блеск",
            "&7&lБлеск без чар &7— выглядит зачарованным (особый предмет).",
            "&7&lЯдро крафта &7— только наши рецепты в верстаке, не ваниль.",
            "&8 ",
            "&7Держи в главной руке &fклон шаблона &7(/itpl give)."), e ->
            open(player, Panel.EDIT_FLAGS));
        setItem(23, MenuItems.item(Material.MILK_BUCKET, "&fСброс Foxaria", "&7Убрать бонусы редактора (атрибуты tpl)"), e -> {
            ItemStack h = requireHand(player);
            if (h.getType().isAir()) {
                messages.send(player, "itemtemplate.editor.empty-hand", "&cНет предмета в главной руке.");
                return;
            }
            HandItemAttributeUtil.clearFoxariaTemplateModifiers(plugin, h);
            player.getInventory().setItemInMainHand(h);
            messages.send(player, "itemtemplate.editor.cleared-tpl", "&eСброшены бонусы шаблонного редактора.");
            open(player, Panel.EDIT_HUB);
        });
    }

    private void drawEnchant(Player player) {
        ItemStack hand = requireHand(player);
        if (hand.getType().isAir()) {
            setItem(13, MenuItems.item(Material.BARRIER, "&cНет предмета", "&7Держи предмет в главной руке"), null);
            setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (meta != null && !meta.getEnchants().isEmpty()) {
            List<String> lines = new ArrayList<>();
            meta.getEnchants().forEach((en, lv) ->
                lines.add("&7" + EditorDisplayNames.enchant(en) + " &f" + lv));
            setItem(4, MenuItems.item(Material.ENCHANTED_BOOK, "&7Сейчас на предмете", lines.toArray(new String[0])), null);
        } else {
            setItem(4, MenuItems.item(Material.BOOK, "&7Сейчас на предмете", "&7— нет зачарований —"), null);
        }

        List<Enchantment> all = HandEnchantUtil.allSorted();
        int start = enchantPage * ENCHANT_PER_PAGE;
        int end = Math.min(all.size(), start + ENCHANT_PER_PAGE);
        int slot = 10;
        for (int i = start; i < end; i++) {
            Enchantment en = all.get(i);
            int lv = HandEnchantUtil.level(hand, en);
            List<String> lore = new ArrayList<>();
            lore.add("&7Уровень: &f" + lv + " &7/ макс. ванилла &f" + en.getMaxLevel());
            if (en.isCursed()) {
                lore.add("&8Проклятие");
            } else if (en.isTreasure()) {
                lore.add("&6Редкое / сокровище");
            }
            lore.add("&7ЛКМ &f+1 &7· Shift+ЛКМ &f-1");
            lore.add("&7ПКМ &fмакс &7· Shift+ПКМ &fснять");
            Material icon = en.isCursed() ? Material.WITHER_SKELETON_SKULL : Material.ENCHANTED_BOOK;
            setItem(slot, MenuItems.item(icon, "&f" + EditorDisplayNames.enchant(en), lore.toArray(new String[0])), e -> {
                ItemStack h = requireHand(player);
                if (h.getType().isAir()) {
                    messages.send(player, "itemtemplate.editor.empty-hand", "&cНет предмета в главной руке.");
                    return;
                }
                if (e.isShiftClick() && e.isRightClick()) {
                    HandEnchantUtil.remove(h, en);
                } else if (e.isRightClick()) {
                    HandEnchantUtil.setMaxLevel(h, en);
                } else if (e.isShiftClick()) {
                    HandEnchantUtil.addLevels(h, en, -1);
                } else {
                    HandEnchantUtil.addLevels(h, en, 1);
                }
                player.getInventory().setItemInMainHand(h);
                open(player, Panel.EDIT_ENCHANT, listPage, enchantPage);
            });
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }

        if (start > 0) {
            setItem(36, MenuItems.item(Material.ARROW, "&7Зачарования назад"), e ->
                open(player, Panel.EDIT_ENCHANT, listPage, enchantPage - 1));
        }
        if (end < all.size()) {
            setItem(44, MenuItems.item(Material.ARROW, "&7Зачарования вперёд"), e ->
                open(player, Panel.EDIT_ENCHANT, listPage, enchantPage + 1));
        }
        setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
    }

    private void drawPotion(Player player) {
        ItemStack hand = requireHand(player);
        if (!HandPotionUtil.supportsPotionMeta(hand)) {
            setItem(13, MenuItems.item(Material.BARRIER, "&cНужно зелье или стрела", "&7Обычное, взрывное,",
                "&7туманное зелье или стрела с эффектом"), null);
            setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
            return;
        }
        ItemStack show = hand.clone();
        show.setAmount(1);
        setItem(13, show, null);

        var session = ItemTemplateWorkbenchSession.get(player);
        setItem(4, MenuItems.item(Material.PAPER, "&7Добавление эффекта",
            "&7Тип: &f" + EditorDisplayNames.potionEffect(session.brewType),
            "&7Длительность: &f" + tickLabel(session.brewDurationTicks),
            "&7Сила: &f" + (session.brewAmplifier + 1) + " &7(I = 1)"), null);

        int ps = 10;
        for (PotionEffectType t : DEBUFF_PRESETS) {
            if (ps > 17) {
                break;
            }
            setItem(ps, MenuItems.item(effectIcon(t), "&f" + EditorDisplayNames.potionEffect(t), "&7ЛКМ — добавить этот эффект",
                "&7(длит./сила как на табличке)"), e -> {
                ItemStack h = requireHand(player);
                if (!HandPotionUtil.supportsPotionMeta(h)) {
                    return;
                }
                session.brewType = t;
                HandPotionUtil.ensureAwkwardBase(h);
                HandPotionUtil.addCustomEffect(h, session.brewType, session.brewDurationTicks, session.brewAmplifier);
                EditorLoreSync.refresh(plugin, h);
                player.getInventory().setItemInMainHand(h);
                messages.send(player, "itemtemplate.editor.potion-added", "&aДобавлен эффект: &f<type>",
                    new MessageService.Placeholder("type", EditorDisplayNames.potionEffect(t)));
                open(player, Panel.EDIT_POTION);
            });
            ps++;
        }

        int step = editorConfig.potionDurationStepTicks();
        int ext = editorConfig.potionExtendAllStepTicks();
        setItem(19, MenuItems.item(Material.MILK_BUCKET, "&eСбросить кастомные", "&7Только кастомные эффекты"), e -> {
            ItemStack h = requireHand(player);
            HandPotionUtil.clearCustomEffects(h);
            EditorLoreSync.refresh(plugin, h);
            player.getInventory().setItemInMainHand(h);
            open(player, Panel.EDIT_POTION);
        });
        setItem(20, MenuItems.item(Material.CLOCK, "&a+ длительность (всем)", "&7+" + tickLabel(ext)), e -> {
            ItemStack h = requireHand(player);
            HandPotionUtil.extendAllCustomDurations(h, ext);
            EditorLoreSync.refresh(plugin, h);
            player.getInventory().setItemInMainHand(h);
            open(player, Panel.EDIT_POTION);
        });
        setItem(21, MenuItems.item(Material.CLOCK, "&c− длительность (всем)", "&7−" + tickLabel(ext)), e -> {
            ItemStack h = requireHand(player);
            HandPotionUtil.extendAllCustomDurations(h, -ext);
            EditorLoreSync.refresh(plugin, h);
            player.getInventory().setItemInMainHand(h);
            open(player, Panel.EDIT_POTION);
        });
        setItem(22, MenuItems.item(Material.LIME_DYE, "&a+ время шага", "&7+" + tickLabel(step) + " к следующему добавлению"), e -> {
            session.brewDurationTicks = Math.min(72000, session.brewDurationTicks + step);
            open(player, Panel.EDIT_POTION);
        });
        setItem(23, MenuItems.item(Material.RED_DYE, "&c− время шага", "&7−" + tickLabel(step)), e -> {
            session.brewDurationTicks = Math.max(20, session.brewDurationTicks - step);
            open(player, Panel.EDIT_POTION);
        });
        setItem(24, MenuItems.item(Material.GLOWSTONE_DUST, "&d+ сила (уровень)", "&7Следующий +1"), e -> {
            session.brewAmplifier = Math.min(4, session.brewAmplifier + 1);
            open(player, Panel.EDIT_POTION);
        });
        setItem(25, MenuItems.item(Material.GUNPOWDER, "&7− сила", "&7Следующий −1"), e -> {
            session.brewAmplifier = Math.max(0, session.brewAmplifier - 1);
            open(player, Panel.EDIT_POTION);
        });
        setItem(28, MenuItems.item(Material.ARROW, "&7Предыдущий тип"), e -> {
            cyclePreset(session, -1);
            open(player, Panel.EDIT_POTION);
        });
        setItem(30, MenuItems.item(Material.ARROW, "&7Следующий тип"), e -> {
            cyclePreset(session, 1);
            open(player, Panel.EDIT_POTION);
        });
        setItem(29, MenuItems.item(Material.SPLASH_POTION, "&eДобавить выбранный", "&7Эффект: &f" + EditorDisplayNames.potionEffect(session.brewType)), e -> {
            ItemStack h = requireHand(player);
            if (!HandPotionUtil.supportsPotionMeta(h)) {
                return;
            }
            HandPotionUtil.ensureAwkwardBase(h);
            HandPotionUtil.addCustomEffect(h, session.brewType, session.brewDurationTicks, session.brewAmplifier);
            EditorLoreSync.refresh(plugin, h);
            player.getInventory().setItemInMainHand(h);
            messages.send(player, "itemtemplate.editor.potion-added", "&aДобавлен эффект: &f<type>",
                new MessageService.Placeholder("type", EditorDisplayNames.potionEffect(session.brewType)));
            open(player, Panel.EDIT_POTION);
        });

        setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
    }

    private void cyclePreset(ItemTemplateWorkbenchSession.State session, int dir) {
        int idx = indexOfPreset(session.brewType);
        if (idx < 0) {
            idx = 0;
        }
        idx = (idx + dir + DEBUFF_PRESETS.length) % DEBUFF_PRESETS.length;
        session.brewType = DEBUFF_PRESETS[idx];
    }

    private int indexOfPreset(PotionEffectType t) {
        for (int i = 0; i < DEBUFF_PRESETS.length; i++) {
            if (DEBUFF_PRESETS[i].equals(t)) {
                return i;
            }
        }
        return -1;
    }

    private static Material effectIcon(PotionEffectType t) {
        if (t == PotionEffectType.WITHER) {
            return Material.WITHER_SKELETON_SKULL;
        }
        if (t == PotionEffectType.POISON) {
            return Material.SPIDER_EYE;
        }
        if (t == PotionEffectType.REGENERATION) {
            return Material.GOLDEN_APPLE;
        }
        if (t == PotionEffectType.STRENGTH || t == PotionEffectType.RESISTANCE) {
            return Material.NETHERITE_SCRAP;
        }
        return Material.SPLASH_POTION;
    }

    private static String tickLabel(int ticks) {
        double sec = ticks / 20.0;
        if (sec >= 60) {
            return String.format("%.1f мин", sec / 60.0);
        }
        return String.format("%.1f с", sec);
    }

    private ItemStack strikeEditStack(Player player) {
        var s = ItemTemplateWorkbenchSession.get(player);
        return s.strikeEditMainHand
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();
    }

    private void putStrikeEditStack(Player player, ItemStack stack) {
        var s = ItemTemplateWorkbenchSession.get(player);
        if (s.strikeEditMainHand) {
            player.getInventory().setItemInMainHand(stack);
        } else {
            player.getInventory().setItemInOffHand(stack);
        }
    }

    private void drawOnHit(Player player) {
        var session = ItemTemplateWorkbenchSession.get(player);
        ItemStack tgt = strikeEditStack(player);

        setItem(1, MenuItems.item(
            session.strikeEditMainHand ? Material.DIAMOND_SWORD : Material.TOTEM_OF_UNDYING,
            session.strikeEditMainHand ? "&fРедакт: правая рука" : "&fРедакт: левая рука (руна)",
            "&7ЛКМ — переключить руку"
        ), e -> {
            session.strikeEditMainHand = !session.strikeEditMainHand;
            open(player, Panel.EDIT_ON_HIT);
        });

        setItem(2, MenuItems.item(
            session.strikeFocus == StrikeFocus.VICTIM ? Material.WITHER_ROSE : Material.GOLDEN_APPLE,
            session.strikeFocus == StrikeFocus.VICTIM ? "&cКрутки: на &nврага" : "&aКрутки: на &nсебя",
            "&7ЛКМ — что меняют кнопки времени/силы/шанса"
        ), e -> {
            session.strikeFocus = session.strikeFocus == StrikeFocus.VICTIM ? StrikeFocus.SELF : StrikeFocus.VICTIM;
            open(player, Panel.EDIT_ON_HIT);
        });

        if (tgt.getType().isAir()) {
            setItem(7, MenuItems.item(Material.BARRIER, "&cПустой слот", "&7Положи предмет в выбранную руку",
                "&7Голова, кристалл, снятая броня — что угодно"), null);
            setItem(4, MenuItems.item(Material.BOOK, "&7Эффекты удара", "&7Любой предмет; в бою считаются обе руки и броня"), null);
            setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
            return;
        }

        if (!editorConfig.onHitEffectsEnabled()) {
            setItem(4, MenuItems.item(Material.BARRIER, "&cЭффекты удара выключены", "&7Включи в &fmodules/item-templates.yml",
                "&7параметр &fon-hit-effects.enabled: true"), null);
        } else {
            List<String> lines = new ArrayList<>();
            lines.add("&7На этой копии предмета:");
            var vics = TemplateOnHitCodec.readAllVictim(plugin, tgt);
            if (vics.isEmpty()) {
                lines.add("&7Враг: &8—");
            } else {
                lines.add("&cВраг &7(" + vics.size() + "):");
                for (var data : vics) {
                    lines.add("&8· &f" + EditorDisplayNames.potionEffect(data.type()) + " &7" + Math.round(data.chance() * 100) + "%");
                }
            }
            var slfs = TemplateOnHitCodec.readAllSelf(plugin, tgt);
            if (slfs.isEmpty()) {
                lines.add("&7Себя: &8—");
            } else {
                lines.add("&aСебя &7(" + slfs.size() + "):");
                for (var data : slfs) {
                    lines.add("&8· &f" + EditorDisplayNames.potionEffect(data.type()) + " &7" + Math.round(data.chance() * 100) + "%");
                }
            }
            lines.add(TemplateOnHitCodec.passiveBeneficialEnabled(plugin, tgt)
                ? "&aПассивные баффы (руки/броня): включены"
                : "&8Пассивные баффы (руки/броня): выключены");
            lines.add("&8При ударе срабатывают все части сета");
            setItem(4, MenuItems.item(Material.KNOWLEDGE_BOOK, "&7Записано", lines.toArray(new String[0])), null);
        }

        setItem(7, tgt.clone(), null);

        PotionEffectType[] strikeSrc = session.strikeFocus == StrikeFocus.VICTIM ? DEBUFF_PRESETS : BUFF_STRIKE_PRESETS;
        int[] presetSlotsFirstRow = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < presetSlotsFirstRow.length && i < strikeSrc.length; i++) {
            final PotionEffectType pick = strikeSrc[i];
            int sl = presetSlotsFirstRow[i];
            setItem(sl, MenuItems.item(effectIcon(pick), "&f" + EditorDisplayNames.potionEffect(pick),
                session.strikeFocus == StrikeFocus.VICTIM
                    ? new String[] {"&7ЛКМ — эффект для &cврага"}
                    : new String[] {"&7ЛКМ — эффект для &aсебя"}), e -> {
                if (session.strikeFocus == StrikeFocus.VICTIM) {
                    session.onHitType = pick;
                } else {
                    session.onHitSelfType = pick;
                }
                open(player, Panel.EDIT_ON_HIT);
            });
        }
        int[] presetSlotsSecondRow = {19, 20};
        for (int j = 0; j < presetSlotsSecondRow.length; j++) {
            int idx = presetSlotsFirstRow.length + j;
            if (idx >= strikeSrc.length) {
                break;
            }
            final PotionEffectType pick = strikeSrc[idx];
            int sl = presetSlotsSecondRow[j];
            setItem(sl, MenuItems.item(effectIcon(pick), "&f" + EditorDisplayNames.potionEffect(pick),
                session.strikeFocus == StrikeFocus.VICTIM
                    ? new String[] {"&7ЛКМ — эффект для &cврага"}
                    : new String[] {"&7ЛКМ — эффект для &aсебя"}), e -> {
                if (session.strikeFocus == StrikeFocus.VICTIM) {
                    session.onHitType = pick;
                } else {
                    session.onHitSelfType = pick;
                }
                open(player, Panel.EDIT_ON_HIT);
            });
        }

        boolean victimF = session.strikeFocus == StrikeFocus.VICTIM;
        int dur = victimF ? session.onHitDurationTicks : session.onHitSelfDurationTicks;
        int amp = victimF ? session.onHitAmplifier : session.onHitSelfAmplifier;
        float ch = victimF ? session.onHitChance : session.onHitSelfChance;
        PotionEffectType typ = victimF ? session.onHitType : session.onHitSelfType;
        float chPct = ch * 100f;

        setItem(28, MenuItems.item(Material.PAPER, "&7Параметры («крутки»)",
            "&7Цель: " + (victimF ? "&cвраг" : "&aты"),
            "&7Эффект: &f" + EditorDisplayNames.potionEffect(typ),
            "&7Длительность: &f" + tickLabel(dur),
            "&7Сила: &f" + (amp + 1),
            "&7Шанс: &f" + String.format("%.0f", chPct) + "%"), null);

        setItem(21, MenuItems.item(Material.CLOCK, "&a+ время", "&7+1 с"), e -> {
            if (session.strikeFocus == StrikeFocus.VICTIM) {
                session.onHitDurationTicks = Math.min(6000, session.onHitDurationTicks + 20);
            } else {
                session.onHitSelfDurationTicks = Math.min(6000, session.onHitSelfDurationTicks + 20);
            }
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(22, MenuItems.item(Material.CLOCK, "&c− время", "&7−1 с"), e -> {
            if (session.strikeFocus == StrikeFocus.VICTIM) {
                session.onHitDurationTicks = Math.max(20, session.onHitDurationTicks - 20);
            } else {
                session.onHitSelfDurationTicks = Math.max(20, session.onHitSelfDurationTicks - 20);
            }
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(23, MenuItems.item(Material.GLOWSTONE_DUST, "&d+ сила", "&7+1 уровень"), e -> {
            if (session.strikeFocus == StrikeFocus.VICTIM) {
                session.onHitAmplifier = Math.min(4, session.onHitAmplifier + 1);
            } else {
                session.onHitSelfAmplifier = Math.min(4, session.onHitSelfAmplifier + 1);
            }
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(24, MenuItems.item(Material.GUNPOWDER, "&7− сила", "&7−1 уровень"), e -> {
            if (session.strikeFocus == StrikeFocus.VICTIM) {
                session.onHitAmplifier = Math.max(0, session.onHitAmplifier - 1);
            } else {
                session.onHitSelfAmplifier = Math.max(0, session.onHitSelfAmplifier - 1);
            }
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(25, MenuItems.item(Material.LIME_DYE, "&a+ шанс", "&7+5%"), e -> {
            if (session.strikeFocus == StrikeFocus.VICTIM) {
                session.onHitChance = Math.min(1f, session.onHitChance + 0.05f);
            } else {
                session.onHitSelfChance = Math.min(1f, session.onHitSelfChance + 0.05f);
            }
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(26, MenuItems.item(Material.RED_DYE, "&c− шанс", "&7−5%"), e -> {
            if (session.strikeFocus == StrikeFocus.VICTIM) {
                session.onHitChance = Math.max(0.05f, session.onHitChance - 0.05f);
            } else {
                session.onHitSelfChance = Math.max(0.05f, session.onHitSelfChance - 0.05f);
            }
            open(player, Panel.EDIT_ON_HIT);
        });

        setItem(37, MenuItems.item(Material.LIME_CONCRETE, "&cДобавить в список &nна врага", "&7Можно несколько эффектов"), e -> {
            if (!editorConfig.onHitEffectsEnabled()) {
                messages.send(player, "itemtemplate.editor.onhit-disabled", "&cЭффекты удара выключены в конфиге модуля.");
                return;
            }
            ItemStack h = strikeEditStack(player);
            if (h.getType().isAir()) {
                return;
            }
            TemplateOnHitCodec.appendVictim(plugin, h, session.onHitType, session.onHitDurationTicks, session.onHitAmplifier, session.onHitChance);
            EditorLoreSync.refresh(plugin, h);
            putStrikeEditStack(player, h);
            messages.send(player, "itemtemplate.editor.onhit-set", "&aЭффект добавлен в список «на врага».");
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(38, MenuItems.item(Material.RED_CONCRETE, "&cОчистить список врага", "&7Все эффекты «на цель»"), e -> {
            ItemStack h = strikeEditStack(player);
            TemplateOnHitCodec.clearVictim(plugin, h);
            EditorLoreSync.refresh(plugin, h);
            putStrikeEditStack(player, h);
            messages.send(player, "itemtemplate.editor.onhit-cleared", "&eСписок эффектов на врага очищен.");
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(39, MenuItems.item(Material.LIME_GLAZED_TERRACOTTA, "&aДобавить в список &nна себя", "&7Можно несколько эффектов"), e -> {
            if (!editorConfig.onHitEffectsEnabled()) {
                messages.send(player, "itemtemplate.editor.onhit-disabled", "&cЭффекты удара выключены в конфиге модуля.");
                return;
            }
            ItemStack h = strikeEditStack(player);
            if (h.getType().isAir()) {
                return;
            }
            TemplateOnHitCodec.appendSelf(plugin, h, session.onHitSelfType, session.onHitSelfDurationTicks, session.onHitSelfAmplifier, session.onHitSelfChance);
            EditorLoreSync.refresh(plugin, h);
            putStrikeEditStack(player, h);
            messages.send(player, "itemtemplate.editor.onhit-self-set", "&aЭффект добавлен в список «на себя».");
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(40, MenuItems.item(Material.ORANGE_GLAZED_TERRACOTTA, "&eОчистить список «на себя»", "&7Все эффекты на себя"), e -> {
            ItemStack h = strikeEditStack(player);
            TemplateOnHitCodec.clearSelf(plugin, h);
            EditorLoreSync.refresh(plugin, h);
            putStrikeEditStack(player, h);
            messages.send(player, "itemtemplate.editor.onhit-self-cleared", "&eСписок эффектов «на себя» очищен.");
            open(player, Panel.EDIT_ON_HIT);
        });
        setItem(41, MenuItems.item(
            TemplateOnHitCodec.passiveBeneficialEnabled(plugin, tgt) ? Material.LIME_DYE : Material.GRAY_DYE,
            TemplateOnHitCodec.passiveBeneficialEnabled(plugin, tgt)
                ? "&aПассивные баффы: ВКЛ"
                : "&7Пассивные баффы: ВЫКЛ",
            "&7ЛКМ — переключить",
            "&7Работают только &aположительные &7эффекты",
            "&7В руке или надето: броня, элитра в нагруднике"
        ), e -> {
            ItemStack h = strikeEditStack(player);
            if (h.getType().isAir()) {
                return;
            }
            boolean newValue = !TemplateOnHitCodec.passiveBeneficialEnabled(plugin, h);
            TemplateOnHitCodec.setPassiveBeneficialEnabled(plugin, h, newValue);
            EditorLoreSync.refresh(plugin, h);
            putStrikeEditStack(player, h);
            messages.send(player, "itemtemplate.editor.passive-toggled",
                newValue ? "&aПассивные баффы (руки/броня) включены." : "&eПассивные баффы (руки/броня) выключены.");
            open(player, Panel.EDIT_ON_HIT);
        });

        setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
    }

    private void drawLore(Player player) {
        var session = ItemTemplateWorkbenchSession.get(player);
        setItem(1, MenuItems.item(
            session.strikeEditMainHand ? Material.DIAMOND_SWORD : Material.TOTEM_OF_UNDYING,
            session.strikeEditMainHand ? "&fРедакт: правая рука" : "&fРедакт: левая рука (руна)",
            "&7ЛКМ — переключить (как в панели удара)"
        ), e -> {
            session.strikeEditMainHand = !session.strikeEditMainHand;
            open(player, Panel.EDIT_LORE);
        });

        ItemStack tgt = strikeEditStack(player);
        if (tgt.getType().isAir()) {
            setItem(7, MenuItems.item(Material.BARRIER, "&cПустой слот", "&7Положи предмет в выбранную руку"), null);
            setItem(4, MenuItems.item(Material.BOOK, "&7Имя и описание", "&7Выбери руку кнопкой слева"), null);
            setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
            return;
        }

        setItem(7, tgt.clone(), null);
        setItem(4, MenuItems.item(Material.KNOWLEDGE_BOOK, "&7Подсказка",
            "&7Имя / строка lore — ввод в &fчат &7после кнопки",
            "&7«Обновить блок» — пересобрать &e⟨Foxaria⟩",
            "&7Отмена: напиши &eотмена"), null);

        setItem(10, MenuItems.item(Material.NAME_TAG, "&fИмя в чат", "&7Меню закроется — строка с &fLegacy-цветами"), e -> {
            session.chatPrompt = ItemTemplateWorkbenchSession.ChatPrompt.DISPLAY_NAME;
            player.closeInventory();
            messages.send(player, "itemtemplate.editor.chat-prompt-name",
                "&eВведите имя предмета в чат &7(Legacy: & + код). &7Отмена: &eотмена");
        });
        setItem(11, MenuItems.item(Material.WRITABLE_BOOK, "&fСтрока описания в чат", "&7Одна строка lore (не ломает блок Foxaria)"), e -> {
            session.chatPrompt = ItemTemplateWorkbenchSession.ChatPrompt.LORE_LINE;
            player.closeInventory();
            messages.send(player, "itemtemplate.editor.chat-prompt-lore",
                "&eВведите одну строку описания в чат. &7Отмена: &eотмена");
        });
        setItem(12, MenuItems.item(Material.EXPERIENCE_BOTTLE, "&aОбновить блок Foxaria", "&7Хвост описания из эффектов удара/зелья"), e -> {
            ItemStack h = strikeEditStack(player);
            if (h.getType().isAir()) {
                return;
            }
            EditorLoreSync.refresh(plugin, h);
            putStrikeEditStack(player, h);
            messages.send(player, "itemtemplate.editor.lore-refreshed", "&aБлок ⟨Foxaria⟩ в описании обновлён.");
            open(player, Panel.EDIT_LORE);
        });

        setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
    }

    private void drawDurability(Player player) {
        ItemStack hand = requireHand(player);
        int max = hand.getType().getMaxDurability();
        int dmg = 0;
        if (hand.getItemMeta() instanceof Damageable d) {
            dmg = d.getDamage();
        }
        setItem(4, MenuItems.item(Material.PAPER, "&7Текущее",
            "&7Повреждение: &f" + dmg + " &7/ &f" + max,
            "&7Неразрушимый: &f" + (hand.getItemMeta() != null && hand.getItemMeta().isUnbreakable())), null);

        var cfg = editorConfig;
        setItem(10, MenuItems.item(Material.LIME_DYE, "&aПолная починка", "&7100% прочности"), e -> applyDurability(player, () ->
            HandItemAttributeUtil.setDurabilityPercent(hand, 1.0)));
        setItem(11, MenuItems.item(Material.YELLOW_DYE, "&e- повреждения", "&7-" + cfg.durabilityStep() + " к полоске износа"), e -> applyDurability(player, () ->
            HandItemAttributeUtil.changeDurability(hand, -cfg.durabilityStep())));
        setItem(12, MenuItems.item(Material.ORANGE_DYE, "&6+ повреждения", "&7+" + cfg.durabilityStep() + " к полоске износа"), e -> applyDurability(player, () ->
            HandItemAttributeUtil.changeDurability(hand, cfg.durabilityStep())));
        setItem(13, MenuItems.item(Material.RED_DYE, "&cПочти сломано", "&7~1% прочности"), e -> applyDurability(player, () ->
            HandItemAttributeUtil.setDurabilityPercent(hand, 0.01)));
        setItem(14, MenuItems.item(Material.PURPLE_DYE, "&d50% прочности"), e -> applyDurability(player, () ->
            HandItemAttributeUtil.setDurabilityPercent(hand, 0.5)));
        setItem(15, MenuItems.item(Material.NETHER_STAR, "&5Неразрушимость"), e -> applyDurability(player, () ->
            HandItemAttributeUtil.toggleUnbreakable(hand)));

        setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
    }

    private void applyDurability(Player player, Runnable change) {
        ItemStack h = requireHand(player);
        if (h.getType().isAir()) {
            messages.send(player, "itemtemplate.editor.empty-hand", "&cНет предмета в главной руке.");
            return;
        }
        change.run();
        player.getInventory().setItemInMainHand(h);
        open(player, Panel.EDIT_DURABILITY);
    }

    private void drawCombat(Player player) {
        ItemStack hand = requireHand(player);
        if (!hand.getType().isAir() && hand.getItemMeta() != null) {
            double ad = HandItemAttributeUtil.ourBonus(hand.getItemMeta(), plugin, Attribute.ATTACK_DAMAGE);
            double as = HandItemAttributeUtil.ourBonus(hand.getItemMeta(), plugin, Attribute.ATTACK_SPEED);
            double kb = HandItemAttributeUtil.ourBonus(hand.getItemMeta(), plugin, Attribute.ATTACK_KNOCKBACK);
            setItem(4, MenuItems.item(Material.IRON_SWORD, "&7Наши бонусы (прибавка)",
                "&7Урон: &f+" + ad,
                "&7Скор. атаки: &f+" + as,
                "&7Отброс: &f+" + kb), null);
        }

        var cfg = editorConfig;
        setItem(10, MenuItems.item(Material.GOLDEN_SWORD, "&6Урон ±", "&7Шаг " + cfg.attackDamageStep()), e -> {});
        setItem(11, MenuItems.item(Material.LIME_DYE, "&a+ урон", "&7+" + cfg.attackDamageStep()), e -> adjCombat(player, Attribute.ATTACK_DAMAGE, cfg.attackDamageStep()));
        setItem(12, MenuItems.item(Material.RED_DYE, "&c- урон", "&7-" + cfg.attackDamageStep()), e -> adjCombat(player, Attribute.ATTACK_DAMAGE, -cfg.attackDamageStep()));

        setItem(14, MenuItems.item(Material.FEATHER, "&eСкорость атаки ±", "&7Шаг " + cfg.attackSpeedStep()), e -> {});
        setItem(15, MenuItems.item(Material.LIME_DYE, "&a+ скорость", "&7+" + cfg.attackSpeedStep()), e -> adjCombat(player, Attribute.ATTACK_SPEED, cfg.attackSpeedStep()));
        setItem(16, MenuItems.item(Material.RED_DYE, "&c- скорость", "&7-" + cfg.attackSpeedStep()), e -> adjCombat(player, Attribute.ATTACK_SPEED, -cfg.attackSpeedStep()));

        setItem(20, MenuItems.item(Material.SLIME_BALL, "&fОтброс ±", "&7Шаг " + cfg.attackKnockbackStep()), e -> {});
        setItem(21, MenuItems.item(Material.LIME_DYE, "&a+ отброс", "&7+" + cfg.attackKnockbackStep()), e -> adjCombat(player, Attribute.ATTACK_KNOCKBACK, cfg.attackKnockbackStep()));
        setItem(22, MenuItems.item(Material.RED_DYE, "&c- отброс", "&7-" + cfg.attackKnockbackStep()), e -> adjCombat(player, Attribute.ATTACK_KNOCKBACK, -cfg.attackKnockbackStep()));

        setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
    }

    private void adjCombat(Player player, Attribute attribute, double delta) {
        ItemStack h = requireHand(player);
        if (h.getType().isAir()) {
            messages.send(player, "itemtemplate.editor.empty-hand", "&cНет предмета в главной руке.");
            return;
        }
        HandItemAttributeUtil.adjustAttributeBonus(plugin, h, attribute, delta);
        player.getInventory().setItemInMainHand(h);
        open(player, Panel.EDIT_COMBAT);
    }

    private void drawArmor(Player player) {
        ItemStack hand = requireHand(player);
        if (!hand.getType().isAir() && hand.getItemMeta() != null) {
            double ar = HandItemAttributeUtil.ourBonus(hand.getItemMeta(), plugin, Attribute.ARMOR);
            double th = HandItemAttributeUtil.ourBonus(hand.getItemMeta(), plugin, Attribute.ARMOR_TOUGHNESS);
            double kr = HandItemAttributeUtil.ourBonus(hand.getItemMeta(), plugin, Attribute.KNOCKBACK_RESISTANCE);
            setItem(4, MenuItems.item(Material.SHIELD, "&7Наши бонусы (прибавка)",
                "&7Броня: &f+" + ar,
                "&7Твёрдость: &f+" + th,
                "&7Стойкость: &f+" + kr), null);
        }

        var cfg = editorConfig;
        setItem(10, MenuItems.item(Material.IRON_CHESTPLATE, "&7Броня ±", "&7Шаг " + cfg.armorStep()), e -> {});
        setItem(11, MenuItems.item(Material.LIME_DYE, "&a+ броня", "&7+" + cfg.armorStep()), e -> adjArmor(player, Attribute.ARMOR, cfg.armorStep()));
        setItem(12, MenuItems.item(Material.RED_DYE, "&c- броня", "&7-" + cfg.armorStep()), e -> adjArmor(player, Attribute.ARMOR, -cfg.armorStep()));

        setItem(14, MenuItems.item(Material.NETHERITE_SCRAP, "&7Твёрдость ±", "&7Шаг " + cfg.armorToughnessStep()), e -> {});
        setItem(15, MenuItems.item(Material.LIME_DYE, "&a+ твёрдость", "&7+" + cfg.armorToughnessStep()), e -> adjArmor(player, Attribute.ARMOR_TOUGHNESS, cfg.armorToughnessStep()));
        setItem(16, MenuItems.item(Material.RED_DYE, "&c- твёрдость", "&7-" + cfg.armorToughnessStep()), e -> adjArmor(player, Attribute.ARMOR_TOUGHNESS, -cfg.armorToughnessStep()));

        setItem(20, MenuItems.item(Material.LEATHER_BOOTS, "&7Стойкость к отбросу ±", "&7Шаг " + cfg.knockbackResistanceStep()), e -> {});
        setItem(21, MenuItems.item(Material.LIME_DYE, "&a+ стойкость", "&7+" + cfg.knockbackResistanceStep()), e -> adjArmor(player, Attribute.KNOCKBACK_RESISTANCE, cfg.knockbackResistanceStep()));
        setItem(22, MenuItems.item(Material.RED_DYE, "&c- стойкость", "&7-" + cfg.knockbackResistanceStep()), e -> adjArmor(player, Attribute.KNOCKBACK_RESISTANCE, -cfg.knockbackResistanceStep()));

        setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
    }

    private void adjArmor(Player player, Attribute attribute, double delta) {
        ItemStack h = requireHand(player);
        if (h.getType().isAir()) {
            messages.send(player, "itemtemplate.editor.empty-hand", "&cНет предмета в главной руке.");
            return;
        }
        HandItemAttributeUtil.adjustAttributeBonus(plugin, h, attribute, delta);
        player.getInventory().setItemInMainHand(h);
        open(player, Panel.EDIT_ARMOR);
    }

    private void drawFlags(Player player) {
        ItemStack hand = requireHand(player);
        Optional<String> idOpt = templates.findMatchingTemplateId(hand);
        if (idOpt.isEmpty()) {
            setItem(13, MenuItems.item(Material.BARRIER, "&cНет совпадения с шаблоном",
                "&7В главной руке должен быть &fклон шаблона&7:",
                "&f/itpl give <id>"), null);
            setItem(31, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
            return;
        }
        String id = idOpt.get();
        ItemTemplateService.TemplateDisplayFlags flags = templates.displayFlags(id)
            .orElse(new ItemTemplateService.TemplateDisplayFlags(false, false));
        ItemStack show = hand.clone();
        show.setAmount(1);
        setItem(4, MenuItems.item(Material.PAPER, "&7Шаблон: &f" + id,
            "&7Пустой блеск: " + (flags.enchantGlint() ? "&aда" : "&7нет"),
            "&7Ядро крафта: " + (flags.craftingCoreIngredient() ? "&aда" : "&7нет")), null);
        setItem(13, show, null);

        setItem(19, MenuItems.item(
            flags.enchantGlint() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            "&6Пустой блеск",
            "&7Визуал «как зачаровано» без настоящих чар.",
            "&7Сейчас: " + (flags.enchantGlint() ? "&aвкл" : "&cвыкл"),
            "&7ЛКМ — переключить"
        ), e -> templates.setTemplateDisplayFlags(id, !flags.enchantGlint(), flags.craftingCoreIngredient()).whenComplete((v, ex) ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ex != null) {
                    messages.send(player, "itemtemplate.flags-error", "&cНе удалось: <msg>",
                        new MessageService.Placeholder("msg", ex.getMessage() == null ? "?" : ex.getMessage()));
                } else {
                    messages.send(player, "itemtemplate.flags-saved", "&aФлаги обновлены: &f<id>", new MessageService.Placeholder("id", id));
                }
                open(player, Panel.EDIT_FLAGS);
            })));

        setItem(21, MenuItems.item(
            flags.craftingCoreIngredient() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            "&5Ядро крафта",
            "&7В верстаке только &fFoxaria-крафты&7, ваниль не подхватит.",
            "&7Сейчас: " + (flags.craftingCoreIngredient() ? "&aда" : "&cнет"),
            "&7ЛКМ — переключить"
        ), e -> templates.setTemplateDisplayFlags(id, flags.enchantGlint(), !flags.craftingCoreIngredient()).whenComplete((v, ex) ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ex != null) {
                    messages.send(player, "itemtemplate.flags-error", "&cНе удалось: <msg>",
                        new MessageService.Placeholder("msg", ex.getMessage() == null ? "?" : ex.getMessage()));
                } else {
                    messages.send(player, "itemtemplate.flags-saved", "&aФлаги обновлены: &f<id>", new MessageService.Placeholder("id", id));
                }
                open(player, Panel.EDIT_FLAGS);
            })));

        setItem(25, MenuItems.item(Material.ARROW, "&7К редактору"), e -> open(player, Panel.EDIT_HUB));
    }
}
