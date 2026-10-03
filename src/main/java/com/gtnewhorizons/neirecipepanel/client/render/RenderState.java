package com.gtnewhorizons.neirecipepanel.client.render;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderItem;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import codechicken.nei.guihook.GuiContainerManager;

/** Restores the incoming render target and state, including stacks left unbalanced by a handler. */
final class RenderState implements AutoCloseable {

    private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int attributes = AttributeStackDepth.attributes();
    private final int clientAttributes = AttributeStackDepth.clientAttributes();
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
    private final boolean separateTargets = GLContext.getCapabilities().OpenGL30
        || GLContext.getCapabilities().GL_ARB_framebuffer_object;
    private final int framebuffer = OpenGlHelper.framebufferSupported ? GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        : 0;
    private final int readFramebuffer = OpenGlHelper.framebufferSupported && separateTargets
        ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        : framebuffer;
    private final int renderbuffer = OpenGlHelper.framebufferSupported ? GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING)
        : 0;
    private final boolean coreShaders = GLContext.getCapabilities().OpenGL20;
    private final boolean arbShaders = GLContext.getCapabilities().GL_ARB_shader_objects;
    private final int program = coreShaders ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        : arbShaders ? ARBShaderObjects.glGetHandleARB(ARBShaderObjects.GL_PROGRAM_OBJECT_ARB) : 0;
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
        for (int unit = 0; unit < textures.length; unit++) {
            OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glDisable(GL11.GL_TEXTURE_1D);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL12.GL_TEXTURE_3D);
            GL11.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_S);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_T);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_R);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_Q);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glLoadIdentity();
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
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
        while (AttributeStackDepth.clientAttributes() > clientAttributes) GL11.glPopClientAttrib();
        while (AttributeStackDepth.attributes() > attributes + 1) GL11.glPopAttrib();
        if (OpenGlHelper.framebufferSupported) {
            if (separateTargets) {
                OpenGlHelper.func_153171_g(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            } else OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
            OpenGlHelper.func_153176_h(OpenGlHelper.field_153199_f, renderbuffer);
        }
        while (AttributeStackDepth.attributes() > attributes) GL11.glPopAttrib();
        OpenGlHelper.setActiveTexture(activeTexture);
        GL11.glMatrixMode(matrixMode);
        useProgram(program);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
        RenderItem.getInstance().zLevel = itemZ;
        RenderItem.getInstance().renderWithColor = itemColor;
        GuiContainerManager.drawItems.zLevel = neiItemZ;
        GuiContainerManager.drawItems.renderWithColor = neiItemColor;
    }

    private void useProgram(int program) {
        if (coreShaders) GL20.glUseProgram(program);
        else if (arbShaders) ARBShaderObjects.glUseProgramObjectARB(program);
    }

    private MatrixState[] captureTextureMatrices() {
        int units = Math.max(1, GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS));
        MatrixState[] matrices = new MatrixState[units];
        for (int unit = 0; unit < units; unit++) {
            OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            matrices[unit] = new MatrixState(GL11.GL_TEXTURE, GL11.GL_TEXTURE_MATRIX, GL11.GL_TEXTURE_STACK_DEPTH);
        }
        OpenGlHelper.setActiveTexture(activeTexture);
        return matrices;
    }

    private static final class MatrixState {

        private final int mode;
        private final int depthProperty;
        private final FloatBuffer[] matrices;

        private MatrixState(int mode, int matrixProperty, int depthProperty) {
            this.mode = mode;
            this.depthProperty = depthProperty;
            int previousMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            GL11.glMatrixMode(mode);
            matrices = new FloatBuffer[GL11.glGetInteger(depthProperty)];
            for (int level = matrices.length - 1; level >= 0; level--) {
                matrices[level] = BufferUtils.createFloatBuffer(16);
                GL11.glGetFloat(matrixProperty, matrices[level]);
                if (level > 0) GL11.glPopMatrix();
            }
            restore();
            GL11.glMatrixMode(previousMode);
        }

        private void restore() {
            GL11.glMatrixMode(mode);
            while (GL11.glGetInteger(depthProperty) > 1) GL11.glPopMatrix();
            for (int level = 0; level < matrices.length; level++) {
                if (level > 0) GL11.glPushMatrix();
                GL11.glLoadMatrix(matrices[level]);
            }
        }
    }
}
