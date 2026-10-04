package com.gtnewhorizons.neirecipepanel.client.render;

import java.nio.ByteBuffer;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

final class PanelTexture {

    static final int SUPERSAMPLE = 3;
    private int framebuffer = -1;
    private int texture = -1;
    private int depthBuffer = -1;
    private int side;
    private boolean complete;

    boolean ready() {
        return complete && texture >= 0 && OpenGlHelper.isFramebufferEnabled();
    }

    long bytes() {
        return texture < 0 ? 0 : estimatedBytes(side);
    }

    static long estimatedBytes(int logicalSide) {
        if (logicalSide <= 0) throw new IllegalArgumentException("Invalid recipe texture size");
        int pixels = Math.multiplyExact(logicalSide, SUPERSAMPLE);
        long depthBytes = Math.multiplyExact((long) pixels * pixels, 4);
        long colorBytes = 0;
        for (int level = pixels; level > 0; level /= 2) {
            colorBytes = Math.addExact(colorBytes, Math.multiplyExact((long) level * level, 4));
        }
        return Math.addExact(depthBytes, colorBytes);
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
        try (RenderState state = new RenderState()) {
            state.prepareGui();
            if (texture < 0 || side != logicalSide) {
                dispose();
                allocate(logicalSide);
            }
            complete = false;
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
            GL11.glViewport(0, 0, side * SUPERSAMPLE, side * SUPERSAMPLE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glStencilMask(-1);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClearDepth(1);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, side, side, 0, 1000, 3000);
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
                draw.run();
            } catch (RuntimeException | LinkageError e) {
                try {
                    Tessellator.instance.draw();
                } catch (IllegalStateException ignoredDrawing) { /* No unfinished tessellation. */ }
                throw e;
            }
            bind();
            boolean mipmaps = generateMipmaps();
            GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER,
                mipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
            complete = true;
        }
    }

    void dispose() {
        complete = false;
        if (depthBuffer >= 0) OpenGlHelper.func_153184_g(depthBuffer);
        if (texture >= 0) TextureUtil.deleteTexture(texture);
        if (framebuffer >= 0) OpenGlHelper.func_153174_h(framebuffer);
        depthBuffer = texture = framebuffer = -1;
    }

    private void allocate(int logicalSide) {
        side = logicalSide;
        int pixels = side * SUPERSAMPLE;
        if (pixels > GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE))
            throw new IllegalArgumentException("Recipe texture exceeds GPU limits");
        try {
            framebuffer = OpenGlHelper.func_153165_e();
            texture = TextureUtil.glGenTextures();
            depthBuffer = OpenGlHelper.func_153185_f();
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
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
            OpenGlHelper.func_153188_a(
                OpenGlHelper.field_153198_e,
                OpenGlHelper.field_153200_g,
                GL11.GL_TEXTURE_2D,
                texture,
                0);
            OpenGlHelper.func_153176_h(OpenGlHelper.field_153199_f, depthBuffer);
            boolean stencil = MinecraftForgeClient.getStencilBits() > 0;
            OpenGlHelper.func_153186_a(
                OpenGlHelper.field_153199_f,
                stencil ? EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT : GL14.GL_DEPTH_COMPONENT24,
                pixels,
                pixels);
            OpenGlHelper.func_153190_b(
                OpenGlHelper.field_153198_e,
                OpenGlHelper.field_153201_h,
                OpenGlHelper.field_153199_f,
                depthBuffer);
            if (stencil) OpenGlHelper.func_153190_b(
                OpenGlHelper.field_153198_e,
                EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,
                OpenGlHelper.field_153199_f,
                depthBuffer);
            if (OpenGlHelper.func_153167_i(OpenGlHelper.field_153198_e) != OpenGlHelper.field_153202_i) {
                throw new IllegalStateException("Recipe framebuffer is incomplete");
            }
        } catch (RuntimeException | LinkageError e) {
            dispose();
            throw e;
        }
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
