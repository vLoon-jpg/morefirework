package com.morefirework.mixin;

import com.morefirework.MoreFirework;
import com.morefirework.component.ModComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Persists firework effect data in player NBT.
 * Called whenever a player is saved to disk or synced over network.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerDataMixin {

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void morefirework$writeNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.put(MoreFirework.MOD_ID, ModComponents.writeNbt((PlayerEntity) (Object) this));
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void morefirework$readNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(MoreFirework.MOD_ID)) {
            ModComponents.readNbt((PlayerEntity) (Object) this, nbt.getCompound(MoreFirework.MOD_ID));
        }
    }
}
