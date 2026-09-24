package com.foxaria.itemtemplates.edit;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Кастомные эффекты для зелий, взрывных, туманных и стрел.
 */
public final class HandPotionUtil {

    private HandPotionUtil() {
    }

    public static boolean supportsPotionMeta(ItemStack stack) {
        if (HandItemAttributeUtil.isAirOrEmpty(stack)) {
            return false;
        }
        return stack.getItemMeta() instanceof PotionMeta;
    }

    public static void addCustomEffect(ItemStack stack, PotionEffectType type, int durationTicks, int amplifier) {
        if (!supportsPotionMeta(stack) || type == null) {
            return;
        }
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, Math.max(1, durationTicks), Math.max(0, amplifier)), true);
        stack.setItemMeta(meta);
    }

    public static void clearCustomEffects(ItemStack stack) {
        if (!supportsPotionMeta(stack)) {
            return;
        }
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.clearCustomEffects();
        stack.setItemMeta(meta);
    }

    public static void extendAllCustomDurations(ItemStack stack, int deltaTicks) {
        if (!supportsPotionMeta(stack)) {
            return;
        }
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        List<PotionEffect> copy = new ArrayList<>(meta.getCustomEffects());
        meta.clearCustomEffects();
        for (PotionEffect pe : copy) {
            int d = Math.max(1, pe.getDuration() + deltaTicks);
            meta.addCustomEffect(new PotionEffect(pe.getType(), d, pe.getAmplifier(), pe.isAmbient(), pe.hasParticles(), pe.hasIcon()), true);
        }
        stack.setItemMeta(meta);
    }

    /**
     * Базовый тип «обычное зелье», чтобы предмет оставался валидным зельем.
     */
    public static void ensureAwkwardBase(ItemStack stack) {
        if (!supportsPotionMeta(stack)) {
            return;
        }
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        PotionType t = meta.getBasePotionType();
        if (t == PotionType.WATER || t == PotionType.MUNDANE || t == PotionType.THICK) {
            meta.setBasePotionType(PotionType.AWKWARD);
        }
        stack.setItemMeta(meta);
    }
}
