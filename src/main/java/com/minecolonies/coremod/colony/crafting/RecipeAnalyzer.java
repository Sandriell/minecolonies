package com.minecolonies.coremod.colony.crafting;

import com.google.common.collect.ImmutableMap;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility helpers for analyzing the available recipes and determining which crafters are able to use them.
 */
public final class RecipeAnalyzer
{
    /**
     * Build a map of all potentially learnable vanilla recipes, converted to {@link IGenericRecipe}.
     *
     * @param recipeManager the vanilla recipe manager
     * @return the recipe map
     */
    public static Map<RecipeType<?>, List<IGenericRecipe>> buildVanillaRecipesMap(@NotNull final RecipeManager recipeManager,
                                                                                  @Nullable final Level world)
    {
        final List<IGenericRecipe> craftingRecipes = new ArrayList<>();
        for (final Recipe<CraftingContainer> recipe : recipeManager.byType(RecipeType.CRAFTING).values())
        {
            if (!recipe.canCraftInDimensions(3, 3)) continue;

            tryAddingVanillaRecipe(craftingRecipes, recipe, world);
        }

        final List<IGenericRecipe> smeltingRecipes = new ArrayList<>();
        for (final Recipe<Container> recipe : recipeManager.byType(RecipeType.SMELTING).values())
        {
            tryAddingVanillaRecipe(smeltingRecipes, recipe, world);
        }

        return new ImmutableMap.Builder<RecipeType<?>, List<IGenericRecipe>>()
                .put(RecipeType.CRAFTING, craftingRecipes)
                .put(RecipeType.SMELTING, smeltingRecipes)
                .build();
    }

    private static void tryAddingVanillaRecipe(@NotNull final List<IGenericRecipe> recipes,
                                               @NotNull final Recipe<?> recipe,
                                               @Nullable final Level world)
    {
        if (recipe.getResultItem().isEmpty()) return;     // invalid or special recipes

        try
        {
            final IGenericRecipe genericRecipe = GenericRecipeUtils.create(recipe, world);
            if (genericRecipe.getInputs().isEmpty()) return;

            recipes.add(genericRecipe);
        }
        catch (final Exception ex)
        {
            Log.getLogger().warn("Error evaluating recipe " + recipe.getId() + "; ignoring.", ex);
        }
    }

    /**
     * Find all recipes for a given crafter.
     *
     * @param vanilla vanilla recipes map.
     * @param crafting crafting module.
     * @return list of recipes
     */
    @NotNull
    public static List<IGenericRecipe> findRecipes(@NotNull final Map<RecipeType<?>, List<IGenericRecipe>> vanilla,
                                                   @NotNull final ICraftingBuildingModule crafting)
    {
        final List<IGenericRecipe> recipes = new ArrayList<>();

        // vanilla shaped and shapeless crafting recipes
        if (crafting.canLearnCraftingRecipes())
        {
            for (final IGenericRecipe recipe : vanilla.get(RecipeType.CRAFTING))
            {
                if (!crafting.canLearnLargeRecipes() && recipe.getGridSize() > 2) continue;

                final IGenericRecipe safeRecipe = GenericRecipeUtils.filterInputs(recipe, crafting.getIngredientValidator());
                if (!crafting.isRecipeCompatible(safeRecipe)) continue;

                recipes.add(safeRecipe);
            }
        }

        // vanilla furnace recipes (do we want to check smoking and blasting too?)
        if (crafting.canLearnFurnaceRecipes())
        {
            for (final IGenericRecipe recipe : vanilla.get(RecipeType.SMELTING))
            {
                final IGenericRecipe safeRecipe = GenericRecipeUtils.filterInputs(recipe, crafting.getIngredientValidator());
                if (!crafting.isRecipeCompatible(safeRecipe)) continue;

                recipes.add(safeRecipe);
            }
        }

        // custom MineColonies additional recipes
        for (final CustomRecipe customRecipe : CustomRecipeManager.getInstance().getRecipes(crafting.getCustomRecipeKey()))
        {
            final IRecipeStorage recipeStorage = customRecipe.getRecipeStorage();
            if (!recipeStorage.getAlternateOutputs().isEmpty())
            {
                // this is a multi-output recipe; assume it replaces a bunch of vanilla
                // recipes we already added above
                recipes.removeIf(r -> ItemStackUtils.compareItemStacksIgnoreStackSize(recipeStorage.getPrimaryOutput(), r.getPrimaryOutput()));
                recipes.removeIf(r -> recipeStorage.getAlternateOutputs().stream()
                        .anyMatch(s -> ItemStackUtils.compareItemStacksIgnoreStackSize(s, r.getPrimaryOutput())));
            }
            recipes.add(GenericRecipeUtils.create(customRecipe, recipeStorage));
        }

        // and even more recipes that can't be taught, but are just inherent in the worker AI
        recipes.addAll(crafting.getAdditionalRecipesForDisplayPurposesOnly());

        return recipes;
    }

    private RecipeAnalyzer()
    {
        /*
         * Intentionally left empty.
         */
    }
}
