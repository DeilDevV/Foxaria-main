package com.foxaria.itemtemplates.listener;

import com.foxaria.api.service.MessageService;
import com.foxaria.itemtemplates.session.ItemTemplateWorkbenchSession;
import com.foxaria.itemtemplates.util.EditorLoreSync;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Переименование и строки lore с &amp;-цветами через чат (после кнопки в GUI).
 */
public final class ItemTemplateChatListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final JavaPlugin plugin;
    private final MessageService messages;

    public ItemTemplateChatListener(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        var state = ItemTemplateWorkbenchSession.get(player);
        if (state.chatPrompt == ItemTemplateWorkbenchSession.ChatPrompt.NONE) {
            return;
        }
        event.setCancelled(true);
        String raw = PLAIN.serialize(event.message()).trim();
        if (raw.equalsIgnoreCase("отмена")) {
            state.chatPrompt = ItemTemplateWorkbenchSession.ChatPrompt.NONE;
            plugin.getServer().getScheduler().runTask(plugin, () ->
                messages.send(player, "itemtemplate.editor.chat-cancelled", "&7Ввод отменён."));
            return;
        }
        ItemTemplateWorkbenchSession.ChatPrompt prompt = state.chatPrompt;
        state.chatPrompt = ItemTemplateWorkbenchSession.ChatPrompt.NONE;
        plugin.getServer().getScheduler().runTask(plugin, () -> handleSync(player, state, prompt, raw));
    }

    private void handleSync(Player player, ItemTemplateWorkbenchSession.State state, ItemTemplateWorkbenchSession.ChatPrompt prompt, String text) {
        ItemStack stack = state.strikeEditMainHand
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();
        if (stack.getType().isAir()) {
            messages.send(player, "itemtemplate.editor.strike-empty-slot", "&cНет предмета в выбранной руке.");
            return;
        }
        if (prompt == ItemTemplateWorkbenchSession.ChatPrompt.DISPLAY_NAME) {
            EditorLoreSync.setColoredDisplayName(stack, text);
        } else {
            EditorLoreSync.appendUserLoreLine(plugin, stack, text);
        }
        EditorLoreSync.refresh(plugin, stack);
        if (state.strikeEditMainHand) {
            player.getInventory().setItemInMainHand(stack);
        } else {
            player.getInventory().setItemInOffHand(stack);
        }
        if (prompt == ItemTemplateWorkbenchSession.ChatPrompt.DISPLAY_NAME) {
            messages.send(player, "itemtemplate.editor.chat-name-done", "&aИмя предмета обновлено.");
        } else {
            messages.send(player, "itemtemplate.editor.chat-lore-done", "&aСтрока добавлена в описание.");
        }
    }
}
