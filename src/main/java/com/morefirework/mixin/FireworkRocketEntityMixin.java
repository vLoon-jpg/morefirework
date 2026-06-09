package com.morefirework.mixin;

import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import com.morefirework.effect.GoldShotgun;
import com.morefirework.effect.SeekerBehavior;
import com.morefirework.item.OreFireworkItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void morefirework$seekerTick(CallbackInfo ci) {
        SeekerBehavior.tick((FireworkRocketEntity) (Object) this);
    }

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void morefirework$onEntityHit(EntityHitResult hitResult, CallbackInfo ci) {
        Entity target = hitResult.getEntity();
        if (!(target instanceof LivingEntity livingTarget)) return;
        if (livingTarget.getWorld().isClient) return;

        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        ItemStack rocket = self.getStack();
        if (!(rocket.getItem() instanceof OreFireworkItem oreItem)) return;

        OreFireworkItem.OreType type = oreItem.getOreType();
        FireworkEffectComponent fx = ModComponents.get(livingTarget);
        long worldTime = self.getWorld().getTime();
        Random random = new Random();

        switch (type) {
            case DIAMOND -> {
                EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET};
                fx.markDiamond(armorSlots[random.nextInt(4)]);
            }
            case IRON -> {
                EquipmentSlot[] slots = shuffledArmorSlots(random);
                for (int i = 0; i < 4; i++) fx.addBleed(slots[i % slots.length], 1);
            }
            case AMETHYST -> {
                if (fx.isCrystalizedImmune(worldTime)) break;
                for (int i = 0; i < 2; i++) {
                    EquipmentSlot slot = rollFractureSlot(random);
                    fx.addFracture(slot, 1);
                    if (fx.getFracture(slot) >= 4) fx.setFractured(slot, true);
                }
            }
            case EMERALD -> fx.addEmeraldHit(worldTime);
            case GOLD -> {
                // Shotgun blast centered on target
                GoldShotgun.shatter(
                    livingTarget.getWorld(),
                    livingTarget.getPos().add(0, livingTarget.getHeight() / 2, 0),
                    self.getVelocity(),
                    self.getOwner() != null ? self.getOwner() : self
                );
            }
        }
    }

    private static EquipmentSlot[] shuffledArmorSlots(Random rng) {
        EquipmentSlot[] all = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = all.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            EquipmentSlot tmp = all[i]; all[i] = all[j]; all[j] = tmp;
        }
        return all;
    }

    private static EquipmentSlot rollFractureSlot(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 15) return EquipmentSlot.HEAD;
        if (roll < 55) return EquipmentSlot.CHEST;
        if (roll < 90) return EquipmentSlot.LEGS;
        return EquipmentSlot.FEET;
    }
}
