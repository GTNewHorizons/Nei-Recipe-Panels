package com.gtnewhorizons.neirecipepanel.client.recipe;

import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeHandlerRef;
import cpw.mods.fml.common.Loader;

/** Entry point for NEI Custom Diagram support that is safe to load when that mod is absent. */
public final class DiagramHandlers {

    private static final boolean LOADED = detect();

    private DiagramHandlers() {}

    private static boolean detect() {
        try {
            return Loader.isModLoaded("neicustomdiagram");
        } catch (RuntimeException | LinkageError e) {
            return false; // outside FML (unit tests)
        }
    }

    public static boolean isDiagram(IRecipeHandler handler) {
        return LOADED && handler != null && DiagramSupport.isDiagram(handler);
    }

    static RecipeSnapshot capture(IRecipeHandler handler, int index) {
        return LOADED ? DiagramSupport.capture(handler, index) : null;
    }

    /** Null when the diagram cannot be found; the ref's handler holds only that diagram, at index 0. */
    static Match resolve(RecipeSnapshot snapshot) {
        return LOADED ? DiagramSupport.resolve(snapshot) : null;
    }

    static final class Match {

        final RecipeHandlerRef ref;
        final boolean exact;

        Match(RecipeHandlerRef ref, boolean exact) {
            this.ref = ref;
            this.exact = exact;
        }
    }
}
