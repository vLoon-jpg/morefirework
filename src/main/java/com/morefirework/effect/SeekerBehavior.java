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
    private static final double INITIAL_SPEED = 0.28;  // blocks/tick — player sprinting speed
    private static final double LOCKED_MAX_SPEED = 0.28; // blocks/tick — constant sprint
    private static final double ACCELERATION = 0.1;    // blocks/tick per second when locked
    private static final double DECELERATION = 0.05;   // blocks/tick per second when lock lost
    private static final double MAX_SPEED = 0.28;

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
            // If chasing a cold target, check if a hotter target entered the cone — switch if so.
            // If chasing a hot target, never abandon.
            int activeheat = ModComponents.get(activeTarget).getEmeraldLevel(worldTime);
            if (activeheat == 0) {
                // Cold target — check cone for a hot one
                LivingEntity hotterTarget = null;
                int maxHeat = 0;
                Box cone = new Box(rocket.getPos().subtract(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE),
                    rocket.getPos().add(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE));
                Vec3d fwd = rocket.getVelocity().normalize();
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, cone, e2 -> !e2.isDead() && canSee(world, rocket, e2))) {
                    Vec3d toE = e.getPos().add(0, e.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                    double ang = Math.acos(Math.min(1.0, Math.max(-1.0, fwd.dotProduct(toE))));
                    if (ang > Math.toRadians(60)) continue;
                    int heat = ModComponents.get(e).getEmeraldLevel(worldTime);
                    if (heat > maxHeat) { maxHeat = heat; hotterTarget = e; }
                }
                if (hotterTarget != null && hotterTarget != activeTarget) {
                    // Switch to hot target
                    if (data.assignedTargetId != -1) SeekerData.releaseTarget(data.assignedTargetId);
                    data.assignedTargetId = hotterTarget.getId();
                    data.lastTargetId = hotterTarget.getId();
                    data.lastTargetPos = hotterTarget.getPos().add(0, hotterTarget.getHeight() / 2, 0);
                    SeekerData.claimTarget(hotterTarget.getId());
                    activeTarget = hotterTarget;
                }
            }

            boolean justLockedOn = !data.wasLockedOn;
            data.wasLockedOn = true;
            data.lastTargetId = activeTarget.getId();
            data.lastTargetPos = activeTarget.getPos().add(0, activeTarget.getHeight() / 2, 0);
            data.lostTicks = 0;

            // Locked on: accelerate toward LOCKED_MAX_SPEED, reduce turn rate as speed climbs
            int emeraldLevel = ModComponents.get(activeTarget).getEmeraldLevel(worldTime);

            // Measure closing speed: positive = rocket closing in, negative = target pulling away
            Vec3d toTargetVec = activeTarget.getPos().add(0, activeTarget.getHeight() / 2, 0).subtract(rocket.getPos());
            double distToTarget = toTargetVec.length();
            Vec3d toTargetDir2 = distToTarget > 0 ? toTargetVec.normalize() : new Vec3d(0,1,0);
            // Project rocket velocity onto the to-target direction
            double rocketClosingSpeed = rocket.getVelocity().dotProduct(toTargetDir2);
            // Project target velocity onto same direction (away from rocket = positive)
            double targetFleeSeed = activeTarget.getVelocity().dotProduct(toTargetDir2);
            // closingDelta > 0 means target is outrunning rocket
            double closingDelta = targetFleeSeed - rocketClosingSpeed;

            double newSpeed;
            if (closingDelta > 0) {
                // Target is faster — accelerate step by step until we close the gap
                // Max speed = target speed + walking pace (0.13 b/t) so it always eventually catches up
                double targetSpeed = activeTarget.getVelocity().length();
                double speedCap = Math.min(LOCKED_MAX_SPEED, targetSpeed + 0.13);
                newSpeed = Math.min(speedCap, currentSpeed + ACCELERATION / 20.0);
            } else {
                // We are already faster than target — bleed back toward walking speed
                newSpeed = Math.max(INITIAL_SPEED, currentSpeed - DECELERATION / 20.0);
            }

            // Turn rate: inversely scales with speed — faster = more committed
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
                // Check if there's a better target in the cone before continuing the chase
                // Heat targets always interrupt. Cold targets only interrupt if the chased target is cold too.
                LivingEntity chased = (LivingEntity) lastTarget;
                int chasedHeat = ModComponents.get(chased).getEmeraldLevel(world.getTime());
                if (chasedHeat == 0) {
                    // Chased target is cold — check if a new target entered the cone
                    LivingEntity betterTarget = findTargetInVisionCone(world, rocket);
                    if (betterTarget != null && betterTarget != chased) {
                        // Found a new target — switch to it
                        if (data.assignedTargetId != -1) SeekerData.releaseTarget(data.assignedTargetId);
                        data.assignedTargetId = betterTarget.getId();
                        data.lastTargetId = betterTarget.getId();
                        data.lastTargetPos = betterTarget.getPos().add(0, betterTarget.getHeight() / 2, 0);
                        SeekerData.claimTarget(betterTarget.getId());
                        data.lostTicks = 0;
                        // Apply steering toward new target this tick
                        Vec3d newDir2 = slerp(rocket.getVelocity().normalize(),
                            data.lastTargetPos.subtract(rocket.getPos()).normalize(), TURN_RATE_HUNTING);
                        rocket.setVelocity(newDir2.multiply(Math.max(INITIAL_SPEED, currentSpeed - DECELERATION / 20.0)));
                        return true;
                    }
                }

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
                // Truly lost — try adaptive retargeting: find a new target preferring least-contested
                LivingEntity newTarget = findLeastContestedTarget(world, rocket);
                if (newTarget != null) {
                    // Release old claim, claim new target
                    if (data.assignedTargetId != -1) {
                        SeekerData.releaseTarget(data.assignedTargetId);
                    }
                    data.assignedTargetId = newTarget.getId();
                    data.lastTargetId = newTarget.getId();
                    data.lastTargetPos = newTarget.getPos().add(0, newTarget.getHeight() / 2, 0);
                    SeekerData.claimTarget(newTarget.getId());
                    data.lostTicks = 0;
                } else {
                    // No targets anywhere — count down to self-destruct
                    data.lostTicks++;
                    if (data.lostTicks >= LOST_TARGET_TICKS) {
                        SeekerData.remove(rocket);
                        rocket.discard();
                        world.createExplosion(null, rocket.getX(), rocket.getY(), rocket.getZ(),
                            0f, false, World.ExplosionSourceType.NONE);
                    }
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
                SeekerData sd = SeekerData.getOrCreate(rocket);
                // Crossbow: exclude owner
                if (!sd.placedOrDispensed && e == rocket.getOwner()) return false;
                // Placed rockets in launch phase (flightTicks < 30): only chase pre-assigned target
                // Placed rockets in homing phase (flightTicks >= 30): switch to cone-based targeting
                // This lets placed rockets do the firework arc first, then hunt via cone like crossbow rockets
                if (sd.placedOrDispensed && sd.flightTicks < 30 && sd.assignedTargetId != -1 && e.getId() != sd.assignedTargetId) return false;

                // Check Line of Sight
                if (!canSee(world, rocket, e)) return false;

                // Check if target is in 120-degree forward vision cone (60-degree half-angle)
                Vec3d forward = rocket.getVelocity().normalize();
                Vec3d toTarget = e.getPos().add(0, e.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                double dot = forward.dotProduct(toTarget);
                double angle = Math.acos(Math.min(1.0, Math.max(-1.0, dot)));
                return angle <= Math.toRadians(60); // 120-degree vision cone
            });

        if (candidates.isEmpty()) return null;

        Vec3d forward = rocket.getVelocity().normalize();
        long worldTime = world.getTime();

        // --- Target priority ---
        // 1. Heat signatures: sort all hot candidates by heat level descending.
        //    Hot targets are NEVER skipped even if already contested — highest heat always wins.
        //    On retargeting (failed target), hot targets are still eligible (ignore failed-target exclusion).
        java.util.List<LivingEntity> hotCandidates = new java.util.ArrayList<>();
        for (LivingEntity e : candidates) {
            if (ModComponents.get(e).getEmeraldLevel(worldTime) > 0) hotCandidates.add(e);
        }
        if (!hotCandidates.isEmpty()) {
            // Also include hot targets that were filtered out by assignedTargetId (failed target)
            // by re-scanning without that exclusion
            Box fullBox = new Box(rocket.getPos().subtract(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE),
                rocket.getPos().add(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE));
            SeekerData sd2 = SeekerData.getOrCreate(rocket);
            Entity owner2 = rocket.getOwner();
            world.getEntitiesByClass(LivingEntity.class, fullBox, e -> {
                if (e.isDead()) return false;
                if (!sd2.placedOrDispensed && e == owner2) return false;
                if (!canSee(world, rocket, e)) return false;
                Vec3d fwd2 = rocket.getVelocity().normalize();
                Vec3d toE = e.getPos().add(0, e.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                double ang = Math.acos(Math.min(1.0, Math.max(-1.0, fwd2.dotProduct(toE))));
                if (ang > Math.toRadians(60)) return false;
                int heat = ModComponents.get(e).getEmeraldLevel(worldTime);
                if (heat > 0 && !hotCandidates.contains(e)) hotCandidates.add(e);
                return false; // just collecting side effects
            });
            hotCandidates.sort((a, b) -> Integer.compare(
                ModComponents.get(b).getEmeraldLevel(worldTime),
                ModComponents.get(a).getEmeraldLevel(worldTime)));
            return hotCandidates.get(0); // highest heat, no contest cap
        }

        // 2. No heat signatures — least contested, break ties by cone-center alignment
        return candidates.stream()
            .min((e1, e2) -> {
                int c1 = SeekerData.getClaimCount(e1.getId());
                int c2 = SeekerData.getClaimCount(e2.getId());
                if (c1 != c2) return Integer.compare(c1, c2);
                Vec3d toE1 = e1.getPos().add(0, e1.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                Vec3d toE2 = e2.getPos().add(0, e2.getHeight() / 2, 0).subtract(rocket.getPos()).normalize();
                return Double.compare(forward.dotProduct(toE2), forward.dotProduct(toE1));
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
     * Finds a new target when the rocket loses its current one.
     * Prefers targets with the fewest rockets already assigned (least contested).
     * Excludes the rocket's owner for crossbow shots.
     */
    private static LivingEntity findLeastContestedTarget(World world, FireworkRocketEntity rocket) {
        if (world.isClient) return null;
        SeekerData data = SeekerData.getOrCreate(rocket);
        Entity owner = rocket.getOwner();

        Box searchBox = new Box(rocket.getPos().subtract(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE),
            rocket.getPos().add(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE));
        java.util.List<LivingEntity> pool = world.getEntitiesByClass(LivingEntity.class, searchBox,
            e -> !e.isDead()
                && !(data.placedOrDispensed ? false : e == owner) // crossbow: skip owner
                && e.getId() != data.assignedTargetId);           // skip current (failed) target

        if (pool.isEmpty()) return null;

        // Sort by claim count ascending — least contested first
        pool.sort(java.util.Comparator.comparingInt(e -> SeekerData.getClaimCount(e.getId())));

        // Pick from the least contested tier (all with same min count)
        int minCount = SeekerData.getClaimCount(pool.get(0).getId());
        java.util.List<LivingEntity> tier = new java.util.ArrayList<>();
        for (LivingEntity e : pool) {
            if (SeekerData.getClaimCount(e.getId()) > minCount) break;
            if (SeekerData.getClaimCount(e.getId()) < MAX_CLAIMS_PER_TARGET) tier.add(e);
        }
        if (tier.isEmpty()) return null;
        java.util.Collections.shuffle(tier);
        return tier.get(0);
    }

    // Expose MAX_CLAIMS_PER_TARGET so findLeastContestedTarget can read it
    private static final int MAX_CLAIMS_PER_TARGET = SeekerData.MAX_CLAIMS_PER_TARGET;

    /**
     * Called at placement time. Scans all living entities within SEEK_RANGE, excludes the placer,
     * shuffles the list, picks the first unclaimed one, claims it, and stores on SeekerData.
     * Returns the assigned entity or null if none available.
     */
    public static LivingEntity assignRandomTarget(World world, FireworkRocketEntity rocket, Entity placer) {
        if (world.isClient) return null;
        Box searchBox = new Box(rocket.getPos().subtract(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE),
            rocket.getPos().add(SEEK_RANGE, SEEK_RANGE, SEEK_RANGE));
        java.util.List<LivingEntity> pool = world.getEntitiesByClass(LivingEntity.class, searchBox,
            e -> !e.isDead() && e != placer);
        java.util.Collections.shuffle(pool);
        for (LivingEntity candidate : pool) {
            if (SeekerData.claimTarget(candidate.getId())) {
                SeekerData data = SeekerData.getOrCreate(rocket);
                data.assignedTargetId = candidate.getId();
                data.lastTargetId = candidate.getId();
                data.lastTargetPos = candidate.getPos().add(0, candidate.getHeight() / 2, 0);
                return candidate;
            }
        }
        return null;
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
        public boolean placedOrDispensed = false; // true = no owner exclusion at all
        public int assignedTargetId = -1; // for placed/dispensed rockets: pre-assigned random target
        private static final java.util.Map<Integer, SeekerData> TRACKER = new java.util.concurrent.ConcurrentHashMap<>();
        // Tracks which entity IDs are already claimed by a placed/dispensed rocket (max 2 per target)
        private static final java.util.Map<Integer, Integer> CLAIM_COUNTS = new java.util.concurrent.ConcurrentHashMap<>();
        private static final int MAX_CLAIMS_PER_TARGET = 2;

        public static SeekerData getOrCreate(FireworkRocketEntity rocket) {
            return TRACKER.computeIfAbsent(rocket.getId(), id -> new SeekerData());
        }

        public static void remove(FireworkRocketEntity rocket) {
            SeekerData d = TRACKER.get(rocket.getId());
            if (d != null && d.assignedTargetId != -1) {
                releaseTarget(d.assignedTargetId);
            }
            TRACKER.remove(rocket.getId());
        }

        /**
         * Periodic cleanup: remove CLAIM_COUNTS entries for entity IDs that no longer
         * exist in any active TRACKER entry. Call this occasionally to prevent stale counts
         * from blocking future rockets.
         */
        public static void purgeStaleEntries() {
            java.util.Set<Integer> active = new java.util.HashSet<>();
            for (SeekerData d : TRACKER.values()) {
                if (d.assignedTargetId != -1) active.add(d.assignedTargetId);
            }
            CLAIM_COUNTS.keySet().retainAll(active);
        }

        public static void releaseTarget(int entityId) {
            CLAIM_COUNTS.merge(entityId, -1, Integer::sum);
            CLAIM_COUNTS.remove(entityId, 0);
        }

        public static int getClaimCount(int entityId) {
            return CLAIM_COUNTS.getOrDefault(entityId, 0);
        }

        public static boolean claimTarget(int entityId) {
            int count = CLAIM_COUNTS.getOrDefault(entityId, 0);
            if (count >= MAX_CLAIMS_PER_TARGET) return false;
            CLAIM_COUNTS.put(entityId, count + 1);
            return true;
        }

        public static boolean isClaimed(int entityId) {
            return CLAIM_COUNTS.getOrDefault(entityId, 0) >= MAX_CLAIMS_PER_TARGET;
        }
    }
}
