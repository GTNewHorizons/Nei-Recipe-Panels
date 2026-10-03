package com.gtnewhorizons.neirecipepanel.client.gui;

import java.util.ArrayList;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.neirecipepanel.client.recipe.ResolvedRecipe;

import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IRecipeHandler;

/** Opens the already resolved recipe directly, avoiding a second, weaker NEI descriptor lookup. */
final class PanelRecipeGui extends GuiRecipe<IRecipeHandler> {

    PanelRecipeGui(GuiScreen previous, ResolvedRecipe recipe) {
        super(previous);
        currenthandlers.add(recipe.pinnedHandler());
    }

    @Override
    public ArrayList<IRecipeHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }
}
