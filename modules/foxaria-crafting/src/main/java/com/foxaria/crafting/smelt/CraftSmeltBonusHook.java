package com.foxaria.crafting.smelt;

import com.foxaria.api.service.SmeltBonusHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;

public final class CraftSmeltBonusHook implements SmeltBonusHook {

    private final SmeltBonusCoordinator coordinator;

    public CraftSmeltBonusHook(SmeltBonusCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void afterOneSmelted(
        UUID smelterUuid,
        Location furnaceBlock,
        Material inputMaterial,
        boolean vanillaBlast,
        boolean modernFurnace,
        Consumer<ItemStack> deliverBonus
    ) {
        coordinator.rollAll(smelterUuid, furnaceBlock, inputMaterial, deliverBonus);
    }
}
