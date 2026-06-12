package com.morefirework;

import com.morefirework.item.ModItems;
import com.morefirework.item.OreFireworkItem;
import com.morefirework.effect.SeekerBehavior;
import com.morefirework.effect.ModEffects;
import com.morefirework.component.ModComponents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoreFirework implements ModInitializer {
    public static final String MOD_ID = "morefirework";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final RegistryKey<ItemGroup> ITEM_GROUP = RegistryKey.of(
        RegistryKeys.ITEM_GROUP,
        Identifier.of(MOD_ID, "morefirework_group")
    );

    public static final net.minecraft.recipe.RecipeSerializer<com.morefirework.recipe.OreFireworkRecipe> ORE_FIREWORK_SERIALIZER = Registry.register(
        Registries.RECIPE_SERIALIZER,
        Identifier.of(MOD_ID, "ore_firework"),
        new net.minecraft.recipe.SpecialRecipeSerializer<>(com.morefirework.recipe.OreFireworkRecipe::new)
    );

    @Override
    public void onInitialize() {
        LOGGER.info("More Firework — combat reimagined.");
        LOGGER.info("Registering items, effects, and components...");

        ModItems.register();
        ModEffects.register();
        ModComponents.register();

        // Periodic cleanup of stale claim counts every 200 ticks (~10s)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 200 == 0) {
                SeekerBehavior.SeekerData.purgeStaleEntries();
            }
        });

        Registry.register(Registries.ITEM_GROUP, ITEM_GROUP,
            FabricItemGroup.builder()
                .displayName(Text.literal("More Firework"))
                .icon(() -> new ItemStack(ModItems.EMERALD_FIREWORK))
                .build()
        );

        ItemGroupEvents.modifyEntriesEvent(ITEM_GROUP).register(entries -> {
            entries.add(ModItems.DIAMOND_INGOT);
            entries.add(ModItems.AMETHYST_INGOT);
            entries.add(ModItems.REDSTONE_ADDON);
            
            // Standard Ore Rockets
            entries.add(ModItems.DIAMOND_FIREWORK.getDefaultStack());
            entries.add(ModItems.IRON_FIREWORK.getDefaultStack());
            entries.add(ModItems.GOLD_FIREWORK.getDefaultStack());
            entries.add(ModItems.EMERALD_FIREWORK.getDefaultStack());
            entries.add(ModItems.AMETHYST_FIREWORK.getDefaultStack());

            // Seeker (Redstoned) Ore Rockets
            entries.add(com.morefirework.item.OreFireworkItem.setRedstone(ModItems.DIAMOND_FIREWORK.getDefaultStack(), true));
            entries.add(com.morefirework.item.OreFireworkItem.setRedstone(ModItems.IRON_FIREWORK.getDefaultStack(), true));
            entries.add(com.morefirework.item.OreFireworkItem.setRedstone(ModItems.GOLD_FIREWORK.getDefaultStack(), true));
            entries.add(com.morefirework.item.OreFireworkItem.setRedstone(ModItems.EMERALD_FIREWORK.getDefaultStack(), true));
            entries.add(com.morefirework.item.OreFireworkItem.setRedstone(ModItems.AMETHYST_FIREWORK.getDefaultStack(), true));
        });

        LOGGER.info("More Firework initialized successfully.");

        // Register dispenser behavior for all ore rockets
        ItemDispenserBehavior seekerDispenserBehavior = new ItemDispenserBehavior() {
            @Override
            protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
                if (!(stack.getItem() instanceof OreFireworkItem) || !OreFireworkItem.hasRedstone(stack)) {
                    return super.dispenseSilently(pointer, stack);
                }
                Direction facing = pointer.state().get(DispenserBlock.FACING);
                Vec3d pos = pointer.pos().toCenterPos().offset(facing, 0.6);
                ItemStack toSpawn = stack.copy();
                OreFireworkItem.ensureFireworks(toSpawn);
                FireworkRocketEntity rocket = new FireworkRocketEntity(
                    pointer.world(), toSpawn,
                    pos.x, pos.y, pos.z,
                    false
                );
                Vec3d dir = Vec3d.of(facing.getVector());
                rocket.setVelocity(dir.multiply(0.13));
                pointer.world().spawnEntity(rocket);
                // Assign a random unclaimed target from the area
                SeekerBehavior.assignRandomTarget(pointer.world(), rocket, null);
                // Mark as dispensed — no owner, locks onto anyone
                SeekerBehavior.SeekerData.getOrCreate(rocket).placedOrDispensed = true;
                stack.decrement(1);
                return stack;
            }
        };
        for (OreFireworkItem item : new OreFireworkItem[]{
            ModItems.DIAMOND_FIREWORK, ModItems.IRON_FIREWORK, ModItems.GOLD_FIREWORK,
            ModItems.EMERALD_FIREWORK, ModItems.AMETHYST_FIREWORK
        }) {
            DispenserBlock.registerBehavior(item, seekerDispenserBehavior);
        }
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
