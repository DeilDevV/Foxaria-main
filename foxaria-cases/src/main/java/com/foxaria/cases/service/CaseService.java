package com.foxaria.cases.service;

import com.foxaria.api.model.AuditEvent;
import com.foxaria.api.service.AuditService;
import com.foxaria.api.service.MessageService;
import com.foxaria.cases.CaseRepository;
import com.foxaria.cases.gui.CaseOpenMenu;
import com.foxaria.cases.model.CaseDefinition;
import com.foxaria.cases.model.CaseLocation;
import com.foxaria.cases.model.CaseReward;
import com.foxaria.core.gui.MenuManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CaseService {

    private final JavaPlugin plugin;
    private final ConfigCaseService config;
    private final CaseRepository repository;
    private final CaseItemService items;
    private final PlacedCaseService placedCases;
    private final CaseOpenLockService locks;
    private final CaseAnimationService animation;
    private final CaseRewardExecutor rewards;
    private final MenuManager menuManager;
    private final MessageService messages;
    private final AuditService audits;

    public CaseService(
        JavaPlugin plugin,
        ConfigCaseService config,
        CaseRepository repository,
        CaseItemService items,
        PlacedCaseService placedCases,
        CaseOpenLockService locks,
        CaseAnimationService animation,
        CaseRewardExecutor rewards,
        MenuManager menuManager,
        MessageService messages,
        AuditService audits
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.items = items;
        this.placedCases = placedCases;
        this.locks = locks;
        this.animation = animation;
        this.rewards = rewards;
        this.menuManager = menuManager;
        this.messages = messages;
        this.audits = audits;
    }

    public ConfigCaseService config() {
        return config;
    }

    public PlacedCaseService placedCases() {
        return placedCases;
    }

    public CaseItemService items() {
        return items;
    }

    public CaseRepository repository() {
        return repository;
    }

    public void reload() {
        placedCases.reloadVisuals();
    }

    public void openMenu(Player player, Block block) {
        CaseLocation location = CaseLocation.from(block, config.serverId());
        Optional<CaseDefinition> definitionOpt = placedCases.definitionAt(location);
        if (definitionOpt.isEmpty()) {
            return;
        }
        CaseDefinition definition = definitionOpt.get();
        if (!canOpen(player, definition.id())) {
            messages.send(player, "cases.no-permission", "&cУ вас нет доступа к этому кейсу.");
            return;
        }
        if (locks.isLockedByOther(location, player.getUniqueId())) {
            messages.send(player, "cases.case-busy", "&cКейс уже открывает другой игрок. Подождите.");
            return;
        }
        repository.keyCount(player.getUniqueId(), definition.id()).whenComplete((count, throwable) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                int keys = throwable == null && count != null ? count : 0;
                menuManager.open(player, new CaseOpenMenu(this, player, location, definition, keys));
            })
        );
    }

    public void beginOpen(Player player, CaseLocation location, CaseDefinition definition) {
        if (locks.isLockedByOther(location, player.getUniqueId())) {
            messages.send(player, "cases.case-busy", "&cКейс уже открывает другой игрок. Подождите.");
            return;
        }
        if (!locks.tryLock(location, player.getUniqueId())) {
            messages.send(player, "cases.case-busy", "&cКейс уже открывает другой игрок. Подождите.");
            return;
        }
        repository.consumeKey(player.getUniqueId(), definition.id()).whenComplete((consumed, throwable) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || !Boolean.TRUE.equals(consumed)) {
                    locks.unlock(location, player.getUniqueId());
                    messages.send(player, "cases.no-keys", "&cУ вас нет ключей от этого кейса.");
                    return;
                }
                CaseReward winningReward = animation.chooseReward(definition);
                if (winningReward == null) {
                    locks.unlock(location, player.getUniqueId());
                    repository.addKeys(player.getUniqueId(), definition.id(), 1);
                    messages.send(player, "cases.invalid-reward", "&cНаграды кейса настроены неверно.");
                    return;
                }
                player.closeInventory();
                animation.play(player, location, definition, winningReward, () -> {
                    rewards.execute(player, definition.id(), winningReward);
                    messages.send(player, "cases.opened", "&aВы открыли <case> и получили <reward>.",
                        new MessageService.Placeholder("case", definition.displayName()),
                        new MessageService.Placeholder("reward", winningReward.displayName())
                    );
                    audits.append(new AuditEvent(
                        "CASE_OPENED",
                        player.getUniqueId(),
                        player.getUniqueId(),
                        player.getName(),
                        player.getName(),
                        "Physical case opened",
                        Map.of(
                            "caseId", definition.id(),
                            "rewardId", winningReward.id(),
                            "serverId", config.serverId()
                        ),
                        System.currentTimeMillis()
                    ));
                    locks.unlock(location, player.getUniqueId());
                });
            })
        );
    }

    public void giveBlock(Player target, String caseId, int amount) {
        Optional<CaseDefinition> definitionOpt = config.definition(caseId);
        if (definitionOpt.isEmpty()) {
            return;
        }
        items.deliverItem(target, items.createBlockItem(caseId, definitionOpt.get().blockItem(), amount));
    }

    public void giveKeys(UUID targetUuid, String caseId, int amount, UUID actorUuid) {
        Optional<CaseDefinition> definitionOpt = config.definition(caseId);
        if (definitionOpt.isEmpty()) {
            return;
        }
        repository.addKeys(targetUuid, caseId, amount).whenComplete((ignored, throwable) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(targetUuid);
                if (online != null) {
                    messages.send(online, "cases.keys-given", "&aВам выдано &f<amount>&a ключ(ей) от &f<case>&a.",
                        new MessageService.Placeholder("amount", String.valueOf(amount)),
                        new MessageService.Placeholder("case", definitionOpt.get().displayName())
                    );
                }
                audits.append(new AuditEvent(
                    "CASE_KEY_GRANTED",
                    actorUuid,
                    targetUuid,
                    actorUuid == null ? "system" : String.valueOf(actorUuid),
                    online == null ? targetUuid.toString() : online.getName(),
                    "Case keys granted",
                    Map.of("caseId", caseId, "amount", String.valueOf(amount)),
                    System.currentTimeMillis()
                ));
            })
        );
    }

    public void onPlayerJoin(Player player) {
        rewards.flushPending(player);
    }

    private boolean canOpen(Player player, String caseId) {
        if (player.hasPermission("foxaria.cases.admin")) {
            return true;
        }
        if (!player.hasPermission("foxaria.cases.use")) {
            return false;
        }
        return player.hasPermission("foxaria.cases.open." + caseId)
            || !player.isPermissionSet("foxaria.cases.open." + caseId);
    }
}
