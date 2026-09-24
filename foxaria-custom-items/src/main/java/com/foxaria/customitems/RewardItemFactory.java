package com.foxaria.customitems;

import com.foxaria.core.text.FoxariaText;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public final class RewardItemFactory {

    private final PdcCustomItemService customItemService;
    private final CustomItemsRepository repository;
    private final FileConfiguration config;

    public RewardItemFactory(PdcCustomItemService customItemService, CustomItemsRepository repository, FileConfiguration config) {
        this.customItemService = customItemService;
        this.repository = repository;
        this.config = config;
    }

    public ItemStack createKey(String crateId) {
        return createKey(crateId, config.getString("crates." + crateId + ".key-name", "&aCrate Key: " + crateId));
    }

    public ItemStack createKey(String crateId, String displayName) {
        ItemStack itemStack = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(FoxariaText.legacy(displayName));
        itemStack.setItemMeta(meta);
        return register(customItemService.stampIdentity(itemStack, keyType(crateId)));
    }

    public ItemStack createToken(int amount) {
        Material material = Material.matchMaterial(config.getString("reward-items.token-material", "SUNFLOWER"));
        if (material == null) {
            material = Material.SUNFLOWER;
        }
        ItemStack itemStack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(FoxariaText.legacy("&6Foxaria Token"));
        itemStack.setItemMeta(meta);
        return register(customItemService.stampIdentity(itemStack, config.getString("reward-items.token-item-type", "token")));
    }

    public ItemStack createOneTimeReward(String rewardId, String displayName, List<String> loreLines, String rarity) {
        Material material = Material.matchMaterial(config.getString("reward-items.claim-item-material", "AMETHYST_SHARD"));
        if (material == null) {
            material = Material.AMETHYST_SHARD;
        }
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(FoxariaText.legacy(displayName));
        if (loreLines != null && !loreLines.isEmpty()) {
            meta.lore(FoxariaText.legacyLore(loreLines));
        }
        itemStack.setItemMeta(meta);
        ItemStack stamped = customItemService.stampIdentity(itemStack, "one_time_reward:" + rewardId);
        stamped = customItemService.stampClaimKey(stamped, rewardId);
        stamped = customItemService.stampRarity(stamped, rarity);
        return register(stamped);
    }

    private ItemStack register(ItemStack itemStack) {
        UUID identity = customItemService.readIdentity(itemStack);
        String type = customItemService.readType(itemStack);
        if (identity != null && type != null) {
            repository.registerIssued(identity, type);
        }
        return itemStack;
    }

    private String keyType(String crateId) {
        return config.getString("reward-items.crate-key-prefix", "crate_key") + ":" + crateId;
    }
}
