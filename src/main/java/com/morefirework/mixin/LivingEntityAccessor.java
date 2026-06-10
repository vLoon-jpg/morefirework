package com.morefirework.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes LivingEntity.hurtTime so we can zero it after bleed ticks
 * — bypassing vanilla damage invulnerability frames.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("hurtTime")
    void setHurtTime(int time);
}
