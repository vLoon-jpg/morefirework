package com.morefirework.effect;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import com.morefirework.item.OreFireworkItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

/**
 * Seeker logic for Redstone-addon rockets.
 * Handles homing, acceleration curve, turning radius, and target timeout.
 */
public class SeekerBehavior {

    // Speed curve: starts slow, speeds up
    private static final double INITIAL_SPEED = 2.0;
    private static final double ACCELERATION = 4.0;  // per second
    private static final double MAX_SPEED = 35.0;

    // Target acquisition
    private static final double SEEK_RANGE = 50.0;
    private static final int LOST_TARGET_TICKS = 8 * 20; // 8 seconds

    /**
     * Called every tick on a firework rocket entity.
     * Returns true if the rocket is a seeker and should handle homing.
     */
    public static boolean tick(FireworkRocketEntity rocket) {
        if (rocket.getWorld().isClient) return false;

        // Check if this is a seeker rocket
        if (!(rocket.getStack().getItem() instanceof OreFireworkItem)) return false;
        if (!OreFireworkItem.hasRedstone(rocket.getStack())) return false;

        World world = rocket.getWorld();
        long worldTime = world.getTime();

        // Track flight time via static seeker data tracker
        SeekerData data = SeekerData.getOrCreate(rocket);
        data.flightTicks++;

        // Calculate turning rate which degrades over time (harder to turn)
        // Starts at 20 degrees/tick, drops to 2 degrees/tick by 9s
        double turnRate = Math.max(Math.toRadians(2), Math.toRadians(20) - (data.flightTicks / 20.0) * Math.toRadians(2));

        // Find nearest Emerald-marked target in our vision cone with line-of-sight
        LivingEntity activeTarget = findTargetInVisionCone(world, rocket);

        if (activeTarget != null) {
            // We have a direct lock-on target!
            data.lastTargetId = activeTarget.getId();
            data.lastTargetPos = activeTarget.getPos().add(0, activeTarget.getHeight() / 2, 0);
            data.lostTicks = 0;

            double speed = Math.min(MAX_SPEED, INITIAL_SPEED + (data.flightTicks / 20.0) * ACCELERATION);
            
            // Turn rate multiplier based on Emerald mark level
            int emeraldLevel = ModComponents.get(activeTarget).getEmeraldLevel(worldTime);
            double currentTurnRate = turnRate * emeraldMultiplier(emeraldLevel);

            Vec3d currentDir = rocket.getVelocity().normalize();
            Vec3d targetCenterPos = activeTarget.getPos().add(0, activeTarget.getHeight() / 2, 0);
            Vec3d toTarget = targetCenterPos.subtract(rocket.getPos()).normalize();

            // Smoothly rotate current direction toward target
            Vec3d newDir = slerp(currentDir, toTarget, currentTurnRate);
            rocket.setVelocity(newDir.multiply(speed));
        } else {
            // Direct lock lost — check if we can track the last known position/direction of the target
            Entity lastTarget = null;
            if (data.lastTargetId != -1) {
                lastTarget = world.getEntityById(data.lastTargetId);
            }

            if (lastTarget instanceof LivingEntity && lastTarget.isAlive() && data.lastTargetPos != null) {
                // Smart prediction: update last target position if they are still alive
                data.lastTargetPos = lastTarget.getPos().add(0, lastTarget.getHeight() / 2, 0);
                
                double speed = Math.min(MAX_SPEED, INITIAL_SPEED + (data.flightTicks / 20.0) * ACCELERATION);
                
                Vec3d currentDir = rocket.getVelocity().normalize();
                Vec3d toTarget = data.lastTargetPos.subtract(rocket.getPos()).normalize();

                // Steer towards last known position
                Vec3d newDir = slerp(currentDir, toTarget, turnRate);
                rocket.setVelocity(newDir.multiply(speed));
            }

            // Increment lost timer
            data.lostTicks++;
            if (data.lostTicks >= LOST_TARGET_TICKS) {
                SeekerData.remove(rocket);
                rocket.discard();
                // Visual self-destruct puff
                world.createExplosion(null, rocket.getX(), rocket.getY(), rocket.getZ(),
                    0f, false, World.ExplosionSourceType.NONE);
            }
        }

        return true;
    }

