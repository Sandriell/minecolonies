package com.minecolonies.coremod.compatibility.jei;

import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.coremod.colony.crafting.CustomRecipeManager;
import com.minecolonies.coremod.colony.crafting.LootTableAnalyzer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.minecolonies.coremod.colony.crafting.RecipeAnalyzer;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IGuiItemStackGroup;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The main JEI recipe category GUI implementation for IGenericRecipe.
 */
@OnlyIn(Dist.CLIENT)
public class GenericRecipeCategory extends JobBasedRecipeCategory<IGenericRecipe>
{
    public GenericRecipeCategory(@NotNull final BuildingEntry building,
                                 @NotNull final IJob<?> job,
                                 @NotNull final ICraftingBuildingModule crafting,
                                 @NotNull final IGuiHelper guiHelper,
                                 @NotNull final IModIdHelper modIdHelper)
    {
        super(job, Objects.requireNonNull(crafting.getUid()), getCatalyst(building), guiHelper);

        this.building = building;
        this.crafting = crafting;
        this.arrow = guiHelper.createDrawable(TEXTURE, 20, 121, 24, 18);
        this.modIdHelper = modIdHelper;

        outputSlotX = CITIZEN_X + CITIZEN_W + 2 + (30 - this.slot.getWidth()) / 2;
        outputSlotY = CITIZEN_Y + CITIZEN_H + 1 - this.slot.getHeight();
    }

    @NotNull private final BuildingEntry building;
    @NotNull private final ICraftingBuildingModule crafting;
    @NotNull private final IDrawableStatic arrow;
    @NotNull private final IModIdHelper modIdHelper;

    private static final int LOOT_SLOTS_X = CITIZEN_X + CITIZEN_W + 4;
    private static final int LOOT_SLOTS_W = WIDTH - LOOT_SLOTS_X;
    private static final int INPUT_SLOT_X = CITIZEN_X + CITIZEN_W + 32;
    private static final int INPUT_SLOT_W = WIDTH - INPUT_SLOT_X;
    private final int outputSlotX;
    private final int outputSlotY;

    @NotNull
    @Override
    public Class<? extends IGenericRecipe> getRecipeClass()
    {
        return IGenericRecipe.class;
    }

    @NotNull
    @Override
    protected List<Component> generateInfoBlocks(@NotNull IGenericRecipe recipe)
    {
        return recipe.getRestrictions();
    }

    @Override
    public void setIngredients(@NotNull final IGenericRecipe recipe, @NotNull final IIngredients ingredients)
    {
        final List<List<ItemStack>> outputs = new ArrayList<>();
        if (!isLootBasedRecipe(recipe))
        {
            outputs.add(recipe.getAllMultiOutputs());
        }
        outputs.addAll(recipe.getAdditionalOutputs().stream().map(Collections::singletonList).collect(Collectors.toList()));

        if (recipe.getLootTable() != null)
        {
            final List<LootTableAnalyzer.LootDrop> drops = getLootDrops(recipe.getLootTable());
            outputs.addAll(drops.stream().map(LootTableAnalyzer.LootDrop::getItemStacks).collect(Collectors.toList()));
        }

        ingredients.setInputLists(VanillaTypes.ITEM, recipe.getInputs());
        ingredients.setOutputLists(VanillaTypes.ITEM, outputs);
    }

    @Override
    public void setRecipe(@NotNull final IRecipeLayout layout, @NotNull final IGenericRecipe recipe, @NotNull final IIngredients ingredients)
    {
        if (isLootBasedRecipe(recipe))
        {
            setLootBasedRecipe(layout, recipe, ingredients);
        }
        else
        {
            setNormalRecipe(layout, recipe, ingredients);
        }
    }

