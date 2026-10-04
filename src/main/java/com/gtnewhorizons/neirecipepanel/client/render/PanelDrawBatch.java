package com.gtnewhorizons.neirecipepanel.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Collects visible faces and submits all copies of each cached image in one draw. */
public final class PanelDrawBatch {

    public static final PanelDrawBatch INSTANCE = new PanelDrawBatch();
    private final Map<PanelFboManager.Panel, Images> images = new WeakHashMap<>();
    private final Map<RecipePanelTile, Submission> submissions = new WeakHashMap<>();
    private final List<PanelFboManager.Panel> visible = new ArrayList<>();
    private long pass;
    private Vec3 eye;

    private PanelDrawBatch() {}

    Vec3 camera(float partialTicks) {
        if (eye == null)
            eye = ActiveRenderInfo.projectViewFromEntity(Minecraft.getMinecraft().renderViewEntity, partialTicks);
        return eye;
    }

    void submit(RecipePanelTile tile, PanelFboManager.Panel panel, double x, double y, double z, ForgeDirection face,
        boolean animated) {
        Submission submission = submissions.computeIfAbsent(tile, ignored -> new Submission());
        if (submission.pass == pass) return;
        submission.pass = pass;
        Images group = images.computeIfAbsent(panel, ignored -> new Images());
        if (group.still.size == 0 && group.animated.size == 0) visible.add(panel);
        (animated ? group.animated : group.still).add(x, y, z, face);
    }

    void reload() {
        visible.clear();
        images.clear();
        submissions.clear();
        eye = null;
        pass++;
    }

    @SubscribeEvent
    public void startFrame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) clearPass();
    }

    @SubscribeEvent
    public void drawWorld(RenderWorldLastEvent event) {
        if (visible.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.mcProfiler.startSection("recipePanels.draw");
        try (PanelBlitState ignored = new PanelBlitState()) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .1F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            GL11.glColor4f(1, 1, 1, 1);
            for (PanelFboManager.Panel panel : visible) {
                Images group = images.get(panel);
                if (!panel.ready()) continue;
                if (panel.transparent()) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                } else GL11.glDisable(GL11.GL_BLEND);
                draw(panel, group.still, false);
                draw(panel, group.animated, true);
            }
        } finally {
            clearPass();
            mc.mcProfiler.endSection();
        }
    }

    private static void draw(PanelFboManager.Panel panel, Quads quads, boolean animated) {
        if (quads.size == 0) return;
        panel.bindTexture(animated);
        quads.draw();
    }

    private void clearPass() {
        for (PanelFboManager.Panel panel : visible) {
            Images group = images.get(panel);
            group.still.size = group.animated.size = 0;
        }
        visible.clear();
        eye = null;
        pass++;
    }

    private static final class Submission {

        private long pass = -1;
    }

    private static final class Images {

        private final Quads still = new Quads();
        private final Quads animated = new Quads();
    }

    private static final class Quads {

        private double[] vertices = new double[48];
        private int size;

        private void add(double x, double y, double z, ForgeDirection face) {
            if (size + 12 > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
            double cx = x + .5 - face.offsetX * RecipePanelRenderer.REACH;
            double cy = y + .5 - face.offsetY * RecipePanelRenderer.REACH;
            double cz = z + .5 - face.offsetZ * RecipePanelRenderer.REACH;
            double half = RecipePanelRenderer.MAX_EXTENT / 2;
            double rightX = face == ForgeDirection.NORTH ? -half
                : face == ForgeDirection.WEST || face == ForgeDirection.EAST ? 0 : half;
            double rightZ = face == ForgeDirection.WEST ? half : face == ForgeDirection.EAST ? -half : 0;
            double upY = face == ForgeDirection.UP || face == ForgeDirection.DOWN ? 0 : half;
            double upZ = face == ForgeDirection.DOWN ? half : face == ForgeDirection.UP ? -half : 0;
            vertex(cx - rightX, cy - upY, cz - rightZ - upZ);
            vertex(cx + rightX, cy - upY, cz + rightZ - upZ);
            vertex(cx + rightX, cy + upY, cz + rightZ + upZ);
            vertex(cx - rightX, cy + upY, cz - rightZ + upZ);
        }

        private void vertex(double x, double y, double z) {
            vertices[size++] = x;
            vertices[size++] = y;
            vertices[size++] = z;
        }

        private void draw() {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setColorOpaque_F(1, 1, 1);
            for (int i = 0; i < size; i += 3) {
                int corner = i / 3 % 4;
                tessellator.addVertexWithUV(
                    vertices[i],
                    vertices[i + 1],
                    vertices[i + 2],
                    corner == 1 || corner == 2 ? 1 : 0,
                    corner >= 2 ? 1 : 0);
            }
            tessellator.draw();
        }
    }
}
