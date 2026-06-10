package com.morefirework.effect;

import com.morefirework.MoreFirework;
import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModEffects {
    private static final Logger LOG = LoggerFactory.getLogger("morefirework:effects");
    private static int tickCounter = 0;

    public static void register() {
        MoreFirework.LOGGER.info("More Firework effects registered.");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            for (var world : server.getWorlds()) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof LivingEntity living) {
                        FireworkEffectComponent fx = ModComponents.get(living);
                        processBleedTick(living, fx);      // every tick — smooth DOT
                        processStunTick(living, fx);        // every tick — velocity lock
                        // Decay runs every 40 ticks (2 seconds)
                        if (tickCounter % 40 == 0) {
                            fx.decayBleed();
                            processFractureDecayTick(living, fx);
                        }
                    }
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ModComponents.remove(handler.player);
            LOG.info("Player {} disconnected — effect data cleaned", handler.player.getName().getString());
        });

        // Brush cleanse
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.BRUSH)) {
                FireworkEffectComponent fx = ModComponents.get(player);
                if (fx.getTotalBleed() > 0 || fx.hasAnyFractureStack() || fx.countFractured() > 0) {
                    LOG.info("Brush cleanse — player={}, bleed={}, fracture={}",
                        player.getName().getString(), fx.getTotalBleed(), fx.countFractured());
                    brushCleanse(player);
                    stack.damage(1, player, hand == net.minecraft.util.Hand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    return TypedActionResult.consume(stack);
                }
            }
            return TypedActionResult.pass(stack);
        });
    }

    private static void processBleedTick(LivingEntity entity, FireworkEffectComponent fx) {
        if (fx.getTotalBleed() == 0) return;

        float totalDamage = 0;
        boolean hasAmethyst = fx.hasAnyFractureStack() || fx.countFractured() > 0;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            int stacks = fx.getBleed(slot);
            if (stacks <= 0) continue;

            if (hasAmethyst) {
                ItemStack armor = entity.getEquippedStack(slot);
                if (!armor.isEmpty()) {
                    // Per-tick durability drain: maxDura × 0.02 × stacks / 40.
                    // Same total as old 40-tick cycle: maxDura × 0.02 × stacks.
                    int duraDamage = Math.max(1, (int)(armor.getMaxDamage() * 0.0005f * stacks));
                    armor.damage(duraDamage, entity, slot);
                    LOG.debug("Bleed (amethyst combo) — {}, slot={}, stacks={}, duraDmg={}",
                        entity.getName().getString(), slot.getName(), stacks, duraDamage);
                }
            } else {
                // Per-tick damage: stacks × 0.025 HP per tick.
                // Over 40 ticks (before decay) = stacks × 1.0 HP total — same as old burst.
                totalDamage += stacks * 0.025f;
            }
        }

        if (totalDamage > 0) {
            entity.damage(entity.getDamageSources().generic(), totalDamage);
            LOG.debug("Bleed tick — {}, damage={}hp, stacks={}",
                entity.getName().getString(), totalDamage, fx.getTotalBleed());
        }
    }

    private static void processStunTick(LivingEntity entity, FireworkEffectComponent fx) {
        if (fx.isStunned(entity.getWorld().getTime())) {
            entity.setVelocity(0, 0, 0);
            entity.velocityModified = true;
        }
    }

    private static void processFractureDecayTick(LivingEntity entity, FireworkEffectComponent fx) {
        if (fx.hasAnyFractureStack()) {
            fx.decayFracture();
        }
    }

    public static float onDamageReceived(LivingEntity entity, float amount) {
        FireworkEffectComponent fx = ModComponents.get(entity);
        long worldTime = entity.getWorld().getTime();

        int fracturedCount = fx.countFractured();
        float afterFracture = amount;

        // --- Step 1: Fracture redirect (Armor → takes durability damage) ---
        if (fracturedCount > 0) {
            float multiplier = switch (fracturedCount) {
                case 1 -> 4.0f;
                case 2 -> 3.0f;
                case 3 -> 3.0f;
                default -> 1.0f;
            };

            float duraPerPiece = amount * multiplier;
            boolean anyArmorExists = false;

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                if (fx.isFractured(slot)) {
                    ItemStack armor = entity.getEquippedStack(slot);
                    if (!armor.isEmpty()) {
                        anyArmorExists = true;
                        armor.damage((int) duraPerPiece, entity, slot);
                    }
                }
            }

            // BUGFIX: only redirect to zero if at least one fractured slot has armor.
            // Naked targets with fracture stacks take normal HP damage.
            if (anyArmorExists) {
                afterFracture = 0;
            }

            if (fracturedCount >= 4 && !fx.isCrystalizedImmune(worldTime)) {
                triggerCrystalized(entity, fx, worldTime);
            }

            LOG.debug("Fracture redirect — {}, incomingDmg={}, fracturedPieces={}, anyArmor={}, resultDmg={}",
                entity.getName().getString(), amount, fracturedCount, anyArmorExists, afterFracture);
        }

        // --- Step 2: Diamond Stab — double damage through marked armor ---
        float result = afterFracture;
        if (result > 0) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                if (fx.isDiamondMarked(slot) && !fx.isStabImmune(slot, worldTime)) {
                    ItemStack armor = entity.getEquippedStack(slot);
                    if (!armor.isEmpty()) {
                        result *= 2.0f; // Marked armor piece provides zero protection → double effective damage
                        LOG.info("DIAMOND STAB — {}, slot={}, baseDmg={}, boostedDmg={}",
                            entity.getName().getString(), slot.getName(), amount, result);
                        break;
                    }
                }
            }
        }

        return result;
    }

    public static void triggerCrystalized(LivingEntity entity, FireworkEffectComponent fx, long worldTime) {
        LOG.info("CRYSTALIZED triggered — {}", entity.getName().getString());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack armor = entity.getEquippedStack(slot);
            if (!armor.isEmpty()) {
                int maxDura = armor.getMaxDamage();
                armor.setDamage(Math.min(armor.getMaxDamage(), armor.getDamage() + (int)(maxDura * 0.15f)));
            }
        }

        fx.clearFracture();
        fx.setCrystalizedImmunity(worldTime, 60 * 20);
        fx.stun(worldTime, 5 * 20);
        fx.setArmorReduced(worldTime, 10 * 20);

        entity.getWorld().playSound(null, entity.getBlockPos(),
            SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 2.0f, 0.5f);

        if (entity instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.literal("§d⚡ CRYSTALIZED! Armor shattered."), true);
        }
    }

    public static void brushCleanse(LivingEntity entity) {
        FireworkEffectComponent fx = ModComponents.get(entity);
        fx.clearBleed();
        fx.clearFracture();

        entity.getWorld().playSound(null, entity.getBlockPos(),
            SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.PLAYERS, 1.0f, 1.5f);
    }
}
