package com.gtnewhorizons.neirecipepanel.config;

import java.io.File;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

public class Config {

    public enum PanelMode {
        DISABLED,
        CREATIVE_ONLY,
        OP_ONLY,
        EVERYONE
    }

    public static boolean registerBlueprintRecipe = true;

    public static PanelMode panelMode = PanelMode.EVERYONE;
    public static boolean consumeInCreative = false;
    public static int maxIngredients = RecipeSnapshot.MAX_SLOTS;
    public static int maxSnapshotBytes = 16384;

    public static int textureBudgetMB = 192;
    public static int supersample = 3;
    public static boolean buttonRequiresBlueprint = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);
        configuration.load();

        registerBlueprintRecipe = configuration.getBoolean(
            "registerBlueprintRecipe",
            "recipe",
            true,
            "Register the built-in crafting recipe for the Recipe Blueprint. Disable to define your own via a tweaker.");

        panelMode = parseMode(
            configuration.getString(
                "panelMode",
                "server",
                PanelMode.EVERYONE.name(),
                "Who may imprint recipe panels.",
                names(PanelMode.values())));
        consumeInCreative = configuration.getBoolean(
            "consumeInCreative",
            "server",
            false,
            "Also spend a Recipe Blueprint when a creative-mode player imprints.");
        maxIngredients = configuration.getInt(
            "maxIngredients",
            "server",
            RecipeSnapshot.MAX_SLOTS,
            1,
            RecipeSnapshot.MAX_SLOTS,
            "Reject an imprint whose recipe has more ingredient / byproduct slots than this.");
        maxSnapshotBytes = configuration.getInt(
            "maxSnapshotBytes",
            "server",
            16384,
            512,
            262144,
            "Maximum uncompressed bytes in an imprinted recipe document, before it is queued or saved.");

        textureBudgetMB = configuration.getInt(
            "textureBudgetMB",
            "client",
            192,
            32,
            4096,
            "Video memory budget for cached panel images. Panels beyond it show a capacity notice.");
        supersample = configuration.getInt(
            "supersample",
            "client",
            3,
            1,
            4,
            "Panel image resolution multiplier. Lower values fit more panels in the same budget.");
        buttonRequiresBlueprint = configuration.getBoolean(
            "buttonRequiresBlueprint",
            "client",
            true,
            "Only show the NEI imprint button while a Recipe Blueprint is in your inventory (always shown in creative).");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    private static PanelMode parseMode(String value) {
        try {
            return PanelMode.valueOf(
                value.trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PanelMode.EVERYONE;
        }
    }

    private static String[] names(PanelMode[] modes) {
        String[] out = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            out[i] = modes[i].name();
        }
        return out;
    }
}
