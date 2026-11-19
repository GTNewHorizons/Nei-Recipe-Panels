package com.gtnewhorizons.neirecipepanel.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

/**
 * Settings in {@code config/nei-recipe-panels.cfg}, one category per nested class, editable in-game from the mod list.
 * Field initialisers mirror the defaults so code outside FML (unit tests) sees the same values.
 */
public final class PanelConfig {

    private static final String LANG_PATTERN = "%mod.cfg.%cat.%field";

    private PanelConfig() {}

    public static void register() {
        try {
            ConfigurationManager.registerConfig(Recipe.class);
            ConfigurationManager.registerConfig(Server.class);
            ConfigurationManager.registerConfig(Client.class);
        } catch (ConfigException e) {
            throw new RuntimeException("Could not load " + NEIRecipePanelsMod.MODID + " config", e);
        }
    }

    public enum PanelMode {
        DISABLED,
        CREATIVE_ONLY,
        OP_ONLY,
        EVERYONE
    }

    @Config(modid = NEIRecipePanelsMod.MODID, category = "recipe")
    @Config.LangKeyPattern(pattern = LANG_PATTERN)
    public static final class Recipe {

        @Config.Comment("Register the built-in crafting recipe for the Recipe Blueprint. Disable to define your own via a tweaker.")
        @Config.DefaultBoolean(true)
        @Config.RequiresMcRestart
        public static boolean registerBlueprintRecipe = true;
    }

    @Config(modid = NEIRecipePanelsMod.MODID, category = "server")
    @Config.LangKeyPattern(pattern = LANG_PATTERN)
    public static final class Server {

        @Config.Comment("Who may imprint recipe panels. Synced to clients so the NEI button reflects it.")
        @Config.DefaultEnum("EVERYONE")
        @Config.Sync
        public static PanelMode panelMode = PanelMode.EVERYONE;

        @Config.Comment("Also spend a Recipe Blueprint when a creative-mode player imprints.")
        @Config.DefaultBoolean(false)
        @Config.Sync
        public static boolean consumeInCreative = false;

        @Config.Comment("Reject an imprint whose recipe has more ingredient / byproduct slots than this.")
        @Config.DefaultInt(RecipeSnapshot.MAX_SLOTS)
        @Config.RangeInt(min = 1, max = RecipeSnapshot.MAX_SLOTS)
        public static int maxIngredients = RecipeSnapshot.MAX_SLOTS;

        @Config.Comment("Maximum uncompressed bytes in an imprinted recipe document, before it is queued or saved.")
        @Config.DefaultInt(16384)
        @Config.RangeInt(min = 512, max = 262144)
        public static int maxSnapshotBytes = 16384;
    }

    @Config(modid = NEIRecipePanelsMod.MODID, category = "client")
    @Config.LangKeyPattern(pattern = LANG_PATTERN)
    public static final class Client {

        @Config.Comment("Video memory budget for cached panel images. Panels beyond it show a capacity notice.")
        @Config.DefaultInt(192)
        @Config.RangeInt(min = 32, max = 4096)
        public static int textureBudgetMB = 256;

        @Config.Comment("Panel image resolution multiplier. Lower values fit more panels in the same budget.")
        @Config.DefaultInt(3)
        @Config.RangeInt(min = 1, max = 4)
        public static int supersample = 3;

        @Config.Comment("Only show the NEI imprint button when you are allowed to imprint (a Recipe Blueprint in your inventory, or creative mode).")
        @Config.DefaultBoolean(true)
        public static boolean buttonRequiresBlueprint = true;
    }
}
