package com.foxaria.cases.service;

import com.foxaria.cases.model.CaseDefinition;
import com.foxaria.cases.model.CaseLocation;
import com.foxaria.cases.model.CaseReward;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CaseAnimationService {

    private final JavaPlugin plugin;
    private final ConfigCaseService config;
    private final CaseItemService items;
    private final PlacedCaseService placedCases;

    public CaseAnimationService(
        JavaPlugin plugin,
        ConfigCaseService config,
        CaseItemService items,
        PlacedCaseService placedCases
    ) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        this.placedCases = placedCases;
    }

    public CaseReward chooseReward(CaseDefinition definition) {
        int total = definition.rewards().stream().mapToInt(CaseReward::weight).sum();
        if (total <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (CaseReward reward : definition.rewards()) {
            cursor += reward.weight();
            if (roll < cursor) {
                return reward;
            }
        }
        return definition.rewards().get(definition.rewards().size() - 1);
    }

    public void play(
        Player player,
        CaseLocation location,
        CaseDefinition definition,
        CaseReward winningReward,
        Runnable onComplete
    ) {
        Location returnLocation = player.getLocation().clone();
        float returnYaw = player.getLocation().getYaw();
        float returnPitch = player.getLocation().getPitch();
        float returnWalkSpeed = player.getWalkSpeed();
        float returnFlySpeed = player.getFlySpeed();

        Location center = placedCases.centerLocation(location);
        if (center == null) {
            onComplete.run();
            return;
        }

        placedCases.hideTemporarily(location);
        player.teleport(center);
        player.setWalkSpeed(0.0F);
        player.setFlySpeed(0.0F);

        List<ItemStack> ringItems = buildRingItems(definition, winningReward);

        List<ItemDisplay> displays = spawnRing(center, ringItems);
        ItemDisplay pointer = spawnPointer(center.clone().add(0, 2.2D, 0));

        int duration = config.animationDurationTicks();
        final int[] tick = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            tick[0]++;
            double progress = Math.min(1.0D, tick[0] / (double) duration);
            double eased = 1.0D - Math.pow(1.0D - progress, 3.0D);
            double speed = 0.35D * (1.0D - eased) + 0.02D;
            double angle = tick[0] * speed;
            rotateRing(displays, center, angle, 1.8D);

            if (tick[0] >= duration) {
                task.cancel();
                cleanupDisplays(displays, pointer);
                Location restored = returnLocation.clone();
                restored.setYaw(returnYaw);
                restored.setPitch(returnPitch);
                player.teleport(restored);
                player.setWalkSpeed(returnWalkSpeed);
                player.setFlySpeed(returnFlySpeed);
                placedCases.restoreAfterAnimation(location);
                onComplete.run();
            }
        }, 0L, 1L);
    }

    private List<ItemStack> buildRingItems(CaseDefinition definition, CaseReward winner) {
        List<ItemStack> ring = new ArrayList<>();
        List<CaseReward> rewards = definition.rewards();
        int count = Math.max(8, config.rouletteItemsCount());
        for (int i = 0; i < count; i++) {
            CaseReward reward = rewards.get(i % rewards.size());
            ring.add(items.rewardIcon(reward));
        }
        int insertAt = ThreadLocalRandom.current().nextInt(ring.size());
        ring.set(insertAt, items.rewardIcon(winner));
        return ring;
    }

    private List<ItemDisplay> spawnRing(Location center, List<ItemStack> ringItems) {
        List<ItemDisplay> displays = new ArrayList<>();
        int size = ringItems.size();
        for (int i = 0; i < size; i++) {
            double angle = i * (Math.PI * 2 / size);
            Location at = center.clone().add(Math.cos(angle) * 1.8D, 0.2D, Math.sin(angle) * 1.8D);
            final ItemStack icon = ringItems.get(i);
            ItemDisplay display = center.getWorld().spawn(at, ItemDisplay.class, entity -> configureDisplay(entity, icon));
            displays.add(display);
        }
        return displays;
    }

    private ItemDisplay spawnPointer(Location location) {
        ItemStack pointerItem = new ItemStack(Material.TRIDENT);
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            configureDisplay(entity, pointerItem);
            entity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f((float) Math.toRadians(180), 1, 0, 0),
                new Vector3f(0.8F, 0.8F, 0.8F),
                new AxisAngle4f(0, 0, 0, 1)
            ));
        });
    }

    private void configureDisplay(ItemDisplay entity, ItemStack stack) {
        entity.setItemStack(stack);
        entity.setBillboard(Display.Billboard.CENTER);
        entity.setTransformation(new Transformation(
            new Vector3f(0, 0, 0),
            new AxisAngle4f(0, 0, 0, 1),
            new Vector3f(0.55F, 0.55F, 0.55F),
            new AxisAngle4f(0, 0, 0, 1)
        ));
    }

    private void rotateRing(List<ItemDisplay> displays, Location center, double angle, double radius) {
        int size = displays.size();
        for (int i = 0; i < size; i++) {
            ItemDisplay display = displays.get(i);
            double slotAngle = angle + i * (Math.PI * 2 / size);
            Location at = center.clone().add(Math.cos(slotAngle) * radius, 0.2D, Math.sin(slotAngle) * radius);
            display.teleport(at);
        }
    }

    private void cleanupDisplays(List<ItemDisplay> displays, ItemDisplay pointer) {
        for (ItemDisplay display : displays) {
            display.remove();
        }
        pointer.remove();
    }
}
