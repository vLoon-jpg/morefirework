package com.morefirework.mixin;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import com.morefirework.effect.GoldShotgun;
import com.morefirework.effect.SeekerBehavior;
import com.morefirework.effect.SeekerBehavior.SeekerData;
import com.morefirework.item.OreFireworkItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext;
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

    @Shadow private void explode() {}

    /** Shadowable vanilla life counter so we can reset it for infinite-flight placed rockets. */
    @Shadow private int life;

    // One-shot flag: Gold proxy fuse must only trigger once per rocket lifetime
    private boolean hasExploded = false;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void morefirework$seekerTick(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        ItemStack stack = self.getStack();

        // Gold proxy fuse: SMART — if this is a seeker, close to within 1 block before detonating.
        // Non-seeker gold rockets use the original 3.5-block fuse.
        // Skip during launch phase.
        boolean inLaunchPhase = false;
        boolean isSeeker = OreFireworkItem.hasRedstone(stack);
        if (isSeeker) {
            SeekerData launchCheck = SeekerBehavior.SeekerData.getOrCreate(self);
            inLaunchPhase = launchCheck.placedOrDispensed && launchCheck.flightTicks < 30;
        }
        if (!inLaunchPhase && !hasExploded && stack.getItem() instanceof OreFireworkItem oreItem && oreItem.getOreType() == OreFireworkItem.OreType.GOLD) {
            if (!self.getWorld().isClient) {
                // Seeker gold: only detonate within 1 block for maximum damage
                // Non-seeker gold: original 3.5-block fuse
                double fuseRadius = isSeeker ? 1.0 : 3.5;
                Box box = self.getBoundingBox().expand(fuseRadius);
                List<LivingEntity> targets = self.getWorld().getEntitiesByClass(LivingEntity.class, box,
                    e -> e != self.getOwner() && e.isAlive());
                if (!targets.isEmpty()) {
                    hasExploded = true;
                    LivingEntity nearest = targets.stream()
                        .min(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(self)))
                        .orElse(targets.get(0));
                    Vec3d dir = nearest.getPos().add(0, nearest.getHeight() / 2, 0).subtract(self.getPos());
                    if (dir.lengthSquared() < 0.001) dir = self.getVelocity();
                    GoldShotgun.shatter(self.getWorld(), self.getPos(), dir.normalize(),
                        self.getOwner() != null ? self.getOwner() : self);
                    self.discard();
                    ci.cancel();
                    return;
                }
            }
        }

        // ALL seeker rockets (crossbow, placed, dispensed) need vanilla tick cancelled.
        // Vanilla FireworkRocketEntity.tick() overwrites velocity to the shooter's
        // movement speed AND repositions the rocket onto the shooter every tick.
        // We handle movement ourselves to keep SeekerBehavior velocity intact.
        if (OreFireworkItem.hasRedstone(stack)) {
            SeekerData data = SeekerBehavior.SeekerData.getOrCreate(self);

            // Reset vanilla life counter each tick — SeekerBehavior handles self-destruct via lostTicks
            this.life = 0;

            // Placed rockets: launch phase — fly straight up for 30 ticks before homing kicks in
            if (data.placedOrDispensed && data.flightTicks < 30) {
                Vec3d upVel = new Vec3d(0, 0.4, 0); // shoot up like a real firework
                self.setVelocity(upVel);
                Vec3d end2 = self.getPos().add(upVel);
                BlockHitResult bh2 = self.getWorld().raycast(new RaycastContext(
                    self.getPos(), end2,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, self));
                if (bh2.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    SeekerData.remove(self);
                    self.setPosition(bh2.getPos().x, bh2.getPos().y, bh2.getPos().z);
                    explode(); self.discard(); ci.cancel(); return;
                }
                self.setPosition(end2.x, end2.y, end2.z);
                data.flightTicks++; // manually tick so SeekerBehavior.tick() startup delay counts
                self.velocityDirty = true;
                ci.cancel();
                return;
            }

            // ALL seekers: run the homing behavior (speed/turn/pursuit)
            SeekerBehavior.tick(self);

            // Check entity collision — if touching assigned target, explode
            if (!self.getWorld().isClient) {
                Box hitBox = self.getBoundingBox().expand(0.5);
                List<LivingEntity> touching = self.getWorld().getEntitiesByClass(LivingEntity.class, hitBox,
                    e -> e.isAlive() && (data.assignedTargetId == -1 || e.getId() == data.assignedTargetId));
                if (!touching.isEmpty()) {
                    SeekerData.remove(self);
                    explode();
                    self.discard();
                    ci.cancel();
                    return;
                }
            }

            // Manual movement with block collision — cancels vanilla tick which would
            // overwrite our velocity with the shooter's movement speed
            Vec3d vel = self.getVelocity();
            Vec3d start = self.getPos();
            Vec3d end = start.add(vel);
            BlockHitResult blockHit = self.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                self
            ));
            if (blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                SeekerData.remove(self);
                Vec3d hp = blockHit.getPos();
                self.setPosition(hp.x, hp.y, hp.z);
                if (self.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                        hp.x, hp.y, hp.z, 1, 0, 0, 0, 0);
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                        hp.x, hp.y, hp.z, 8, 0.3, 0.3, 0.3, 0.05);
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                        hp.x, hp.y, hp.z, 12, 0.2, 0.2, 0.2, 0.08);
                }
                explode();
                self.discard();
                ci.cancel();
                return;
            }
            self.setPosition(end.x, end.y, end.z);
            self.velocityDirty = true;

            ci.cancel();
            return;
        }
    }

    // Helper to access SeekerData without a static import collision
    private static SeekerBehavior.SeekerData SeekerData(FireworkRocketEntity rocket) {
        return SeekerBehavior.SeekerData.getOrCreate(rocket);
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

        // Gold shotgun is handled by the proximity fuse above — skip here
        if (type == OreFireworkItem.OreType.GOLD) return;

        Box area = new Box(
            self.getX() - radius, self.getY() - radius, self.getZ() - radius,
            self.getX() + radius, self.getY() + radius, self.getZ() + radius
        );

        for (Entity entity : self.getWorld().getOtherEntities(null, area)) {
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
                EquipmentSlot nextSlot = fx.getNextDiamondTarget();
                if (nextSlot != null) {
                    fx.markDiamond(nextSlot);
                    LOG.info("  DIAMOND — marked slot: {}", nextSlot.getName());
                } else {
                    LOG.info("  DIAMOND — all slots already marked");
                }
            }
            case IRON -> {
                applyWeightedStack(fx, "IRON", worldTime, random, 4);
                LOG.info("  IRON — bleed stacks: {}", fx.getTotalBleed());
            }
            case AMETHYST -> {
                if (fx.isCrystalizedImmune(worldTime)) break;
                applyWeightedStack(fx, "AMETHYST", worldTime, random, 6);
                LOG.info("  AMETHYST — fractured slots active: {}", fx.countFractured(worldTime));
            }
            case EMERALD -> {
                fx.addEmeraldHit(worldTime);
                LOG.info("  EMERALD — heat level: {}", fx.getEmeraldLevel(worldTime));
            }
            case GOLD -> {
                Vec3d dir = target.getPos().subtract(self.getPos());
                if (dir.lengthSquared() < 0.001) dir = new Vec3d(0, 1, 0);
                GoldShotgun.shatter(target.getWorld(), target.getPos(), dir.normalize(),
                    self.getOwner() != null ? self.getOwner() : self);
            }
        }
    }

    private static void applyWeightedStack(FireworkEffectComponent fx, String type, long worldTime, Random random, int count) {
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