    private void setNormalRecipe(@NotNull final IRecipeLayout layout, @NotNull final IGenericRecipe recipe, @NotNull final IIngredients ingredients)
    {
        final IGuiItemStackGroup guiItemStacks = layout.getItemStacks();
        final ResourceLocation id = recipe.getRecipeId();

        int x = outputSlotX;
        int y = outputSlotY;
        int slot = 0;
        guiItemStacks.init(slot, false, x, y);
        guiItemStacks.setBackground(slot, this.slot);
        guiItemStacks.set(slot, ingredients.getOutputs(VanillaTypes.ITEM).get(0));
        if (id != null)
        {
            guiItemStacks.addTooltipCallback(new RecipeIdTooltipCallback(slot, id, this.modIdHelper));
        }
        x += this.slot.getWidth();
        ++slot;

        for (final ItemStack extra : recipe.getAdditionalOutputs())
        {
            guiItemStacks.init(slot, false, x, y);
            guiItemStacks.setBackground(slot, this.slot);
            guiItemStacks.set(slot, extra);
            x += this.slot.getWidth();
            ++slot;
        }

        if (recipe.getLootTable() != null)
        {
            final List<LootTableAnalyzer.LootDrop> drops = getLootDrops(recipe.getLootTable());
            guiItemStacks.addTooltipCallback(new LootTableTooltipCallback(slot, drops, recipe.getLootTable()));
            for (final LootTableAnalyzer.LootDrop drop : drops)
            {
                guiItemStacks.init(slot, false, x, y);
                guiItemStacks.setBackground(slot, this.chanceSlot);
                guiItemStacks.set(slot, drop.getItemStacks());
                x += this.chanceSlot.getWidth();
                ++slot;
            }
        }

        final List<List<ItemStack>> inputs = recipe.getInputs();
        if (!inputs.isEmpty())
        {
            final int initialInputColumns = INPUT_SLOT_W / this.slot.getWidth();
            final int inputRows = (inputs.size() + initialInputColumns - 1) / initialInputColumns;
            final int inputColumns = (inputs.size() + inputRows - 1) / inputRows;

            x = INPUT_SLOT_X;
            y = CITIZEN_Y + (CITIZEN_H - inputRows * this.slot.getHeight()) / 2;
            int c = 0;
            for (final List<ItemStack> input : inputs)
            {
                guiItemStacks.init(slot, true, x, y);
                guiItemStacks.set(slot, input);
                ++slot;
                if (++c >= inputColumns)
                {
                    c = 0;
                    x = INPUT_SLOT_X;
                    y += this.slot.getHeight();
                }
                else
                {
                    x += this.slot.getWidth();
                }
            }
        }
    }

    private void setLootBasedRecipe(@NotNull final IRecipeLayout layout, @NotNull final IGenericRecipe recipe, @NotNull final IIngredients ingredients)
    {
        assert recipe.getLootTable() != null;
        final IGuiItemStackGroup guiItemStacks = layout.getItemStacks();
        final List<LootTableAnalyzer.LootDrop> drops = getLootDrops(recipe.getLootTable());
        final ResourceLocation id = recipe.getRecipeId();

        int x = LOOT_SLOTS_X;
        int y = CITIZEN_Y;
        int slot = 0;

        final List<List<ItemStack>> inputs = recipe.getInputs();
        if (!inputs.isEmpty())
        {
            for (final List<ItemStack> input : inputs)
            {
                guiItemStacks.init(slot, false, x, y);
                guiItemStacks.set(slot, input);
                ++slot;

                x += this.slot.getWidth() + 2;
            }
        }

        boolean showLootTooltip = true;
        if (drops.isEmpty())
        {
            // this is a temporary workaround for cases where we currently fail to load the loot table
            // (mostly when it's in a datapack).  assume that someone has set the alternate-outputs
            // appropriately, but we can't display the percentage chances.
            showLootTooltip = false;
            drops.addAll(recipe.getAdditionalOutputs().stream()
                    .map(stack -> new LootTableAnalyzer.LootDrop(Collections.singletonList(stack), 0, 0, false))
                    .collect(Collectors.toList()));
        }
        if (!drops.isEmpty())
        {
            final int initialColumns = LOOT_SLOTS_W / this.slot.getWidth();
            final int rows = (drops.size() + initialColumns - 1) / initialColumns;
            final int columns = (drops.size() + rows - 1) / rows;
            final int startX = LOOT_SLOTS_X + (LOOT_SLOTS_W - (columns * this.slot.getWidth())) / 2;
            x = startX;
            y = CITIZEN_Y + CITIZEN_H - rows * this.slot.getHeight() + 1;
            int c = 0;

            if (showLootTooltip)
            {
                guiItemStacks.addTooltipCallback(new LootTableTooltipCallback(slot, drops, recipe.getLootTable()));
            }
            for (final LootTableAnalyzer.LootDrop drop : drops)
            {
                guiItemStacks.init(slot, true, x, y);
                guiItemStacks.setBackground(slot, this.chanceSlot);
                guiItemStacks.set(slot, drop.getItemStacks());
                if (id != null)
                {
                    guiItemStacks.addTooltipCallback(new RecipeIdTooltipCallback(slot, id, this.modIdHelper));
                }
                ++slot;
                if (++c >= columns)
                {
                    c = 0;
                    x = startX;
                    y += this.slot.getHeight();
                } else
                {
                    x += this.slot.getWidth();
                }
            }
        }
    }

