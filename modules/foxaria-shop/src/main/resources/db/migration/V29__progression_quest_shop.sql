CREATE TABLE IF NOT EXISTS fx_player_progression (
    player_uuid VARCHAR(36) PRIMARY KEY,
    knowledge_level INT NOT NULL DEFAULT 1,
    current_quest_id VARCHAR(64),
    quest_progress BIGINT NOT NULL DEFAULT 0,
    completed_quests TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fx_progression_shop_offers (
    id VARCHAR(36) PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    required_knowledge INT NOT NULL DEFAULT 1,
    price DECIMAL(20, 2) NOT NULL,
    item_blob TEXT,
    item_template VARCHAR(64) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    INDEX idx_prog_shop_cat (category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
