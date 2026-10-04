package com.gtnewhorizons.neirecipepanel.client.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;
import com.gtnewhorizons.neirecipepanel.client.recipe.RecipeResolver;
import com.gtnewhorizons.neirecipepanel.client.recipe.ResolvedRecipe;
import com.gtnewhorizons.neirecipepanel.config.PanelSettings;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class PanelFboManager {

    public static final PanelFboManager INSTANCE = new PanelFboManager();
    private static final int MAX_PANELS = 128;
    private static final long MAX_TEXTURE_BYTES = 96L * 1024 * 1024;
    private static final long FRAME_BUDGET_NS = 2_000_000;
    private static final long EVICT_AFTER_NS = 10_000_000_000L;
    private static final double ANIMATION_DISTANCE_SQUARED = 3 * 3;
    private final Map<Key, Panel> panels = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<RecipePanelTile, Binding> bindings = new WeakHashMap<>();
    private final Panel unavailable = new Panel(null, null);
    private World world;
    private long frame;
    private long clientTick;

    private PanelFboManager() {}

    public void reload() {
        PanelDrawBatch.INSTANCE.reload();
        if (panels.values()
            .stream()
            .anyMatch(panel -> panel.bytes() > 0)) {
            try (RenderState ignored = new RenderState()) {
                for (Panel panel : panels.values()) panel.dispose();
            }
        }
        panels.clear();
        bindings.clear();
        RecipeResolver.INSTANCE.clear();
    }

    public Panel visible(RecipePanelTile tile) {
        if (!tile.hasSnapshot()) return unavailable;
        Binding binding = bindings.get(tile);
        Panel panel;
        if (binding != null && binding.contentVersion == tile.contentVersion() && !binding.panel.evicted) {
            panel = binding.panel;
        } else {
            NBTTagCompound snapshot = tile.getSnapshot();
            Key key = new Key(snapshot, tile.getSettings());
            panel = panels.get(key);
            if (panel == null) {
                if (panels.size() >= MAX_PANELS) return unavailable;
                panel = new Panel(snapshot, key.settings);
                panels.put(key, panel);
            }
            bindings.put(tile, new Binding(tile.contentVersion(), panel));
        }
        panel.lastSeen = System.nanoTime();
        panel.lastSeenFrame = frame;
        return panel;
    }

    boolean requestAnimation(RecipePanelTile tile, Vec3 eye, double x, double y, double z) {
        double dx = eye.xCoord - x;
        double dy = eye.yCoord - y;
        double dz = eye.zCoord - z;
        if (dx * dx + dy * dy + dz * dz > ANIMATION_DISTANCE_SQUARED) return false;
        Binding binding = bindings.get(tile);
        if (binding == null || binding.panel.animationFailed || binding.panel.recipe == null) return false;
        if (binding.visibilityTick != clientTick) {
            binding.visibilityTick = clientTick;
            // Minecraft's ray trace mutates its start vector.
            Vec3 rayStart = Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord);
            MovingObjectPosition obstruction = tile.getWorldObj()
                .rayTraceBlocks(rayStart, Vec3.createVectorHelper(x, y, z));
            binding.unobstructed = obstruction == null
                || obstruction.blockX == tile.xCoord && obstruction.blockY == tile.yCoord
                    && obstruction.blockZ == tile.zCoord;
        }
        if (binding.unobstructed) binding.panel.lastAnimatedFrame = frame;
        return binding.unobstructed;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clientTick++;
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld) {
            reload();
            world = mc.theWorld;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || panels.isEmpty()) return;
        frame++;
        long start = System.nanoTime();
        List<Panel> dispose = null;
        Iterator<Panel> iterator = panels.values()
            .iterator();
        boolean framebuffersEnabled = OpenGlHelper.isFramebufferEnabled();
        while (iterator.hasNext()) {
            Panel panel = iterator.next();
            if (start - panel.lastSeen > EVICT_AFTER_NS && !panel.visibleInFrame(frame)) {
                panel.evicted = true;
                iterator.remove();
            }
            boolean disposeAll = panel.evicted || panel.renderFailed || !framebuffersEnabled;
            if ((disposeAll && panel.bytes() > 0) || (!panel.animationRequested(frame) && panel.animationBytes() > 0)) {
                if (dispose == null) dispose = new ArrayList<>();
                dispose.add(panel);
            }
        }
        if (dispose != null) {
            try (RenderState ignored = new RenderState()) {
                for (Panel panel : dispose) {
                    if (panel.evicted || panel.renderFailed || !framebuffersEnabled) panel.dispose();
                    else panel.disposeAnimation();
                }
            }
        }
        if (!framebuffersEnabled) return;
        long memory = 0;
        for (Panel panel : panels.values()) memory += panel.bytes();
        for (Panel panel : panels.values()) {
            if (panel.renderFailed || !panel.visibleInFrame(frame)) continue;
            if (panel.ready()) {
                if (!panel.animationRequested(frame) || panel.lastAnimationTick == clientTick) continue;
                long additionalBytes = 2 * PanelTexture.estimatedBytes(panel.width) - panel.animationBytes();
                if (memory + additionalBytes > MAX_TEXTURE_BYTES) continue;
                long previousBytes = panel.bytes();
                panel.animate(clientTick);
                memory += panel.bytes() - previousBytes;
                if (System.nanoTime() - start >= FRAME_BUDGET_NS) break;
                continue;
            }
            long previousBytes = panel.bytes();
            panel.capacityLimited = false;
            if (memory >= MAX_TEXTURE_BYTES && previousBytes == 0) {
                panel.capacityLimited = true;
                continue;
            }
            panel.prepare();
            if (panel.renderFailed) continue;
            long requiredBytes = PanelTexture.estimatedBytes(panel.width);
            if (memory - previousBytes + requiredBytes > MAX_TEXTURE_BYTES) {
                panel.capacityLimited = true;
                continue;
            }
            panel.render();
            memory += panel.bytes() - previousBytes;
            if (System.nanoTime() - start >= FRAME_BUDGET_NS) break;
        }
    }

    private static final class Binding {

        private final long contentVersion;
        private final Panel panel;
        private long visibilityTick = -1;
        private boolean unobstructed;

        private Binding(long contentVersion, Panel panel) {
            this.contentVersion = contentVersion;
            this.panel = panel;
        }
    }

    private static final class Key {

        private final NBTTagCompound snapshot;
        private final NBTTagCompound settings;
        private final int hash;

        private Key(NBTTagCompound snapshot, NBTTagCompound settings) {
            this.snapshot = snapshot;
            this.settings = settings == null ? new NBTTagCompound() : settings;
            hash = 31 * snapshot.hashCode() + this.settings.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && snapshot.equals(((Key) other).snapshot)
                && settings.equals(((Key) other).settings);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    public static final class Panel {

        private final NBTTagCompound snapshot;
        private final PanelSettings settings;
        private final PanelTexture texture = new PanelTexture();
        private final PanelTexture background = new PanelTexture();
        private final PanelTexture animated = new PanelTexture();
        private RecipeResolver.Resolution resolution;
        private ResolvedRecipe recipe;
        private PanelRecipeWidget widget;
        private boolean evicted;
        private boolean renderFailed;
        private boolean capacityLimited;
        private boolean animationFailed;
        private long lastSeen;
        private long lastSeenFrame;
        private long lastAnimatedFrame = -2;
        private long lastAnimationTick = -1;
        private int originX;
        private int originY;
        private int yShift;
        int width = 176;
        int height = 176;

        private Panel(NBTTagCompound snapshot, NBTTagCompound settings) {
            this.snapshot = snapshot;
            this.settings = PanelSettings.fromNBT(settings);
            capacityLimited = snapshot == null;
        }

        boolean ready() {
            return !renderFailed && texture.ready();
        }

        private boolean visibleInFrame(long frame) {
            return frame - lastSeenFrame <= 1;
        }

        boolean transparent() {
            return settings.transparent;
        }

        void bindTexture(boolean animate) {
            (animate && animated.ready() ? animated : texture).bind();
        }

        private long bytes() {
            return texture.bytes() + animationBytes();
        }

        private long animationBytes() {
            return background.bytes() + animated.bytes();
        }

        private boolean animationRequested(long frame) {
            return !animationFailed && recipe != null && frame - lastAnimatedFrame <= 1;
        }

        private void dispose() {
            texture.dispose();
            disposeAnimation();
        }

        private void disposeAnimation() {
            background.dispose();
            animated.dispose();
            lastAnimationTick = -1;
        }

        String statusKey() {
            if (!OpenGlHelper.isFramebufferEnabled()) return "nei-recipe-panels.render.unavailable";
            if (renderFailed) return "nei-recipe-panels.render.failed";
            if (capacityLimited) return "nei-recipe-panels.render.capacity";
            return resolution == null ? "nei-recipe-panels.render.loading"
                : resolution.status()
                    .translationKey();
        }

        ItemStack stackAt(int x, int y) {
            if (recipe == null || !ready()) return null;
            for (PositionedStack stack : recipe.allStacks()) {
                if (!(stack instanceof PositionedStack.Placeholder)
                    && stack.contains(x - originX, y - originY - yShift)) {
                    return stack.item == null ? null : stack.item.copy();
                }
            }
            return null;
        }

        private void prepare() {
            try {
                if (resolution == null) {
                    resolution = RecipeResolver.INSTANCE.resolve(snapshot);
                    recipe = resolution.recipe();
                    if (recipe != null) widget = new PanelRecipeWidget(recipe);
                }
                if (recipe == null) return;
                recipe.refresh();
                recipe.name();
                HandlerInfo info = GuiRecipeTab.getHandlerInfo(recipe.handler);
                yShift = info.getYShift();
                int minX = 0;
                int minY = Math.min(0, yShift);
                int maxX = Math.max(166, info.getWidth());
                int recipeHeight = recipe.handler.getRecipeHeight(recipe.index);
                int maxY = (recipeHeight > 0 ? recipeHeight : info.getHeight()) + Math.max(0, yShift);
                for (PositionedStack stack : recipe.allStacks()) {
                    minX = Math.min(minX, stack.relx);
                    minY = Math.min(minY, stack.rely + yShift);
                    maxX = Math.max(maxX, stack.relx + stack.width);
                    maxY = Math.max(maxY, stack.rely + yShift + stack.height);
                }
                int bodyWidth = maxX - minX;
                int bodyHeight = maxY - minY;
                int side = Math.max(176, Math.max(bodyWidth + 24, bodyHeight + 38));
                if (side > 512 || bodyWidth <= 0 || bodyHeight <= 0)
                    throw new IllegalArgumentException("Recipe exceeds panel layout limits");
                width = height = side;
                originX = (side - bodyWidth) / 2 - minX;
                originY = 14 + (side - 14 - bodyHeight) / 2 - minY;
                widget.setLocation(originX, originY);
            } catch (RuntimeException | LinkageError e) {
                fail(e);
            }
        }

        private void render() {
            if (renderFailed) return;
            Minecraft mc = Minecraft.getMinecraft();
            mc.mcProfiler.startSection("recipePanels.capture");
            try {
                texture.render(width, () -> draw(false));
            } catch (RuntimeException | LinkageError e) {
                fail(e);
            } finally {
                mc.mcProfiler.endSection();
            }
        }

        private void animate(long tick) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.mcProfiler.startSection("recipePanels.items");
            try {
                if (!background.ready()) background.render(width, () -> draw(true));
                animated.render(width, () -> {
                    background.draw(width, height);
                    widget.drawItems();
                    finishImage();
                });
                lastAnimationTick = tick;
            } catch (RuntimeException | LinkageError failure) {
                animationFailed = true;
                NEIRecipePanelsMod.LOG.warn("Could not animate recipe panel", failure);
                try (RenderState ignored = new RenderState()) {
                    disposeAnimation();
                }
            } finally {
                mc.mcProfiler.endSection();
            }
        }

        private void draw(boolean backgroundOnly) {
            Minecraft mc = Minecraft.getMinecraft();
            if (!settings.transparent) PanelBackdrop.draw(width, height);
            String title = settings.customName.isEmpty() ? recipe == null ? "" : recipe.handler.getRecipeTabName()
                : settings.customName;
            FontRenderer font = mc.fontRenderer;
            title = font.trimStringToWidth(title, width - 16);
            font.drawString(
                title,
                (width - font.getStringWidth(title)) / 2,
                5,
                settings.transparent ? 0xFFFFFF : 0x404040);
            if (widget != null) {
                if (backgroundOnly) widget.drawBackground();
                else widget.draw(-10000, -10000);
            } else {
                PanelStatusText.draw(font, statusKey(), width, height, settings.transparent ? 0xFFFFFF : 0x404040);
            }
            finishImage();
        }

        private void finishImage() {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glTranslatef(0, 0, -2000);
            if (!settings.transparent) PanelBackdrop.stampAlpha(width, height);
        }

        private void fail(Throwable failure) {
            renderFailed = true;
            NEIRecipePanelsMod.LOG.warn("Could not render recipe panel", failure);
        }
    }
}
