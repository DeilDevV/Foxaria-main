package com.foxaria.cases.model;

import java.util.List;

public record CaseMenuConfig(
    String title,
    int openButtonSlot,
    int keysHintSlot,
    List<String> buyKeysLore
) {
}
