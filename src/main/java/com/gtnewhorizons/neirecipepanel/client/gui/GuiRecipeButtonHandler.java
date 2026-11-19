package com.gtnewhorizons.neirecipepanel.client.gui;

import java.util.List;

import com.gtnewhorizons.neirecipepanel.client.render.RecipeFootprint;

import codechicken.nei.recipe.GuiRecipeButton;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Adds the "imprint panel" button to NEI's recipe screen. Its default spot is above the favourite / overlay buttons;
 * when the recipe draws there, it moves to the nearest free spot on the same grid.
 */
public class GuiRecipeButtonHandler {

    private static final int STEP = GuiRecipeButton.BUTTON_HEIGHT + 1;
    private static final int SIZE = GuiRecipeButton.BUTTON_WIDTH;

    @SubscribeEvent
    public void onUpdateRecipeButtons(GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
        List<GuiRecipeButton> buttons = event.buttonList;
        int columnX = buttons.isEmpty() ? Math.min(166, event.width) - SIZE : buttons.get(buttons.size() - 1).xPosition;
        int bottomY = event.height - 18;
        int x = columnX;
        int y = bottomY - STEP * buttons.size();
        RecipeFootprint footprint = RecipeFootprint
            .of(event.handlerRef.handler, event.handlerRef.recipeIndex, event.width, event.height);
        if (footprint != null && !isFree(footprint, buttons, x, y)) {
            int defaultX = x;
            int defaultY = y;
            int best = Integer.MAX_VALUE;
            for (int row = 0; bottomY - STEP * row >= 0; row++) {
                for (int column = 0; columnX - STEP * column >= 0; column++) {
                    int candidateX = columnX - STEP * column;
                    int candidateY = bottomY - STEP * row;
                    // Nearest to the default spot; on ties stay in NEI's button column.
                    int distance = 2 * (Math.abs(candidateX - defaultX) + Math.abs(candidateY - defaultY))
                        + (column == 0 ? 0 : 1);
                    if (distance < best && isFree(footprint, buttons, candidateX, candidateY)) {
                        best = distance;
                        x = candidateX;
                        y = candidateY;
                    }
                }
            }
        }
        buttons.add(new GuiRecipePanelButton(event.handlerRef, x, y));
    }

    private static boolean isFree(RecipeFootprint footprint, List<GuiRecipeButton> buttons, int x, int y) {
        for (GuiRecipeButton button : buttons) {
            if (x < button.xPosition + button.width + 1 && button.xPosition < x + SIZE + 1
                && y < button.yPosition + button.height + 1
                && button.yPosition < y + SIZE + 1) return false;
        }
        return footprint.isFree(x - 1, y - 1, SIZE + 2, SIZE + 2);
    }
}
