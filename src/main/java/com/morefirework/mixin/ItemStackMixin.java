package com.morefirework.mixin;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import com.morefirework.item.OreFireworkItem;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    private void morefirework$modifyMaxDamage(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.contains(OreFireworkItem.DURABILITY_PENALTY)) {
            Integer penalty = self.get(OreFireworkItem.DURABILITY_PENALTY);
            if (penalty != null && penalty > 0) {
                int baseMax = cir.getReturnValue();
                cir.setReturnValue(Math.max(1, baseMax - penalty));
            }
        }
    }

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void morefirework$addArmorTooltip(net.minecraft.item.Item.TooltipContext context, PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        if (player == null) return;
        ItemStack self = (ItemStack) (Object) this;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && player.getEquippedStack(slot) == self) {
                List<Text> tooltip = cir.getReturnValue();
                List<Text> mutableTooltip = new ArrayList<>(tooltip);

                FireworkEffectComponent fx = ModComponents.get(player);
                long worldTime = player.getWorld().getTime();
                boolean addedAny = false;

                int bleed = fx.getBleed(slot);
                if (bleed > 0) {
                    mutableTooltip.add(Text.literal("§4❤ Bleed Stacks: " + bleed + " / 6"));
                    addedAny = true;
                }

                int fracture = fx.getFracture(slot);
                boolean fractured = fx.isFractured(slot, worldTime);
                if (fractured) {
                    mutableTooltip.add(Text.literal("§d☠ FRACTURED (Expires in 2m)"));
                    addedAny = true;
                } else if (fracture > 0) {
                    mutableTooltip.add(Text.literal("§d⚡ Fracture Stacks: " + fracture + " / 4"));
                    addedAny = true;
                }

                if (fx.isDiamondMarked(slot) && !fx.isStabImmune(slot, worldTime)) {
                    mutableTooltip.add(Text.literal("§b💎 Diamond Stab Marked"));
                    addedAny = true;
                }

                if (addedAny) {
                    cir.setReturnValue(mutableTooltip);
                }
                break;
            }
        }
    }
}
