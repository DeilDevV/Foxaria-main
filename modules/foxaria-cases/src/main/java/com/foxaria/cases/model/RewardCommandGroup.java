package com.foxaria.cases.model;

import java.util.List;

public record RewardCommandGroup(String target, List<String> commands) {
}
