package com.morefirework.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gold rocket shotgun: on impact, sprays 8 shrapnel pieces in a 60° cone.
 * Each piece deals 4 hearts (8 HP), scaled by center alignment and target armor.
 */
public class GoldShotgun {

    private static final Logger LOG = LoggerFactory.getLogger("morefirework:shotgun");

    private static final int SHRAPNEL_COUNT = 8;
    private static final float CONE_ANGLE = 60.0f; // degrees total
    private static final double RANGE = 5.0;
    private static final float DAMAGE_PER_SHRAPNEL = 8.0f; // 4 hearts (8 HP)
    private static final float KNOCKBACK_STRENGTH = 1.5f;

    public static void shatter(World world, Vec3d impactPos, Vec3d direction, Entity shooter) {
        if (world.isClient) return;

        LOG.info("Shotgun shatter — pos=({},{},{}), shooter={}",
            (int)impactPos.x, (int)impactPos.y, (int)impactPos.z,
            shooter != null ? shooter.getName().getString() : "none");

        // Normalize and compute perpendicular axes for the cone
        Vec3d forward = direction.normalize();

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
            double angle = Math.acos(Math.min(1.0, Math.max(-1.0, dot)));

            if (angle > halfAngleRad) continue; // outside cone

            // The closer they are to the center line of the cone, the higher the damage (centerFactor)
            double centerFactor = 1.0 - (angle / halfAngleRad);
            // Distance factor (decreases farther away)
            double distFactor = 1.0 - (distance / RANGE);

            // Shrapnel count scales based on both alignment and distance
            int shrapnelHitting = Math.max(1, (int) Math.round(SHRAPNEL_COUNT * centerFactor * distFactor));
            float baseDamage = DAMAGE_PER_SHRAPNEL * shrapnelHitting;

            // Deal more damage to unarmored targets (up to 2.0x base damage at 0 armor)
            float armorFactor = 1.0f + 1.0f * (1.0f - (Math.min(20, target.getArmor()) / 20.0f));
            float totalDamage = baseDamage * armorFactor;

            // Shields partially block (50% reduction)
            ItemStack offhand = target.getOffHandStack();
            boolean hasShield = offhand.getItem().toString().contains("shield");
            if (hasShield) {
                totalDamage *= 0.5f;
                offhand.damage(20 * shrapnelHitting, target, EquipmentSlot.OFFHAND);
            }

            // Apply damage
            DamageSource source = target.getDamageSources().explosion(null, shooter);
            target.damage(source, totalDamage);
            LOG.info("  Shrapnel: target={}, pieces={}, baseDmg={}hp, armorFactor={}, finalDmg={}hp, shield={}",
                target.getName().getString(), shrapnelHitting, baseDamage / 2, armorFactor, totalDamage / 2, hasShield);

            // Knockback away from impact
            Vec3d knockback = toTargetDir.multiply(KNOCKBACK_STRENGTH);
            target.addVelocity(knockback.x, 0.3, knockback.z);
            target.velocityModified = true;
        }
    }
}
