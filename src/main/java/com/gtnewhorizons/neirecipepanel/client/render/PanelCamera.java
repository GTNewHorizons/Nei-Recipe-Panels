package com.gtnewhorizons.neirecipepanel.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.util.Vec3;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** The camera position for the current frame, computed once and shared by every panel renderer call. */
public final class PanelCamera {

    public static final PanelCamera INSTANCE = new PanelCamera();
    private Vec3 eye;

    private PanelCamera() {}

    Vec3 camera(float partialTicks) {
        if (eye == null)
            eye = ActiveRenderInfo.projectViewFromEntity(Minecraft.getMinecraft().renderViewEntity, partialTicks);
        return eye;
    }

    void reload() {
        eye = null;
    }

    @SubscribeEvent
    public void startFrame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) eye = null;
    }
}
