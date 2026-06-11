package com.morefirework.recipe;

import com.morefirework.item.ModItems;
import com.morefirework.item.OreFireworkItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

public class OreFireworkRecipe extends SpecialCraftingRecipe {

    public OreFireworkRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        int fireworkCount = 0;
        int ingotCount = 0;
        int oreFireworkCount = 0;
        int redstoneCount = 0;

        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(Items.FIREWORK_ROCKET)) {
                FireworksComponent fw = stack.get(DataComponentTypes.FIREWORKS);
                boolean hasStar = fw != null && !fw.explosions().isEmpty();
                if (hasStar) {
                    fireworkCount++;
                } else {
                    return false;
                }
            } else if (stack.getItem() instanceof OreFireworkItem) {
                oreFireworkCount++;
            } else if (stack.isOf(ModItems.REDSTONE_ADDON)) {
                redstoneCount++;
            } else if (isIngot(stack)) {
                ingotCount++;
            } else {
                return false;
            }
        }

        if (fireworkCount == 1 && ingotCount == 1 && oreFireworkCount == 0 && redstoneCount == 0) {
            return true;
        }

        if (oreFireworkCount == 1 && redstoneCount == 1 && fireworkCount == 0 && ingotCount == 0) {
            for (int i = 0; i < input.getSize(); i++) {
                ItemStack stack = input.getStackInSlot(i);
                if (stack.getItem() instanceof OreFireworkItem) {
                    return !OreFireworkItem.hasRedstone(stack);
                }
            }
        }

        return false;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack fireworkStack = ItemStack.EMPTY;
        ItemStack ingotStack = ItemStack.EMPTY;
        ItemStack oreFireworkStack = ItemStack.EMPTY;

        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(Items.FIREWORK_ROCKET)) {
                fireworkStack = stack;
            } else if (stack.getItem() instanceof OreFireworkItem) {
                oreFireworkStack = stack;
            } else if (isIngot(stack)) {
                ingotStack = stack;
            }
        }

        if (!oreFireworkStack.isEmpty()) {
            ItemStack result = oreFireworkStack.copy();
            result.setCount(1);
            result.set(OreFireworkItem.HAS_REDSTONE, true);
            return result;
        }

        if (!fireworkStack.isEmpty() && !ingotStack.isEmpty()) {
            net.minecraft.item.Item resultItem = getResultItem(ingotStack);
            if (resultItem == null) return ItemStack.EMPTY;

            ItemStack result = new ItemStack(resultItem);
            result.set(DataComponentTypes.FIREWORKS, fireworkStack.get(DataComponentTypes.FIREWORKS));
            result.set(DataComponentTypes.CUSTOM_NAME, fireworkStack.get(DataComponentTypes.CUSTOM_NAME));
            result.set(DataComponentTypes.LORE, fireworkStack.get(DataComponentTypes.LORE));

            OreFireworkItem.OreType type = getOreType(ingotStack);
            if (type != null) {
                result.set(OreFireworkItem.ORE_TYPE, type.name());
            }

            FireworksComponent fw = fireworkStack.get(DataComponentTypes.FIREWORKS);
            if (fw != null) {
                result.set(OreFireworkItem.GUNPOWDER_LEVEL, fw.flightDuration());
            }

            return result;
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return com.morefirework.MoreFirework.ORE_FIREWORK_SERIALIZER;
    }

    private boolean isIngot(ItemStack stack) {
        return stack.isOf(Items.IRON_INGOT)
            || stack.isOf(ModItems.DIAMOND_INGOT)
            || stack.isOf(Items.GOLD_INGOT)
            || stack.isOf(Items.EMERALD)
            || stack.isOf(ModItems.AMETHYST_INGOT);
    }

    private OreFireworkItem.OreType getOreType(ItemStack ingot) {
        if (ingot.isOf(Items.IRON_INGOT)) return OreFireworkItem.OreType.IRON;
        if (ingot.isOf(ModItems.DIAMOND_INGOT)) return OreFireworkItem.OreType.DIAMOND;
        if (ingot.isOf(Items.GOLD_INGOT)) return OreFireworkItem.OreType.GOLD;
        if (ingot.isOf(Items.EMERALD)) return OreFireworkItem.OreType.EMERALD;
        if (ingot.isOf(ModItems.AMETHYST_INGOT)) return OreFireworkItem.OreType.AMETHYST;
        return null;
    }

    private net.minecraft.item.Item getResultItem(ItemStack ingot) {
        if (ingot.isOf(Items.IRON_INGOT)) return ModItems.IRON_FIREWORK;
        if (ingot.isOf(ModItems.DIAMOND_INGOT)) return ModItems.DIAMOND_FIREWORK;
        if (ingot.isOf(Items.GOLD_INGOT)) return ModItems.GOLD_FIREWORK;
        if (ingot.isOf(Items.EMERALD)) return ModItems.EMERALD_FIREWORK;
        if (ingot.isOf(ModItems.AMETHYST_INGOT)) return ModItems.AMETHYST_FIREWORK;
        return null;
    }
}
