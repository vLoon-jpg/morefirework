package com.morefirework.item;

import com.morefirework.MoreFirework;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModItems {
    // Ingots
    public static final Item DIAMOND_INGOT = register("diamond_ingot",
        new Item(new Item.Settings()));

    public static final Item AMETHYST_INGOT = register("amethyst_ingot",
        new Item(new Item.Settings()));

    // Ore Firework Rockets
    public static final OreFireworkItem DIAMOND_FIREWORK = register("diamond_firework",
        new OreFireworkItem(OreFireworkItem.OreType.DIAMOND));

    public static final OreFireworkItem IRON_FIREWORK = register("iron_firework",
        new OreFireworkItem(OreFireworkItem.OreType.IRON));

    public static final OreFireworkItem GOLD_FIREWORK = register("gold_firework",
        new OreFireworkItem(OreFireworkItem.OreType.GOLD));

    public static final OreFireworkItem EMERALD_FIREWORK = register("emerald_firework",
        new OreFireworkItem(OreFireworkItem.OreType.EMERALD));

    public static final OreFireworkItem AMETHYST_FIREWORK = register("amethyst_firework",
        new OreFireworkItem(OreFireworkItem.OreType.AMETHYST));

    // Redstone addon component
    public static final Item REDSTONE_ADDON = register("redstone_addon",
        new Item(new Item.Settings()));

    private static <T extends Item> T register(String id, T item) {
        return Registry.register(Registries.ITEM, MoreFirework.id(id), item);
    }

    public static void register() {
        MoreFirework.LOGGER.info("  Registered {} items", 8);
    }
}