    @Override
    public void draw(@NotNull final IGenericRecipe recipe, @NotNull final PoseStack matrixStack, final double mouseX, final double mouseY)
    {
        super.draw(recipe, matrixStack, mouseX, mouseY);

        if (!isLootBasedRecipe(recipe))
        {
            this.arrow.draw(matrixStack, CITIZEN_X + CITIZEN_W + 4, CITIZEN_Y + (CITIZEN_H - this.arrow.getHeight()) / 2);
        }

        if (recipe.getIntermediate() != Blocks.AIR)
        {
            final BlockState block = recipe.getIntermediate().defaultBlockState();
            RenderHelper.renderBlock(matrixStack, block, outputSlotX + 8, CITIZEN_Y + 6, 100, -30F, 30F, 16F);
        }
    }

    @NotNull
    @Override
    public List<Component> getTooltipStrings(@NotNull final IGenericRecipe recipe, final double mouseX, final double mouseY)
    {
        final List<Component> tooltips = new ArrayList<>(super.getTooltipStrings(recipe, mouseX, mouseY));

        if (recipe.getIntermediate() != Blocks.AIR)
        {
            if (new Rect2i(CITIZEN_X + CITIZEN_W + 4, CITIZEN_Y - 2, 24, 24).contains((int) mouseX, (int) mouseY))
            {
                tooltips.add(Component.translatable(TranslationConstants.PARTIAL_JEI_INFO + "intermediate.tip", recipe.getIntermediate().getName()));
            }
        }

        return tooltips;
    }

    private static boolean isLootBasedRecipe(@NotNull final IGenericRecipe recipe)
    {
        return recipe.getLootTable() != null && recipe.getPrimaryOutput().isEmpty();
    }

    private static List<LootTableAnalyzer.LootDrop> getLootDrops(@NotNull final ResourceLocation lootTableId)
    {
        final List<LootTableAnalyzer.LootDrop> drops = CustomRecipeManager.getInstance().getLootDrops(lootTableId);
        return drops.size() > 18 ? LootTableAnalyzer.consolidate(drops) : drops;
    }

    @NotNull
    public List<IGenericRecipe> findRecipes(@NotNull final Map<CraftingType, List<IGenericRecipe>> vanilla)
    {
        final List<IGenericRecipe> recipes = RecipeAnalyzer.findRecipes(vanilla, this.crafting);

        return recipes.stream()
                .sorted(Comparator.comparing(IGenericRecipe::getLevelSort)
                    .thenComparing(r -> r.getPrimaryOutput().getItem().getRegistryName()))
                .collect(Collectors.toList());
    }
}