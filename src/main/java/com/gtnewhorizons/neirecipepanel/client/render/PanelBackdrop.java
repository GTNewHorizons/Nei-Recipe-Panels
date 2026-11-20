package com.gtnewhorizons.neirecipepanel.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import codechicken.nei.ColorUtils;

/** NEI's recipe screen background, so resource packs that retheme NEI also retheme panels. */
final class PanelBackdrop {

    private static final ResourceLocation TEXTURE = new ResourceLocation("nei", "textures/gui/recipebg.png");
    // recipebg.png: 176x166 window inside a 4px transparent margin, 5px frame.
    private static final int MARGIN = 4;
    private static final int SOURCE_WIDTH = 176;
    private static final int SOURCE_HEIGHT = 166;
    private static final int BORDER = 5;

    private PanelBackdrop() {}

    static int textColor() {
        return ColorUtils.textGray.getColor() & 0xFFFFFF;
    }

    /** Leaves the alpha-test state alone so transparent corner texels can be discarded by the caller. */
    static void draw(int width, int height) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        blitAll(width, height);
    }

    /** Rewrites destination alpha from the backdrop's own alpha, repairing renderers that erased coverage. */
    static void stampAlpha(int width, int height) {
        GL11.glColorMask(false, false, false, true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1, 1, 1, 1);
        blitAll(width, height);
        GL11.glColorMask(true, true, true, true);
    }

    private static void blitAll(int width, int height) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TEXTURE);
        int border = Math.min(BORDER, Math.min(width, height) / 2);
        int[] dx = { 0, border, width - border, width };
        int[] dy = { 0, border, height - border, height };
        int[] sx = { MARGIN, MARGIN + BORDER, MARGIN + SOURCE_WIDTH - BORDER, MARGIN + SOURCE_WIDTH };
        int[] sy = { MARGIN, MARGIN + BORDER, MARGIN + SOURCE_HEIGHT - BORDER, MARGIN + SOURCE_HEIGHT };
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                blit(tessellator, dx[x], dy[y], dx[x + 1], dy[y + 1], sx[x], sy[y], sx[x + 1], sy[y + 1]);
            }
        }
        tessellator.draw();
    }

    private static void blit(Tessellator tessellator, int x0, int y0, int x1, int y1, int u0, int v0, int u1, int v1) {
        tessellator.addVertexWithUV(x0, y1, 0, u0 / 256D, v1 / 256D);
        tessellator.addVertexWithUV(x1, y1, 0, u1 / 256D, v1 / 256D);
        tessellator.addVertexWithUV(x1, y0, 0, u1 / 256D, v0 / 256D);
        tessellator.addVertexWithUV(x0, y0, 0, u0 / 256D, v0 / 256D);
    }
}
