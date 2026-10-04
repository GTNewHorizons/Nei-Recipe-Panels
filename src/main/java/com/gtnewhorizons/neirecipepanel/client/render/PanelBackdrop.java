package com.gtnewhorizons.neirecipepanel.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

final class PanelBackdrop {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/demo_background.png");
    private static final int BORDER = 4;

    private PanelBackdrop() {}

    static void draw(int width, int height) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TEXTURE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1, 1, 1, 1);
        int[] dx = { 0, BORDER, width - BORDER, width };
        int[] dy = { 0, BORDER, height - BORDER, height };
        int[] sx = { 0, BORDER, 248 - BORDER, 248 };
        int[] sy = { 0, BORDER, 166 - BORDER, 166 };
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                blit(dx[x], dy[y], dx[x + 1], dy[y + 1], sx[x], sy[y], sx[x + 1], sy[y + 1]);
            }
        }
    }

    static void stampAlpha(int width, int height) {
        alphaState();
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        quad(tessellator, 0, 0, width, height);
        tessellator.draw();
        GL11.glColorMask(true, true, true, true);
    }

    private static void alphaState() {
        // Some item renderers erase framebuffer alpha; repair coverage after their draw calls.
        GL11.glColorMask(false, false, false, true);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void quad(Tessellator tessellator, int x, int y, int width, int height) {
        tessellator.addVertex(x, y + height, 0);
        tessellator.addVertex(x + width, y + height, 0);
        tessellator.addVertex(x + width, y, 0);
        tessellator.addVertex(x, y, 0);
    }

    private static void blit(int x0, int y0, int x1, int y1, int u0, int v0, int u1, int v1) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x0, y1, 0, u0 / 256D, v1 / 256D);
        tessellator.addVertexWithUV(x1, y1, 0, u1 / 256D, v1 / 256D);
        tessellator.addVertexWithUV(x1, y0, 0, u1 / 256D, v0 / 256D);
        tessellator.addVertexWithUV(x0, y0, 0, u0 / 256D, v0 / 256D);
        tessellator.draw();
    }
}
