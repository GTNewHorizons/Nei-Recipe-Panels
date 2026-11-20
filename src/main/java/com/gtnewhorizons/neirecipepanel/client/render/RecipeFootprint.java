package com.gtnewhorizons.neirecipepanel.client.render;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.client.recipe.ResolvedRecipe;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

/** Which pixels of a recipe widget the handler draws on, in NEI widget coordinates (buttons use the same space). */
public final class RecipeFootprint {

    private static final int MAX_SIDE = 512;

    private final int width;
    private final int height;
    private final int side;
    private final boolean[] drawn;
    private final List<int[]> slots;

    RecipeFootprint(int width, int height, int side, boolean[] drawn, List<int[]> slots) {
        this.width = width;
        this.height = height;
        this.side = side;
        this.drawn = drawn;
        this.slots = slots;
    }

    /** Renders the recipe's background and foreground once offscreen; null when that isn't possible. */
    public static RecipeFootprint of(IRecipeHandler handler, int index, int width, int height) {
        int side = Math.max(width, height);
        if (width <= 0 || height <= 0 || side > MAX_SIDE) return null;
        try {
            ResolvedRecipe recipe = ResolvedRecipe.live(handler, index);
            if (recipe == null) return null;
            PanelRecipeWidget widget = new PanelRecipeWidget(recipe);
            widget.setLocation(0, 0);
            boolean[] drawn = PanelTexture.coverage(side, widget::drawBackground);
            if (drawn == null) return null;
            int yShift = widget.getHandlerInfo()
                .getYShift();
            List<int[]> slots = new ArrayList<>();
            for (PositionedStack stack : recipe.allStacks()) {
                slots.add(new int[] { stack.relx, stack.rely + yShift, stack.width, stack.height });
            }
            return new RecipeFootprint(width, height, side, drawn, slots);
        } catch (RuntimeException | LinkageError e) {
            NEIRecipePanelsMod.LOG.debug("Could not measure recipe for button placement", e);
            return null;
        }
    }

    /**
     * True when the rectangle lies inside the widget and neither it nor a {@code margin} around it touches a drawn
     * pixel or item slot. The margin is clipped to the widget: NEI's button column sits flush with its right edge.
     */
    public boolean isFree(int x, int y, int w, int h, int margin) {
        if (x < 0 || y < 0 || x + w > width || y + h > height) return false;
        int left = Math.max(0, x - margin);
        int top = Math.max(0, y - margin);
        int right = Math.min(width, x + w + margin);
        int bottom = Math.min(height, y + h + margin);
        for (int[] slot : slots) {
            if (left < slot[0] + slot[2] && slot[0] < right && top < slot[1] + slot[3] && slot[1] < bottom)
                return false;
        }
        for (int row = top; row < bottom; row++) {
            for (int column = left; column < right; column++) {
                if (drawn[row * side + column]) return false;
            }
        }
        return true;
    }
}
