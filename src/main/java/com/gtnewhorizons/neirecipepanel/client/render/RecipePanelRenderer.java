package com.gtnewhorizons.neirecipepanel.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;

/** Submits visible panel faces to the shared draw batch. */
public class RecipePanelRenderer extends TileEntitySpecialRenderer {

    private static final int PANEL_RGB = 0xC6C6C6;
    /** Panel quad size as a fraction of the block face. */
    static final float MAX_EXTENT = 0.92F;
    /** How far off the block centre the panel quad sits, towards its face. */
    static final float REACH = 0.5F - 0.01F;

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof RecipePanelTile)) return;
        if (!((RecipePanelTile) tile).hasSnapshot()) return;

        ForgeDirection face = ForgeDirection.getOrientation(tile.getBlockMetadata());
        Vec3 eye = PanelDrawBatch.INSTANCE.camera(partialTicks);
        double centreX = tile.xCoord + .5 - face.offsetX * REACH;
        double centreY = tile.yCoord + .5 - face.offsetY * REACH;
        double centreZ = tile.zCoord + .5 - face.offsetZ * REACH;
        if ((eye.xCoord - centreX) * face.offsetX + (eye.yCoord - centreY) * face.offsetY
            + (eye.zCoord - centreZ) * face.offsetZ <= 0) return;
        RecipePanelTile recipeTile = (RecipePanelTile) tile;
        PanelFboManager.Panel panel = PanelFboManager.INSTANCE.visible(recipeTile);
        if (panel.ready()) {
            boolean animated = PanelFboManager.INSTANCE.requestAnimation(recipeTile, eye, centreX, centreY, centreZ);
            PanelDrawBatch.INSTANCE.submit(recipeTile, panel, x, y, z, face, animated);
            return;
        }
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

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            drawQuad(halfW, halfH, PANEL_RGB);
            drawStatus(panel.statusKey(), halfW, halfH);

        }
    }

    private static void drawStatus(String key, float halfWidth, float halfHeight) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GL11.glTranslatef(-halfWidth, halfHeight, 0.001F);
        GL11.glScalef(MAX_EXTENT / 176, -MAX_EXTENT / 176, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        PanelStatusText.draw(font, key, 176, 176, 0x404040);
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
