package com.foxaria.itemtemplates.util;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

/**
 * Русские подписи зачарований и эффектов для GUI редактора (ванилла + запасной вариант по ключу).
 */
public final class EditorDisplayNames {

    private static final Map<String, String> ENCHANTS = new HashMap<>();
    private static final Map<String, String> EFFECTS = new HashMap<>();

    static {
        // Зачарования (minecraft:)
        e("minecraft:protection", "Защита");
        e("minecraft:fire_protection", "Огнеупорность");
        e("minecraft:feather_falling", "Невесомость");
        e("minecraft:blast_protection", "Взрывоустойчивость");
        e("minecraft:projectile_protection", "Защита от снарядов");
        e("minecraft:respiration", "Подводное дыхание");
        e("minecraft:aqua_affinity", "Подводное исправление");
        e("minecraft:thorns", "Шипы");
        e("minecraft:depth_strider", "Глубинный странник");
        e("minecraft:frost_walker", "Ледоход");
        e("minecraft:binding_curse", "Проклятие несъёмности");
        e("minecraft:soul_speed", "Скорость души");
        e("minecraft:swift_sneak", "Проворство");
        e("minecraft:sharpness", "Острота");
        e("minecraft:smite", "Небесная кара");
        e("minecraft:bane_of_arthropods", "Засекатель");
        e("minecraft:knockback", "Отдача");
        e("minecraft:fire_aspect", "Заговор огня");
        e("minecraft:looting", "Добыча");
        e("minecraft:sweeping_edge", "Разящий клинок");
        e("minecraft:efficiency", "Эффективность");
        e("minecraft:silk_touch", "Шёлковое касание");
        e("minecraft:unbreaking", "Прочность");
        e("minecraft:fortune", "Удача");
        e("minecraft:power", "Сила");
        e("minecraft:punch", "Толчок");
        e("minecraft:flame", "Воспламенение");
        e("minecraft:infinity", "Бесконечность");
        e("minecraft:luck_of_the_sea", "Морская удача");
        e("minecraft:lure", "Приманка");
        e("minecraft:loyalty", "Верность");
        e("minecraft:impaling", "Пронзатель");
        e("minecraft:riptide", "Тягун");
        e("minecraft:channeling", "Громовержец");
        e("minecraft:multishot", "Мультивыстрел");
        e("minecraft:quick_charge", "Быстрая зарядка");
        e("minecraft:piercing", "Пронзающий выстрел");
        e("minecraft:mending", "Починка");
        e("minecraft:vanishing_curse", "Проклятие утраты");
        e("minecraft:density", "Плотность");
        e("minecraft:breach", "Пробой");
        e("minecraft:wind_burst", "Порыв ветра");

        // Эффекты зелий
        fx("minecraft:speed", "Скорость");
        fx("minecraft:slowness", "Медлительность");
        fx("minecraft:haste", "Спешка");
        fx("minecraft:mining_fatigue", "Усталость");
        fx("minecraft:strength", "Сила");
        fx("minecraft:instant_health", "Мгновенное лечение");
        fx("minecraft:instant_damage", "Мгновенный урон");
        fx("minecraft:jump_boost", "Прыгучесть");
        fx("minecraft:nausea", "Тошнота");
        fx("minecraft:regeneration", "Регенерация");
        fx("minecraft:resistance", "Сопротивление");
        fx("minecraft:fire_resistance", "Огнестойкость");
        fx("minecraft:water_breathing", "Подводное дыхание");
        fx("minecraft:invisibility", "Невидимость");
        fx("minecraft:blindness", "Слепота");
        fx("minecraft:night_vision", "Ночное зрение");
        fx("minecraft:hunger", "Голод");
        fx("minecraft:weakness", "Слабость");
        fx("minecraft:poison", "Отравление");
        fx("minecraft:wither", "Иссушение");
        fx("minecraft:health_boost", "Здоровье");
        fx("minecraft:absorption", "Поглощение");
        fx("minecraft:saturation", "Насыщение");
        fx("minecraft:glowing", "Свечение");
        fx("minecraft:levitation", "Левитация");
        fx("minecraft:luck", "Удача");
        fx("minecraft:unluck", "Неудача");
        fx("minecraft:slow_falling", "Медленное падение");
        fx("minecraft:conduit_power", "Сила морского проводника");
        fx("minecraft:dolphins_grace", "Грация дельфина");
        fx("minecraft:bad_omen", "Дурное знамение");
        fx("minecraft:hero_of_the_village", "Герой деревни");
        fx("minecraft:darkness", "Тьма");
        fx("minecraft:trial_omen", "Знамение испытания");
        fx("minecraft:raid_omen", "Знамение рейда");
        fx("minecraft:weaving", "Плетение");
        fx("minecraft:oozing", "Слизь");
        fx("minecraft:infested", "Заражение");
        fx("minecraft:wind_charged", "Заряд ветра");
    }

    private static void e(String key, String ru) {
        ENCHANTS.put(key, ru);
    }

    private static void fx(String key, String ru) {
        EFFECTS.put(key, ru);
    }

    public static String enchant(Enchantment en) {
        String key = en.getKey().asString();
        return ENCHANTS.getOrDefault(key, humanizeFallback(key));
    }

    public static String potionEffect(PotionEffectType type) {
        if (type == null) {
            return "?";
        }
        String key = type.getKey().toString();
        return EFFECTS.getOrDefault(key, humanizeFallback(key));
    }

    private static String humanizeFallback(String namespacedKey) {
        int colon = namespacedKey.indexOf(':');
        String path = colon >= 0 ? namespacedKey.substring(colon + 1) : namespacedKey;
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String p = parts[i];
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
