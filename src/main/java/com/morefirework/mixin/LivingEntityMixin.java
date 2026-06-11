package com.morefirework.mixin;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import com.morefirework.effect.ModEffects;
import com.morefirework.item.OreFireworkItem;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float morefirework$modifyDamage(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        return ModEffects.onDamageReceived(self, amount);
    }

    @Inject(method = "applyArmorToDamage(Lnet/minecraft/entity/damage/DamageSource;F)F", at = @At("HEAD"), cancellable = true)
    private void morefirework$applyArmorToDamage(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_ARMOR)) return;

        LivingEntity self = (LivingEntity) (Object) this;
        
        // Skip if this is direct damage from a diamond rocket to let them mark other pieces
        net.minecraft.entity.Entity attacker = source.getSource();
        if (attacker instanceof FireworkRocketEntity rocket) {
            ItemStack stack = rocket.getStack();
            if (stack.getItem() instanceof OreFireworkItem oreItem && oreItem.getOreType() == OreFireworkItem.OreType.DIAMOND) {
                return;
            }
        }

        FireworkEffectComponent fx = ModComponents.get(self);
        long worldTime = self.getWorld().getTime();

        int totalArmor = self.getArmor();
        double totalToughness = self.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);

        boolean hasMarkedArmor = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            if (fx.isDiamondMarked(slot) && !fx.isStabImmune(slot, worldTime)) {
                ItemStack armorStack = self.getEquippedStack(slot);
                if (!armorStack.isEmpty()) {
                    hasMarkedArmor = true;
                    if (armorStack.getItem() instanceof ArmorItem armorItem) {
                        totalArmor -= armorItem.getProtection();
                        totalToughness -= armorItem.getToughness();
                    }
                }
            }
        }

        if (hasMarkedArmor) {
            totalArmor = Math.max(0, totalArmor);
            totalToughness = Math.max(0.0, totalToughness);

            // Apply durability damage to armor normally using the full amount
            self.damageArmor(source, amount);

            // Calculate damage left with the ignored armor/toughness values
            float damageLeft = DamageUtil.getDamageLeft(self, amount, source, (float) totalArmor, (float) totalToughness);

            // Consume the marks that contributed to this hit
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                if (fx.isDiamondMarked(slot) && !fx.isStabImmune(slot, worldTime)) {
                    ItemStack armorStack = self.getEquippedStack(slot);
                    if (!armorStack.isEmpty()) {
                        fx.clearDiamondMark(slot);
                    }
                }
            }

            cir.setReturnValue(damageLeft);
        }
    }
}
