package com.gtnewhorizons.neirecipepanel.client.recipe;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeHandlerRef;

final class PinnedRecipeHandler implements IRecipeHandler {

    static RecipeHandlerRef unwrap(IRecipeHandler handler, int index) {
        if (handler instanceof PinnedRecipeHandler) {
            PinnedRecipeHandler single = (PinnedRecipeHandler) handler;
            return RecipeHandlerRef.of(single.handler, single.index);
        }
        return RecipeHandlerRef.of(handler, index);
    }

    private final ResolvedRecipe recipe;
    private final IRecipeHandler handler;
    private final int index;

    PinnedRecipeHandler(ResolvedRecipe recipe) {
        this.recipe = recipe;
        handler = recipe.handler;
        index = recipe.index;
    }

    @Override
    public String getHandlerId() {
        return GuiRecipeTab.getHandlerInfo(handler)
            .getHandlerName();
    }

    @Override
    public String getRecipeName() {
        return handler.getRecipeName();
    }

    @Override
    public String getRecipeTabName() {
        return handler.getRecipeTabName();
    }

    @Override
    public int numRecipes() {
        return 1;
    }

    @Override
    public int getRecipeHeight(int ignored) {
        return handler.getRecipeHeight(index);
    }

    @Override
    public void drawBackground(int ignored) {
        handler.drawBackground(index);
    }

    @Override
    public void drawForeground(int ignored) {
        handler.drawForeground(index);
    }

    @Override
    public List<PositionedStack> getIngredientStacks(int ignored) {
        return pinned(recipe.inputs());
    }

    @Override
    public List<PositionedStack> getOtherStacks(int ignored) {
        return pinned(recipe.others());
    }

    @Override
    public PositionedStack getResultStack(int ignored) {
        return recipe.result() == null ? null : pin(recipe.result());
    }

    @Override
    public String getOverlayIdentifier() {
        return handler.getOverlayIdentifier();
    }

    @Override
    public boolean hasOverlay(GuiContainer gui, Container container, int ignored) {
        return handler.hasOverlay(gui, container, index);
    }

    @Override
    public IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int ignored) {
        return handler.getOverlayRenderer(gui, index);
    }

    @Override
    public IOverlayHandler getOverlayHandler(GuiContainer gui, int ignored) {
        return handler.getOverlayHandler(gui, index);
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> tooltip, int ignored) {
        return handler.handleTooltip(gui, tooltip, index);
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> tooltip, int ignored) {
        return handler.handleItemTooltip(gui, stack, tooltip, index);
    }

    @Override
    public boolean keyTyped(GuiRecipe<?> gui, char key, int code, int ignored) {
        return handler.keyTyped(gui, key, code, index);
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int ignored) {
        return handler.mouseClicked(gui, button, index);
    }

    @Override
    public boolean mouseScrolled(GuiRecipe<?> gui, int scroll, int ignored) {
        return handler.mouseScrolled(gui, scroll, index);
    }

    @Override
    public void onUpdate() {
        handler.onUpdate();
        recipe.refresh();
    }

    private static List<PositionedStack> pinned(List<PositionedStack> stacks) {
        return stacks.stream()
            .map(PinnedRecipeHandler::pin)
            .collect(Collectors.toList());
    }

    private static PositionedStack pin(PositionedStack stack) {
        PositionedStack pinned = stack.copy();
        pinned.items = new ItemStack[] { stack.item.copy() };
        return pinned;
    }
}
