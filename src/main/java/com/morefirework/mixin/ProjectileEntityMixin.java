package com.morefirework.mixin;

import com.morefirework.item.OreFireworkItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FireworkRocketEntity inherits canHit() from ProjectileEntity without
 * overriding it. Inject into the parent so ore rockets hit all entities.
 */
@Mixin(ProjectileEntity.class)
public class ProjectileEntityMixin {

    @Inject(method = "canHit()Z", at = @At("HEAD"), cancellable = true)
    private void morefirework$oreFireworkAlwaysHit(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof FireworkRocketEntity firework) {
            ItemStack stack = firework.getStack();
            if (stack.getItem() instanceof OreFireworkItem) {
                cir.setReturnValue(true);
            }
        }
    }
}
