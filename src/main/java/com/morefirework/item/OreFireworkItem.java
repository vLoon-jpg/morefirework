package com.morefirework.item;

import com.mojang.serialization.Codec;
import com.morefirework.MoreFirework;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.List;

public class OreFireworkItem extends FireworkRocketItem {
    private final OreType oreType;

    public static final ComponentType<Integer> GUNPOWDER_LEVEL = Registry.register(
        Registries.DATA_COMPONENT_TYPE,
        MoreFirework.id("gunpowder_level"),
        ComponentType.<Integer>builder().codec(Codec.INT).build()
    );

    public static final ComponentType<Boolean> HAS_REDSTONE = Registry.register(
        Registries.DATA_COMPONENT_TYPE,
        MoreFirework.id("has_redstone"),
        ComponentType.<Boolean>builder().codec(Codec.BOOL).build()
    );

    public static final ComponentType<String> ORE_TYPE = Registry.register(
        Registries.DATA_COMPONENT_TYPE,
        MoreFirework.id("ore_type"),
        ComponentType.<String>builder().codec(Codec.STRING).build()
    );

    public static final ComponentType<Integer> DURABILITY_PENALTY = Registry.register(
        Registries.DATA_COMPONENT_TYPE,
        MoreFirework.id("durability_penalty"),
        ComponentType.<Integer>builder().codec(Codec.INT).build()
    );

    public OreFireworkItem(OreType oreType) {
        super(new Item.Settings().maxCount(16));
        this.oreType = oreType;
    }

    public OreType getOreType() {
        return oreType;
    }

    @Override
    public ItemStack getDefaultStack() {
        return ensureFireworks(super.getDefaultStack());
    }

    @Override
    public Text getName(ItemStack stack) {
        String base = oreType.displayName;
        boolean hasRedstone = Boolean.TRUE.equals(stack.get(HAS_REDSTONE));
        if (hasRedstone) {
            return Text.literal("Seeker " + base + " Rocket");
        }
        return Text.literal(base + " Rocket");
    }

    public static OreType getOreType(ItemStack stack) {
        String type = stack.get(ORE_TYPE);
        if (type != null) {
            return OreType.valueOf(type);
        }
        if (stack.getItem() instanceof OreFireworkItem ofi) {
            return ofi.oreType;
        }
        return null;
    }

    public static int getGunpowderLevel(ItemStack stack) {
        Integer level = stack.get(GUNPOWDER_LEVEL);
        return level != null ? level : 0;
    }

    public static boolean hasRedstone(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(HAS_REDSTONE));
    }

    public static ItemStack setGunpowderLevel(ItemStack stack, int level) {
        stack.set(GUNPOWDER_LEVEL, Math.min(level, 2));
        return stack;
    }

    public static ItemStack setRedstone(ItemStack stack, boolean flag) {
        stack.set(HAS_REDSTONE, flag);
        return stack;
    }

    /**
     * Ensures every OreFireworkItem stack has FIREWORKS explosion data.
     * Creative menu items have none — this gives them a default white burst.
     * Crafted items (made from dyed rockets) already have FIREWORKS — we leave them.
     */
    public static ItemStack ensureFireworks(ItemStack stack) {
        FireworksComponent fw = stack.get(DataComponentTypes.FIREWORKS);
        if (fw == null || fw.explosions().isEmpty()) {
            IntList colors = IntArrayList.of(0xFFFFFF);
            var defaultBurst = new FireworkExplosionComponent(
                FireworkExplosionComponent.Type.BURST,
                colors, colors, false, false
            );
            stack.set(DataComponentTypes.FIREWORKS,
                new FireworksComponent(10, List.of(defaultBurst)));
        }
        return stack;
    }

    public enum OreType {
        DIAMOND("Diamond"),
        IRON("Iron"),
        GOLD("Gold"),
        EMERALD("Emerald"),
        AMETHYST("Amethyst");

        public final String displayName;

        OreType(String displayName) {
            this.displayName = displayName;
        }
    }
}
