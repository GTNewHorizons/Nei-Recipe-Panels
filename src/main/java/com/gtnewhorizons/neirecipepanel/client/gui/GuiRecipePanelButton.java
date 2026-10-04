package com.gtnewhorizons.neirecipepanel.client.gui;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.neirecipepanel.ModItems;
import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.client.recipe.RecipeCapture;
import com.gtnewhorizons.neirecipepanel.client.recipe.RecipeResolver;
import com.gtnewhorizons.neirecipepanel.network.MakeRecipePanelMessage;
import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.RecipeHandlerRef;

/** Per-recipe button in NEI's recipe screen; sits with the favourite / overlay buttons and imprints a panel. */
public class GuiRecipePanelButton extends GuiRecipeButton {

    private static final int BUTTON_ID = 44251001;
    private Boolean supported;

    public GuiRecipePanelButton(RecipeHandlerRef handlerRef, int x, int y) {
        super(handlerRef, x, y, BUTTON_ID, "P");
    }

    @Override
    public void update() {
        super.update();
        if (supported == null) supported = RecipeCapture.capture(handlerRef.handler, handlerRef.recipeIndex) != null;
        this.enabled = supported && hasBlueprint(Minecraft.getMinecraft().thePlayer);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (!this.enabled || !contains(mouseX, mouseY)) {
            return;
        }
        RecipeSnapshot captured = RecipeCapture.capture(handlerRef.handler, handlerRef.recipeIndex);
        if (captured == null) return;
        NBTTagCompound snapshot = captured.writeToNBT();
        RecipeResolver.Resolution resolution = RecipeResolver.INSTANCE.resolve(snapshot);
        if (resolution.recipe() == null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(
                new net.minecraft.util.ChatComponentTranslation(
                    resolution.status()
                        .translationKey()));
            return;
        }
        NEIRecipePanelsMod.NETWORK.sendToServer(new MakeRecipePanelMessage(snapshot));
    }

    @Override
    public List<String> handleTooltip(List<String> tooltip) {
        tooltip.add(
            StatCollector.translateToLocal(
                Boolean.FALSE.equals(supported) ? "nei-recipe-panels.button.unsupported"
                    : this.enabled ? "nei-recipe-panels.button.imprint" : "nei-recipe-panels.button.needBlueprint"));
        return tooltip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mouseX, int mouseY, Map<String, String> hotkeys) {
        return hotkeys;
    }

    @Override
    public void lastKeyTyped(char keyChar, int keyCode) {}

    @Override
    public void drawItemOverlay() {}

    private static boolean hasBlueprint(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        if (isBlueprint(player.inventory.getItemStack())) {
            return true;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isBlueprint(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlueprint(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() == ModItems.recipeBlueprint;
    }
}
