package com.gtnewhorizons.neirecipepanel.client.render;

import java.lang.reflect.Field;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * Panels are static images, so animated progress bars would freeze on whatever frame they were captured at. This zeroes
 * the tick counters handlers derive progress from while a panel draws, so bars always show empty.
 */
final class ProgressFreeze implements AutoCloseable {

    /** GregTech's recipe screens share one static tick counter for their progress bars. */
    private static final Class<?> GT_HANDLER;
    private static final Field GT_DRAW_TICKS;

    static {
        Class<?> handler = null;
        Field ticks = null;
        try {
            handler = Class.forName("gregtech.nei.GTNEIDefaultHandler");
            ticks = handler.getDeclaredField("drawTicks");
            ticks.setAccessible(true);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            handler = null;
            ticks = null;
        }
        GT_HANDLER = handler;
        GT_DRAW_TICKS = ticks;
    }

    private final TemplateRecipeHandler template;
    private final int templateTicks;
    private final boolean gregtech;
    private final int gregtechTicks;

    ProgressFreeze(IRecipeHandler handler) {
        template = handler instanceof TemplateRecipeHandler ? (TemplateRecipeHandler) handler : null;
        templateTicks = template == null ? 0 : template.cycleticks;
        if (template != null) template.cycleticks = 0;
        gregtech = GT_DRAW_TICKS != null && GT_HANDLER.isInstance(handler);
        gregtechTicks = gregtech ? swapGregTechTicks(0) : 0;
    }

    @Override
    public void close() {
        if (template != null) template.cycleticks = templateTicks;
        if (gregtech) swapGregTechTicks(gregtechTicks);
    }

    private static int swapGregTechTicks(int value) {
        try {
            int previous = GT_DRAW_TICKS.getInt(null);
            GT_DRAW_TICKS.setInt(null, value);
            return previous;
        } catch (IllegalAccessException e) {
            return value;
        }
    }
}
