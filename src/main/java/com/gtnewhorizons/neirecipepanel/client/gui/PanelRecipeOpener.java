package com.gtnewhorizons.neirecipepanel.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;

import com.gtnewhorizons.neirecipepanel.client.recipe.RecipeResolver;

import codechicken.nei.recipe.GuiUsageRecipe;

public final class PanelRecipeOpener {

    private PanelRecipeOpener() {}

    public static void open(NBTTagCompound snapshot, boolean usage) {
        if (snapshot == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        RecipeResolver.Resolution resolution = RecipeResolver.INSTANCE.resolveFresh(snapshot);
        if (resolution.recipe() == null) {
            if (mc.thePlayer != null) mc.thePlayer.addChatMessage(
                new ChatComponentTranslation(
                    resolution.status()
                        .translationKey()));
            return;
        }
        ItemStack result = resolution.displayResult();
        if (usage) GuiUsageRecipe.openRecipeGui("item", result);
        else mc.displayGuiScreen(new PanelRecipeGui(mc.currentScreen, resolution.recipe()));
    }
}
