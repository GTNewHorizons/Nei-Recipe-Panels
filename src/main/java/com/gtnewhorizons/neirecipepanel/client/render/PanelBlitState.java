package com.gtnewhorizons.neirecipepanel.client.render;

import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

final class PanelBlitState implements AutoCloseable {

    private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final float brightnessX = OpenGlHelper.lastBrightnessX;
    private final float brightnessY = OpenGlHelper.lastBrightnessY;

    PanelBlitState() {
        GL11.glPushAttrib(
            GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
    }

    @Override
    public void close() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
        OpenGlHelper.setActiveTexture(activeTexture);
        GL11.glMatrixMode(matrixMode);
    }
}
