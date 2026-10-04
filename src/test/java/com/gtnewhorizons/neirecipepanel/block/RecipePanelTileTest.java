package com.gtnewhorizons.neirecipepanel.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecipePanelTileTest {

    @BeforeAll
    static void registerFixtureTile() {
        TileEntity.addMapping(RecipePanelTile.class, "nei-recipe-panels:test_recipe_panel_tile");
    }

    @Test
    void renderBoundsCoverOnlyThePanelBlock() {
        RecipePanelTile tile = new RecipePanelTile();
        tile.xCoord = 7;
        tile.yCoord = 91;
        tile.zCoord = -4;

        AxisAlignedBB bounds = tile.getRenderBoundingBox();

        assertEquals(7, bounds.minX);
        assertEquals(91, bounds.minY);
        assertEquals(-4, bounds.minZ);
        assertEquals(8, bounds.maxX);
        assertEquals(92, bounds.maxY);
        assertEquals(-3, bounds.maxZ);
    }

    @Test
    void tileOwnsCopiesOfAssignedAndReturnedData() {
        RecipePanelTile tile = new RecipePanelTile();
        NBTTagCompound snapshot = unknownSnapshot();
        NBTTagCompound settings = settings();
        tile.setData(snapshot, settings);

        snapshot.getCompoundTag("extension")
            .setString("value", "Changed input");
        settings.setString("name", "Changed input");
        tile.getSnapshot()
            .getCompoundTag("extension")
            .setString("value", "Changed getter");
        tile.getSettings()
            .setString("name", "Changed getter");

        assertEquals(unknownSnapshot(), tile.getSnapshot());
        assertEquals(settings(), tile.getSettings());
    }

    @Test
    void individualSettersOwnTheirInputData() {
        RecipePanelTile tile = new RecipePanelTile();
        NBTTagCompound snapshot = unknownSnapshot();
        NBTTagCompound settings = settings();
        tile.setSnapshot(snapshot);
        tile.setSettings(settings);
        snapshot.setInteger("ver", 1);
        settings.setBoolean("transparent", false);

        assertEquals(unknownSnapshot(), tile.getSnapshot());
        assertEquals(settings(), tile.getSettings());
    }

    @Test
    void serializedDataCannotMutateTileState() {
        RecipePanelTile tile = new RecipePanelTile();
        tile.setData(unknownSnapshot(), settings());
        NBTTagCompound serialized = new NBTTagCompound();
        tile.writeToNBT(serialized);
        serialized.getCompoundTag("snapshot")
            .getCompoundTag("extension")
            .setString("value", "Changed save");
        serialized.getCompoundTag("cfg")
            .setString("name", "Changed save");

        assertEquals(unknownSnapshot(), tile.getSnapshot());
        assertEquals(settings(), tile.getSettings());
    }

    @Test
    void loadedDataCannotMutateTileState() {
        NBTTagCompound serialized = new NBTTagCompound();
        serialized.setTag("snapshot", unknownSnapshot());
        serialized.setTag("cfg", settings());
        RecipePanelTile tile = new RecipePanelTile();
        tile.readFromNBT(serialized);
        serialized.getCompoundTag("snapshot")
            .getCompoundTag("extension")
            .setString("value", "Changed load");
        serialized.getCompoundTag("cfg")
            .setString("name", "Changed load");

        assertEquals(unknownSnapshot(), tile.getSnapshot());
        assertEquals(settings(), tile.getSettings());
    }

    @Test
    void clearingDataRemovesStaleTagsFromReusedSaveCompound() {
        RecipePanelTile tile = new RecipePanelTile();
        NBTTagCompound serialized = new NBTTagCompound();
        serialized.setString("unrelated", "Retained");
        tile.setData(unknownSnapshot(), settings());
        tile.writeToNBT(serialized);

        tile.setData(null, null);
        tile.writeToNBT(serialized);

        assertFalse(serialized.hasKey("snapshot"));
        assertFalse(serialized.hasKey("cfg"));
        assertEquals("Retained", serialized.getString("unrelated"));
        assertNull(tile.getSnapshot());
        assertNull(tile.getSettings());
    }

    @Test
    void unknownSnapshotVersionSurvivesSaveAndReload() {
        RecipePanelTile tile = new RecipePanelTile();
        tile.xCoord = 7;
        tile.yCoord = 91;
        tile.zCoord = -4;
        tile.setData(unknownSnapshot(), settings());
        NBTTagCompound serialized = new NBTTagCompound();
        tile.writeToNBT(serialized);

        RecipePanelTile reloaded = assertInstanceOf(RecipePanelTile.class, TileEntity.createAndLoadEntity(serialized));

        assertEquals(7, reloaded.xCoord);
        assertEquals(91, reloaded.yCoord);
        assertEquals(-4, reloaded.zCoord);
        assertEquals(unknownSnapshot(), reloaded.getSnapshot());
        assertEquals(settings(), reloaded.getSettings());
    }

    @Test
    void absentSettingsUseDefaultsAndReplacePreviouslyLoadedSettings() {
        RecipePanelTile tile = new RecipePanelTile();
        tile.setSettings(settings());
        NBTTagCompound serialized = new NBTTagCompound();
        serialized.setTag("snapshot", unknownSnapshot());

        tile.readFromNBT(serialized);

        assertNull(tile.getSettings());
        assertEquals("", tile.settings().customName);
        assertFalse(tile.settings().transparent);
    }

    @Test
    void emptySettingsAreOmittedFromSaveData() {
        RecipePanelTile tile = new RecipePanelTile();
        tile.setData(unknownSnapshot(), new NBTTagCompound());
        NBTTagCompound serialized = new NBTTagCompound();
        tile.writeToNBT(serialized);

        assertFalse(serialized.hasKey("cfg"));
        assertEquals("", tile.settings().customName);
        assertFalse(tile.settings().transparent);
    }

    private static NBTTagCompound unknownSnapshot() {
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setInteger("ver", 100);
        snapshot.setString("recipeId", "future recipe encoding");
        NBTTagCompound extension = new NBTTagCompound();
        extension.setString("value", "Preserved");
        extension.setIntArray("choices", new int[] { 7, 3, 9 });
        snapshot.setTag("extension", extension);
        return snapshot;
    }

    private static NBTTagCompound settings() {
        NBTTagCompound settings = new NBTTagCompound();
        settings.setString("name", "Original");
        settings.setBoolean("transparent", true);
        return settings;
    }
}
