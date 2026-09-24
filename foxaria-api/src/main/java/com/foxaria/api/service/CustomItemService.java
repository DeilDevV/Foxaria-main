package com.foxaria.api.service;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface CustomItemService {

    ItemStack stampIdentity(ItemStack itemStack, String type);

    boolean hasIdentity(ItemStack itemStack);

    UUID readIdentity(ItemStack itemStack);
}
