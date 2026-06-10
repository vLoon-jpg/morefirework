package com.morefirework.mixin;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import com.morefirework.effect.GoldShotgun;
import com.morefirework.effect.SeekerBehavior;
import com.morefirework.item.OreFireworkItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {
    private static final Logger LOG = LoggerFactory.getLogger("morefirework:rocket");

    // Track entities directly hit by THIS rocket so they don't get double effects
    private final Set<UUID> directlyHitEntities = new HashSet<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void morefirework$seekerTick(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        ItemStack stack = self.getStack();
        if (OreFireworkItem.hasRedstone(stack)) {
            LOG.debug("Seeker tick — entity={}, pos=({},{},{})", 
                self.getId(), (int)self.getX(), (int)self.getY(), (int)self.getZ());
        }
        SeekerBehavior.tick(self);
    }

    /** Direct entity collision — apply ore effect */
    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void morefirework$onEntityHit(EntityHitResult hitResult, CallbackInfo ci) {
        Entity target = hitResult.getEntity();
        if (!(target instanceof LivingEntity livingTarget)) return;
        if (livingTarget.getWorld().isClient) return;

        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        ItemStack rocket = self.getStack();
        if (!(rocket.getItem() instanceof OreFireworkItem oreItem)) return;

        // Track this entity so the AOE explosion doesn't hit it again
        directlyHitEntities.add(target.getUuid());

        applyOreEffect(livingTarget, oreItem.getOreType(), self);
    }

    /**
     * Hook AFTER the vanilla explosion to apply AOE effects.
     * Fireworks that expire mid-flight or hit blocks explode without
     * triggering onEntityHit — this catches those cases.
     */
    @Inject(method = "explode", at = @At("TAIL"))
    private void morefirework$explodeAoeEffect(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        ItemStack rocket = self.getStack();
        if (!(rocket.getItem() instanceof OreFireworkItem oreItem)) return;
        if (self.getWorld().isClient) return;

        OreFireworkItem.OreType type = oreItem.getOreType();
        var explosions = rocket.get(net.minecraft.component.DataComponentTypes.FIREWORKS);
        float radius = (explosions != null ? explosions.explosions().size() * 2f : 0f) + 5f;

        // Gold shotgun is handled here — explosion AOE
        if (type == OreFireworkItem.OreType.GOLD) {
            Vec3d dir = self.getVelocity();
            if (dir.lengthSquared() < 0.001) {
                dir = new Vec3d(0, 1, 0); // default upward if stationary
            }
            GoldShotgun.shatter(
                self.getWorld(),
                self.getPos(),
                dir.normalize(),
                self.getOwner() != null ? self.getOwner() : self
            );
            return;
        }

        Box area = new Box(
            self.getX() - radius, self.getY() - radius, self.getZ() - radius,
            self.getX() + radius, self.getY() + radius, self.getZ() + radius
        );

        for (Entity entity : self.getWorld().getOtherEntities(self.getOwner(), area)) {
            if (!(entity instanceof LivingEntity livingTarget)) continue;
            if (livingTarget.getWorld().isClient) continue;

            // Skip entities that were already hit directly by onEntityHit
            if (directlyHitEntities.remove(livingTarget.getUuid())) continue;

            double dist = livingTarget.getPos().distanceTo(self.getPos());
            if (dist > radius) continue;

            applyOreEffect(livingTarget, type, self);

            // AOE knockback
            Vec3d knockback = livingTarget.getPos().subtract(self.getPos()).normalize().multiply(0.75);
            livingTarget.addVelocity(knockback.x, 0.2, knockback.z);
            livingTarget.velocityModified = true;
        }
    }

    private static void applyOreEffect(LivingEntity target, OreFireworkItem.OreType type, FireworkRocketEntity self) {
        FireworkEffectComponent fx = ModComponents.get(target);
        long worldTime = target.getWorld().getTime();
        Random random = new Random();

        LOG.info("ROCKET EFFECT — type={}, target={}, pos=({},{},{})",
            type, target.getName().getString(),
            (int)target.getX(), (int)target.getY(), (int)target.getZ());

        switch (type) {
            case DIAMOND -> {
                EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET};
                EquipmentSlot marked = armorSlots[random.nextInt(4)];
                fx.markDiamond(marked);
                LOG.info("  DIAMOND — marked: {}", marked.getName());
            }
            case IRON -> {
                EquipmentSlot[] slots = shuffledArmorSlots(random);
                for (int i = 0; i < 4; i++) fx.addBleed(slots[i % slots.length], 1);
                LOG.info("  IRON — bleed stacks: {}", fx.getTotalBleed());
            }
            case AMETHYST -> {
                if (fx.isCrystalizedImmune(worldTime)) break;
                for (int i = 0; i < 2; i++) {
                    EquipmentSlot slot = rollFractureSlot(random);
                    fx.addFracture(slot, 1);
                    if (fx.getFracture(slot) >= 4) fx.setFractured(slot, true);
                }
                LOG.info("  AMETHYST — fractured pieces: {}", fx.countFractured());
            }
            case EMERALD -> {
                fx.addEmeraldHit(worldTime);
                LOG.info("  EMERALD — heat level: {}", fx.getEmeraldLevel(worldTime));
            }
            case GOLD -> {
                // Direct hit — shrapnel cone from impact toward target
                Vec3d dir = target.getPos().subtract(self.getPos());
                if (dir.lengthSquared() < 0.001) dir = new Vec3d(0, 1, 0);
                GoldShotgun.shatter(target.getWorld(), target.getPos(), dir.normalize(), 
                    self.getOwner() != null ? self.getOwner() : self);
            }
        }
    }

    private static EquipmentSlot[] shuffledArmorSlots(Random rng) {
        EquipmentSlot[] all = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = all.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            EquipmentSlot tmp = all[i]; all[i] = all[j]; all[j] = tmp;
        }
        return all;
    }

    private static EquipmentSlot rollFractureSlot(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 15) return EquipmentSlot.HEAD;
        if (roll < 55) return EquipmentSlot.CHEST;
        if (roll < 90) return EquipmentSlot.LEGS;
        return EquipmentSlot.FEET;
    }
}
