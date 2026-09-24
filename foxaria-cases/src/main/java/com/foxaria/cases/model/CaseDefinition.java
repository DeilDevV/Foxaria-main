package com.foxaria.cases.model;

import java.util.List;

public record CaseDefinition(
    String id,
    String displayName,
    CaseBlockItemConfig blockItem,
    CaseHologramConfig hologram,
    CaseMenuConfig menu,
    CaseIdleEffectsConfig idleEffects,
    List<CaseReward> rewards
) {
}
