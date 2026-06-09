package com.morefirework.effect;

import com.morefirework.MoreFirework;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Gold rocket shotgun: on impact, sprays 8 shrapnel pieces in a 60° cone.
 * Each piece deals 3 hearts (6 HP), reduced by armor normally.
 */
public class GoldShotgun {

    private static final int SHRAPNEL_COUNT = 8;
    private static final float CONE_ANGLE = 60.0f; // degrees total
    private static final double RANGE = 5.0;
    private static final float DAMAGE_PER_SHRAPNEL = 6.0f; // 3 hearts
    private static final float KNOCKBACK_STRENGTH = 1.5f;

    public static void shatter(World world, Vec3d impactPos, Vec3d direction, Entity shooter) {
        if (world.isClient) return;

        // Normalize and compute perpendicular axes for the cone
        Vec3d forward = direction.normalize();
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = forward.crossProduct(up).normalize();
        Vec3d trueUp = right.crossProduct(forward).normalize();

        // Collect all living entities in range
        Box area = new Box(
            impactPos.x - RANGE, impactPos.y - RANGE, impactPos.z - RANGE,
            impactPos.x + RANGE, impactPos.y + RANGE, impactPos.z + RANGE
        );

        for (Entity entity : world.getOtherEntities(shooter, area)) {
            if (!(entity instanceof LivingEntity target)) continue;

            Vec3d toTarget = target.getPos().add(0, target.getHeight() / 2, 0).subtract(impactPos);
            double distance = toTarget.length();
            if (distance > RANGE || distance == 0) continue;

            Vec3d toTargetDir = toTarget.normalize();
            double dot = forward.dotProduct(toTargetDir);
            double halfAngleRad = Math.toRadians(CONE_ANGLE / 2);
            double angle = Math.acos(Math.min(1, Math.max(-1, dot)));

            if (angle > halfAngleRad) continue; // outside cone

            // How many shrapnel hit depends on distance
            double hitRatio = 1.0 - (distance / RANGE);
            int shrapnelHitting = Math.max(1, (int) Math.round(SHRAPNEL_COUNT * hitRatio));
            float totalDamage = DAMAGE_PER_SHRAPNEL * shrapnelHitting;

            // Shields partially block
            ItemStack offhand = target.getOffHandStack();
            boolean hasShield = offhand.getItem().toString().contains("shield");
            if (hasShield) {
                totalDamage *= 0.5f;
                offhand.damage(20 * shrapnelHitting, target, EquipmentSlot.OFFHAND);
            }

            // Apply damage
            DamageSource source = target.getDamageSources().explosion(null, shooter);
            target.damage(source, totalDamage);

            // Knockback away from impact
            Vec3d knockback = toTargetDir.multiply(KNOCKBACK_STRENGTH);
            target.addVelocity(knockback.x, 0.3, knockback.z);
            target.velocityModified = true;
        }
    }
}
