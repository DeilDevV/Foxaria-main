package com.foxaria.itemtemplates;

import com.foxaria.api.FoxariaModule;
import com.foxaria.api.MigrationScript;
import com.foxaria.api.ModuleContext;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.itemtemplates.command.ItemTemplateCommand;
import com.foxaria.itemtemplates.listener.ItemTemplateChatListener;
import com.foxaria.itemtemplates.listener.ItemTemplateOnHitListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class FoxariaItemTemplatesModule implements FoxariaModule {

    @Override
    public String id() {
        return "item-templates";
    }

    @Override
    public List<String> dependencies() {
        return List.of("core");
    }

    @Override
    public List<MigrationScript> migrations() {
        return List.of(
            new MigrationScript(24, "item_templates", "db/migration/V24__item_templates.sql"),
            new MigrationScript(35, "item_template_flags", "db/migration/V35__item_template_flags.sql")
        );
    }

    @Override
    public void start(ModuleContext context) {
        context.configs().saveDefault("modules/item-templates.yml");
        context.database().applyMigrations(migrations());
        ItemTemplateRepository repository = new ItemTemplateRepository(context.database());
        DefaultItemTemplateService service = new DefaultItemTemplateService(context.plugin(), repository);
        service.bootstrapCache();
        BuiltinSulfurTemplate.ensure(context.plugin(), service);
        context.services().register(ItemTemplateService.class, service);

        FileConfiguration editorYaml = context.configs().module("modules/item-templates.yml");
        ItemTemplateEditorConfig editorConfig = ItemTemplateEditorConfig.from(editorYaml);

        ItemTemplateCommand command = new ItemTemplateCommand(
            context.plugin(),
            service,
            context.messages(),
            context.services().require(MenuManager.class),
            editorConfig
        );
        PluginCommand cmd = context.plugin().getCommand("itemtemplate");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        PluginCommand alias = context.plugin().getCommand("itpl");
        if (alias != null) {
            alias.setExecutor(command);
            alias.setTabCompleter(command);
        }

        context.plugin().getServer().getPluginManager().registerEvents(
            new ItemTemplateOnHitListener(context.plugin(), editorConfig.onHitEffectsEnabled()),
            context.plugin()
        );
        context.plugin().getServer().getPluginManager().registerEvents(
            new ItemTemplateChatListener(context.plugin(), context.messages()),
            context.plugin()
        );
    }

    @Override
    public void stop() {
    }
}
