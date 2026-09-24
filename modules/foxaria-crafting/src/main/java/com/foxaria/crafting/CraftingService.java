package com.foxaria.crafting;

import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.KnowledgeService;
import com.foxaria.crafting.model.CraftRecipeJson;
import org.bukkit.inventory.ItemStack;
import com.foxaria.crafting.smelt.SmeltBonusCoordinator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class CraftingService {

    private final JavaPlugin plugin;
    private final CraftRepository repository;
    private final ItemTemplateService templates;
    private final KnowledgeService knowledge;
    private final CraftRecipeRegistry registry = new CraftRecipeRegistry();
    private final SmeltBonusCoordinator smeltBonuses;
    private volatile Map<String, CustomCraftDefinition> byId = Map.of();

    public CraftingService(JavaPlugin plugin, CraftRepository repository, ItemTemplateService templates, KnowledgeService knowledge) {
        this.plugin = plugin;
        this.repository = repository;
        this.templates = templates;
        this.knowledge = knowledge;
        this.smeltBonuses = new SmeltBonusCoordinator(plugin, templates, knowledge);
    }

    public SmeltBonusCoordinator smeltBonuses() {
        return smeltBonuses;
    }

    public void bootstrap() {
        reloadBlocking();
    }

    public void reloadBlocking() {
        List<CustomCraftDefinition> list = repository.loadAll().join();
        Map<String, CustomCraftDefinition> map = new ConcurrentHashMap<>();
        for (CustomCraftDefinition d : list) {
            map.put(d.craftId(), d);
        }
        byId = map;
        registry.reload(plugin, templates, list);
        smeltBonuses.reloadFromCrafts(list);
    }

    public CompletableFuture<Void> reloadAsync() {
        return repository.loadAll().thenAccept(list -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            Map<String, CustomCraftDefinition> map = new ConcurrentHashMap<>();
            for (CustomCraftDefinition d : list) {
                map.put(d.craftId(), d);
            }
            byId = map;
            registry.reload(plugin, templates, list);
            smeltBonuses.reloadFromCrafts(list);
        }));
    }

    public List<CustomCraftDefinition> allCrafts() {
        return byId.values().stream().sorted((a, b) -> {
            int c = Integer.compare(a.sortOrder(), b.sortOrder());
            return c != 0 ? c : a.craftId().compareToIgnoreCase(b.craftId());
        }).toList();
    }

    /** Первый слот в /craft: встроенная сера, если есть шаблон {@code sulfur}. */
    public Optional<CustomCraftDefinition> builtinSulfurDefinition() {
        if (!templates.exists("sulfur")) {
            return Optional.empty();
        }
        return Optional.of(new CustomCraftDefinition(CraftBuiltinIds.SULFUR, "{}", Integer.MIN_VALUE));
    }

    /**
     * Не показывать в списке /craft отдельную строку БД, если результат — тот же шаблон {@code sulfur},
     * что и встроенный крафт (иначе дубликат иконок).
     */
    public boolean isRedundantDbSulfurCraft(CustomCraftDefinition def) {
        if (builtinSulfurDefinition().isEmpty()) {
            return false;
        }
        if (CraftBuiltinIds.SULFUR.equals(def.craftId())) {
            return true;
        }
        CraftRecipeJson j = def.parsed();
        if (!CraftJson.isSmeltingKind(j)) {
            return false;
        }
        try {
            ItemStack res = CraftJson.resolveResult(templates, j);
            return templates.findMatchingTemplateId(res).filter("sulfur"::equalsIgnoreCase).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<CustomCraftDefinition> find(String craftId) {
        return Optional.ofNullable(byId.get(craftId));
    }

    public int requiredKnowledge(String craftId) {
        return find(craftId).map(d -> d.parsed().requiredKnowledge).orElse(1);
    }

    public boolean playerMeetsKnowledge(Player player, String craftId) {
        int need = requiredKnowledge(craftId);
        int have = knowledge.knowledgeLevel(player.getUniqueId()).join();
        return have >= need;
    }

    public CompletableFuture<Void> saveNewCraft(String craftId, CraftRecipeJson json, int sortOrder) {
        String body = CraftJson.serialize(json);
        CustomCraftDefinition def = new CustomCraftDefinition(craftId, body, sortOrder);
        return repository.upsert(def).thenCompose(v -> reloadAsync());
    }

    public CompletableFuture<Void> deleteCraft(String craftId) {
        return repository.delete(craftId).thenCompose(deleted -> {
            if (!Boolean.TRUE.equals(deleted)) {
                return CompletableFuture.completedFuture(null);
            }
            return reloadAsync();
        });
    }

    public CraftRecipeRegistry registry() {
        return registry;
    }

    public ItemTemplateService templates() {
        return templates;
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
