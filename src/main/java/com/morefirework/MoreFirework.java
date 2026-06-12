package com.morefirework;

import com.morefirework.item.ModItems;
import com.morefirework.effect.ModEffects;
import com.morefirework.component.ModComponents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
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
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
