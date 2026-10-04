package com.gtnewhorizons.neirecipepanel;

import net.minecraft.block.Block;

import com.gtnewhorizons.neirecipepanel.block.RecipePanelBlock;
import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModBlocks {

    public static Block recipePanel;

    private ModBlocks() {}

    public static void register() {
        recipePanel = new RecipePanelBlock();
        GameRegistry.registerBlock(recipePanel, null, "recipe_panel");
        GameRegistry.registerTileEntity(RecipePanelTile.class, NEIRecipePanelsMod.MODID + ":recipe_panel");
    }
}
