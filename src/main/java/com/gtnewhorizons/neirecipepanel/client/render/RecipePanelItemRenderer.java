package com.gtnewhorizons.neirecipepanel.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.item.ItemRecipePanel;

/** In inventory slots, draws an imprinted panel as its recipe's result item while Shift is held. */
public class RecipePanelItemRenderer implements IItemRenderer {

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.INVENTORY && GuiScreen.isShiftKeyDown()
            && ItemRecipePanel.getResult(item) != null;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        ItemStack result = ItemRecipePanel.getResult(item);
        if (result == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try (RenderState state = new RenderState()) {
            state.prepareGui();
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            try {
                RenderItem.getInstance()
                    .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), result, 0, 0);
            } catch (RuntimeException | LinkageError t) {
                NEIRecipePanelsMod.LOG.warn("Recipe panel: could not render result icon", t);
            }
        }
    }
}
