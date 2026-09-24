package com.foxaria.itemtemplates;

import com.foxaria.api.item.FoxariaItemPdc;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class DefaultItemTemplateService implements ItemTemplateService {

    private final JavaPlugin plugin;
    private final ItemTemplateRepository repository;
    private volatile Map<String, TemplateRow> cache = Map.of();

    public DefaultItemTemplateService(JavaPlugin plugin, ItemTemplateRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void bootstrapCache() {
        Map<String, TemplateRow> map = repository.loadAllRows().join();
        cache = Map.copyOf(map);
    }

    @Override
    public Optional<ItemStack> cloneTemplate(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return Optional.empty();
        }
        TemplateRow row = cache.get(templateId);
        if (row == null) {
            return Optional.empty();
        }
        try {
            ItemStack stack = ItemStackSerializer.deserialize(row.stackData()).clone();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (row.enchantGlint() != 0) {
                    meta.setEnchantmentGlintOverride(true);
                }
                if (row.craftingCore() != 0) {
                    meta.getPersistentDataContainer().set(FoxariaItemPdc.craftingCoreIngredient(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                }
                stack.setItemMeta(meta);
            }
            return Optional.of(stack);
        } catch (Exception e) {
            plugin.getLogger().warning("Bad template data for '" + templateId + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String templateId) {
        return templateId != null && cache.containsKey(templateId);
    }

    @Override
    public List<String> listTemplateIds() {
        return new ArrayList<>(cache.keySet());
    }

    @Override
    public CompletableFuture<Void> reload() {
        return repository.loadAllRows().thenAccept(map -> cache = Map.copyOf(map));
    }

    @Override
    public CompletableFuture<Void> saveTemplate(String templateId, ItemStack stack, String notes) {
        if (templateId == null || templateId.isBlank() || stack == null || stack.getType().isAir()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("invalid template or empty stack"));
        }
        TemplateRow existing = cache.get(templateId);
        int glint = existing != null ? existing.enchantGlint() : 0;
        int core = existing != null ? existing.craftingCore() : 0;
        String data = ItemStackSerializer.serialize(stack);
        return repository.upsert(templateId, data, notes, glint, core)
            .thenCompose(v -> repository.loadAllRows().thenAccept(map -> cache = Map.copyOf(map)));
    }

    @Override
    public CompletableFuture<Boolean> deleteTemplate(String templateId) {
        return repository.delete(templateId).thenCompose(removed -> {
            if (!removed) {
                return CompletableFuture.completedFuture(false);
            }
            return repository.loadAllRows().thenApply(map -> {
                cache = Map.copyOf(map);
                return true;
            });
        });
    }

    @Override
    public Optional<TemplateDisplayFlags> displayFlags(String templateId) {
        TemplateRow row = cache.get(templateId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new TemplateDisplayFlags(row.enchantGlint() != 0, row.craftingCore() != 0));
    }

    @Override
    public Optional<String> findMatchingTemplateId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        ItemStack probe = stack.clone();
        probe.setAmount(1);
        for (String id : cache.keySet()) {
            Optional<ItemStack> opt = cloneTemplate(id);
            if (opt.isEmpty()) {
                continue;
            }
            ItemStack t = opt.get();
            t.setAmount(1);
            if (t.isSimilar(probe)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Void> setTemplateDisplayFlags(String templateId, boolean enchantGlint, boolean craftingCoreIngredient) {
        TemplateRow row = cache.get(templateId);
        if (row == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("unknown template: " + templateId));
        }
        int g = enchantGlint ? 1 : 0;
        int c = craftingCoreIngredient ? 1 : 0;
        return repository.upsert(templateId, row.stackData(), row.notes(), g, c)
            .thenCompose(v -> repository.loadAllRows().thenAccept(map -> cache = Map.copyOf(map)));
    }

    @Override
    public String yamlReference(String templateId) {
        return "item-template: \"" + templateId + "\"";
    }
}
