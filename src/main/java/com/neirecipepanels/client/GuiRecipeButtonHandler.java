package com.neirecipepanels.client;

import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Adds the "imprint panel" button to NEI's recipe screen, in the column above the favourite / overlay buttons. */
public class GuiRecipeButtonHandler {

    @SubscribeEvent
    public void onUpdateRecipeButtons(GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
        int x = 166 - 12;
        int y;
        if (event.buttonList.isEmpty()) {
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(event.handlerRef.handler);
            y = info.getHeight() + info.getYShift() - 18;
        } else {
            GuiRecipeButton top = event.buttonList.get(event.buttonList.size() - 1);
            x = top.xPosition;
            y = top.yPosition - 13;
        }
        event.buttonList.add(new GuiRecipePanelButton(event.handlerRef, x, y));
    }
}
