-- Запись для /craft: описание бонуса «сера» при плавке (рецепт Bukkit не регистрируется).
INSERT IGNORE INTO fx_custom_crafts (craft_id, recipe_json, sort_order, updated_at)
VALUES (
    'sulfur_smelting',
    '{"creationKind":"FURNACE","registerPrimaryRecipe":false,"displayName":"&e&lСера &7при плавке руд и песка","requiredKnowledge":1,"resultTemplateId":"sulfur","resultAmount":1,"resultStackBase64":"","bonusTemplateId":"","bonusChancePercent":0,"smeltCookTicks":200,"smeltExperience":0.2,"grid":[null,null,null,null,null,null,null,null,null],"ingredients":[]}',
    0,
    0
);
