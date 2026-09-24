package com.foxaria.shop;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.KnowledgeService;
import com.foxaria.api.service.ShopService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.shop.progression.AdminProgressionShopCommand;
import com.foxaria.shop.progression.ProgressionLifecycleListener;
import com.foxaria.shop.progression.ProgressionRepository;
import com.foxaria.shop.progression.ProgressionService;
import com.foxaria.shop.progression.QuestCatalog;
import com.foxaria.shop.progression.QuestCommand;
import com.foxaria.shop.progression.PlayerPlacedBlockTracker;
import com.foxaria.shop.progression.QuestProgressListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.List;

public final class FoxariaShopModule implements FoxariaModule {

    private static QuestCatalog loadQuestCatalog(JavaPlugin plugin, FileConfiguration diskYaml) {
        try {
            QuestCatalog c = QuestCatalog.fromConfig(diskYaml);
            if (c.firstQuestId() != null) {
                return c;
            }
            plugin.getLogger().warning(
                "[Foxaria] quests.yml на диске без валидного каталога (нужна секция tiers: со ступенями \"1\"..\"5\", "
                    + "по 21 квесту; старый формат chain-order/quests не подходит) — загружаю встроенный из JAR.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Foxaria] Ошибка чтения quests.yml с диска, беру встроенный.", e);
        }
        try {
            QuestCatalog c = QuestCatalog.fromResource(plugin);
            if (c.firstQuestId() == null) {
                throw new IllegalStateException("Встроенный modules/quests.yml пуст или повреждён.");
            }
            return c;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Foxaria] Не удалось загрузить квесты даже из JAR.", e);
            throw new IllegalStateException("Quest catalog load failed", e);
        }
    }

    private ProgressionLifecycleListener lifecycleListener;
    private QuestProgressListener questListener;
    private ProgressionService progressionService;

    @Override
    public String id() {
        return "shop";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core", "economy");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(4, "shop_base", "db/migration/V4__shop_base.sql"),
            new MigrationScript(29, "progression_quest_shop", "db/migration/V29__progression_quest_shop.sql"),
            new MigrationScript(30, "quest_objective_progress", "db/migration/V30__quest_objective_progress.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/quests.yml");
        context.configs().saveDefault("modules/progression-shop.yml");
        context.database().applyMigrations(migrations());

        JavaPlugin plugin = context.plugin();
        FileConfiguration questsYaml = context.configs().module("modules/quests.yml");
        FileConfiguration shopGuiYaml = context.configs().module("modules/progression-shop.yml");
        QuestCatalog catalog = loadQuestCatalog(plugin, questsYaml);

        ProgressionRepository progressionRepository = new ProgressionRepository(context.database());
        MenuManager menuManager = context.services().require(MenuManager.class);
        EconomyService economyService = context.services().require(EconomyService.class);
        ItemTemplateService itemTemplates = context.services().optional(ItemTemplateService.class);
        this.progressionService = new ProgressionService(
            plugin,
            progressionRepository,
            catalog,
            context.messages(),
            menuManager,
            economyService,
            itemTemplates
        );
        context.services().register(KnowledgeService.class, progressionService);
        progressionService.start();
        ProgressionShopService shopService = new ProgressionShopService(
            plugin,
            economyService,
            context.messages(),
            context.audits(),
            menuManager,
            itemTemplates,
            progressionRepository,
            progressionService,
            shopGuiYaml
        );
        context.services().register(ShopService.class, shopService);

        lifecycleListener = new ProgressionLifecycleListener(plugin, progressionService);
        plugin.getServer().getPluginManager().registerEvents(lifecycleListener, plugin);
        PlayerPlacedBlockTracker placedBlocks = new PlayerPlacedBlockTracker();
        questListener = new QuestProgressListener(progressionService, placedBlocks);
        plugin.getServer().getPluginManager().registerEvents(questListener, plugin);

        PluginCommand shopCmd = plugin.getCommand("shop");
        if (shopCmd != null) {
            shopCmd.setExecutor(new com.foxaria.shop.command.ShopCommand(shopService, context.messages()));
        }

        PluginCommand questCmd = plugin.getCommand("quest");
        if (questCmd != null) {
            QuestCommand qc = new QuestCommand(progressionService, context.messages());
            questCmd.setExecutor(qc);
        }

        PluginCommand adminQs = plugin.getCommand("adminquestshop");
        if (adminQs != null) {
            AdminProgressionShopCommand ac = new AdminProgressionShopCommand(shopService, context.messages());
            adminQs.setExecutor(ac);
            adminQs.setTabCompleter(ac);
        }
    }

    @Override
    public void stop() {
        if (lifecycleListener != null) {
            HandlerList.unregisterAll(lifecycleListener);
            lifecycleListener = null;
        }
        if (questListener != null) {
            HandlerList.unregisterAll(questListener);
            questListener = null;
        }
        if (progressionService != null) {
            progressionService.stop();
            progressionService = null;
        }
    }
}
