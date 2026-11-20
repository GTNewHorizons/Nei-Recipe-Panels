package com.gtnewhorizons.neirecipepanel.client.gui;

import java.awt.Point;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.client.recipe.DiagramHandlers;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.guihook.IContainerDrawHandler;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.scroll.ScrollContainer;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * NEI only gives recipes with outputs a button column, so NEI Custom Diagram pages get none. This draws the imprint
 * button on those pages itself, placed like the regular one.
 */
public final class DiagramPanelButtons
    implements IContainerDrawHandler, IContainerInputHandler, IContainerTooltipHandler {

    private static final int SIZE = 12;
    private static final Field GUI_LEFT = ReflectionHelper.findField(GuiContainer.class, "guiLeft", "field_147003_i");
    private static final Field GUI_TOP = ReflectionHelper.findField(GuiContainer.class, "guiTop", "field_147009_r");
    private static final Field X_SIZE = ReflectionHelper.findField(GuiContainer.class, "xSize", "field_146999_f");
    private static final Field Y_SIZE = ReflectionHelper.findField(GuiContainer.class, "ySize", "field_147000_g");
    private static final Field CONTAINER = findContainer();

    // Buttons hold their handler, so only the handler on screen is cached; switching or leaving drops it.
    private IRecipeHandler cachedHandler;
    private final Map<Integer, Entry> entries = new HashMap<>();
    private final List<Entry> shown = new ArrayList<>();
    private boolean warned;

    @Override
    public void renderObjects(GuiContainer gui, int mouseX, int mouseY) {
        shown.clear();
        IRecipeHandler handler = gui instanceof GuiRecipe ? ((GuiRecipe<?>) gui).getHandler() : null;
        if (handler != cachedHandler) {
            entries.clear();
            cachedHandler = null;
        }
        if (!DiagramHandlers.isDiagram(handler)) return;
        GuiRecipe<?> screen = (GuiRecipe<?>) gui;
        cachedHandler = handler;
        try {
            int left = GUI_LEFT.getInt(gui);
            int top = GUI_TOP.getInt(gui);
            Rectangle4i view = viewport(screen, left, top);
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                for (int index : screen.getRecipeIndices()) {
                    Entry entry = entries.computeIfAbsent(index, i -> create(handler, i, info));
                    Point position = screen.getRecipePosition(index);
                    int x = left + position.x + entry.x;
                    int y = top + position.y - info.getYShift() + entry.y;
                    entry.button.xPosition = x;
                    entry.button.yPosition = y;
                    if (x < view.x || y < view.y || x + SIZE > view.x + view.w || y + SIZE > view.y + view.h) continue;
                    entry.button.update();
                    if (!entry.button.visible) continue;
                    GL11.glColor4f(1, 1, 1, 1);
                    entry.button.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
                    shown.add(entry);
                }
            } finally {
                GL11.glPopAttrib();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (!warned) NEIRecipePanelsMod.LOG.warn("Could not draw the recipe panel button on a diagram page", e);
            warned = true;
        }
    }

    private static Entry create(IRecipeHandler handler, int index, HandlerInfo info) {
        RecipeHandlerRef ref = RecipeHandlerRef.of(handler, index);
        int width = Math.max(166, info.getWidth());
        int recipeHeight = handler.getRecipeHeight(index);
        int height = (recipeHeight > 0 ? recipeHeight : info.getHeight()) + info.getYShift();
        int[] spot = GuiRecipeButtonHandler.place(ref, width, height, Collections.emptyList());
        return new Entry(new GuiRecipePanelButton(ref, 0, 0), spot[0], spot[1]);
    }

    /** NEI's scrolling recipe area, so buttons scrolled out of it aren't drawn over the frame. */
    private static Rectangle4i viewport(GuiRecipe<?> screen, int left, int top) throws IllegalAccessException {
        if (CONTAINER != null) {
            Object container = CONTAINER.get(screen);
            if (container instanceof ScrollContainer) return ((ScrollContainer) container).boundsInside();
        }
        return new Rectangle4i(left + 3, top + 32, X_SIZE.getInt(screen) - 6, Y_SIZE.getInt(screen) - 36);
    }

    private static Field findContainer() {
        try {
            Field field = GuiRecipe.class.getDeclaredField("container");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private Entry hovered(int mouseX, int mouseY) {
        for (Entry entry : shown) if (entry.button.contains(mouseX, mouseY)) return entry;
        return null;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mouseX, int mouseY, int button) {
        Entry entry = button == 0 ? hovered(mouseX, mouseY) : null;
        if (entry == null || !entry.button.enabled) return false;
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
        entry.button.mouseReleased(mouseX, mouseY);
        return true;
    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mouseX, int mouseY, List<String> tooltip) {
        Entry entry = hovered(mouseX, mouseY);
        return entry == null ? tooltip : entry.button.handleTooltip(tooltip);
    }

    @Override
    public void onPreDraw(GuiContainer gui) {}

    @Override
    public void postRenderObjects(GuiContainer gui, int mouseX, int mouseY) {}

    @Override
    public void renderSlotUnderlay(GuiContainer gui, Slot slot) {}

    @Override
    public void renderSlotOverlay(GuiContainer gui, Slot slot) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyCode) {}

    @Override
    public void onMouseClicked(GuiContainer gui, int mouseX, int mouseY, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mouseX, int mouseY, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mouseX, int mouseY, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mouseX, int mouseY, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mouseX, int mouseY, int button, long heldTime) {}

    private static final class Entry {

        private final GuiRecipePanelButton button;
        private final int x;
        private final int y;

        private Entry(GuiRecipePanelButton button, int x, int y) {
            this.button = button;
            this.x = x;
            this.y = y;
        }
    }
}
