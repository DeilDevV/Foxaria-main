package com.foxaria.modernfurnace;

import com.foxaria.api.service.SmeltBonusHook;
import com.foxaria.core.util.ItemStackSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Один тик обработки одной печи: топливо, плавка, трубы.
 */
public final class ModernFurnaceEngine {

    private ModernFurnaceEngine() {
    }

    public static void processTick(Location furnaceLoc, PersistedFurnaceJson d, ModernFurnaceConfig cfg, SmeltBonusHook smeltBonusHook) {
        ModernFurnaceStateFactory.normalize(d);
        int lanes = cfg.parallelLanes(d.parallelLevel);

        tryPipeImportFuel(furnaceLoc, d, cfg);
        tryPipeImport(furnaceLoc, d, lanes, cfg);
        tryPipeExport(furnaceLoc, d, lanes, cfg);

        ItemStack fuel = stackFromB64(d.fuelStackB64);
        boolean needHeat = needsHeat(d, lanes, cfg);
        if (d.fuelTicksRemaining <= 0 && needHeat) {
            if (fuel == null || fuel.getType().isAir() || fuel.getAmount() <= 0) {
                // нет топлива
            } else {
                int base = FuelTicks.burnTicks(fuel);
                if (base > 0) {
                    double mult = cfg.fuelDurationMultiplier(d.fuelLevel);
                    d.fuelTicksRemaining = (int) (base * mult);
                    fuel.setAmount(fuel.getAmount() - 1);
                    if (fuel.getAmount() <= 0) {
                        fuel = null;
                    }
                    d.fuelStackB64 = stackToB64(fuel);
                }
            }
        }

        if (d.fuelTicksRemaining > 0) {
            d.fuelTicksRemaining--;
            double cookFactor = cfg.cookTimeFactor(d.speedLevel);
            int outMul = cfg.outputMultiplier(d.outputLevel);

            for (int lane = 0; lane < lanes; lane++) {
                PersistedFurnaceJson.CookLineJson line = d.lines.get(lane);
                ItemStack input = stackFromB64(line.inputB64);
                if (input == null || input.getType().isAir()) {
                    line.cookProgress = 0;
                    continue;
                }
                CookingRecipe<?> recipe = SmeltingUtil.findCookingRecipe(input);
                if (recipe == null) {
                    continue;
                }
                int needTicks = Math.max(1, (int) Math.ceil(SmeltingUtil.baseCookTicks(recipe) * cookFactor));
                ItemStack result = recipe.getResult().clone();
                result.setAmount(result.getAmount() * outMul);
                ItemStack outSlot = stackFromB64(d.outputStacksB64.get(lane));
                if (!canMergeInto(outSlot, result)) {
                    continue;
                }
                line.cookProgress++;
                if (line.cookProgress >= needTicks) {
                    line.cookProgress = 0;
                    Material consumedType = input.getType();
                    input.setAmount(input.getAmount() - 1);
                    if (input.getAmount() <= 0) {
                        input = null;
                    }
                    line.inputB64 = stackToB64(input);
                    d.outputStacksB64.set(lane, stackToB64(mergeInto(outSlot, result)));
                    if (smeltBonusHook != null) {
                        UUID who = parseUuid(d.lastSmelterUuid);
                        final int laneForBonus = lane;
                        smeltBonusHook.afterOneSmelted(who, furnaceLoc, consumedType, false, true, bonus ->
                            deliverBonus(d, laneForBonus, bonus, furnaceLoc));
                    }
                }
            }
        }
    }

    private static void deliverBonus(PersistedFurnaceJson d, int lane, ItemStack bonus, Location furnaceLoc) {
        if (bonus == null || bonus.getType().isAir()) {
            return;
        }
        ItemStack one = bonus.clone();
        one.setAmount(1);
        ItemStack out = stackFromB64(d.outputStacksB64.get(lane));
        if (canMergeInto(out, one)) {
            d.outputStacksB64.set(lane, stackToB64(mergeInto(out, one)));
            return;
        }
        if (out == null || out.getType().isAir()) {
            d.outputStacksB64.set(lane, stackToB64(one));
            return;
        }
        if (tryInsertBonusToPipeOutput(d, one, furnaceLoc)) {
            return;
        }
        if (tryInsertIntoHopperBelow(furnaceLoc, one)) {
            return;
        }
        if (furnaceLoc.getWorld() != null) {
            furnaceLoc.getWorld().dropItemNaturally(furnaceLoc.clone().add(0.5, 1.0, 0.5), one);
        }
    }

