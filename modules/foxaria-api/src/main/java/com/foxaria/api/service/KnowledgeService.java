package com.foxaria.api.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Уровень знаний игрока (прогрессия / магазин / квесты).
 */
public interface KnowledgeService {

    CompletableFuture<Integer> knowledgeLevel(UUID playerUuid);
}
