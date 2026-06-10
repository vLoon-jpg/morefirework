package com.morefirework.mixin;

import com.morefirework.item.OreFireworkItem;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(CrossbowItem.class)
public class    CrossbowItemMixin {

    /**
     * Accept OreFireworkItem as crossbow ammo in addition to vanilla fireworks.
     * @author vLoon
     * @reason Ore rockets must be valid crossbow ammo alongside vanilla fireworks
     */
    @Overwrite
    public Predicate<ItemStack> getHeldProjectiles() {
        return stack -> stack.getItem() instanceof OreFireworkItem
            || stack.isOf(Items.FIREWORK_ROCKET);
    }

    /**
     * Firework speed (1.6f) for ore rockets; arrow speed (3.15f) otherwise.
     * @author vLoon
     * @reason Ore rockets share firework trajectory speed
     */
    @Overwrite
    private static float getSpeed(ChargedProjectilesComponent projectiles) {
        return projectiles.contains(Items.FIREWORK_ROCKET) || hasOreRocket(projectiles)
            ? 1.6f : 3.15f;
    }

    private static boolean hasOreRocket(ChargedProjectilesComponent projectiles) {
        for (ItemStack stack : projectiles.getProjectiles()) {
            if (stack.getItem() instanceof OreFireworkItem) return true;
        }
        return false;
    }

    @Inject(method = "createArrowEntity", at = @At("HEAD"), cancellable = true)
    private void morefirework$spawnOreFirework(
            World world, LivingEntity shooter,
            ItemStack crossbowStack, ItemStack projectileStack,
            boolean critical,
            CallbackInfoReturnable<ProjectileEntity> cir) {

        if (projectileStack.getItem() instanceof OreFireworkItem) {
            // Ensure FIREWORKS explosion data BEFORE entity reads it during construction
            OreFireworkItem.ensureFireworks(projectileStack);

            // shotAtAngle=true → flat trajectory (crossbow shot, not placed rocket)
            FireworkRocketEntity rocket = new FireworkRocketEntity(
                world,
                projectileStack,
                shooter.getX(), shooter.getEyeY() - 0.15, shooter.getZ(),
                true
            );
            rocket.setOwner(shooter);
            cir.setReturnValue(rocket);
        }
    }
}