    private static boolean tryInsertBonusToPipeOutput(PersistedFurnaceJson d, ItemStack one, Location furnaceLoc) {
        if (!d.pipesUnlocked || d.outputChestWorld == null || d.outputChestX == null) {
            return false;
        }
        Location chestLoc = chestWorldLoc(d.outputChestWorld, d.outputChestX, d.outputChestY, d.outputChestZ);
        if (chestLoc == null || !pipeReach(furnaceLoc, chestLoc)) {
            return false;
        }
        Inventory inv = chestInventory(chestLoc.getBlock());
        if (inv == null) {
            return false;
        }
        ItemStack single = one.clone();
        single.setAmount(1);
        return moveStackToInventory(inv, single) >= 1;
    }

    private static boolean tryInsertIntoHopperBelow(Location furnaceLoc, ItemStack one) {
        if (furnaceLoc.getWorld() == null) {
            return false;
        }
        Block under = furnaceLoc.getWorld().getBlockAt(
            furnaceLoc.getBlockX(), furnaceLoc.getBlockY() - 1, furnaceLoc.getBlockZ());
        if (under.getType() != Material.HOPPER) {
            return false;
        }
        BlockState st = under.getState();
        if (!(st instanceof Hopper hopper)) {
            return false;
        }
        ItemStack add = one.clone();
        add.setAmount(1);
        Map<Integer, ItemStack> left = hopper.getInventory().addItem(add);
        return left.isEmpty();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean needsHeat(PersistedFurnaceJson d, int lanes, ModernFurnaceConfig cfg) {
        for (int lane = 0; lane < lanes; lane++) {
            ItemStack input = stackFromB64(d.lines.get(lane).inputB64);
            if (input != null && !input.getType().isAir() && SmeltingUtil.findCookingRecipe(input) != null) {
                ItemStack outSlot = stackFromB64(d.outputStacksB64.get(lane));
                CookingRecipe<?> r = SmeltingUtil.findCookingRecipe(input);
                ItemStack res = r.getResult().clone();
                res.setAmount(res.getAmount() * cfg.outputMultiplier(d.outputLevel));
                if (canMergeInto(outSlot, res)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void tryPipeImportFuel(Location furnace, PersistedFurnaceJson d, ModernFurnaceConfig cfg) {
        if (!d.pipesUnlocked || d.inputChestX == null || d.inputChestWorld == null) {
            return;
        }
        Location chestLoc = chestWorldLoc(d.inputChestWorld, d.inputChestX, d.inputChestY, d.inputChestZ);
        if (chestLoc == null || !pipeReach(furnace, chestLoc)) {
            return;
        }
        Inventory inv = chestInventory(chestLoc.getBlock());
        if (inv == null) {
            return;
        }
        ItemStack fuel = stackFromB64(d.fuelStackB64);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) {
                continue;
            }
            if (!FuelTicks.isFuel(s)) {
                continue;
            }
            if (SmeltingUtil.findCookingRecipe(s) != null) {
                continue;
            }
            int max = s.getMaxStackSize();
            if (fuel == null || fuel.getType().isAir()) {
                int take = Math.min(s.getAmount(), max);
                ItemStack pull = s.clone();
                pull.setAmount(take);
                s.setAmount(s.getAmount() - take);
                if (s.getAmount() <= 0) {
                    inv.setItem(i, null);
                }
                d.fuelStackB64 = stackToB64(pull);
                return;
            }
            if (fuel.isSimilar(s) && fuel.getAmount() < max) {
                int space = max - fuel.getAmount();
                int take = Math.min(space, s.getAmount());
                fuel.setAmount(fuel.getAmount() + take);
                s.setAmount(s.getAmount() - take);
                if (s.getAmount() <= 0) {
                    inv.setItem(i, null);
                }
                d.fuelStackB64 = stackToB64(fuel);
                return;
            }
        }
    }

    private static void tryPipeImport(Location furnace, PersistedFurnaceJson d, int lanes, ModernFurnaceConfig cfg) {
        if (!d.pipesUnlocked || d.inputChestX == null || d.inputChestWorld == null) {
            return;
        }
        Location chestLoc = chestWorldLoc(d.inputChestWorld, d.inputChestX, d.inputChestY, d.inputChestZ);
        if (chestLoc == null || !pipeReach(furnace, chestLoc)) {
            return;
        }
        Inventory inv = chestInventory(chestLoc.getBlock());
        if (inv == null) {
            return;
        }
        outer:
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) {
                continue;
            }
            if (SmeltingUtil.findCookingRecipe(s) == null) {
                continue;
            }
            for (int lane = 0; lane < lanes; lane++) {
                if (d.lines.get(lane).inputB64 == null) {
                    ItemStack one = s.clone();
                    one.setAmount(1);
                    s.setAmount(s.getAmount() - 1);
                    if (s.getAmount() <= 0) {
                        inv.setItem(i, null);
                    }
                    d.lines.get(lane).inputB64 = stackToB64(one);
                    break outer;
                }
            }
            return;
        }
    }

    private static void tryPipeExport(Location furnace, PersistedFurnaceJson d, int lanes, ModernFurnaceConfig cfg) {
        if (!d.pipesUnlocked || d.outputChestX == null || d.outputChestWorld == null) {
            return;
        }
        Location chestLoc = chestWorldLoc(d.outputChestWorld, d.outputChestX, d.outputChestY, d.outputChestZ);
        if (chestLoc == null || !pipeReach(furnace, chestLoc)) {
            return;
        }
        Inventory inv = chestInventory(chestLoc.getBlock());
        if (inv == null) {
            return;
        }
        for (int lane = 0; lane < lanes; lane++) {
            ItemStack out = stackFromB64(d.outputStacksB64.get(lane));
            if (out == null || out.getType().isAir()) {
                continue;
            }
            int moved = moveStackToInventory(inv, out.clone());
            if (moved <= 0) {
                continue;
            }
            out.setAmount(out.getAmount() - moved);
            if (out.getAmount() <= 0) {
                d.outputStacksB64.set(lane, null);
            } else {
                d.outputStacksB64.set(lane, stackToB64(out));
            }
            return;
        }
    }

    private static int moveStackToInventory(Inventory inv, ItemStack stack) {
        int toMove = stack.getAmount();
        int moved = 0;
        for (int i = 0; i < inv.getSize() && toMove > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                int put = Math.min(toMove, stack.getMaxStackSize());
                ItemStack ins = stack.clone();
                ins.setAmount(put);
                inv.setItem(i, ins);
                moved += put;
                toMove -= put;
                continue;
            }
            if (slot.isSimilar(stack) && slot.getAmount() < slot.getMaxStackSize()) {
                int space = slot.getMaxStackSize() - slot.getAmount();
                int put = Math.min(space, toMove);
                slot.setAmount(slot.getAmount() + put);
                inv.setItem(i, slot);
                moved += put;
                toMove -= put;
            }
        }
        return moved;
    }

