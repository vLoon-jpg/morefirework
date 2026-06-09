package com.morefirework.mixin;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "isBlockBreakingRestricted", at = @At("HEAD"), cancellable = true)
    private void morefirework$blockBreaking(CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        FireworkEffectComponent fx = ModComponents.get(self);
        if (fx.isStunned(self.getWorld().getTime())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canConsume", at = @At("HEAD"), cancellable = true)
    private void morefirework$blockConsume(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        FireworkEffectComponent fx = ModComponents.get(self);
        if (fx.isStunned(self.getWorld().getTime())) {
            cir.setReturnValue(false);
        }
    }
}
