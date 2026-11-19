package com.gtnewhorizons.neirecipepanel.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;

/**
 * Angelica recolours GUI text for dark resource packs (their {@code dark_mode_utils} rules) only inside recolour
 * sections, which it opens around {@code GuiContainer} drawing. Panels draw outside any GUI, so they open one
 * themselves. A no-op without Angelica or on versions that lack the feature.
 */
final class DarkModeText {

    private static final MethodHandle ENTER;
    private static final MethodHandle EXIT;

    static {
        MethodHandle enter = null;
        MethodHandle exit = null;
        try {
            Class<?> renderer = Class.forName("com.gtnewhorizons.angelica.client.font.BatchingFontRenderer");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            enter = lookup
                .findStatic(renderer, "enterRecolorSection", MethodType.methodType(boolean.class, boolean.class));
            exit = lookup.findStatic(renderer, "exitRecolorSection", MethodType.methodType(void.class, boolean.class));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Angelica absent or too old: GUI text keeps its own colours.
        }
        ENTER = enter;
        EXIT = exit;
    }

    private DarkModeText() {}

    /** Runs {@code draw} with dark-mode text recolouring enabled, restoring the previous state afterwards. */
    static void recolored(Runnable draw) {
        if (ENTER == null) {
            draw.run();
            return;
        }
        boolean previous = enter();
        try {
            draw.run();
        } finally {
            exit(previous);
        }
    }

    private static boolean enter() {
        try {
            return (boolean) ENTER.invokeExact(true);
        } catch (Throwable e) {
            NEIRecipePanelsMod.LOG.debug("Could not enter Angelica text recolour section", e);
            return false;
        }
    }

    private static void exit(boolean previous) {
        try {
            EXIT.invokeExact(previous);
        } catch (Throwable e) {
            NEIRecipePanelsMod.LOG.debug("Could not exit Angelica text recolour section", e);
        }
    }
}
