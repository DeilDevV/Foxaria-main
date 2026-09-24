package com.foxaria.admin.command;

import com.foxaria.api.service.MessageService;
import com.foxaria.core.command.FoxariaStaffPermissions;
import io.papermc.paper.plugin.configuration.PluginMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

/**
 * Полная перезагрузка jar Foxaria без рестарта сервера: disable → loadPlugin → enablePlugin.
 * После сборки замените Foxaria.jar в plugins и выполните команду.
 */
public final class FoxariaReloadCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final MessageService messages;

    public FoxariaReloadCommand(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!FoxariaStaffPermissions.has(sender, "foxaria.admin.reload")) {
            messages.send(sender, "general.no-permission", "&cУ вас нет прав.");
            return true;
        }

        if (!plugin.getName().equals("Foxaria")) {
            sender.sendMessage(Component.text("Эта команда доступна только для плагина Foxaria.", NamedTextColor.RED));
            return true;
        }

        messages.send(sender, "admin.foxreload-start", "&eПерезагрузка Foxaria...");

        try {
            Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
        } catch (ClassNotFoundException e) {
            sender.sendMessage(Component.text(
                "Перезагрузка Foxaria без рестарта сервера поддерживается только на Paper.",
                NamedTextColor.RED
            ));
            return true;
        }

        PluginManager pm = Bukkit.getPluginManager();
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File jar = new File(pluginsDir, plugin.getName() + ".jar");
        if (!jar.isFile()) {
            sender.sendMessage(Component.text(
                "Не найден файл плагина: " + jar.getPath(),
                NamedTextColor.RED
            ));
            return true;
        }

        PluginMeta meta = plugin.getPluginMeta();

        try {
            pm.disablePlugin(plugin);
        } catch (Throwable t) {
            plugin.getLogger().severe("Foxaria disable during reload failed: " + t.getMessage());
            t.printStackTrace();
            sender.sendMessage(Component.text("Ошибка при отключении Foxaria: " + t.getMessage(), NamedTextColor.RED));
            return true;
        }

        try {
            unregisterDisabledPluginInPaper(plugin, meta);
        } catch (IllegalStateException e) {
            Bukkit.getLogger().severe("Не удалось обновить реестр плагинов Paper после disable: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(Component.text(
                "Foxaria отключена, но перезагрузка сорвалась. Перезапустите сервер.",
                NamedTextColor.RED
            ));
            return true;
        }

        try {
            unregisterPluginCommands(plugin);
        } catch (IllegalStateException e) {
            Bukkit.getLogger().warning("Команды старого Foxaria не очищены полностью: " + e.getMessage());
        }

        try {
            Plugin loaded = pm.loadPlugin(jar);
            if (loaded == null) {
                sender.sendMessage(Component.text(
                    "Не удалось загрузить Foxaria из jar. Перезапустите сервер вручную.",
                    NamedTextColor.RED
                ));
                return true;
            }
            pm.enablePlugin(loaded);
            syncCommands();
        } catch (InvalidPluginException | InvalidDescriptionException | UnknownDependencyException e) {
            Bukkit.getLogger().severe("Foxaria reload failed while loading jar: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(Component.text(
                "Ошибка загрузки нового Foxaria.jar: " + e.getMessage() + ". Перезапустите сервер.",
                NamedTextColor.RED
            ));
            return true;
        }

        sender.sendMessage(Component.text("Foxaria перезагружена.", NamedTextColor.GREEN));
        return true;
    }

    private void unregisterDisabledPluginInPaper(JavaPlugin disabledPlugin, PluginMeta meta) {
        try {
            Class<?> implClass = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
            Method getInstance = implClass.getMethod("getInstance");
            Object paperPluginManager = getInstance.invoke(null);

            Field instanceManagerField = implClass.getDeclaredField("instanceManager");
            instanceManagerField.setAccessible(true);
            Object instanceManager = instanceManagerField.get(paperPluginManager);

            Class<?> imClass = instanceManager.getClass();
            Field pluginsField = imClass.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Plugin> plugins = (List<Plugin>) pluginsField.get(instanceManager);

            Field lookupField = imClass.getDeclaredField("lookupNames");
            lookupField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Object, Plugin> lookupNames = (Map<Object, Plugin>) lookupField.get(instanceManager);

            Field dependencyTreeField = imClass.getDeclaredField("dependencyTree");
            dependencyTreeField.setAccessible(true);
            Object dependencyTree = dependencyTreeField.get(instanceManager);

            synchronized (instanceManager) {
                plugins.remove(disabledPlugin);
                Iterator<Map.Entry<Object, Plugin>> iterator = lookupNames.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Object, Plugin> entry = iterator.next();
                    if (entry.getValue() == disabledPlugin) {
                        iterator.remove();
                    }
                }
                Method removeMethod = dependencyTree.getClass().getMethod("remove", PluginMeta.class);
                removeMethod.invoke(dependencyTree, meta);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Paper plugin registry cleanup failed", e);
        }
    }

    private void unregisterPluginCommands(JavaPlugin disabledPlugin) {
        try {
            Server server = Bukkit.getServer();
            Field commandMapField = findField(server.getClass(), "commandMap");
            commandMapField.setAccessible(true);
            Object commandMapObject = commandMapField.get(server);
            if (!(commandMapObject instanceof CommandMap commandMap)) {
                throw new IllegalStateException("Server commandMap is not a CommandMap: " + commandMapObject);
            }

            Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            Set<Command> removed = new HashSet<>();
            List<String> keysToRemove = new ArrayList<>();
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                Command cmd = entry.getValue();
                if (cmd instanceof PluginIdentifiableCommand pic && pic.getPlugin() == disabledPlugin) {
                    keysToRemove.add(entry.getKey());
                    removed.add(cmd);
                }
            }
            for (String key : keysToRemove) {
                knownCommands.remove(key);
            }

            for (Command cmd : removed) {
                cmd.unregister(commandMap);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Command map cleanup failed", e);
        }
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }

    private void syncCommands() {
        try {
            Method sync = Bukkit.getServer().getClass().getMethod("syncCommands");
            sync.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException ignored) {
            // Best effort on non-Craft/Paper implementations.
        }
    }
}
