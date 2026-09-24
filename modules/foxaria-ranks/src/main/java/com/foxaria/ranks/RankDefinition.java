package com.foxaria.ranks;

import java.util.List;
import java.util.Set;

public record RankDefinition(
    String id,
    String displayName,
    String prefix,
    List<String> inherits,
    Set<String> permissions
) {
}
