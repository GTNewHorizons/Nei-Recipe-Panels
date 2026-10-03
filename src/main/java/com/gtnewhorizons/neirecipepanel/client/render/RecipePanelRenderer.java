package com.gtnewhorizons.neirecipepanel.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;

/** Blits the offscreen NEI render (see {@link PanelFboManager}) onto the panel face as one flat quad. */
public class RecipePanelRenderer extends TileEntitySpecialRenderer {

    private static final int PANEL_RGB = 0xC6C6C6;
    /** Panel quad size as a fraction of the block face. */
    private static final float MAX_EXTENT = 0.92F;
    /** How far off the block centre the panel quad sits, towards its face. */
    private static final float REACH = 0.5F - 0.01F;

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof RecipePanelTile)) return;
        if (!((RecipePanelTile) tile).hasSnapshot()) return;

        PanelFboManager.Panel panel = PanelFboManager.INSTANCE.visible((RecipePanelTile) tile);
        ForgeDirection face = ForgeDirection.getOrientation(tile.getBlockMetadata());
        float halfW = MAX_EXTENT / 2F;
        float halfH = MAX_EXTENT / 2F;

        try (PanelBlitState ignored = new PanelBlitState()) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glTranslated(x + 0.5D, y + 0.5D, z + 0.5D);
            GL11.glTranslatef(-face.offsetX * REACH, -face.offsetY * REACH, -face.offsetZ * REACH);
            orientOutward(face);

            GL11.glDisable(GL11.GL_LIGHTING);
            // full-bright, like the GUI it mirrors - block/sky light and torch colour shouldn't tint it
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

            if (panel.ready()) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                if (panel.transparent()) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                } else {
                    GL11.glDisable(GL11.GL_BLEND);
                }
                GL11.glColor4f(1F, 1F, 1F, 1F);
                panel.bindTexture();
                Tessellator t = Tessellator.instance;
                t.startDrawingQuads();
                t.addVertexWithUV(-halfW, -halfH, 0D, 0D, 0D);
                t.addVertexWithUV(halfW, -halfH, 0D, 1D, 0D);
                t.addVertexWithUV(halfW, halfH, 0D, 1D, 1D);
                t.addVertexWithUV(-halfW, halfH, 0D, 0D, 1D);
                t.draw();
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                drawQuad(halfW, halfH, PANEL_RGB);
                drawStatus(panel.statusKey(), halfW, halfH);
            }

        }
    }

    private static void drawStatus(String key, float halfWidth, float halfHeight) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = font.trimStringToWidth(StatCollector.translateToLocal(key), 168);
        GL11.glTranslatef(-halfWidth, halfHeight, 0.001F);
        GL11.glScalef(MAX_EXTENT / 176, -MAX_EXTENT / 176, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        font.drawString(text, (176 - font.getStringWidth(text)) / 2, 84, 0x404040);
    }

    private static void drawQuad(float halfW, float halfH, int rgb) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorOpaque_I(rgb);
        t.addVertex(-halfW, -halfH, 0D);
        t.addVertex(halfW, -halfH, 0D);
        t.addVertex(halfW, halfH, 0D);
        t.addVertex(-halfW, halfH, 0D);
        t.draw();
    }

    private static void orientOutward(ForgeDirection face) {
        switch (face) {
            case NORTH:
                GL11.glRotatef(180F, 0F, 1F, 0F);
                break;
            case WEST:
                GL11.glRotatef(-90F, 0F, 1F, 0F);
                break;
            case EAST:
                GL11.glRotatef(90F, 0F, 1F, 0F);
                break;
            case DOWN:
                GL11.glRotatef(90F, 1F, 0F, 0F);
                break;
            case UP:
                GL11.glRotatef(-90F, 1F, 0F, 0F);
                break;
            default: // SOUTH
                break;
        }
    }

    /** Converts a world hit to the same recipe coordinates used by the offscreen widget. */
    public static ItemStack hoveredStack(RecipePanelTile tile, ForgeDirection face, Vec3 hitVec) {
        PanelFboManager.Panel panel = PanelFboManager.INSTANCE.visible(tile);
        if (!panel.ready()) return null;

        double rx = hitVec.xCoord - (tile.xCoord + 0.5D) + face.offsetX * REACH;
        double ry = hitVec.yCoord - (tile.yCoord + 0.5D) + face.offsetY * REACH;
        double rz = hitVec.zCoord - (tile.zCoord + 0.5D) + face.offsetZ * REACH;

        float lx;
        float ly;
        switch (face) {
            case NORTH:
                lx = (float) -rx;
                ly = (float) ry;
                break;
            case WEST:
                lx = (float) rz;
                ly = (float) ry;
                break;
            case EAST:
                lx = (float) -rz;
                ly = (float) ry;
                break;
            case DOWN:
                lx = (float) rx;
                ly = (float) rz;
                break;
            case UP:
                lx = (float) rx;
                ly = (float) -rz;
                break;
            default: // SOUTH
                lx = (float) rx;
                ly = (float) ry;
                break;
        }

        float half = MAX_EXTENT / 2F;
        float u = (lx + half) / (2F * half);
        float v = (ly + half) / (2F * half);
        if (u < 0F || u > 1F || v < 0F || v > 1F) return null;

        // ortho projection puts recipe-pixel y=0 at the top; the FBO texture's v=0 samples its
        // bottom row, so v needs flipping back to recipe-pixel space.
        int fx = (int) (panel.width * u);
        int fy = (int) (panel.height * (1F - v));
        return panel.stackAt(fx, fy);
    }
}
