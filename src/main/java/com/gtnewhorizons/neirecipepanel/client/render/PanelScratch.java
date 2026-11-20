package com.gtnewhorizons.neirecipepanel.client.render;

import java.nio.ByteBuffer;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/** One shared offscreen target; panels render here and copy the result into colour-only textures. */
final class PanelScratch {

    private static int framebuffer = -1;
    private static int color = -1;
    private static int depth = -1;
    private static int size;

    private PanelScratch() {}

    static long bytes() {
        return framebuffer < 0 ? 0 : 8L * size * size;
    }

    /** Binds a target of at least {@code pixels}², limited to and cleared over the requested square. */
    static void begin(int pixels) {
        if (framebuffer < 0 || size < pixels) allocate(pixels);
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
        GL11.glViewport(0, 0, pixels, pixels);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, 0, pixels, pixels);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glStencilMask(-1);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClearDepth(1);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** Rebinds the target in case a renderer switched framebuffers while drawing. */
    static void bind() {
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
    }

    static void dispose() {
        if (depth >= 0) OpenGlHelper.func_153184_g(depth);
        if (color >= 0) TextureUtil.deleteTexture(color);
        if (framebuffer >= 0) OpenGlHelper.func_153174_h(framebuffer);
        depth = color = framebuffer = -1;
        size = 0;
    }

    private static void allocate(int pixels) {
        dispose();
        if (pixels > GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE))
            throw new IllegalArgumentException("Recipe texture exceeds GPU limits");
        try {
            framebuffer = OpenGlHelper.func_153165_e();
            color = TextureUtil.glGenTextures();
            depth = OpenGlHelper.func_153185_f();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
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
            OpenGlHelper
                .func_153188_a(OpenGlHelper.field_153198_e, OpenGlHelper.field_153200_g, GL11.GL_TEXTURE_2D, color, 0);
            OpenGlHelper.func_153176_h(OpenGlHelper.field_153199_f, depth);
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
                depth);
            if (stencil) OpenGlHelper.func_153190_b(
                OpenGlHelper.field_153198_e,
                EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,
                OpenGlHelper.field_153199_f,
                depth);
            if (OpenGlHelper.func_153167_i(OpenGlHelper.field_153198_e) != OpenGlHelper.field_153202_i) {
                throw new IllegalStateException("Recipe framebuffer is incomplete");
            }
            size = pixels;
        } catch (RuntimeException | LinkageError e) {
            dispose();
            throw e;
        }
    }
}
