package com.gtnewhorizons.neirecipepanel.client.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

import cpw.mods.fml.common.Loader;
import sun.misc.Unsafe;

@Tag("gpu")
class PanelTextureGpuTest {

    private static final int SIDE = 8;
    private static final Map<Field, Object> ORIGINAL_GLOBALS = new LinkedHashMap<>();
    private static Unsafe unsafe;
    private static Pbuffer context;
    private static int source;

    @BeforeAll
    static void createContext() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        minecraft.gameSettings = new GameSettings();
        minecraft.gameSettings.fboEnable = true;
        replace(Minecraft.class, "theMinecraft", minecraft);
        replace(Loader.class, "instance", unsafe.allocateInstance(Loader.class));
        Class.forName(Blocks.class.getName());
        replace(Blocks.class, "water", new Block(Material.water) {});
        replace(Blocks.class, "lava", new Block(Material.lava) {});
        context = new Pbuffer(32, 32, new PixelFormat(), null, null);
        context.makeCurrent();
        OpenGlHelper.initializeTextures();
        assertTrue(OpenGlHelper.isFramebufferEnabled());
        source = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, source);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put(new byte[] { (byte) 200, (byte) 160, (byte) 120, (byte) 128 })
            .flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
    }

    @AfterAll
    static void destroyContext() {
        if (context != null) context.destroy();
        if (unsafe != null) ORIGINAL_GLOBALS.forEach(
            (field, value) -> unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value));
    }

    @ParameterizedTest
    @EnumSource(IncomingState.class)
    void cachedPixelsMatchNormalGuiRenderingAndRestoreIncomingState(IncomingState incoming) {
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        PanelTexture texture = new PanelTexture();
        try {
            texture.render(SIDE, PanelTextureGpuTest::drawLayers);
            int[] expected = readCentre(texture);
            assertEquals(196, expected[0], 1);
            assertEquals(175, expected[1], 1);
            assertEquals(155, expected[2], 1);
            incoming.apply();
            boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
            boolean logic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
            boolean colorArray = GL11.glIsEnabled(GL11.GL_COLOR_ARRAY);
            int blendSource = GL11.glGetInteger(GL11.GL_BLEND_SRC);
            int blendEquation = GL11.glGetInteger(GL14.GL_BLEND_EQUATION);

            texture.render(SIDE, PanelTextureGpuTest::drawLayers);

            assertEquals(fog, GL11.glIsEnabled(GL11.GL_FOG));
            assertEquals(logic, GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP));
            assertEquals(colorArray, GL11.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertEquals(blendSource, GL11.glGetInteger(GL11.GL_BLEND_SRC));
            assertEquals(blendEquation, GL11.glGetInteger(GL14.GL_BLEND_EQUATION));
            assertArrayEquals(expected, readCentre(texture), "Incoming state must not darken or recolour the image");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            try (RenderState ignored = new RenderState()) {
                texture.dispose();
            }
        }
    }

    @Test
    void itemRefreshPreservesBackgroundOrientationAndTheStaticImage() {
        PanelTexture background = new PanelTexture();
        PanelTexture animated = new PanelTexture();
        try {
            background.render(SIDE, () -> {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                solidQuad(0, 0, SIDE, SIDE / 2, 0xFF0000);
                solidQuad(0, SIDE / 2, SIDE, SIDE, 0x0000FF);
            });
            int[] original = readCentre(background);

            for (int color : new int[] { 0x00FF00, 0xFFFF00 }) {
                animated.render(SIDE, () -> {
                    background.draw(SIDE, SIDE);
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    solidQuad(0, 0, SIDE / 2, SIDE / 2, color);
                });

                assertArrayEquals(
                    new int[] { color >> 16 & 255, color >> 8 & 255, color & 255, 255 },
                    readPixel(animated, 2, 2));
                assertArrayEquals(new int[] { 255, 0, 0, 255 }, readPixel(animated, 6, 2));
                assertArrayEquals(new int[] { 0, 0, 255, 255 }, readPixel(animated, 2, 6));
                assertArrayEquals(original, readCentre(background));
            }
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            try (RenderState ignored = new RenderState()) {
                background.dispose();
                animated.dispose();
            }
        }
    }

    @Test
    void refreshedImageDoesNotShimmerWhenMinified() {
        int imageSide = 32;
        PanelTexture texture = new PanelTexture();
        try {
            texture.render(imageSide, () -> {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                for (int x = 0; x < imageSide; x++) solidQuad(x, 0, x + 1, imageSide, x % 2 == 0 ? 0 : 0xFFFFFF);
            });
            int darkest = 255;
            int brightest = 0;
            try (RenderState state = new RenderState()) {
                state.prepareGui();
                OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, 0);
                GL11.glViewport(0, 0, 2, 2);
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glLoadIdentity();
                GL11.glOrtho(0, imageSide, imageSide, 0, -1, 1);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glLoadIdentity();
                ByteBuffer pixel = BufferUtils.createByteBuffer(4);
                for (int step = 0; step < 12; step++) {
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glLoadIdentity();
                    GL11.glTranslatef(step / (float) (12 * imageSide * PanelTexture.SUPERSAMPLE), 0, 0);
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    texture.draw(imageSide, imageSide);
                    pixel.clear();
                    GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                    int brightness = pixel.get(0) & 255;
                    darkest = Math.min(darkest, brightness);
                    brightest = Math.max(brightest, brightness);
                }
            }
            assertEquals(128, darkest, 8, "Minified black and white lines must average to grey");
            assertTrue(
                brightest - darkest <= 8,
                "Subpixel movement must not flicker between dark and light grid lines: " + darkest + ".." + brightest);
        } finally {
            try (RenderState ignored = new RenderState()) {
                texture.dispose();
            }
        }
    }

    private static void solidQuad(int left, int top, int right, int bottom, int color) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorOpaque_I(color);
        tessellator.addVertex(left, top, 0);
        tessellator.addVertex(left, bottom, 0);
        tessellator.addVertex(right, bottom, 0);
        tessellator.addVertex(right, top, 0);
        tessellator.draw();
    }

    private static void drawLayers() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(.75F, .75F, .75F, 1);
        drawQuad();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, source);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        drawQuad();
    }

    private static void drawQuad() {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0, 0, 0, 0, 0);
        tessellator.addVertexWithUV(0, SIDE, 0, 0, 1);
        tessellator.addVertexWithUV(SIDE, SIDE, 0, 1, 1);
        tessellator.addVertexWithUV(SIDE, 0, 0, 1, 0);
        tessellator.draw();
    }

    private static int[] readCentre(PanelTexture texture) {
        return readPixel(texture, SIDE / 2, SIDE / 2);
    }

    private static int[] readPixel(PanelTexture texture, int x, int y) {
        int pixels = SIDE * PanelTexture.SUPERSAMPLE;
        ByteBuffer image = BufferUtils.createByteBuffer(pixels * pixels * 4);
        texture.bind();
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);
        int offset = ((SIDE - 1 - y) * PanelTexture.SUPERSAMPLE * pixels + x * PanelTexture.SUPERSAMPLE) * 4;
        return new int[] { image.get(offset) & 255, image.get(offset + 1) & 255, image.get(offset + 2) & 255,
            image.get(offset + 3) & 255 };
    }

    private static void replace(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        ORIGINAL_GLOBALS.put(field, field.get(null));
        unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    private enum IncomingState {

        FOG {

            @Override
            void apply() {
                GL11.glEnable(GL11.GL_FOG);
                GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
                GL11.glFogf(GL11.GL_FOG_START, 0);
                GL11.glFogf(GL11.GL_FOG_END, 3000);
                FloatBuffer color = BufferUtils.createFloatBuffer(4);
                color.put(new float[] { 0, 0, 0, 1 })
                    .flip();
                GL11.glFog(GL11.GL_FOG_COLOR, color);
            }
        },
        ADDITIVE_BLEND {

            @Override
            void apply() {
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            }
        },
        SUBTRACTIVE_BLEND {

            @Override
            void apply() {
                GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            }
        },
        COLOR_ARRAY {

            private final FloatBuffer colors = BufferUtils.createFloatBuffer(16);

            @Override
            void apply() {
                colors.clear();
                for (int vertex = 0; vertex < 4; vertex++) colors.put(new float[] { 0, 1, 0, 1 });
                colors.flip();
                GL11.glColorPointer(4, 0, colors);
                GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
            }
        },
        LOGIC_OPERATION {

            @Override
            void apply() {
                GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
                GL11.glLogicOp(GL11.GL_XOR);
            }
        };

        abstract void apply();
    }
}
