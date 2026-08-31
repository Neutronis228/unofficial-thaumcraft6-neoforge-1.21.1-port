package thaumcraft.client.integration.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.crafting.arcane.TCArcaneRecipe;
import thaumcraft.common.crafting.crucible.TCCrucibleRecipe;
import thaumcraft.common.crafting.infusion.TCInfusionRecipe;
import thaumcraft.common.items.TCAspectVariantStacks;
import thaumcraft.common.registry.TCBlocks;
import thaumcraft.common.registry.TCRecipes;

/** Native EMI presentation for Thaumcraft's non-vanilla recipe families. */
@EmiEntrypoint
public final class ThaumcraftEmiPlugin implements EmiPlugin {
    private static final EmiRecipeCategory ARCANE = new EmiRecipeCategory(
            id("arcane"), EmiStack.of(TCBlocks.ARCANE_WORKBENCH.get())
    );
    private static final EmiRecipeCategory CRUCIBLE = new EmiRecipeCategory(
            id("crucible"), EmiStack.of(TCBlocks.CRUCIBLE.get())
    );
    private static final EmiRecipeCategory INFUSION = new EmiRecipeCategory(
            id("infusion"), EmiStack.of(TCBlocks.INFUSION_MATRIX.get())
    );

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(ARCANE);
        registry.addCategory(CRUCIBLE);
        registry.addCategory(INFUSION);
        registry.addWorkstation(ARCANE, EmiStack.of(TCBlocks.ARCANE_WORKBENCH.get()));
        registry.addWorkstation(CRUCIBLE, EmiStack.of(TCBlocks.CRUCIBLE.get()));
        registry.addWorkstation(INFUSION, EmiStack.of(TCBlocks.INFUSION_MATRIX.get()));

        registry.getRecipeManager().getAllRecipesFor(TCRecipes.ARCANE_TYPE.get())
                .forEach(holder -> registry.addRecipe(new ArcaneEmiRecipe(holder.id(), holder.value())));
        registry.getRecipeManager().getAllRecipesFor(TCRecipes.CRUCIBLE_TYPE.get())
                .forEach(holder -> registry.addRecipe(new CrucibleEmiRecipe(holder.id(), holder.value())));
        registry.getRecipeManager().getAllRecipesFor(TCRecipes.INFUSION_TYPE.get())
                .forEach(holder -> registry.addRecipe(new InfusionEmiRecipe(holder.id(), holder.value())));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path);
    }

    private static EmiIngredient ingredient(Ingredient ingredient) {
        return ingredient == null || ingredient.isEmpty() ? EmiStack.EMPTY : EmiIngredient.of(ingredient);
    }

    private static EmiStack aspectStack(String aspectTag, int amount, boolean crystal) {
        Aspect aspect = Aspect.getAspect(aspectTag);
        if (aspect == null) {
            return EmiStack.EMPTY;
        }
        ItemStack stack = crystal
                ? TCAspectVariantStacks.crystal(aspect)
                : TCAspectVariantStacks.phial(aspect);
        return stack.isEmpty() ? EmiStack.EMPTY : EmiStack.of(stack, amount);
    }

    private static final class ArcaneEmiRecipe extends BasicEmiRecipe {
        private final TCArcaneRecipe recipe;
        private final List<EmiIngredient> grid;
        private final List<EmiStack> crystals;

        private ArcaneEmiRecipe(ResourceLocation id, TCArcaneRecipe recipe) {
            super(ARCANE, id, 150, 96);
            this.recipe = recipe;
            this.grid = new ArrayList<>();
            for (Ingredient source : recipe.getIngredients()) {
                EmiIngredient converted = ingredient(source);
                grid.add(converted);
                if (!converted.isEmpty()) {
                    inputs.add(converted);
                }
            }
            this.crystals = recipe.crystalCosts().stream()
                    .map(cost -> aspectStack(cost.aspect(), cost.amount(), true))
                    .filter(stack -> !stack.isEmpty())
                    .toList();
            inputs.addAll(crystals);
            outputs.add(EmiStack.of(recipe.result()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addText(
                    Component.literal("Vis " + recipe.getVis() + "  •  " + recipe.getResearch()),
                    4, 4, 0x665522, false
            );
            int columns = Math.max(1, Math.min(3, recipe.width()));
            for (int index = 0; index < grid.size(); index++) {
                widgets.addSlot(grid.get(index), 4 + (index % columns) * 18, 18 + (index / columns) * 18);
            }
            widgets.addFillingArrow(96, 37, 20);
            widgets.addSlot(outputs.getFirst(), 126, 36).recipeContext(this);
            for (int index = 0; index < crystals.size() && index < 8; index++) {
                widgets.addSlot(crystals.get(index), 4 + index * 18, 74);
            }
        }
    }

    private static final class CrucibleEmiRecipe extends BasicEmiRecipe {
        private final TCCrucibleRecipe recipe;
        private final EmiIngredient catalyst;
        private final List<EmiStack> aspects;

        private CrucibleEmiRecipe(ResourceLocation id, TCCrucibleRecipe recipe) {
            super(CRUCIBLE, id, 150, 74);
            this.recipe = recipe;
            this.catalyst = ingredient(recipe.catalyst());
            this.aspects = recipe.aspectCosts().stream()
                    .map(cost -> aspectStack(cost.aspect(), cost.amount(), false))
                    .filter(stack -> !stack.isEmpty())
                    .toList();
            inputs.add(catalyst);
            inputs.addAll(aspects);
            outputs.add(EmiStack.of(recipe.result()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addText(Component.literal(recipe.getResearch()), 4, 4, 0x665522, false);
            widgets.addSlot(catalyst, 10, 28);
            widgets.addFillingArrow(60, 27, 20);
            widgets.addSlot(outputs.getFirst(), 116, 27).recipeContext(this);
            for (int index = 0; index < aspects.size() && index < 7; index++) {
                widgets.addSlot(aspects.get(index), 4 + index * 18, 52);
            }
        }
    }

    private static final class InfusionEmiRecipe extends BasicEmiRecipe {
        private final TCInfusionRecipe recipe;
        private final EmiIngredient catalyst;
        private final List<EmiIngredient> components;
        private final List<EmiStack> aspects;

        private InfusionEmiRecipe(ResourceLocation id, TCInfusionRecipe recipe) {
            super(INFUSION, id, 168, 112);
            this.recipe = recipe;
            this.catalyst = ingredient(recipe.catalyst());
            this.components = recipe.components().stream().map(ThaumcraftEmiPlugin::ingredient).toList();
            this.aspects = recipe.aspectCosts().stream()
                    .map(cost -> aspectStack(cost.aspect(), cost.amount(), false))
                    .filter(stack -> !stack.isEmpty())
                    .toList();
            inputs.add(catalyst);
            inputs.addAll(components);
            inputs.addAll(aspects);
            outputs.add(EmiStack.of(recipe.result()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addText(
                    Component.literal("Instability " + recipe.instability() + "  •  " + recipe.getResearch()),
                    4, 4, 0x665522, false
            );
            widgets.addSlot(catalyst, 4, 28);
            for (int index = 0; index < components.size() && index < 12; index++) {
                widgets.addSlot(components.get(index), 28 + (index % 6) * 18, 19 + (index / 6) * 18);
            }
            widgets.addFillingArrow(112, 58, 20);
            widgets.addSlot(outputs.getFirst(), 144, 57).recipeContext(this);
            for (int index = 0; index < aspects.size() && index < 9; index++) {
                widgets.addSlot(aspects.get(index), 4 + index * 18, 88);
            }
        }
    }
}
