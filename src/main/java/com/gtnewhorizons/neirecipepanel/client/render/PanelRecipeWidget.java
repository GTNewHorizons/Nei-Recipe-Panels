package com.gtnewhorizons.neirecipepanel.client.render;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.neirecipepanel.client.recipe.ResolvedRecipe;

import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.widget.RecipeWidget;
import cpw.mods.fml.relauncher.ReflectionHelper;

/** Embeds NEI's recipe body with reconciled stack copies and no GUI controls or permutation cycling. */
final class PanelRecipeWidget extends RecipeWidget {

    private final ResolvedRecipe recipe;
    private final EmbeddedRecipeScreen screen;
    // NEI exposes entry/exit calls but no depth accessor for recovering after a renderer fails.
    private static final Field CONTEXT_DEPTH = ReflectionHelper.findField(GuiContainerManager.class, "contextDepth");

    PanelRecipeWidget(ResolvedRecipe recipe) {
        super(RecipeHandlerRef.of(recipe.handler, recipe.index));
        this.recipe = recipe;
        screen = new EmbeddedRecipeScreen();
        showAsWidget(true);
        update = false;
        lastcycle = 0;
    }

    @Override
    public List<GuiRecipeButton> getRecipeButtons() {
        return Collections.emptyList();
    }

    @Override
    protected List<PositionedStack> getCyclingStacks() {
        return recipe.cycling();
    }

    @Override
    protected List<PositionedStack> getOutputs() {
        return recipe.outputs();
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        update = false;
        badgeCache.clear();
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen previous = mc.currentScreen;
        screen.configure(mc);
        int depth = contextDepth();
        mc.currentScreen = screen;
        try {
            super.draw(mouseX, mouseY);
        } finally {
            mc.currentScreen = previous;
            while (contextDepth() > depth) GuiContainerManager.disableMatrixStackLogging();
        }
    }

    private static int contextDepth() {
        try {
            return CONTEXT_DEPTH.getInt(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot restore NEI render context", e);
        }
    }

    private final class EmbeddedRecipeScreen extends GuiRecipe<IRecipeHandler> {

        private EmbeddedRecipeScreen() {
            super(null);
            currenthandlers.add(recipe.handler);
        }

        private void configure(Minecraft mc) {
            guiLeft = x;
            guiTop = y + getHandlerInfo().getYShift();
            setWorldAndResolution(mc, 2 * guiLeft + 176, 2 * guiTop + 166);
        }

        @Override
        public void initGui() {}

        @Override
        public ArrayList<IRecipeHandler> getCurrentRecipeHandlers() {
            return currenthandlers;
        }

        @Override
        public IRecipeHandler getHandler() {
            return recipe.handler;
        }

        @Override
        public String getHandlerName() {
            return GuiRecipeTab.getHandlerInfo(recipe.handler)
                .getHandlerName();
        }

        @Override
        public List<Integer> getRecipeIndices() {
            return Collections.singletonList(recipe.index);
        }

        @Override
        public Point getRecipePosition(int index) {
            return new Point(0, 0);
        }

        @Override
        protected Point getRefIndexPosition(int index) {
            return new Point(0, 0);
        }

        @Override
        public Point getRecipeMousePosition(int index) {
            return new Point(-10000, -10000);
        }
    }
}
