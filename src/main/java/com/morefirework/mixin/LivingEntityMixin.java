package com.morefirework.mixin;

import com.morefirework.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepts damage received by LivingEntities.
 * If the entity has fractured armor, HP damage is redirected to armor durability.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float morefirework$modifyDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        return ModEffects.onDamageReceived(self, amount);
    }
}
