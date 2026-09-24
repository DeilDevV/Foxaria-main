package com.foxaria.itemtemplates;

import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.core.text.FoxariaText;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * Встроенный шаблон {@code sulfur}: жёлтый краситель «Сера» с блеском (enchant_glint в БД).
 */
public final class BuiltinSulfurTemplate {

    private BuiltinSulfurTemplate() {
    }

    public static void ensure(JavaPlugin plugin, DefaultItemTemplateService templates) {
        if (templates.exists("sulfur")) {
            return;
        }
        ItemStack s = new ItemStack(Material.YELLOW_DYE);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(FoxariaText.legacy("&e&lСера"));
            s.setItemMeta(m);
        }
        CompletableFuture<Void> save = templates.saveTemplate("sulfur", s, "Встроенно: бонус при плавке руд и песка");
        save.join();
        templates.setTemplateDisplayFlags("sulfur", true, false).join();
        plugin.getLogger().info("[item-templates] Создан встроенный шаблон sulfur");
    }
}
