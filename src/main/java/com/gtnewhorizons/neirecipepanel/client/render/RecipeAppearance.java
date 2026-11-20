package com.gtnewhorizons.neirecipepanel.client.render;

import java.util.Arrays;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.client.recipe.ResolvedRecipe;

import codechicken.nei.recipe.RecipeHandlerRef;

/** Compares how two recipes would look on a panel, pixel for pixel, including handler-drawn text and badges. */
public final class RecipeAppearance {

    private static final int SIDE = 512;

    private RecipeAppearance() {}

    /** False whenever the comparison can't be made (no framebuffers, capture failed), so callers stay cautious. */
    public static boolean same(RecipeHandlerRef first, RecipeHandlerRef second) {
        byte[] a = image(first);
        return a != null && Arrays.equals(a, image(second));
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
