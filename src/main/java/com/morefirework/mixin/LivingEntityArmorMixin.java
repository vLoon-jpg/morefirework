package com.morefirework.mixin;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin({PlayerEntity.class, MobEntity.class, ArmorStandEntity.class})
public abstract class LivingEntityArmorMixin {

    @Inject(method = "getArmorItems", at = @At("HEAD"), cancellable = true)
    private void morefirework$getArmorItems(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        FireworkEffectComponent fx = ModComponents.get(self);
        if (fx.isIgnoringMarkedArmor()) {
            long worldTime = self.getWorld().getTime();
            List<ItemStack> stacks = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                if (fx.isDiamondMarked(slot) && !fx.isStabImmune(slot, worldTime)) {
                    stacks.add(ItemStack.EMPTY);
                } else {
                    stacks.add(self.getEquippedStack(slot));
                }
            }
            cir.setReturnValue(stacks);
        }
    }
}
