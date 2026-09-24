package com.foxaria.shop.progression;

import java.util.List;

public record QuestDefinition(
    String id,
    int tier,
    int indexInTier,
    String title,
    List<String> descriptionLines,
    List<QuestObjective> objectives,
    QuestRewards rewards
) {
    public boolean isComplete(int[] progress) {
        if (progress == null || progress.length != objectives.size()) {
            return false;
        }
        for (int i = 0; i < objectives.size(); i++) {
            if (progress[i] < objectives.get(i).amount()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasAnySubmitObjective() {
        for (QuestObjective o : objectives) {
            if (o.type() == QuestType.SUBMIT_ITEMS) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли незавершённая сдача предметов (для клика по меню). */
    public boolean hasIncompleteSubmit(int[] progress) {
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective o = objectives.get(i);
            if (o.type() == QuestType.SUBMIT_ITEMS && progress[i] < o.amount()) {
                return true;
            }
        }
        return false;
    }
}
