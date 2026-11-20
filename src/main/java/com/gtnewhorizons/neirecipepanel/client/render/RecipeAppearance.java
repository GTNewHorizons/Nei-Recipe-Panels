package com.gtnewhorizons.neirecipepanel.client.render;

import java.util.Arrays;
import java.util.List;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.client.recipe.ResolvedRecipe;

import codechicken.nei.recipe.RecipeHandlerRef;

/** Compares how two recipes would look on a panel, pixel for pixel, including handler-drawn text and badges. */
public final class RecipeAppearance {

    private static final int SIDE = 512;

    private RecipeAppearance() {}

    /**
     * Renders the first recipe once and compares the others against it, stopping at the first difference. False
     * whenever a comparison can't be made (no framebuffers, capture failed), so callers stay cautious.
     */
    public static boolean allSame(List<RecipeHandlerRef> recipes) {
        byte[] reference = image(recipes.get(0));
        if (reference == null) return false;
        for (int i = 1; i < recipes.size(); i++) {
            if (!Arrays.equals(reference, image(recipes.get(i)))) return false;
        }
        return true;
    }

    private static byte[] image(RecipeHandlerRef ref) {
        try {
            ResolvedRecipe recipe = ResolvedRecipe.live(ref.handler, ref.recipeIndex);
            if (recipe == null) return null;
            PanelRecipeWidget widget = new PanelRecipeWidget(recipe);
            widget.setLocation(0, 0);
            return PanelTexture.pixels(SIDE, () -> widget.draw(-10000, -10000));
        } catch (RuntimeException | LinkageError e) {
            NEIRecipePanelsMod.LOG.debug("Could not render recipe for comparison", e);
            return null;
        }
    }
}
