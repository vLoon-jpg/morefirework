package com.morefirework.effect;

import com.morefirework.MoreFirework;
import com.morefirework.component.FireworkEffectComponent;
import com.morefirework.component.ModComponents;
import com.morefirework.item.OreFireworkItem;
import com.morefirework.mixin.LivingEntityAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModEffects {
    private static final Logger LOG = LoggerFactory.getLogger("morefirework:effects");
    private static int tickCounter = 0;

    public static final RegistryKey<DamageType> BLEED_DAMAGE_TYPE = RegistryKey.of(
        RegistryKeys.DAMAGE_TYPE,
        Identifier.of(MoreFirework.MOD_ID, "bleed")
    );

    public static void register() {
        MoreFirework.LOGGER.info("More Firework effects registered.");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            for (var world : server.getWorlds()) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof LivingEntity living) {
                        FireworkEffectComponent fx = ModComponents.get(living);
                        
                        // Process bleed once every 20 ticks (1 second) for drama/DoT
                        if (tickCounter % 20 == 0) {
                            processBleedTick(living, fx);
                        }
                        
                        processStunTick(living, fx);        // every tick — velocity lock
                        
                        // Decay runs every 40 ticks (2 seconds)
                        if (tickCounter % 40 == 0) {
                            processFractureDecayTick(living, fx);
                        }

                        // Process Brush Cleansing progress if entity is a player using a brush
                        if (living instanceof ServerPlayerEntity player) {
                            if (player.isUsingItem() && player.getActiveItem().isOf(Items.BRUSH)) {
                                net.minecraft.util.hit.HitResult hit = player.raycast(5.0, 1.0f, false);
                                if (hit != null && hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
                                    fx.incrementBrushingTicks();
                                    if (fx.getBrushingTicks() >= 60) { // 3 seconds
                                        boolean cleansed = fx.cleanseOneMark(player.getWorld().getTime());
                                        fx.setBrushingTicks(0);
                                        if (cleansed) {
                                            player.getWorld().playSound(null, player.getBlockPos(),
                                                SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.PLAYERS, 1.0f, 1.5f);
                                            player.getActiveItem().damage(1, player, player.getActiveHand() == net.minecraft.util.Hand.MAIN_HAND 
                                                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                                            player.sendMessage(Text.literal("§a✔ Cleansed 1 combat mark."), true);
                                        }
                                    }
                                } else {
                                    fx.setBrushingTicks(0);
                                }
                            } else {
                                fx.setBrushingTicks(0);
                            }

                            // Dynamic actionbar HUD message for active stacks/marks
                            long worldTime = player.getWorld().getTime();
                            if (worldTime % 10 == 0) {
                                StringBuilder sb = new StringBuilder();
                                boolean hasAny = false;

                                int totalBleed = fx.getTotalBleed();
                                if (totalBleed > 0) {
                                    sb.append("§4❤ Bleed: ").append(totalBleed).append(" stacks");
                                    hasAny = true;
                                }

                                int fracturedCount = fx.countFractured(worldTime);
                                if (fracturedCount > 0) {
                                    if (hasAny) sb.append(" | ");
                                    sb.append("§d⚡ Fractured: ").append(fracturedCount).append(" pieces");
                                    hasAny = true;
                                } else if (fx.hasAnyFractureStack()) {
                                    if (hasAny) sb.append(" | ");
                                    sb.append("§d⚡ Fracture stacks active");
                                    hasAny = true;
                                }

                                int diamondCount = 0;
                                for (EquipmentSlot slot : EquipmentSlot.values()) {
                                    if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && fx.isDiamondMarked(slot) && !fx.isStabImmune(slot, worldTime)) {
                                        diamondCount++;
                                    }
                                }
                                if (diamondCount > 0) {
                                    if (hasAny) sb.append(" | ");
                                    sb.append("§b💎 Diamond Marked: ").append(diamondCount).append(" pieces");
                                    hasAny = true;
                                }

                                if (fx.hasEmeraldMark(worldTime)) {
                                    if (hasAny) sb.append(" | ");
                                    sb.append("§a🟢 Heat Signature Active");
                                    hasAny = true;
                                }

                                if (hasAny) {
                                    player.sendMessage(Text.literal(sb.toString()), true);
                                }
                            }
                        }
                    }
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ModComponents.remove(handler.player);
            LOG.info("Player {} disconnected — effect data cleaned", handler.player.getName().getString());
        });
    }

    private static void processBleedTick(LivingEntity entity, FireworkEffectComponent fx) {
        int bleedCount = fx.getTotalBleed();
        if (bleedCount == 0) return;

        boolean hasAmethyst = fx.hasAnyActiveFracture(entity.getWorld().getTime());
        float totalDamage = 0;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            int stacks = fx.getBleed(slot);
            if (stacks <= 0) continue;

            if (hasAmethyst) {
                ItemStack armor = entity.getEquippedStack(slot);
                if (!armor.isEmpty()) {
                    // Durability damage run once a second (1% of max durability per stack)
                    int duraDamage = Math.max(1, (int)(armor.getMaxDamage() * 0.01f * stacks));
                    armor.damage(duraDamage, entity, slot);
                    LOG.debug("Bleed (amethyst combo) — {}, slot={}, stacks={}, duraDmg={}",
                        entity.getName().getString(), slot.getName(), stacks, duraDamage);
                }
            } else {
                // Total damage: stacks * 1.5f HP per second (0.75 hearts per stack per tick, ensuring death at full stacks)
                totalDamage += stacks * 1.5f;
            }
        }

        if (totalDamage > 0) {
            DamageSource source = entity.getWorld().getDamageSources().create(BLEED_DAMAGE_TYPE);
            entity.damage(source, totalDamage);
            // Bypass damage immunity frames so bleed registers instantly without delays
            ((LivingEntityAccessor) entity).setHurtTime(0);
            LOG.debug("Bleed tick — {}, damage={}hp, stacks={}",
                entity.getName().getString(), totalDamage, bleedCount);
        }

        // Consume exactly 1 stack total per damage/DoT tick
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            int stacks = fx.getBleed(slot);
            if (stacks > 0) {
                fx.addBleed(slot, -1);
                break; // only decrement 1 stack total
            }
        }
    }

    private static void processStunTick(LivingEntity entity, FireworkEffectComponent fx) {
        if (fx.isStunned(entity.getWorld().getTime())) {
            entity.setVelocity(0, 0, 0);
            entity.velocityModified = true;
        }
    }

    private static void processFractureDecayTick(LivingEntity entity, FireworkEffectComponent fx) {
        fx.decayFracture(entity.getWorld().getTime());
    }

    public static float onDamageReceived(LivingEntity entity, float amount) {
        FireworkEffectComponent fx = ModComponents.get(entity);
        long worldTime = entity.getWorld().getTime();

        int fracturedCount = fx.countFractured(worldTime);
        float afterFracture = amount;

        // --- Step 1: Fracture redirect (Armor → takes durability damage) ---
        if (fracturedCount > 0) {
            float multiplier = switch (fracturedCount) {
                case 1 -> 4.0f;
                case 2, 3 -> 3.0f;
                case 4 -> 2.0f;
                default -> 1.0f;
            };

            float duraPerPiece = amount * multiplier;
            boolean anyArmorExists = false;

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                if (fx.isFractured(slot, worldTime)) {
                    ItemStack armor = entity.getEquippedStack(slot);
                    if (!armor.isEmpty()) {
                        anyArmorExists = true;
                        armor.damage((int) duraPerPiece, entity, slot);
                    }
                }
            }

            // Only redirect health damage to 0 if armor actually took durability damage
            if (anyArmorExists) {
                afterFracture = 0;
            }

            if (fracturedCount >= 4 && !fx.isCrystalizedImmune(worldTime)) {
                triggerCrystalized(entity, fx, worldTime);
            }

            LOG.debug("Fracture redirect — {}, incomingDmg={}, fracturedPieces={}, anyArmor={}, resultDmg={}",
                entity.getName().getString(), amount, fracturedCount, anyArmorExists, afterFracture);
        }

        return afterFracture;
    }

    public static void triggerCrystalized(LivingEntity entity, FireworkEffectComponent fx, long worldTime) {
        LOG.info("CRYSTALIZED triggered — {}", entity.getName().getString());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack armor = entity.getEquippedStack(slot);
            if (!armor.isEmpty()) {
                // Apply a permanent 20% durability penalty
                int currentMax = armor.getMaxDamage();
                int penaltyAmount = Math.max(1, (int) (currentMax * 0.20f));
                
                int existingPenalty = armor.getOrDefault(OreFireworkItem.DURABILITY_PENALTY, 0);
                armor.set(OreFireworkItem.DURABILITY_PENALTY, existingPenalty + penaltyAmount);

                int newMax = Math.max(1, currentMax - penaltyAmount);
                if (armor.getDamage() > newMax) {
                    armor.setDamage(newMax);
                }
            }
        }

        fx.clearFracture();
        fx.setCrystalizedImmunity(worldTime, 60 * 20); // 1-minute immunity
        fx.stun(worldTime, 5 * 20); // 5-second stun

        entity.getWorld().playSound(null, entity.getBlockPos(),
            SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 2.0f, 0.5f);

        if (entity instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.literal("§d⚡ CRYSTALIZED! Armor permanently shattered."), true);
        }
    }
}
