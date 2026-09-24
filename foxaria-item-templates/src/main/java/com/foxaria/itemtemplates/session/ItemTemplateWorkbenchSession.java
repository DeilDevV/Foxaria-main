package com.foxaria.itemtemplates.session;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Временное состояние GUI (выбор эффекта для зелья / дебаффа удара).
 */
public final class ItemTemplateWorkbenchSession {

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private ItemTemplateWorkbenchSession() {
    }

    public static State get(Player player) {
        return STATES.computeIfAbsent(player.getUniqueId(), u -> new State());
    }

    public static void forget(Player player) {
        STATES.remove(player.getUniqueId());
    }

    public static final class State {
        public PotionEffectType brewType = PotionEffectType.POISON;
        public int brewDurationTicks = 100;
        public int brewAmplifier;

        /** Редактировать эффекты удара на предмете в правой или левой руке (руны). */
        public boolean strikeEditMainHand = true;

        /** Настраиваем «на врага» или «на себя» (общие кнопки длительности/шанса). */
        public StrikeFocus strikeFocus = StrikeFocus.VICTIM;

        public PotionEffectType onHitType = PotionEffectType.POISON;
        public int onHitDurationTicks = 60;
        public int onHitAmplifier;
        /** 0.0–1.0 */
        public float onHitChance = 0.25f;

        public PotionEffectType onHitSelfType = PotionEffectType.REGENERATION;
        public int onHitSelfDurationTicks = 60;
        public int onHitSelfAmplifier;
        public float onHitSelfChance = 0.35f;

        /** Ввод в чат из панели «Имя и описание». */
        public ChatPrompt chatPrompt = ChatPrompt.NONE;
    }

    public enum ChatPrompt {
        NONE,
        DISPLAY_NAME,
        LORE_LINE
    }

    public enum StrikeFocus {
        VICTIM,
        SELF
    }
}
