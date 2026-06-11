package com.morefirework.component;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import java.util.*;

/**
 * Persistent state for all firework combat effects on a LivingEntity.
 */
public class FireworkEffectComponent {

    // --- Bleed (Iron) ---
    private final Map<EquipmentSlot, Integer> bleedStacks = new HashMap<>();

    // --- Fracture (Amethyst) ---
    private final Map<EquipmentSlot, Integer> fractureStacks = new HashMap<>();
    private final Map<EquipmentSlot, Long> fracturedExpiry = new HashMap<>();

    // --- Crystalized immunity ---
    private long crystalizedImmunityUntil = 0;

    // --- Stab (Diamond) ---
    private final Map<EquipmentSlot, Boolean> diamondMarked = new HashMap<>();
    private final Map<EquipmentSlot, Long> stabImmunityUntil = new HashMap<>();

    // --- Heat Signature (Emerald) ---
    private int emeraldLevel = 0;
    private long emeraldExpiry = 0;

    // --- Stun (Crystalized) ---
    private boolean stunned = false;
    private long stunnedUntil = 0;
    private long armorReductionUntil = 0;

    // --- Brush Timer ---
    private int brushingTicks = 0;

    // === Persistence ===

    public void read(NbtCompound tag) {
        bleedStacks.clear();
        NbtCompound bleedTag = tag.getCompound("bleed");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            bleedStacks.put(slot, bleedTag.getInt(slot.getName()));
        }