    public static boolean pipeReach(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return false;
        }
        int dx = Math.abs(a.getBlockX() - b.getBlockX());
        int dy = Math.abs(a.getBlockY() - b.getBlockY());
        int dz = Math.abs(a.getBlockZ() - b.getBlockZ());
        return dx + dy + dz <= 5;
    }

    public static Location chestWorldLoc(String wid, int x, int y, int z) {
        try {
            java.util.UUID u = java.util.UUID.fromString(wid);
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(u);
            if (w == null) {
                return null;
            }
            return new Location(w, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    private static Inventory chestInventory(Block block) {
        Material t = block.getType();
        if (t != Material.CHEST && t != Material.TRAPPED_CHEST && t != Material.BARREL) {
            return null;
        }
        BlockState st = block.getState();
        if (st instanceof Chest c) {
            return c.getInventory();
        }
        if (st instanceof org.bukkit.block.Barrel barrel) {
            return barrel.getInventory();
        }
        return null;
    }

    private static boolean canMergeInto(ItemStack existing, ItemStack result) {
        if (existing == null || existing.getType().isAir()) {
            return true;
        }
        if (!existing.isSimilar(result)) {
            return false;
        }
        return existing.getAmount() + result.getAmount() <= existing.getMaxStackSize();
    }

    private static ItemStack mergeInto(ItemStack existing, ItemStack result) {
        if (existing == null || existing.getType().isAir()) {
            return result.clone();
        }
        ItemStack m = existing.clone();
        m.setAmount(Math.min(m.getMaxStackSize(), m.getAmount() + result.getAmount()));
        return m;
    }

    private static ItemStack stackFromB64(String b64) {
        if (b64 == null || b64.isBlank()) {
            return null;
        }
        try {
            return ItemStackSerializer.deserialize(b64);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stackToB64(ItemStack s) {
        if (s == null || s.getType().isAir()) {
            return null;
        }
        return ItemStackSerializer.serialize(s);
    }
}
