package com.gtnewhorizons.neirecipepanel.client.render;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderItem;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;

import codechicken.nei.guihook.GuiContainerManager;

/** Restores the incoming render target and state, including stacks left unbalanced by a handler. */
final class RenderState implements AutoCloseable {

    /** Above every attrib and matrix stack limit; a depth query that never settles must not hang the client. */
    private static final int MAX_POPS = 64;
    private static final int ATTRIB = 0;
    private static final int CLIENT_ATTRIB = 1;
    private static final int MATRIX = 2;
    private static boolean warnedUnsettled;

    private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int attributes = GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH);
    private final int clientAttributes = GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final MatrixState projection = new MatrixState(
        GL11.GL_PROJECTION,
        GL11.GL_PROJECTION_MATRIX,
        GL11.GL_PROJECTION_STACK_DEPTH);
    private final MatrixState modelview = new MatrixState(
        GL11.GL_MODELVIEW,
        GL11.GL_MODELVIEW_MATRIX,
        GL11.GL_MODELVIEW_STACK_DEPTH);
    private final MatrixState[] textures = captureTextureMatrices();
    private final int framebuffer = OpenGlHelper.framebufferSupported ? GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        : 0;
    private final int readFramebuffer = OpenGlHelper.framebufferSupported && Context.separateTargets
        ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        : framebuffer;
    private final int renderbuffer = OpenGlHelper.framebufferSupported ? GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING)
        : 0;
    private final int program = Context.coreShaders ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        : Context.arbShaders ? ARBShaderObjects.glGetHandleARB(ARBShaderObjects.GL_PROGRAM_OBJECT_ARB) : 0;
    private final float brightnessX = OpenGlHelper.lastBrightnessX;
    private final float brightnessY = OpenGlHelper.lastBrightnessY;
    private final float itemZ = RenderItem.getInstance().zLevel;
    private final boolean itemColor = RenderItem.getInstance().renderWithColor;
    private final float neiItemZ = GuiContainerManager.drawItems.zLevel;
    private final boolean neiItemColor = GuiContainerManager.drawItems.renderWithColor;

    RenderState() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
    }

    void prepareGui() {
        useProgram(0);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        for (int unit = 0; unit < textures.length; unit++) {
            OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            OpenGlHelper.setClientActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL11.glDisable(GL11.GL_TEXTURE_1D);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL12.GL_TEXTURE_3D);
            if (Context.legacyTextureTargets) GL11.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_S);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_T);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_R);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_Q);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glLoadIdentity();
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Override
    public void close() {
        projection.restore();
        modelview.restore();
        for (int unit = 0; unit < textures.length; unit++) {
            OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            textures[unit].restore();
        }
        popTo(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH, clientAttributes, CLIENT_ATTRIB);
        popTo(GL11.GL_ATTRIB_STACK_DEPTH, attributes + 1, ATTRIB);
        if (OpenGlHelper.framebufferSupported) {
            if (Context.separateTargets) {
                OpenGlHelper.func_153171_g(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            } else OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
            OpenGlHelper.func_153176_h(OpenGlHelper.field_153199_f, renderbuffer);
        }
        popTo(GL11.GL_ATTRIB_STACK_DEPTH, attributes, ATTRIB);
        OpenGlHelper.setActiveTexture(activeTexture);
        GL11.glMatrixMode(matrixMode);
        useProgram(program);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
        RenderItem.getInstance().zLevel = itemZ;
        RenderItem.getInstance().renderWithColor = itemColor;
        GuiContainerManager.drawItems.zLevel = neiItemZ;
        GuiContainerManager.drawItems.renderWithColor = neiItemColor;
    }

    private static void popTo(int depthProperty, int depth, int stack) {
        for (int pops = 0; GL11.glGetInteger(depthProperty) > depth; pops++) {
            if (pops == MAX_POPS) {
                if (!warnedUnsettled) {
                    warnedUnsettled = true;
                    NEIRecipePanelsMod.LOG.warn(
                        "Recipe panel: GL stack depth query 0x{} did not settle after {} pops",
                        Integer.toHexString(depthProperty),
                        MAX_POPS);
                }
                return;
            }
            if (stack == ATTRIB) GL11.glPopAttrib();
            else if (stack == CLIENT_ATTRIB) GL11.glPopClientAttrib();
            else GL11.glPopMatrix();
        }
    }

    private void useProgram(int program) {
        if (Context.coreShaders) GL20.glUseProgram(program);
        else if (Context.arbShaders) ARBShaderObjects.glUseProgramObjectARB(program);
    }

    private MatrixState[] captureTextureMatrices() {
        int units = Context.textureUnits;
        MatrixState[] matrices = new MatrixState[units];
        for (int unit = 0; unit < units; unit++) {
            OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            matrices[unit] = new MatrixState(GL11.GL_TEXTURE, GL11.GL_TEXTURE_MATRIX, GL11.GL_TEXTURE_STACK_DEPTH);
        }
        OpenGlHelper.setActiveTexture(activeTexture);
        GL11.glMatrixMode(matrixMode);
        return matrices;
    }

    private static final class Context {

        private static final boolean separateTargets = GLContext.getCapabilities().OpenGL30
            || GLContext.getCapabilities().GL_ARB_framebuffer_object;
        private static final boolean legacyTextureTargets = !GLContext.getCapabilities().OpenGL32
            || (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK) & GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) != 0;
        private static final boolean coreShaders = GLContext.getCapabilities().OpenGL20;
        private static final boolean arbShaders = GLContext.getCapabilities().GL_ARB_shader_objects;
        private static final int textureUnits = Math.max(1, GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS));
    }

    private static final class MatrixState {

        private static final FloatBuffer scratch = BufferUtils.createFloatBuffer(16);

        private final int mode;
        private final int depthProperty;
        private final int levels;
        private final float[] matrices;

        /** Leaves the matrix mode at {@code mode}; the owner restores it once after all captures. */
        private MatrixState(int mode, int matrixProperty, int depthProperty) {
            this.mode = mode;
            this.depthProperty = depthProperty;
            GL11.glMatrixMode(mode);
            levels = GL11.glGetInteger(depthProperty);
            matrices = new float[levels * 16];
            for (int level = levels - 1; level >= 0; level--) {
                scratch.clear();
                GL11.glGetFloat(matrixProperty, scratch);
                scratch.clear();
                scratch.get(matrices, level * 16, 16);
                if (level > 0) GL11.glPopMatrix();
            }
            restore();
        }

        private void restore() {
            GL11.glMatrixMode(mode);
            popTo(depthProperty, 1, MATRIX);
            for (int level = 0; level < levels; level++) {
                if (level > 0) GL11.glPushMatrix();
                scratch.clear();
                scratch.put(matrices, level * 16, 16);
                scratch.flip();
                GL11.glLoadMatrix(scratch);
            }
        }
    }
}
