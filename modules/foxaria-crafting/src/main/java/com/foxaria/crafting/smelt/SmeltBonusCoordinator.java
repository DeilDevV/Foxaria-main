package com.foxaria.crafting.smelt;

import com.foxaria.api.service.ItemTemplateService;
import com.foxaria.api.service.KnowledgeService;
import com.foxaria.crafting.CraftJson;
import com.foxaria.crafting.CustomCraftDefinition;
import com.foxaria.crafting.model.CraftRecipeJson;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Встроенная сера + бонусы из JSON рецептов печи.
 */
public final class SmeltBonusCoordinator {

    private static final String SULFUR_TEMPLATE = "sulfur";

    private final JavaPlugin plugin;
    private final ItemTemplateService templates;
    private final KnowledgeService knowledge;
    private volatile List<JsonBonusRule> jsonRules = List.of();

    public SmeltBonusCoordinator(JavaPlugin plugin, ItemTemplateService templates, KnowledgeService knowledge) {
        this.plugin = plugin;
        this.templates = templates;
        this.knowledge = knowledge;
    }

    public void reloadFromCrafts(List<CustomCraftDefinition> defs) {
        List<JsonBonusRule> list = new ArrayList<>();
        for (CustomCraftDefinition d : defs) {
            CraftRecipeJson j = d.parsed();
            if (!CraftJson.isSmeltingKind(j)) {
                continue;
            }
            if (j.bonusTemplateId == null || j.bonusTemplateId.isBlank()) {
                continue;
            }
            if (j.bonusChancePercent <= 0) {
                continue;
            }
            Material input = CraftJson.firstSmeltInputMaterial(templates, j);
            if (input == null || input.isAir()) {
                continue;
            }
            list.add(new JsonBonusRule(input, j.bonusTemplateId, j.bonusChancePercent / 100.0, j.requiredKnowledge));
        }
        jsonRules = List.copyOf(list);
    }

    public void rollAll(UUID smelterUuid, Location furnaceBlock, Material inputMaterial, Consumer<ItemStack> deliverOne) {
        if (tryBuiltinSulfur(smelterUuid, inputMaterial, deliverOne)) {
            return;
        }
        for (JsonBonusRule r : jsonRules) {
            if (r.input != inputMaterial) {
                continue;
            }
            if (!meetsKnowledge(smelterUuid, r.requiredKnowledge)) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() >= r.chance) {
                continue;
            }
            templates.cloneTemplate(r.templateId).ifPresent(b -> {
                ItemStack one = b.clone();
                one.setAmount(1);
                deliverOne.accept(one);
            });
            return;
        }
    }

    /** @return true если сработала встроенная сера (и дальнейшие JSON-правила не проверяем). */
    private boolean tryBuiltinSulfur(UUID smelterUuid, Material inputMaterial, Consumer<ItemStack> deliverOne) {
        double p = builtinSulfurChance(inputMaterial);
        if (p < 0) {
            return false;
        }
        if (!meetsKnowledge(smelterUuid, 1)) {
            return false;
        }
        if (ThreadLocalRandom.current().nextDouble() >= p) {
            return false;
        }
        if (!templates.exists(SULFUR_TEMPLATE)) {
            plugin.getLogger().fine("[Craft] Шаблон sulfur не найден, пропуск бонуса.");
            return false;
        }
        templates.cloneTemplate(SULFUR_TEMPLATE).ifPresent(b -> {
            ItemStack one = b.clone();
            one.setAmount(1);
            deliverOne.accept(one);
        });
        return true;
    }

    /**
     * @return вероятность 0..1, либо -1 если не для серы
     */
    public static double builtinSulfurChance(Material m) {
        if (m == Material.SAND || m == Material.RED_SAND) {
            return 0.15;
        }
        String n = m.name();
        if (n.endsWith("_ORE")) {
            int lo = 10;
            int hi = 25;
            int draw = lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
            return draw / 100.0;
        }
        return -1;
    }

    private boolean meetsKnowledge(UUID smelterUuid, int required) {
        if (smelterUuid == null) {
            return false;
        }
        int have = knowledge.knowledgeLevel(smelterUuid).join();
        return have >= Math.max(1, required);
    }

    private record JsonBonusRule(Material input, String templateId, double chance, int requiredKnowledge) {
    }
}
