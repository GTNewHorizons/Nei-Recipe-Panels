package com.gtnewhorizons.neirecipepanel.client.render;

import java.nio.ByteBuffer;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;

import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.gtnewhorizons.neirecipepanel.config.Config;

/** A colour-only, mipmapped panel image; drawing happens in {@link PanelScratch} and is copied in. */
final class PanelTexture {

    private int texture = -1;
    private int side;
    private int scale;
    private boolean complete;

    boolean ready() {
        return complete && texture >= 0 && OpenGlHelper.isFramebufferEnabled();
    }

    long bytes() {
        return texture < 0 ? 0 : colorBytes(Math.multiplyExact(side, scale));
    }

    static int supersample() {
        return Math.max(1, Math.min(4, Config.supersample));
    }

    static long estimatedBytes(int logicalSide) {
        if (logicalSide <= 0) throw new IllegalArgumentException("Invalid recipe texture size");
        return colorBytes(Math.multiplyExact(logicalSide, supersample()));
    }

    private static long colorBytes(int pixels) {
        long bytes = 0;
        for (int level = pixels; level > 0; level /= 2) {
            bytes = Math.addExact(bytes, Math.multiplyExact((long) level * level, 4));
        }
        return bytes;
    }

    void bind() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    void draw(int width, int height) {
        bind();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1, 1, 1, 1);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0, height, 0, 0, 0);
        tessellator.addVertexWithUV(width, height, 0, 1, 0);
        tessellator.addVertexWithUV(width, 0, 0, 1, 1);
        tessellator.addVertexWithUV(0, 0, 0, 0, 1);
        tessellator.draw();
    }

    void render(int logicalSide, Runnable draw) {
        if (!OpenGlHelper.isFramebufferEnabled()) return;
        int scale = supersample();
        int pixels = Math.multiplyExact(logicalSide, scale);
        try (RenderState state = new RenderState()) {
            state.prepareGui();
            complete = false;
            drawInto(logicalSide, pixels, draw);
            if (texture < 0 || side != logicalSide || this.scale != scale) {
                dispose();
                allocate(pixels);
                side = logicalSide;
                this.scale = scale;
            }
            PanelScratch.bind();
            bind();
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, pixels, pixels);
            boolean mipmaps = generateMipmaps();
            GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER,
                mipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
            complete = true;
        }
    }

    /**
     * Draws at 1:1 scale on a transparent target and returns the covered area as {minX, minY, maxX, maxY}
     * (exclusive maxima, top-left origin), or null when nothing was drawn.
     */
    static int[] measure(int logicalSide, Runnable draw) {
        boolean[] covered = coverage(logicalSide, draw);
        if (covered == null) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < logicalSide; y++) {
            for (int x = 0; x < logicalSide; x++) {
                if (!covered[y * logicalSide + x]) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        return maxX < 0 ? null : new int[] { minX, minY, maxX + 1, maxY + 1 };
    }

    /** Draws at 1:1 scale on a transparent target; returns drawn pixels row-major from the top-left, or null. */
    static boolean[] coverage(int logicalSide, Runnable draw) {
        if (!OpenGlHelper.isFramebufferEnabled()) return null;
        try (RenderState state = new RenderState()) {
            state.prepareGui();
            drawInto(logicalSide, logicalSide, draw);
            PanelScratch.bind();
            ByteBuffer alpha = GLAllocation.createDirectByteBuffer(logicalSide * logicalSide);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, logicalSide, logicalSide, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, alpha);
            boolean[] covered = new boolean[logicalSide * logicalSide];
            for (int row = 0; row < logicalSide; row++) {
                int y = logicalSide - 1 - row;
                for (int x = 0; x < logicalSide; x++) {
                    covered[y * logicalSide + x] = alpha.get(row * logicalSide + x) != 0;
                }
            }
            return covered;
        }
    }

    private static void drawInto(int logicalSide, int pixels, Runnable draw) {
        PanelScratch.begin(pixels);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, logicalSide, logicalSide, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 0, -2000);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glColor4f(1, 1, 1, 1);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        try {
            DarkModeText.recolored(draw);
        } catch (RuntimeException | LinkageError e) {
            try {
                Tessellator.instance.draw();
            } catch (IllegalStateException ignoredDrawing) { /* No unfinished tessellation. */ }
            throw e;
        }
    }

    void dispose() {
        complete = false;
        if (texture >= 0) TextureUtil.deleteTexture(texture);
        texture = -1;
    }

    private void allocate(int pixels) {
        if (pixels > GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE))
            throw new IllegalArgumentException("Recipe texture exceeds GPU limits");
        texture = TextureUtil.glGenTextures();
        bind();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            pixels,
            pixels,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            (ByteBuffer) null);
    }

    private static boolean generateMipmaps() {
        if (GLContext.getCapabilities().OpenGL30) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        else if (GLContext.getCapabilities().GL_ARB_framebuffer_object)
            ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        else if (GLContext.getCapabilities().GL_EXT_framebuffer_object)
            EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
        else return false;
        return true;
    }

}
