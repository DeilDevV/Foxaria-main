package com.foxaria.customitems;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.customitems.gui.CrateBrowserMenu;
import com.foxaria.customitems.gui.CratePreviewMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class ConfigCrateService {

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final MessageService messages;
    private final AuditService audits;
    private final EconomyService economyService;
    private final MenuManager menuManager;
    private final PdcCustomItemService itemService;
    private final RewardItemFactory rewardFactory;
    private final CustomItemsRepository repository;
    private final Map<String, CrateDefinition> crates;
    private final Map<String, ClaimRewardDefinition> claimRewards;

    public ConfigCrateService(
        JavaPlugin plugin,
        FileConfiguration config,
        MessageService messages,
        AuditService audits,
        EconomyService economyService,
        MenuManager menuManager,
        PdcCustomItemService itemService,
        RewardItemFactory rewardFactory,
        CustomItemsRepository repository
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.audits = audits;
        this.economyService = economyService;
        this.menuManager = menuManager;
        this.itemService = itemService;
        this.rewardFactory = rewardFactory;
        this.repository = repository;
        this.crates = loadCrates(config.getConfigurationSection("crates"));
        this.claimRewards = loadClaimRewards(config.getConfigurationSection("claim-rewards"));
    }

    public List<CrateDefinition> crates() {
        return crates.values().stream().sorted(Comparator.comparing(CrateDefinition::id)).toList();
    }

    public Optional<CrateDefinition> crate(String crateId) {
        return Optional.ofNullable(crates.get(crateId.toLowerCase(Locale.ROOT)));
    }

    public void openBrowser(Player player) {
        menuManager.open(player, new CrateBrowserMenu(this));
    }

    public void preview(Player player, String crateId) {
        CrateDefinition crate = crates.get(crateId.toLowerCase(Locale.ROOT));
        if (crate == null) {
            messages.send(player, "customitems.crate-missing", "&cCrate not found.");
            return;
        }
        menuManager.open(player, new CratePreviewMenu(crate));
    }

    public void giveKey(OfflinePlayer target, String crateId, int amount, UUID actorUuid) {
        CrateDefinition crate = crates.get(crateId.toLowerCase(Locale.ROOT));
        if (crate == null) {
            return;
        }
        Runnable grant = () -> {
            Player online = target.getPlayer();
            if (online == null) {
                return;
            }
            int finalAmount = Math.max(1, amount);
            for (int index = 0; index < finalAmount; index++) {
                deliverItem(online, rewardFactory.createKey(crate.id(), crate.keyName()));
            }
            messages.send(online, "customitems.key-given", "&aYou received <amount>x <crate> key(s).",
                new MessageService.Placeholder("amount", String.valueOf(finalAmount)),
                new MessageService.Placeholder("crate", crate.displayName()));
            audits.append(new AuditEvent(
                "CRATE_KEY_GRANTED",
                actorUuid,
                online.getUniqueId(),
                actorUuid == null ? "system" : actorUuid.toString(),
                online.getName(),
                "Granted crate key",
                Map.of("crateId", crate.id(), "amount", String.valueOf(finalAmount)),
                System.currentTimeMillis()
            ));
        };
        if (Bukkit.isPrimaryThread()) {
            grant.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, grant);
        }
    }

    public void openCrate(Player player, String crateId) {
        CrateDefinition crate = crates.get(crateId.toLowerCase(Locale.ROOT));
        if (crate == null) {
            messages.send(player, "customitems.crate-missing", "&cCrate not found.");
            return;
        }
        LocatedItem located = findMatchingKey(player, crate.id());
        if (located == null || located.identity() == null) {
            messages.send(player, "customitems.crate-no-key", "&cYou do not have the required key.");
            return;
        }
        ItemStack removed = removeSingleItem(player, located.slot(), located.item());
        repository.consumeItem(located.identity(), located.type()).whenComplete((consumed, throwable) ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (throwable != null || !Boolean.TRUE.equals(consumed)) {
                    refundItem(player, removed);
                    messages.send(player, "customitems.crate-invalid", "&cThat crate key is no longer valid.");
                    return;
                }
                CrateReward reward = chooseReward(crate);
                if (reward == null) {
                    refundItem(player, removed);
                    messages.send(player, "customitems.crate-invalid", "&cThat crate key is no longer valid.");
                    return;
                }
                executeAction(player, reward.action(), "crate:" + crate.id(), reward.id());
                repository.logCrateOpen(player.getUniqueId(), crate.id(), reward.id(), reward.action());
                messages.send(player, "customitems.crate-opened", "&aOpened <crate> and received <reward>.",
                    new MessageService.Placeholder("crate", crate.displayName()),
                    new MessageService.Placeholder("reward", reward.displayName()));
                audits.append(new AuditEvent(
                    "CRATE_OPENED",
                    player.getUniqueId(),
                    player.getUniqueId(),
                    player.getName(),
                    player.getName(),
                    "Crate opened",
                    Map.of("crateId", crate.id(), "rewardId", reward.id(), "action", reward.action()),
                    System.currentTimeMillis()
                ));
            })
        );
    }

    public void redeemOneTimeReward(Player player, ItemStack itemStack) {
        UUID identity = itemService.readIdentity(itemStack);
        String type = itemService.readType(itemStack);
        String rewardKey = itemService.readClaimKey(itemStack);
        if (identity == null || type == null || rewardKey == null) {
            messages.send(player, "customitems.reward-invalid", "&cThat reward item is invalid.");
            return;
        }
        ItemStack removed = removeSingleHeld(player, itemStack);
        repository.consumeItem(identity, type).thenCompose(consumed -> {
            if (!Boolean.TRUE.equals(consumed)) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            return repository.claimReward(player.getUniqueId(), rewardKey);
        }).whenComplete((claimed, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                refundItem(player, removed);
                messages.send(player, "customitems.reward-invalid", "&cThat reward item is invalid.");
                return;
            }
            if (!Boolean.TRUE.equals(claimed)) {
                messages.send(player, "customitems.reward-duplicate", "&cYou already claimed that reward.");
                return;
            }
            ClaimRewardDefinition definition = claimRewards.get(rewardKey.toLowerCase(Locale.ROOT));
            if (definition == null) {
                messages.send(player, "customitems.reward-invalid", "&cThat reward item is invalid.");
                return;
            }
            executeAction(player, definition.action(), "claim:" + rewardKey, rewardKey);
            messages.send(player, "customitems.reward-claimed", "&aReward <reward> claimed.",
                new MessageService.Placeholder("reward", definition.displayName()));
            audits.append(new AuditEvent(
                "ONE_TIME_REWARD_CLAIMED",
                player.getUniqueId(),
                player.getUniqueId(),
                player.getName(),
                player.getName(),
                "One-time reward claimed",
                Map.of("rewardKey", rewardKey, "action", definition.action()),
                System.currentTimeMillis()
            ));
        }));
    }

    private void executeAction(Player player, String action, String source, String rewardId) {
        if (action == null || action.isBlank()) {
            return;
        }
        String[] parts = action.split(":", 3);
        switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "coins" -> economyService.deposit(player.getUniqueId(), new BigDecimal(parts[1]), "custom_reward:" + source, null);
            case "tokens" -> deliverItem(player, rewardFactory.createToken(Integer.parseInt(parts[1])));
            case "key" -> {
                int amount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                giveKey(player, parts[1], amount, null);
            }
            case "material" -> {
                Material material = Material.matchMaterial(parts[1]);
                if (material != null) {
                    int amount = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                    deliverItem(player, new ItemStack(material, Math.max(1, amount)));
                }
            }
            case "command" -> plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                action.substring("command:".length()).replace("%player%", player.getName()).replace("%reward%", rewardId)
            );
            case "one_time" -> {
                ClaimRewardDefinition definition = claimRewards.get(parts[1].toLowerCase(Locale.ROOT));
                if (definition != null) {
                    deliverItem(player, rewardFactory.createOneTimeReward(definition.id(), definition.displayName(), definition.lore(), definition.rarity()));
                }
            }
            default -> {
            }
        }
    }

    private LocatedItem findMatchingKey(Player player, String crateId) {
        String expectedType = config.getString("reward-items.crate-key-prefix", "crate_key") + ":" + crateId;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (itemStack == null) {
                continue;
            }
            String type = itemService.readType(itemStack);
            if (expectedType.equalsIgnoreCase(type)) {
                return new LocatedItem(slot, itemStack.clone(), itemService.readIdentity(itemStack), type);
            }
        }
        return null;
    }

    private ItemStack removeSingleItem(Player player, int slot, ItemStack reference) {
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null) {
            return reference;
        }
        ItemStack removed = current.clone();
        if (current.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
        } else {
            current.setAmount(current.getAmount() - 1);
            player.getInventory().setItem(slot, current);
            removed.setAmount(1);
        }
        player.updateInventory();
        return removed;
    }

    private ItemStack removeSingleHeld(Player player, ItemStack reference) {
        UUID referenceId = itemService.readIdentity(reference);
        if (referenceId == null) {
            return reference;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current == null) {
                continue;
            }
            UUID currentId = itemService.readIdentity(current);
            if (referenceId.equals(currentId)) {
                return removeSingleItem(player, slot, current);
            }
        }
        return reference;
    }

    private void refundItem(Player player, ItemStack itemStack) {
        deliverItem(player, itemStack);
    }

    private void deliverItem(Player player, ItemStack itemStack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(itemStack);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.updateInventory();
    }

    private CrateReward chooseReward(CrateDefinition crate) {
        int totalWeight = crate.rewards().stream().mapToInt(CrateReward::weight).sum();
        if (totalWeight <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (CrateReward reward : crate.rewards()) {
            cursor += reward.weight();
            if (roll < cursor) {
                return reward;
            }
        }
        return crate.rewards().isEmpty() ? null : crate.rewards().get(0);
    }

    private Map<String, CrateDefinition> loadCrates(ConfigurationSection section) {
        Map<String, CrateDefinition> definitions = new LinkedHashMap<>();
        if (section == null) {
            return definitions;
        }
        for (String crateId : section.getKeys(false)) {
            ConfigurationSection crateSection = section.getConfigurationSection(crateId);
            if (crateSection == null) {
                continue;
            }
            List<CrateReward> rewards = new ArrayList<>();
            ConfigurationSection rewardsSection = crateSection.getConfigurationSection("rewards");
            if (rewardsSection != null) {
                for (String rewardId : rewardsSection.getKeys(false)) {
                    ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(rewardId);
                    if (rewardSection == null) {
                        continue;
                    }
                    rewards.add(new CrateReward(
                        rewardId,
                        rewardSection.getString("display", rewardId),
                        rewardSection.getString("rarity", "common"),
                        rewardSection.getInt("weight", 1),
                        rewardSection.getString("action", "")
                    ));
                }
            }
            definitions.put(crateId.toLowerCase(Locale.ROOT), new CrateDefinition(
                crateId,
                crateSection.getString("display", crateId),
                crateSection.getString("key-name", "&aCrate Key: " + crateId),
                rewards
            ));
        }
        return definitions;
    }

    private Map<String, ClaimRewardDefinition> loadClaimRewards(ConfigurationSection section) {
        Map<String, ClaimRewardDefinition> definitions = new LinkedHashMap<>();
        if (section == null) {
            return definitions;
        }
        for (String rewardId : section.getKeys(false)) {
            ConfigurationSection rewardSection = section.getConfigurationSection(rewardId);
            if (rewardSection == null) {
                continue;
            }
            definitions.put(rewardId.toLowerCase(Locale.ROOT), new ClaimRewardDefinition(
                rewardId,
                rewardSection.getString("display", rewardId),
                rewardSection.getStringList("lore"),
                rewardSection.getString("rarity", "epic"),
                rewardSection.getString("action", "")
            ));
        }
        return definitions;
    }

    public record CrateDefinition(String id, String displayName, String keyName, List<CrateReward> rewards) {
    }

    public record CrateReward(String id, String displayName, String rarity, int weight, String action) {
    }

    public record ClaimRewardDefinition(String id, String displayName, List<String> lore, String rarity, String action) {
    }

    private record LocatedItem(int slot, ItemStack item, UUID identity, String type) {
    }
}
