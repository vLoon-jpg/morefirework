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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {
    private static final Logger LOG = LoggerFactory.getLogger("morefirework:rocket");

    @Inject(method = "tick", at = @At("HEAD"))
    private void morefirework$seekerTick(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        ItemStack stack = self.getStack();
        if (OreFireworkItem.hasRedstone(stack)) {
            LOG.debug("Seeker tick — entity={}, pos=({},{},{})", 
                self.getId(), (int)self.getX(), (int)self.getY(), (int)self.getZ());
        }
        SeekerBehavior.tick(self);
    }

    /** Allow firework rockets to hit ALL entities, not just players */
    @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
    protected void morefirework$alwaysHit(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void morefirework$onEntityHit(EntityHitResult hitResult, CallbackInfo ci) {
        Entity target = hitResult.getEntity();
        if (!(target instanceof LivingEntity livingTarget)) {
            LOG.debug("Non-living entity hit: {}", target.getType().getName().getString());
            return;
        }
        if (livingTarget.getWorld().isClient) return;

        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        ItemStack rocket = self.getStack();
        if (!(rocket.getItem() instanceof OreFireworkItem oreItem)) {
            LOG.trace("Non-ore firework rocket hit — skipping");
            return;
        }

        OreFireworkItem.OreType type = oreItem.getOreType();
        FireworkEffectComponent fx = ModComponents.get(livingTarget);
        long worldTime = self.getWorld().getTime();
        Random random = new Random();

        LOG.info("ROCKET HIT — type={}, target={}, pos=({},{},{})",
            type, livingTarget.getName().getString(),
            (int)livingTarget.getX(), (int)livingTarget.getY(), (int)livingTarget.getZ());

        switch (type) {
            case DIAMOND -> {
                EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET};
                EquipmentSlot marked = armorSlots[random.nextInt(4)];
                fx.markDiamond(marked);
                LOG.info("  DIAMOND — marked armor slot: {}", marked.getName());
            }
            case IRON -> {
                EquipmentSlot[] slots = shuffledArmorSlots(random);
                for (int i = 0; i < 4; i++) fx.addBleed(slots[i % slots.length], 1);
                LOG.info("  IRON — applied 4 bleed stacks. Total bleed: {}", fx.getTotalBleed());
            }
            case AMETHYST -> {
                if (fx.isCrystalizedImmune(worldTime)) {
                    LOG.info("  AMETHYST — target is crystalized-immune, skipping");
                    break;
                }
                for (int i = 0; i < 2; i++) {
                    EquipmentSlot slot = rollFractureSlot(random);
                    fx.addFracture(slot, 1);
                    if (fx.getFracture(slot) >= 4) fx.setFractured(slot, true);
                }
                LOG.info("  AMETHYST — applied 2 fracture stacks. Fractured pieces: {}", fx.countFractured());
            }
            case EMERALD -> {
                fx.addEmeraldHit(worldTime);
                LOG.info("  EMERALD — heat signature level: {}", fx.getEmeraldLevel(worldTime));
            }
            case GOLD -> {
                LOG.info("  GOLD — triggering shotgun blast");
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
