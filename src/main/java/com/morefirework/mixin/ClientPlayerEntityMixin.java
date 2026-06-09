package com.morefirework.mixin;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void morefirework$stunMovement(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        FireworkEffectComponent fx = ModComponents.get(self);
        if (fx.isStunned(self.getWorld().getTime())) {
            self.setVelocity(0, 0, 0);
        }
    }
}
