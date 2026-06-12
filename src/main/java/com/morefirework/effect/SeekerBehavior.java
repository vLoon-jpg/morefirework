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

    // Speed curve
    private static final double INITIAL_SPEED = 0.13;  // blocks/tick — player walking speed
    private static final double LOCKED_MAX_SPEED = 0.6; // blocks/tick — light jog when locked
    private static final double ACCELERATION = 0.1;    // blocks/tick per second when locked
    private static final double DECELERATION = 0.05;   // blocks/tick per second when lock lost
    private static final double MAX_SPEED = 0.6;

    // Turn rate curve (inverse of speed — faster = less agile)
    private static final double TURN_RATE_HUNTING = Math.toRadians(15); // slow, tight turns when no lock
    private static final double TURN_RATE_LOCKED_MIN = Math.toRadians(3); // committed fast turn when locked

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

        // --- Speed and turn rate ---
        // Current speed: read from actual velocity magnitude so acceleration/decel is smooth
        double currentSpeed = rocket.getVelocity().length();
        // Clamp to INITIAL_SPEED range so crossbow launch velocity doesn't bleed through
        currentSpeed = Math.min(MAX_SPEED, Math.max(INITIAL_SPEED, currentSpeed));
        // On first tick, force walking pace regardless of launch velocity
        if (data.flightTicks == 1) {
            Vec3d dir = rocket.getVelocity().normalize();
            if (dir.lengthSquared() < 0.001) dir = new Vec3d(0, 0, 1);
            rocket.setVelocity(dir.multiply(INITIAL_SPEED));
            currentSpeed = INITIAL_SPEED;
        }

        // Spawn menacing/sinister particle trail
        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            double rx = rocket.getX();
            double ry = rocket.getY() + 0.15;
            double rz = rocket.getZ();

            // Ominous trial spawner detection particles
            serverWorld.spawnParticles(
                net.minecraft.particle.ParticleTypes.TRIAL_SPAWNER_DETECTION_OMINOUS,
                rx, ry, rz,
                2,
                0.1, 0.1, 0.1,
                0.02
            );

            // Angry Villager (stormy red crosses)
            if (data.flightTicks % 2 == 0) {
                serverWorld.spawnParticles(
                    net.minecraft.particle.ParticleTypes.ANGRY_VILLAGER,
                    rx, ry, rz,
                    1,
                    0.2, 0.2, 0.2,
                    0.0
                );
            }

            // Soul Fire Flame (sinister blue-green jet flame)
            serverWorld.spawnParticles(
                net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                rx, ry, rz,
                1,
                0.05, 0.05, 0.05,
                0.01
            );
        }

        // Find nearest Emerald-marked target in our vision cone with line-of-sight
        LivingEntity activeTarget = findTargetInVisionCone(world, rocket);

        if (activeTarget != null) {
            // We have a direct lock-on target!
            boolean justLockedOn = !data.wasLockedOn;
            data.wasLockedOn = true;
            data.lastTargetId = activeTarget.getId();
            data.lastTargetPos = activeTarget.getPos().add(0, activeTarget.getHeight() / 2, 0);
            data.lostTicks = 0;

            // Locked on: accelerate toward LOCKED_MAX_SPEED, reduce turn rate as speed climbs
            // Turn rate inversely scales with speed: fast = committed, can't snap turn
            int emeraldLevel = ModComponents.get(activeTarget).getEmeraldLevel(worldTime);
            double newSpeed = Math.min(LOCKED_MAX_SPEED, currentSpeed + ACCELERATION / 20.0);
            // Turn rate: lerp from HUNTING (slow) to LOCKED_MIN (fast) based on speed fraction
            double speedFraction = Math.min(1.0, newSpeed / LOCKED_MAX_SPEED);
            double turnRate = TURN_RATE_HUNTING - (TURN_RATE_HUNTING - TURN_RATE_LOCKED_MIN) * speedFraction;
            turnRate *= emeraldMultiplier(emeraldLevel);

            Vec3d currentDir = rocket.getVelocity().normalize();
            Vec3d targetCenterPos = activeTarget.getPos().add(0, activeTarget.getHeight() / 2, 0);
            Vec3d toTarget = targetCenterPos.subtract(rocket.getPos()).normalize();

            // Play lock-on warning sounds at the target player
            if (justLockedOn) {
                world.playSound(null, activeTarget.getX(), activeTarget.getY(), activeTarget.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_TRIAL_SPAWNER_DETECT_PLAYER, net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 0.8f);

                // Play lock-on confirmation sound for the attacker (shooter)
                net.minecraft.entity.Entity owner = rocket.getOwner();
                if (owner instanceof net.minecraft.entity.player.PlayerEntity playerOwner) {
                    playerOwner.getWorld().playSound(null, playerOwner.getX(), playerOwner.getY(), playerOwner.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_TRIAL_SPAWNER_DETECT_PLAYER, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.4f);
                }
            }

            // Dynamic homing warning beep that speeds up as the rocket gets closer
            double dist = rocket.getPos().distanceTo(activeTarget.getPos());
            int beepInterval = dist > 30 ? 20 : (dist > 15 ? 10 : (dist > 5 ? 5 : 2));
            if (data.flightTicks % beepInterval == 0) {
                world.playSound(null, activeTarget.getX(), activeTarget.getY(), activeTarget.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING, net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.8f);
            }

            // Extra dragon breath trails during active lock-on homing
            if (world instanceof net.minecraft.server.world.ServerWorld serverWorld && data.flightTicks % 3 == 0) {
                serverWorld.spawnParticles(
                    net.minecraft.particle.ParticleTypes.DRAGON_BREATH,
                    rocket.getX(), rocket.getY() + 0.15, rocket.getZ(),
                    3,
                    0.1, 0.1, 0.1,
                    0.02
                );
            }

            // Smoothly rotate current direction toward target
            Vec3d newDir = slerp(currentDir, toTarget, turnRate);
            rocket.setVelocity(newDir.multiply(newSpeed));
        } else {
            data.wasLockedOn = false;
            // Direct lock lost — check if we can track the last known position/direction of the target
            Entity lastTarget = null;
            if (data.lastTargetId != -1) {
                lastTarget = world.getEntityById(data.lastTargetId);
            }

            if (lastTarget instanceof LivingEntity && lastTarget.isAlive() && data.lastTargetPos != null) {
                // Smart prediction: update last target position if they are still alive
                data.lastTargetPos = lastTarget.getPos().add(0, lastTarget.getHeight() / 2, 0);

                Vec3d currentDir = rocket.getVelocity().normalize();
                Vec3d toTarget = data.lastTargetPos.subtract(rocket.getPos()).normalize();

                // Lost lock but still chasing: decelerate and ramp turn rate back up step-by-step
                // Turn rate increases as speed drops — the slower it gets the tighter it can turn
                double newSpeed = Math.max(INITIAL_SPEED, currentSpeed - DECELERATION / 20.0);
                double speedFraction = Math.min(1.0, newSpeed / LOCKED_MAX_SPEED);
                double turnRate = TURN_RATE_HUNTING - (TURN_RATE_HUNTING - TURN_RATE_LOCKED_MIN) * speedFraction;

                // Steer towards last known position — reset lost timer, we still have a chase target
                Vec3d newDir = slerp(currentDir, toTarget, turnRate);
                rocket.setVelocity(newDir.multiply(newSpeed));
                data.lostTicks = 0; // still chasing, don't count down
            } else {
                // Truly lost — no lock and no living target to chase. Count down to self-destruct.
                data.lostTicks++;
                if (data.lostTicks >= LOST_TARGET_TICKS) {
                    SeekerData.remove(rocket);
                    rocket.discard();
                    // Visual self-destruct puff
                    world.createExplosion(null, rocket.getX(), rocket.getY(), rocket.getZ(),
                        0f, false, World.ExplosionSourceType.NONE);
                }
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
                if (e.isDead()) return false;

                // Check Line of Sight
                if (!canSee(world, rocket, e)) return false;

                // Check if target is in 90-degree forward vision cone (45-degree half-angle)
                // Emerald seeker locks onto anything; all others require heat signature
                OreFireworkItem.OreType rocketType = OreFireworkItem.getOreType(rocket.getStack());
                boolean needsHeat = rocketType != OreFireworkItem.OreType.EMERALD;
                if (needsHeat) {
                    FireworkEffectComponent fx = ModComponents.get(e);
                    if (!fx.hasEmeraldMark(e.getWorld().getTime())) return false;
                }

                Vec3d forward = rocket.getVelocity().normalize();
                Vec3d toTarget = e.getPos().add(0, e.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                double dot = forward.dotProduct(toTarget);
                double angle = Math.acos(Math.min(1.0, Math.max(-1.0, dot)));
                return angle <= Math.toRadians(60); // 120-degree vision cone
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
        public boolean wasLockedOn = false;
        private static final java.util.Map<Integer, SeekerData> TRACKER = new java.util.concurrent.ConcurrentHashMap<>();

        public static SeekerData getOrCreate(FireworkRocketEntity rocket) {
            return TRACKER.computeIfAbsent(rocket.getId(), id -> new SeekerData());
        }

        public static void remove(FireworkRocketEntity rocket) {
            TRACKER.remove(rocket.getId());
        }
    }
}
