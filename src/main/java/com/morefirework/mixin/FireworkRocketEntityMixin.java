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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
    private static final Logger LOG = LoggerFactory.getLogger("morefirework:rocket");

    @Shadow
    private void explode() {}

    // One-shot flag: Gold proxy fuse must only trigger once per rocket lifetime
    private boolean hasExploded = false;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void morefirework$seekerTick(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        ItemStack stack = self.getStack();
        if (OreFireworkItem.hasRedstone(stack)) {
            LOG.debug("Seeker tick — entity={}, pos=({},{},{})", 
                self.getId(), (int)self.getX(), (int)self.getY(), (int)self.getZ());
        }

        // Gold proxy fuse: check if within 3.5 blocks of any target — ONE SHOT ONLY
        if (!hasExploded && stack.getItem() instanceof OreFireworkItem oreItem && oreItem.getOreType() == OreFireworkItem.OreType.GOLD) {
            if (!self.getWorld().isClient) {
                Box box = self.getBoundingBox().expand(3.5);
                List<LivingEntity> targets = self.getWorld().getEntitiesByClass(LivingEntity.class, box,
                    e -> e != self.getOwner() && e.isAlive());
                if (!targets.isEmpty()) {
                    hasExploded = true;
                    this.explode();
                    ci.cancel();
                    return;
                }
            }
        }

        SeekerBehavior.tick(self);
    }

    /**
     * Hook AFTER the vanilla explosion to apply AOE effects.
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
                // Target sequential pattern: CHEST -> LEGS -> HEAD -> FEET
                EquipmentSlot nextSlot = fx.getNextDiamondTarget();
                if (nextSlot != null) {
                    fx.markDiamond(nextSlot);
                    LOG.info("  DIAMOND — marked slot: {}", nextSlot.getName());
                } else {
                    LOG.info("  DIAMOND — all slots already marked");
                }
            }
            case IRON -> {
                // Apply 4 stacks unevenly: CHEST (highest) -> LEGS -> HEAD -> FEET (lowest)
                applyWeightedStack(fx, "IRON", worldTime, random, 4);
                LOG.info("  IRON — bleed stacks: {}", fx.getTotalBleed());
            }
            case AMETHYST -> {
                if (fx.isCrystalizedImmune(worldTime)) break;
                // Apply 6 stacks unevenly: CHEST (highest) -> LEGS -> HEAD -> FEET (lowest)
                applyWeightedStack(fx, "AMETHYST", worldTime, random, 6);
                LOG.info("  AMETHYST — fractured slots active: {}", fx.countFractured(worldTime));
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

    private static void applyWeightedStack(FireworkEffectComponent fx, String type, long worldTime, Random random, int count) {
        // Weights: CHEST=40, LEGS=30, HEAD=20, FEET=10
        EquipmentSlot[] slots = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.FEET};
        int[] weights = {40, 30, 20, 10};

        for (int c = 0; c < count; c++) {
            List<Integer> eligibleIndices = new ArrayList<>();
            int totalWeight = 0;
            for (int i = 0; i < slots.length; i++) {
                int currentStacks = type.equals("IRON") ? fx.getBleed(slots[i]) : fx.getFracture(slots[i]);
                if (currentStacks < 6) {
                    eligibleIndices.add(i);
                    totalWeight += weights[i];
                }
            }

            if (totalWeight > 0) {
                int roll = random.nextInt(totalWeight);
                int cumulative = 0;
                for (int idx : eligibleIndices) {
                    cumulative += weights[idx];
                    if (cumulative > roll) {
                        EquipmentSlot chosen = slots[idx];
                        if (type.equals("IRON")) {
                            fx.addBleed(chosen, 1);
                        } else {
                            fx.addFracture(chosen, 1, worldTime);
                        }
                        break;
                    }
                }
            }
        }
    }
}