        fractureStacks.clear();
        fracturedExpiry.clear();
        NbtCompound fractureTag = tag.getCompound("fracture");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            fractureStacks.put(slot, fractureTag.getInt("stack_" + slot.getName()));
            fracturedExpiry.put(slot, fractureTag.getLong("expiry_" + slot.getName()));
        }
        crystalizedImmunityUntil = tag.getLong("crystalizedImmunity");

        diamondMarked.clear();
        stabImmunityUntil.clear();
        NbtCompound diamondTag = tag.getCompound("diamond");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            diamondMarked.put(slot, diamondTag.getBoolean("marked_" + slot.getName()));
            stabImmunityUntil.put(slot, diamondTag.getLong("immunity_" + slot.getName()));
        }

        emeraldLevel = tag.getInt("emeraldLevel");
        emeraldExpiry = tag.getLong("emeraldExpiry");
        stunned = tag.getBoolean("stunned");
        stunnedUntil = tag.getLong("stunnedUntil");
        armorReductionUntil = tag.getLong("armorReductionUntil");
        brushingTicks = tag.getInt("brushingTicks");
    }

    public NbtCompound write(NbtCompound tag) {
        NbtCompound bleedTag = new NbtCompound();
        for (var entry : bleedStacks.entrySet()) bleedTag.putInt(entry.getKey().getName(), entry.getValue());
        tag.put("bleed", bleedTag);

        NbtCompound fractureTag = new NbtCompound();
        for (var entry : fractureStacks.entrySet()) fractureTag.putInt("stack_" + entry.getKey().getName(), entry.getValue());
        for (var entry : fracturedExpiry.entrySet()) fractureTag.putLong("expiry_" + entry.getKey().getName(), entry.getValue());
        tag.put("fracture", fractureTag);
        tag.putLong("crystalizedImmunity", crystalizedImmunityUntil);

        NbtCompound diamondTag = new NbtCompound();
        for (var entry : diamondMarked.entrySet()) diamondTag.putBoolean("marked_" + entry.getKey().getName(), entry.getValue());
        for (var entry : stabImmunityUntil.entrySet()) diamondTag.putLong("immunity_" + entry.getKey().getName(), entry.getValue());
        tag.put("diamond", diamondTag);

        tag.putInt("emeraldLevel", emeraldLevel);
        tag.putLong("emeraldExpiry", emeraldExpiry);
        tag.putBoolean("stunned", stunned);
        tag.putLong("stunnedUntil", stunnedUntil);
        tag.putLong("armorReductionUntil", armorReductionUntil);
        tag.putInt("brushingTicks", brushingTicks);
        return tag;
    }

    // === Diamond Stab ===
    public void markDiamond(EquipmentSlot slot) { diamondMarked.put(slot, true); }
    public boolean isDiamondMarked(EquipmentSlot slot) { return diamondMarked.getOrDefault(slot, false); }
    public void clearDiamondMark(EquipmentSlot slot) { diamondMarked.put(slot, false); }
    public boolean isStabImmune(EquipmentSlot slot, long worldTime) { return worldTime < stabImmunityUntil.getOrDefault(slot, 0L); }
    public void setStabImmunity(EquipmentSlot slot, long worldTime, int durationTicks) { stabImmunityUntil.put(slot, worldTime + durationTicks); }
    public void clearDiamond() { diamondMarked.clear(); }

    public EquipmentSlot getNextDiamondTarget() {
        EquipmentSlot[] order = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.FEET};
        for (EquipmentSlot slot : order) {
            if (!isDiamondMarked(slot)) return slot;
        }
        return null;
    }

    // === Iron Bleed ===
    public void addBleed(EquipmentSlot slot, int amount) { bleedStacks.put(slot, Math.min(6, bleedStacks.getOrDefault(slot, 0) + amount)); }
    public int getBleed(EquipmentSlot slot) { return bleedStacks.getOrDefault(slot, 0); }
    public int getTotalBleed() { return bleedStacks.values().stream().mapToInt(i -> i).sum(); }
    public void decayBleed() { bleedStacks.replaceAll((s, v) -> Math.max(0, v - 1)); }
    public void clearBleed() { bleedStacks.clear(); }

    // === Amethyst Fracture ===
    public void addFracture(EquipmentSlot slot, int amount, long worldTime) {
        fractureStacks.put(slot, Math.min(6, fractureStacks.getOrDefault(slot, 0) + amount));
        if (fractureStacks.get(slot) >= 4) {
            setFractured(slot, worldTime, 2400); // 2 minutes (2400 ticks)
        }
    }
    public int getFracture(EquipmentSlot slot) { return fractureStacks.getOrDefault(slot, 0); }
    public boolean isFractured(EquipmentSlot slot, long worldTime) { return worldTime < fracturedExpiry.getOrDefault(slot, 0L); }
    public void setFractured(EquipmentSlot slot, long worldTime, long durationTicks) { fracturedExpiry.put(slot, worldTime + durationTicks); }
    public int countFractured(long worldTime) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            if (isFractured(slot, worldTime)) count++;
        }
        return count;
    }
    public void clearFracture() { fractureStacks.clear(); fracturedExpiry.clear(); }
    public boolean hasAnyFractureStack() { return fractureStacks.values().stream().anyMatch(i -> i > 0); }
    public boolean hasAnyActiveFracture(long worldTime) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            if (isFractured(slot, worldTime)) return true;
        }
        return false;
    }
    public void decayFracture(long worldTime) {
        fractureStacks.replaceAll((s, v) -> Math.max(0, v - 1));
        // Note: active fractures expire naturally via the timer fracturedExpiry.
    }

    // === Crystalized ===
    public boolean isCrystalizedImmune(long worldTime) { return worldTime < crystalizedImmunityUntil; }
    public void setCrystalizedImmunity(long worldTime, int durationTicks) { crystalizedImmunityUntil = worldTime + durationTicks; }

    // === Emerald ===
    public void addEmeraldHit(long worldTime) { emeraldLevel = Math.min(3, emeraldLevel + 1); emeraldExpiry = worldTime + 9600; } // 8 minutes
    public int getEmeraldLevel(long worldTime) { if (worldTime > emeraldExpiry) emeraldLevel = 0; return emeraldLevel; }
    public boolean hasEmeraldMark(long worldTime) { return getEmeraldLevel(worldTime) > 0; }

    // === Stun ===
    public boolean isStunned(long worldTime) { if (stunned && worldTime > stunnedUntil) stunned = false; return stunned; }
    public void stun(long worldTime, int durationTicks) { stunned = true; stunnedUntil = worldTime + durationTicks; }
    public boolean isArmorReduced(long worldTime) { return worldTime < armorReductionUntil; }
    public void setArmorReduced(long worldTime, int durationTicks) { armorReductionUntil = worldTime + durationTicks; }

    // === Brush Timer ===
    public int getBrushingTicks() { return brushingTicks; }
    public void setBrushingTicks(int ticks) { this.brushingTicks = ticks; }
    public void incrementBrushingTicks() { this.brushingTicks++; }

    // === Prioritized Mark Cleansing ===
    public boolean cleanseOneMark(long worldTime) {
        // 1. Diamond mark
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            if (isDiamondMarked(slot)) {
                clearDiamondMark(slot);
                return true;
            }
        }

        // 2. Emerald mark
        if (hasEmeraldMark(worldTime)) {
            emeraldLevel = 0;
            emeraldExpiry = 0;
            return true;
        }

        // 3. Bleed stacks (cleanses one slot)
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            if (getBleed(slot) > 0) {
                bleedStacks.put(slot, 0);
                return true;
            }
        }

        // 4. Fracture stacks and active fractures (cleanses one slot)
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            if (getFracture(slot) > 0 || isFractured(slot, worldTime)) {
                fractureStacks.put(slot, 0);
                fracturedExpiry.put(slot, 0L);
                return true;
            }
        }

        return false;
    }
}
