package com.morefirework.effect;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import com.morefirework.item.OreFireworkItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

/**
 * Seeker logic for Redstone-addon rockets.
 * Handles homing, acceleration curve, turning radius, and target timeout.
 */
public class SeekerBehavior {

    // Speed curve
    private static final double INITIAL_SPEED = 8.0;
    private static final double ACCELERATION = 2.0;  // per second
    private static final double MAX_SPEED = 35.0;

    // Turning: radians per tick (20 ticks/sec)
    private static final double TURN_LOW_SPEED = Math.PI / 4;        // 45°/tick  (< 15 b/s)
    private static final double TURN_MID_SPEED = Math.PI / 12;        // 15°/tick  (15-25 b/s)
    private static final double TURN_HIGH_SPEED = Math.PI / 36;       // 5°/tick   (> 25 b/s)

    // Emerald homing multipliers
    private static final double EMERALD_L2_MULT = 1.5;
    private static final double EMERALD_L3_MULT = 2.0;

    // Target acquisition
    private static final double SEEK_RANGE = 50.0;
    private static final double LOCK_RANGE = 100.0;
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

        // Track flight time via persistent NBT on the entity
        int flightTicks = getFlightTicks(rocket);
        flightTicks++;
        setFlightTicks(rocket, flightTicks);

        World world = rocket.getWorld();

        // Find nearest Emerald-marked target
        LivingEntity target = findTarget(world, rocket, flightTicks);
        long worldTime = world.getTime();

        if (target != null) {
            // Homing
            double speed = Math.min(MAX_SPEED, INITIAL_SPEED + (flightTicks / 20.0) * ACCELERATION);
            double turnRate = getTurnRate(speed);
            int emeraldLevel = getTargetEmeraldLevel(target, worldTime);
            turnRate *= emeraldMultiplier(emeraldLevel);

            Vec3d currentDir = rocket.getVelocity().normalize();
            Vec3d toTarget = target.getPos().add(0, target.getHeight() / 2, 0)
                .subtract(rocket.getPos()).normalize();

            // Smoothly rotate current direction toward target
            Vec3d newDir = slerp(currentDir, toTarget, turnRate);
            rocket.setVelocity(newDir.multiply(speed));

            // Reset lost timer
            setLostTicks(rocket, 0);
        } else {
            // No target — check timeout
            int lostTicks = getLostTicks(rocket) + 1;
            setLostTicks(rocket, lostTicks);
            if (lostTicks >= LOST_TARGET_TICKS) {
                SeekerData.remove(rocket);
                rocket.discard();
                // Visual puff
                world.createExplosion(null, rocket.getX(), rocket.getY(), rocket.getZ(),
                    0f, false, World.ExplosionSourceType.NONE);
            }
        }

        return true;
    }

    private static LivingEntity findTarget(World world, FireworkRocketEntity rocket, int flightTicks) {
        if (flightTicks < 40) return null; // 2-second startup before seeking

        Box searchBox = new Box(rocket.getPos().subtract(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE),
            rocket.getPos().add(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE));

        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, searchBox,
            e -> {
                if (e == rocket.getOwner()) return false;
                if (e.isDead()) return false;
                FireworkEffectComponent fx = ModComponents.get(e);
                return fx.hasEmeraldMark(e.getWorld().getTime());
            });

        if (candidates.isEmpty()) return null;

        // Pick closest
        return candidates.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(rocket)))
            .orElse(null);
    }

    private static int getTargetEmeraldLevel(LivingEntity target, long worldTime) {
        return ModComponents.get(target).getEmeraldLevel(worldTime);
    }

    private static double getTurnRate(double speed) {
        if (speed < 15) return TURN_LOW_SPEED;
        if (speed < 25) return TURN_MID_SPEED;
        return TURN_HIGH_SPEED;
    }

    private static double emeraldMultiplier(int level) {
        return switch (level) {
            case 2 -> EMERALD_L2_MULT;
            case 3 -> EMERALD_L3_MULT;
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

    // === Entity persistent data (stored as NBT on the firework entity) ===

    private static final String KEY_FLIGHT_TICKS = "MoreFireworkFlightTicks";
    private static final String KEY_LOST_TICKS = "MoreFireworkLostTicks";

    private static int getFlightTicks(FireworkRocketEntity rocket) {
        // Use the rocket's NBT to track flight time across ticks.
        // In practice, store in the Entity NBT or a static map.
        // For simplicity, we track via a static map keyed by entity ID.
        return SeekerData.getOrCreate(rocket).flightTicks;
    }

    private static void setFlightTicks(FireworkRocketEntity rocket, int ticks) {
        SeekerData.getOrCreate(rocket).flightTicks = ticks;
    }

    private static int getLostTicks(FireworkRocketEntity rocket) {
        return SeekerData.getOrCreate(rocket).lostTicks;
    }

    private static void setLostTicks(FireworkRocketEntity rocket, int ticks) {
        SeekerData.getOrCreate(rocket).lostTicks = ticks;
    }

    /**
     * Simple mutable data holder keyed by entity ID (not UUID — entity IDs recycle).
     */
    static class SeekerData {
        int flightTicks = 0;
        int lostTicks = 0;
        private static final java.util.Map<Integer, SeekerData> TRACKER = new java.util.concurrent.ConcurrentHashMap<>();

        static SeekerData getOrCreate(FireworkRocketEntity rocket) {
            return TRACKER.computeIfAbsent(rocket.getId(), id -> new SeekerData());
        }

        static void remove(FireworkRocketEntity rocket) {
            TRACKER.remove(rocket.getId());
        }
    }
}
