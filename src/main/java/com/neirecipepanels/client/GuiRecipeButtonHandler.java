package com.neirecipepanels.client;

import codechicken.nei.recipe.GuiRecipeButton;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Adds the "imprint panel" button to NEI's recipe screen, in the column above the favourite / overlay buttons. */
public class GuiRecipeButtonHandler {

    @SubscribeEvent
    public void onUpdateRecipeButtons(GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
        int x = event.buttonList.isEmpty() ? Math.min(166, event.width) - 12
            : event.buttonList.get(event.buttonList.size() - 1).xPosition;
        int y = event.height - 18 - 13 * event.buttonList.size();
        event.buttonList.add(new GuiRecipePanelButton(event.handlerRef, x, y));
    }
}
