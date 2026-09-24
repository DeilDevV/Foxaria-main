package com.foxaria.shop.progression;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

public final class PlayerProgressionState {

    private final UUID playerUuid;
    private int knowledgeLevel;
    private String currentQuestId;
    /** Совместимость: первый счётчик или единственный для старых квестов. */
    private long questProgress;
    /** Прогресс по целям: "3;5;0" или null — тогда для одной цели берётся questProgress. */
    private String questObjectiveCsv;
    private final Set<String> completedQuests;

    public PlayerProgressionState(
        UUID playerUuid,
        int knowledgeLevel,
        String currentQuestId,
        long questProgress,
        String questObjectiveCsv,
        Set<String> completedQuests
    ) {
        this.playerUuid = playerUuid;
        this.knowledgeLevel = knowledgeLevel;
        this.currentQuestId = currentQuestId;
        this.questProgress = questProgress;
        this.questObjectiveCsv = questObjectiveCsv;
        this.completedQuests = new LinkedHashSet<>(completedQuests);
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public int knowledgeLevel() {
        return knowledgeLevel;
    }

    public void setKnowledgeLevel(int knowledgeLevel) {
        this.knowledgeLevel = knowledgeLevel;
    }

    public String currentQuestId() {
        return currentQuestId;
    }

    public void setCurrentQuestId(String currentQuestId) {
        this.currentQuestId = currentQuestId;
    }

    public long questProgress() {
        return questProgress;
    }

    public void setQuestProgress(long questProgress) {
        this.questProgress = questProgress;
    }

    public String questObjectiveCsv() {
        return questObjectiveCsv;
    }

    public void setQuestObjectiveCsv(String questObjectiveCsv) {
        this.questObjectiveCsv = questObjectiveCsv;
    }

    public Set<String> completedQuests() {
        return completedQuests;
    }

    public boolean isCompleted(String questId) {
        return completedQuests.contains(questId);
    }

    public void markCompleted(String questId) {
        completedQuests.add(questId);
    }

    public int[] objectiveProgressArray(QuestDefinition def) {
        int n = def.objectives().size();
        int[] arr = new int[n];
        if (questObjectiveCsv != null && !questObjectiveCsv.isBlank()) {
            String[] parts = questObjectiveCsv.split(";");
            for (int i = 0; i < n && i < parts.length; i++) {
                try {
                    arr[i] = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException e) {
                    arr[i] = 0;
                }
            }
            return arr;
        }
        if (n == 1) {
            arr[0] = (int) Math.min(Math.max(0L, questProgress), Integer.MAX_VALUE);
        }
        return arr;
    }

    public void setObjectiveProgressArray(int[] progress) {
        if (progress == null || progress.length == 0) {
            questObjectiveCsv = null;
            questProgress = 0L;
            return;
        }
        StringJoiner j = new StringJoiner(";");
        for (int v : progress) {
            j.add(String.valueOf(v));
        }
        questObjectiveCsv = j.toString();
        questProgress = progress[0];
    }

    public void clearQuestProgress() {
        questObjectiveCsv = null;
        questProgress = 0L;
    }

    public void ensureProgressShape(QuestDefinition def) {
        int n = def.objectives().size();
        if (n == 0) {
            return;
        }
        int[] arr = objectiveProgressArray(def);
        if (arr.length != n) {
            arr = Arrays.copyOf(arr, n);
        }
        setObjectiveProgressArray(arr);
    }

    public static String serializeCompleted(Set<String> set) {
        return String.join(",", set);
    }

    public static Set<String> deserializeCompleted(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