    private static LivingEntity findTargetInVisionCone(World world, FireworkRocketEntity rocket) {
        // Homing starts after a brief startup delay of 10 ticks (0.5s) to allow launching
        if (SeekerData.getOrCreate(rocket).flightTicks < 10) return null;

        Box searchBox = new Box(rocket.getPos().subtract(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE),
            rocket.getPos().add(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE));

        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, searchBox,
            e -> {
                if (e == rocket.getOwner()) return false;
                if (e.isDead()) return false;
                
                FireworkEffectComponent fx = ModComponents.get(e);
                if (!fx.hasEmeraldMark(e.getWorld().getTime())) return false;

                // Check Line of Sight
                if (!canSee(world, rocket, e)) return false;

                // Check if target is in 90-degree forward vision cone (45-degree half-angle)
                Vec3d forward = rocket.getVelocity().normalize();
                Vec3d toTarget = e.getPos().add(0, e.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                double dot = forward.dotProduct(toTarget);
                double angle = Math.acos(Math.min(1.0, Math.max(-1.0, dot)));
                return angle <= Math.toRadians(45); // 90-degree vision cone
            });

        if (candidates.isEmpty()) return null;

        // Lock on to target nearest to the center of the cone (largest dot product)
        Vec3d forward = rocket.getVelocity().normalize();
        return candidates.stream()
            .max((e1, e2) -> {
                Vec3d toE1 = e1.getPos().add(0, e1.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                Vec3d toE2 = e2.getPos().add(0, e2.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                return Double.compare(forward.dotProduct(toE1), forward.dotProduct(toE2));
            })
            .orElse(null);
    }

    private static boolean canSee(World world, Entity source, Entity target) {
        Vec3d start = source.getPos();
        Vec3d end = target.getPos().add(0, target.getHeight() / 2, 0);
        BlockHitResult hit = world.raycast(new RaycastContext(
            start, end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            source
        ));
        return hit.getType() == BlockHitResult.Type.MISS;
    }

    private static double emeraldMultiplier(int level) {
        return switch (level) {
            case 2 -> 1.5;
            case 3 -> 2.0;
            default -> 1.0;
        };
    }

    /**
     * Spherical linear interpolation between two direction vectors.
     * Turns from a toward b by at most maxAngle radians.
     */
    private static Vec3d slerp(Vec3d a, Vec3d b, double maxAngle) {
        double dot = a.dotProduct(b);
        dot = Math.max(-1, Math.min(1, dot));
        double angle = Math.acos(dot);

        if (angle < 1e-6 || angle < maxAngle) return b;

        double ratio = maxAngle / angle;
        double sinAngle = Math.sin(angle);
        double s0 = Math.sin((1 - ratio) * angle) / sinAngle;
        double s1 = Math.sin(ratio * angle) / sinAngle;

        return a.multiply(s0).add(b.multiply(s1));
    }

    // === Entity persistent data ===

    public static class SeekerData {
        public int flightTicks = 0;
        public int lostTicks = 0;
        public int lastTargetId = -1;
        public Vec3d lastTargetPos = null;
        private static final java.util.Map<Integer, SeekerData> TRACKER = new java.util.concurrent.ConcurrentHashMap<>();

        public static SeekerData getOrCreate(FireworkRocketEntity rocket) {
            return TRACKER.computeIfAbsent(rocket.getId(), id -> new SeekerData());
        }

        public static void remove(FireworkRocketEntity rocket) {
            TRACKER.remove(rocket.getId());
        }
    }
}
