-- Сброс прогресса цепочки гильдейских квестов (переход на 21 квест / новая логика)
DELETE FROM fx_guild_level_objective_progress;
DELETE FROM fx_guild_level_rewards_claimed;
