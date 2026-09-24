package com.foxaria.shop.progression;

import com.foxaria.api.service.EconomyService;
import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.KnowledgeService;
import com.foxaria.api.service.MessageService;
import com.foxaria.core.gui.MenuManager;
import com.foxaria.shop.gui.QuestMainMenu;
import com.foxaria.shop.gui.QuestTierMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressionService implements KnowledgeService {

    private final JavaPlugin plugin;
    private final ProgressionRepository repository;
    private final QuestCatalog catalog;
    private final MessageService messages;
    private final MenuManager menuManager;
    private final EconomyService economy;
    private final ItemTemplateService itemTemplates;
    private final ConcurrentHashMap<UUID, PlayerProgressionState> cache = new ConcurrentHashMap<>();
    private BukkitTask playtimeTask;

    public ProgressionService(
        JavaPlugin plugin,
        ProgressionRepository repository,
        QuestCatalog catalog,
        MessageService messages,
        MenuManager menuManager,
        EconomyService economy,
        ItemTemplateService itemTemplates
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.catalog = catalog;
        this.messages = messages;
        this.menuManager = menuManager;
        this.economy = economy;
        this.itemTemplates = itemTemplates;
    }

    public void start() {
        playtimeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPlayMinutes, 1200L, 1200L);
    }

    public void stop() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
            playtimeTask = null;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            unload(p.getUniqueId());
        }
    }

    private void tickPlayMinutes() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProgressionState state = cache.get(player.getUniqueId());
            if (state == null) {
                continue;
            }
            QuestDefinition def = activeDefinition(state);
            if (def == null) {
                continue;
            }
            boolean hasPlay = false;
            for (QuestObjective o : def.objectives()) {
                if (o.type() == QuestType.PLAY_MINUTES) {
                    hasPlay = true;
                    break;
                }
            }
            if (hasPlay) {
                addProgress(player, QuestType.PLAY_MINUTES, "", 1L);
            }
        }
    }

    @Override
    public CompletableFuture<Integer> knowledgeLevel(UUID playerUuid) {
        PlayerProgressionState s = cache.get(playerUuid);
        if (s != null) {
            return CompletableFuture.completedFuture(s.knowledgeLevel());
        }
        return repository.load(playerUuid).thenApply(PlayerProgressionState::knowledgeLevel);
    }

    public void onJoin(Player player) {
        String first = catalog.firstQuestId();
        repository.ensureRow(player.getUniqueId(), first).thenCompose(v -> repository.load(player.getUniqueId()))
            .thenAccept(state -> {
                normalize(state);
                cache.put(player.getUniqueId(), state);
                repository.save(state);
            });
    }

    public void onQuit(Player player) {
        unload(player.getUniqueId());
    }

    private void unload(UUID uuid) {
        PlayerProgressionState s = cache.remove(uuid);
        if (s != null) {
            repository.save(s);
        }
    }

    public PlayerProgressionState cachedState(UUID uuid) {
        return cache.get(uuid);
    }

    public QuestCatalog catalog() {
        return catalog;
    }

    public MessageService messages() {
        return messages;
    }

    public EconomyService economy() {
        return economy;
    }

    public ItemTemplateService itemTemplates() {
        return itemTemplates;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public MenuManager menuManager() {
        return menuManager;
    }

    private void normalize(PlayerProgressionState state) {
        if (catalog.firstQuestId() == null) {
            return;
        }
        String cur = state.currentQuestId();
        if (cur != null && catalog.definition(cur) == null) {
            cur = null;
            state.setCurrentQuestId(null);
        }
        if (cur == null || cur.isBlank() || state.isCompleted(cur)) {
            String next = catalog.findNextOpenQuestId(state);
            state.setCurrentQuestId(next);
            state.clearQuestProgress();
        }
        QuestDefinition active = activeDefinition(state);
        if (active != null) {
            state.ensureProgressShape(active);
        }
        syncKnowledgeLevel(state);
    }

    private void syncKnowledgeLevel(PlayerProgressionState state) {
        state.setKnowledgeLevel(catalog.knowledgeFromProgress(state));
    }

    private QuestDefinition activeDefinition(PlayerProgressionState state) {
        String qid = state.currentQuestId();
        if (qid == null) {
            return null;
        }
        return catalog.definition(qid);
    }

    public void addProgress(Player player, QuestType type, String key, long delta) {
        if (delta <= 0) {
            return;
        }
        PlayerProgressionState state = cache.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        QuestDefinition def = activeDefinition(state);
        if (def == null) {
            return;
        }
        boolean any = false;
        for (QuestObjective o : def.objectives()) {
            if (o.type() != QuestType.SUBMIT_ITEMS && o.matchesEvent(type, key == null ? "" : key)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        int[] prog = state.objectiveProgressArray(def);
        boolean changed = false;
        for (int i = 0; i < def.objectives().size(); i++) {
            QuestObjective o = def.objectives().get(i);
            if (o.type() == QuestType.SUBMIT_ITEMS) {
                continue;
            }
            if (!o.matchesEvent(type, key == null ? "" : key)) {
                continue;
            }
            long cap = (long) o.amount() - prog[i];
            if (cap <= 0) {
                continue;
            }
            long add = Math.min(delta, cap);
            prog[i] = (int) Math.min((long) prog[i] + add, Integer.MAX_VALUE);
            changed = true;
        }
        if (!changed) {
            return;
        }
        state.setObjectiveProgressArray(prog);
        if (def.isComplete(prog)) {
            completeQuest(player, state, def);
        }
        repository.save(state);
    }

    private void completeQuest(Player player, PlayerProgressionState state, QuestDefinition def) {
        state.markCompleted(def.id());
        if (def.rewards() != null && def.rewards().hasAny()) {
            def.rewards().grant(player, plugin, economy, itemTemplates, messages);
        }
        state.setCurrentQuestId(catalog.findNextOpenQuestId(state));
        state.clearQuestProgress();
        QuestDefinition nextDef = activeDefinition(state);
        if (nextDef != null) {
            state.ensureProgressShape(nextDef);
        }
        syncKnowledgeLevel(state);

        messages.send(player, "quest.completed", "&aКвест выполнен: <title>",
            new MessageService.Placeholder("title", def.title()));
        messages.send(player, "quest.knowledge-now", "&7Уровень знаний: &d<kl>",
            new MessageService.Placeholder("kl", String.valueOf(state.knowledgeLevel())));

        if (state.currentQuestId() == null) {
            messages.send(player, "quest.all-done", "&eВы прошли все квесты всех ступеней!");
        } else {
            QuestDefinition next = catalog.definition(state.currentQuestId());
            if (next != null) {
                messages.send(player, "quest.next", "&7Следующая цель: <title>",
                    new MessageService.Placeholder("title", next.title()));
            }
        }
    }

    public void openQuestMenu(Player player) {
        menuManager.open(player, new QuestMainMenu(this));
    }

    public void openQuestTierMenu(Player player, int tier) {
        if (tier < 1 || tier > QuestCatalog.TIER_COUNT) {
            return;
        }
        menuManager.open(player, new QuestTierMenu(this, tier));
    }

    /**
     * Сдача предметов для целей {@link QuestType#SUBMIT_ITEMS} (частями). ЛКМ по активной ячейке в меню ступени.
     */
    public void trySubmitItems(Player player, int menuTier) {
        PlayerProgressionState state = cache.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        QuestDefinition def = activeDefinition(state);
        if (def == null || def.tier() != menuTier || !def.hasAnySubmitObjective()) {
            return;
        }
        int[] prog = state.objectiveProgressArray(def);
        if (!def.hasIncompleteSubmit(prog)) {
            return;
        }
        int submitIndex = -1;
        QuestObjective targetObj = null;
        for (int i = 0; i < def.objectives().size(); i++) {
            QuestObjective o = def.objectives().get(i);
            if (o.type() != QuestType.SUBMIT_ITEMS) {
                continue;
            }
            if (prog[i] < o.amount()) {
                submitIndex = i;
                targetObj = o;
                break;
            }
        }
        if (submitIndex < 0 || targetObj == null) {
            return;
        }
        Material mat;
        try {
            mat = Material.valueOf(targetObj.material().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            messages.send(player, "quest.submit-bad-config", "&cНеверный material в конфиге квеста.");
            return;
        }
        long needTotal = targetObj.amount();
        long have = prog[submitIndex];
        long still = needTotal - have;
        if (still <= 0) {
            return;
        }
        int toTake = (int) Math.min(still, Integer.MAX_VALUE);
        PlayerInventory inv = player.getInventory();
        int removed = removeMaterialFromInventory(inv, mat, toTake);
        if (removed <= 0) {
            messages.send(player, "quest.submit-none", "&cНет предметов &f<mat>&c в инвентаре (горячая панель, рюкзак, вторая рука).",
                new MessageService.Placeholder("mat", mat.name()));
            return;
        }
        prog[submitIndex] = (int) Math.min((long) prog[submitIndex] + removed, Integer.MAX_VALUE);
        state.setObjectiveProgressArray(prog);
        if (def.isComplete(prog)) {
            completeQuest(player, state, def);
            repository.save(state);
        } else {
            messages.send(player, "quest.submit-partial", "&aСдано &e+<taken>&a, всего по этой цели &e<cur>&7/&f<need>&a.",
                new MessageService.Placeholder("taken", String.valueOf(removed)),
                new MessageService.Placeholder("cur", String.valueOf(prog[submitIndex])),
                new MessageService.Placeholder("need", String.valueOf(needTotal)));
            repository.save(state);
        }
        plugin.getServer().getScheduler().runTask(plugin, () ->
            menuManager.open(player, new QuestTierMenu(this, menuTier)));
    }

    private static int removeMaterialFromInventory(PlayerInventory inv, Material mat, int maxTake) {
        int taken = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (taken >= maxTake) {
                break;
            }
            taken += takeFromStack(inv, slot, mat, maxTake - taken);
        }
        if (taken < maxTake) {
            taken += takeFromStack(inv, 40, mat, maxTake - taken);
        }
        return taken;
    }

    private static int takeFromStack(PlayerInventory inv, int slot, Material mat, int maxTake) {
        if (maxTake <= 0) {
            return 0;
        }
        var stack = inv.getItem(slot);
        if (stack == null || stack.getType() != mat) {
            return 0;
        }
        int n = stack.getAmount();
        int grab = Math.min(n, maxTake);
        stack.setAmount(n - grab);
        if (stack.getAmount() <= 0) {
            inv.setItem(slot, null);
        }
        return grab;
    }
}
