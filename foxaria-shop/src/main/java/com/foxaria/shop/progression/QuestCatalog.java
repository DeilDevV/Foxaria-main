package com.foxaria.shop.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class QuestCatalog {

    public static final int TIER_COUNT = 5;
    public static final int QUESTS_PER_TIER = 21;

    private final Map<Integer, List<QuestDefinition>> tiers;
    private final Map<String, QuestDefinition> byId;
    private final Map<Integer, String> tierMenuTitles;
    private final Map<Integer, List<String>> tierMenuLore;

    public QuestCatalog(
        Map<Integer, List<QuestDefinition>> tiers,
        Map<String, QuestDefinition> byId,
        Map<Integer, String> tierMenuTitles,
        Map<Integer, List<String>> tierMenuLore
    ) {
        this.tiers = Map.copyOf(tiers);
        this.byId = Map.copyOf(byId);
        this.tierMenuTitles = Map.copyOf(tierMenuTitles);
        this.tierMenuLore = Map.copyOf(tierMenuLore);
    }

    /**
     * Загрузка встроенного конфига из JAR (fallback при битом файле на диске).
     */
    public static QuestCatalog fromResource(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("modules/quests.yml")) {
            if (in == null) {
                return new QuestCatalog(Map.of(), Map.of(), Map.of(), Map.of());
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            return fromConfig(yaml);
        } catch (IOException e) {
            throw new IllegalStateException("quests.yml из JAR", e);
        }
    }

    public static QuestCatalog fromConfig(FileConfiguration config) {
        Map<Integer, List<QuestDefinition>> tiers = new LinkedHashMap<>();
        Map<String, QuestDefinition> byId = new LinkedHashMap<>();
        Map<Integer, String> titles = new LinkedHashMap<>();
        Map<Integer, List<String>> tierLore = new LinkedHashMap<>();

        ConfigurationSection tiersRoot = config.getConfigurationSection("tiers");
        if (tiersRoot == null) {
            return new QuestCatalog(Map.of(), Map.of(), Map.of(), Map.of());
        }

        for (int tier = 1; tier <= TIER_COUNT; tier++) {
            ConfigurationSection tierSec = resolveTierSection(tiersRoot, tier);
            if (tierSec == null) {
                continue;
            }
            titles.put(tier, tierSec.getString("menu-title", "&fСтупень " + tier));
            List<String> ml = tierSec.getStringList("menu-description");
            tierLore.put(tier, ml.isEmpty() ? List.of() : List.copyOf(ml));

            List<Map<?, ?>> rawList = readQuestEntryMaps(tierSec);
            List<QuestDefinition> list = new ArrayList<>();
            for (int i = 0; i < rawList.size(); i++) {
                Map<?, ?> m = rawList.get(i);
                QuestDefinition def = parseQuestFromMap(tier, i, m, byId);
                if (def != null) {
                    list.add(def);
                    byId.put(def.id(), def);
                }
            }
            if (list.size() != QUESTS_PER_TIER) {
                throw new IllegalStateException(
                    "quests.yml: ступень " + tier + " — нужно ровно " + QUESTS_PER_TIER
                        + " валидных квестов, сейчас " + list.size()
                        + " (сырых записей в quests: " + rawList.size()
                        + "). Проверьте objectives/type, UTF-8 и отступы YAML."
                );
            }
            tiers.put(tier, Collections.unmodifiableList(list));
        }

        if (tiers.size() != TIER_COUNT) {
            throw new IllegalStateException(
                "quests.yml: нужны все ступени 1.." + TIER_COUNT + " (ключи \"1\"..\"5\", tier-1..tier-5 или tier_1..tier_5)."
            );
        }

        return new QuestCatalog(tiers, byId, titles, tierLore);
    }

    private static ConfigurationSection resolveTierSection(ConfigurationSection root, int tier) {
        ConfigurationSection s = root.getConfigurationSection(String.valueOf(tier));
        if (s != null) {
            return s;
        }
        s = root.getConfigurationSection("tier-" + tier);
        if (s != null) {
            return s;
        }
        s = root.getConfigurationSection("tier_" + tier);
        if (s != null) {
            return s;
        }
        // YAML «1:» без кавычек или иные варианты ключа
        for (String key : root.getKeys(false)) {
            if (!tierKeyMatches(key, tier)) {
                continue;
            }
            ConfigurationSection cs = root.getConfigurationSection(key);
            if (cs != null) {
                return cs;
            }
        }
        return null;
    }

    private static boolean tierKeyMatches(String key, int tier) {
        if (key == null) {
            return false;
        }
        if (key.equals(String.valueOf(tier))) {
            return true;
        }
        if (key.equals("tier-" + tier) || key.equals("tier_" + tier)) {
            return true;
        }
        try {
            return Integer.parseInt(key.trim()) == tier;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<Map<?, ?>> readQuestEntryMaps(ConfigurationSection tierSec) {
        Object node = tierSec.get("quests");
        List<Map<?, ?>> maps = readMapListFromValue(node);
        if (!maps.isEmpty()) {
            return maps;
        }
        List<Map<?, ?>> mapList = tierSec.getMapList("quests");
        if (mapList != null && !mapList.isEmpty()) {
            return readMapListFromValue(mapList);
        }
        List<?> raw = tierSec.getList("quests");
        return readMapListFromValue(raw);
    }

    /**
     * Bukkit иногда кладёт в списки {@link ConfigurationSection} вместо Map — тогда вложенные objectives
     * теряются при обёртке в YamlConfiguration. Нормализуем всё в Map.
     */
    private static List<Map<?, ?>> readMapListFromValue(Object node) {
        if (node == null) {
            return List.of();
        }
        Object normRoot = normalizeYamlValue(node);
        if (!(normRoot instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<?, ?>> out = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map<?, ?> m) {
                out.add(m);
            }
        }
        return out;
    }

    private static Object normalizeYamlValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof ConfigurationSection cs) {
            return configurationSectionToDeepMap(cs);
        }
        if (v instanceof List<?> list) {
            List<Object> nl = new ArrayList<>();
            for (Object o : list) {
                nl.add(normalizeYamlValue(o));
            }
            return nl;
        }
        return v;
    }

    private static Map<String, Object> configurationSectionToDeepMap(ConfigurationSection sec) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String k : sec.getKeys(false)) {
            Object v = sec.get(k);
            map.put(k, v == null ? null : normalizeYamlValue(v));
        }
        return map;
    }

    private static QuestDefinition parseQuestFromMap(int tier, int index0, Map<?, ?> m, Map<String, QuestDefinition> byId) {
        String id = stringVal(m.get("id"), "").trim();
        if (id.isEmpty()) {
            id = "t" + tier + "_q" + String.format(Locale.ROOT, "%02d", index0 + 1);
        }
        if (byId.containsKey(id)) {
            throw new IllegalStateException("Duplicate quest id: " + id);
        }
        List<String> desc = stringListVal(m.get("description"));
        if (desc.isEmpty()) {
            desc = List.of("&7Выполните задание.");
        }

        List<QuestObjective> objectives = parseObjectivesFromRaw(m.get("objectives"), id);
        if (objectives.isEmpty()) {
            QuestType type = QuestType.parse(stringVal(m.get("type"), ""));
            if (type == QuestType.UNKNOWN) {
                return null;
            }
            if (type == QuestType.SUBMIT_ITEMS) {
                String mat = stringVal(m.get("material"), "");
                if (mat.isBlank()) {
                    throw new IllegalStateException("quests.yml: SUBMIT_ITEMS требует material: для квеста " + id);
                }
            }
            objectives.add(QuestObjective.fromLegacy(
                type,
                stringVal(m.get("entity"), ""),
                stringVal(m.get("material"), ""),
                Math.max(1, parsePositiveInt(m.get("amount"), 1))
            ));
        }

        if (objectives.isEmpty()) {
            return null;
        }

        QuestRewards rewards = rewardsFromRaw(m.get("rewards"));
        return new QuestDefinition(
            id,
            tier,
            index0 + 1,
            stringVal(m.get("title"), id),
            List.copyOf(desc),
            List.copyOf(objectives),
            rewards
        );
    }

    private static List<QuestObjective> parseObjectivesFromRaw(Object rawObj, String questId) {
        if (!(rawObj instanceof List<?> list)) {
            return List.of();
        }
        List<QuestObjective> out = new ArrayList<>();
        for (Object item : list) {
            Object normalized = normalizeYamlValue(item);
            if (!(normalized instanceof Map<?, ?> om)) {
                continue;
            }
            QuestType type = QuestType.parse(stringVal(om.get("type"), ""));
            if (type == QuestType.UNKNOWN) {
                continue;
            }
            int amount = Math.max(1, parsePositiveInt(om.get("amount"), 1));
            String entity = stringVal(om.get("entity"), "");
            String material = stringVal(om.get("material"), "");
            String label = stringVal(om.get("label"), "");
            if (type == QuestType.SUBMIT_ITEMS && material.isBlank()) {
                throw new IllegalStateException("quests.yml: SUBMIT_ITEMS требует material в objectives, квест " + questId);
            }
            out.add(new QuestObjective(type, entity, material, amount, label));
        }
        return out;
    }

    private static QuestRewards rewardsFromRaw(Object rewardsObj) {
        if (!(rewardsObj instanceof Map<?, ?>)) {
            return QuestRewards.empty();
        }
        YamlConfiguration w = new YamlConfiguration();
        w.set("root", normalizeYamlValue(rewardsObj));
        return QuestRewards.fromConfig(w.getConfigurationSection("root"));
    }

    private static List<String> stringListVal(Object o) {
        if (o instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object x : l) {
                if (x != null) {
                    out.add(String.valueOf(x));
                }
            }
            return out;
        }
        if (o instanceof String s) {
            return List.of(s);
        }
        return List.of();
    }

    private static String stringVal(Object o, String def) {
        if (o == null) {
            return def;
        }
        return String.valueOf(o);
    }

    private static int parsePositiveInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(o).trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public QuestDefinition definition(String id) {
        return byId.get(id);
    }

    /** Текущий активный квест (по current_quest_id), если есть в каталоге. */
    public QuestDefinition activeQuest(PlayerProgressionState state) {
        if (state == null || state.currentQuestId() == null) {
            return null;
        }
        return definition(state.currentQuestId());
    }

    public String firstQuestId() {
        List<QuestDefinition> t1 = tiers.get(1);
        if (t1 == null || t1.isEmpty()) {
            return null;
        }
        return t1.get(0).id();
    }

    public List<QuestDefinition> tierQuests(int tier) {
        return tiers.getOrDefault(tier, List.of());
    }

    public String tierMenuTitle(int tier) {
        return tierMenuTitles.getOrDefault(tier, "&fСтупень " + tier);
    }

    public List<String> tierMenuDescription(int tier) {
        return tierMenuLore.getOrDefault(tier, List.of());
    }

    public boolean tierFullyCompleted(PlayerProgressionState state, int tier) {
        List<QuestDefinition> qs = tierQuests(tier);
        for (QuestDefinition q : qs) {
            if (!state.isCompleted(q.id())) {
                return false;
            }
        }
        return !qs.isEmpty();
    }

    public boolean tierUnlocked(PlayerProgressionState state, int tier) {
        if (tier == 1) {
            return true;
        }
        return tierFullyCompleted(state, tier - 1);
    }

    /** Всего выполнено квестов по всем ступеням (макс. 105). */
    public int totalCompletedQuests(PlayerProgressionState state) {
        int n = 0;
        for (int t = 1; t <= TIER_COUNT; t++) {
            n += completedCountInTier(state, t);
        }
        return n;
    }

    public int completedCountInTier(PlayerProgressionState state, int tier) {
        int n = 0;
        for (QuestDefinition q : tierQuests(tier)) {
            if (state.isCompleted(q.id())) {
                n++;
            }
        }
        return n;
    }

    /**
     * Сколько ступеней полностью пройдено подряд с 1-й (для уровня знаний).
     */
    public int consecutiveCompletedTiers(PlayerProgressionState state) {
        int streak = 0;
        for (int t = 1; t <= TIER_COUNT; t++) {
            if (tierFullyCompleted(state, t)) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    public int knowledgeFromProgress(PlayerProgressionState state) {
        int streak = consecutiveCompletedTiers(state);
        return Math.min(5, 1 + streak);
    }

    public String findNextOpenQuestId(PlayerProgressionState state) {
        for (int tier = 1; tier <= TIER_COUNT; tier++) {
            if (!tierUnlocked(state, tier)) {
                break;
            }
            for (QuestDefinition q : tierQuests(tier)) {
                if (!state.isCompleted(q.id())) {
                    return q.id();
                }
            }
        }
        return null;
    }

    /**
     * Индекс 0..20 первого незавершённого в ступени, или -1 если ступень пройдена / недоступна.
     */
    public int firstIncompleteIndex(PlayerProgressionState state, int tier) {
        if (!tierUnlocked(state, tier)) {
            return -1;
        }
        List<QuestDefinition> qs = tierQuests(tier);
        for (int i = 0; i < qs.size(); i++) {
            if (!state.isCompleted(qs.get(i).id())) {
                return i;
            }
        }
        return -1;
    }

    public QuestSlotState slotState(PlayerProgressionState state, int tier, int index0) {
        if (!tierUnlocked(state, tier)) {
            return QuestSlotState.TIER_LOCKED;
        }
        List<QuestDefinition> qs = tierQuests(tier);
        QuestDefinition q = qs.get(index0);
        if (state.isCompleted(q.id())) {
            return QuestSlotState.DONE;
        }
        int first = firstIncompleteIndex(state, tier);
        if (first < 0) {
            return QuestSlotState.DONE;
        }
        if (index0 < first) {
            return QuestSlotState.LOCKED;
        }
        if (index0 == first) {
            return QuestSlotState.ACTIVE;
        }
        if (index0 <= first + 2) {
            return QuestSlotState.NEXT_PREVIEW;
        }
        return QuestSlotState.LOCKED;
    }

    public enum QuestSlotState {
        TIER_LOCKED,
        LOCKED,
        NEXT_PREVIEW,
        ACTIVE,
        DONE
    }
}
