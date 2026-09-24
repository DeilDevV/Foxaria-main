package com.foxaria.api.service;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Глобальные шаблоны предметов (полный ItemStack: зелья, зачарования, имя, lore).
 * Загружаются из БД и кешируются в памяти.
 */
public interface ItemTemplateService {

    record TemplateDisplayFlags(boolean enchantGlint, boolean craftingCoreIngredient) {
    }

    /**
     * Клон шаблона из кеша. Пусто, если id неизвестен.
     */
    Optional<ItemStack> cloneTemplate(String templateId);

    boolean exists(String templateId);

    List<String> listTemplateIds();

    CompletableFuture<Void> reload();

    CompletableFuture<Void> saveTemplate(String templateId, ItemStack stack, String notes);

    CompletableFuture<Boolean> deleteTemplate(String templateId);

    Optional<TemplateDisplayFlags> displayFlags(String templateId);

    CompletableFuture<Void> setTemplateDisplayFlags(String templateId, boolean enchantGlint, boolean craftingCoreIngredient);

    /**
     * Находит id шаблона, если стак совпадает с клоном шаблона (имя, мета, NBT; количество игнорируется).
     */
    Optional<String> findMatchingTemplateId(ItemStack stack);

    /**
     * Фрагмент для вставки в YAML (магазины, награды).
     */
    String yamlReference(String templateId);
}
