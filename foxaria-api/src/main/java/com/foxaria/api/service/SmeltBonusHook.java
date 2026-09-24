package com.foxaria.api.service;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Дополнительный дроп при плавке (шаблоны, шансы). Реализация в модуле crafting.
 */
@FunctionalInterface
public interface SmeltBonusHook {

    /**
     * Вызывается после того, как из входа «съели» одну единицу и основной результат уже учтён.
     *
     * @param smelterUuid      последний игрок, открывавший печь (null — воронка / неизвестно)
     * @param furnaceBlock     блок печи / плавильной печи или модерн-печи
     * @param inputMaterial    тип сырья, которое переплавилось на этот шаг
     * @param vanillaBlast     true если это плавильная печь ваниллы
     * @param modernFurnace    true если это модерн-печь Foxaria
     * @param deliverBonus     выдать один бонусный стак (слияние в инвентарь печи или дроп — решает вызывающий)
     */
    void afterOneSmelted(
        UUID smelterUuid,
        Location furnaceBlock,
        Material inputMaterial,
        boolean vanillaBlast,
        boolean modernFurnace,
        Consumer<ItemStack> deliverBonus
    );
}
