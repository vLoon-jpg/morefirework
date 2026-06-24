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

/**
 * Gold rocket shotgun: on impact, sprays shrapnel in a 60° cone.
 * Each piece deals damage scaled by center alignment and target armor.
 */
public class GoldShotgun {

    private static final Logger LOG = LoggerFactory.getLogger("morefirework:shotgun");

    private static final int SHRAPNEL_COUNT = 8;
    private static final float CONE_ANGLE = 60.0f;
    private static final double RANGE = 5.0;
    private static final float DAMAGE_PER_SHRAPNEL = 5.0f;
    private static final float KNOCKBACK_STRENGTH = 1.5f;

    public static void shatter(World world, Vec3d impactPos, Vec3d direction, Entity shooter) {
        if (world.isClient) return;

        LOG.info("Shotgun shatter — pos=({},{},{}), shooter={}",
            (int)impactPos.x, (int)impactPos.y, (int)impactPos.z,
            shooter != null ? shooter.getName().getString() : "none");

        Vec3d forward = direction.normalize();

        Box area = new Box(
            impactPos.x - RANGE, impactPos.y - RANGE, impactPos.z - RANGE,
            impactPos.x + RANGE, impactPos.y + RANGE, impactPos.z + RANGE
        );

        // Single pass: particles + damage in one iteration
        for (Entity entity : world.getOtherEntities(shooter, area)) {
            if (!(entity instanceof LivingEntity target)) continue;

            Vec3d toTarget = target.getPos().add(0, target.getHeight() / 2, 0).subtract(impactPos);
            double distance = toTarget.length();
            if (distance > RANGE || distance == 0) continue;

            Vec3d toTargetDir = toTarget.normalize();
            double dot = forward.dotProduct(toTargetDir);
            double halfAngleRad = Math.toRadians(CONE_ANGLE / 2);
            double angle = Math.acos(Math.min(1.0, Math.max(-1.0, dot)));

            if (angle > halfAngleRad) continue;

            double centerFactor = 1.0 - (angle / halfAngleRad);
            double distFactor = 1.0 - (distance / RANGE);

            int shrapnelHitting = Math.max(1, (int) Math.round(SHRAPNEL_COUNT * centerFactor * distFactor));
            float baseDamage = DAMAGE_PER_SHRAPNEL * shrapnelHitting;

            float armorFactor = 1.0f + 0.5f * (1.0f - (Math.min(20, target.getArmor()) / 20.0f));
            float totalDamage = baseDamage * armorFactor;

            ItemStack offhand = target.getOffHandStack();
            boolean hasShield = !offhand.isEmpty() && offhand.getItem() instanceof net.minecraft.item.ShieldItem;
            if (hasShield) {
                totalDamage *= 0.5f;
                offhand.damage(20 * shrapnelHitting, target, EquipmentSlot.OFFHAND);
            }

            // Particles — blind the target with visual noise
            if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                serverWorld.spawnParticles(
                    net.minecraft.particle.ParticleTypes.CRIT,
                    impactPos.x, impactPos.y + 0.5, impactPos.z,
                    12,
                    toTargetDir.x * 1.5, toTargetDir.y * 1.5, toTargetDir.z * 1.5,
                    0.3
                );
                serverWorld.spawnParticles(
                    net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                    impactPos.x, impactPos.y + 0.5, impactPos.z,
                    8,
                    toTargetDir.x * 0.8, toTargetDir.y * 0.8, toTargetDir.z * 0.8,
                    0.15
                );
                serverWorld.spawnParticles(
                    net.minecraft.particle.ParticleTypes.ENCHANTED_HIT,
                    target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                    16,
                    0.3, 0.3, 0.3,
                    0.2
                );
            }

            DamageSource source = target.getDamageSources().explosion(null, shooter);
            target.damage(source, totalDamage);
            LOG.info("  Shrapnel: target={}, pieces={}, baseDmg={}hp, armorFactor={}, finalDmg={}hp, shield={}",
                target.getName().getString(), shrapnelHitting, baseDamage / 2, armorFactor, totalDamage / 2, hasShield);

            Vec3d knockback = toTargetDir.multiply(KNOCKBACK_STRENGTH);
            target.addVelocity(knockback.x, 0.3, knockback.z);
            target.velocityModified = true;
        }
    }
}
