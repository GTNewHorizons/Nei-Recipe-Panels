package com.gtnewhorizons.neirecipepanel.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Constructor;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;

class PanelFboManagerTest {

    private PanelFboManager manager;

    @BeforeEach
    void createManager() throws ReflectiveOperationException {
        Constructor<PanelFboManager> constructor = PanelFboManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        manager = constructor.newInstance();
    }

    @Test
    void unchangedTilesReuseTheirBindingWithoutCopyingRecipeDataEachFrame() {
        CountingTile tile = tile();
        PanelFboManager.Panel panel = manager.visible(tile);

        for (int frame = 0; frame < 120; frame++) assertSame(panel, manager.visible(tile));

        assertEquals(1, tile.snapshotReads);
        assertEquals(1, tile.settingsReads);
    }

    @Test
    void settingsChangesReplaceTheCachedImage() {
        CountingTile tile = tile();
        PanelFboManager.Panel original = manager.visible(tile);
        NBTTagCompound settings = new NBTTagCompound();
        settings.setString("name", "New title");

        tile.setSettings(settings);
        PanelFboManager.Panel changed = manager.visible(tile);

        assertNotSame(original, changed);
        assertSame(changed, manager.visible(tile));
        assertEquals(2, tile.snapshotReads);
        assertEquals(2, tile.settingsReads);
    }

    @Test
    void synchronizedRecipeChangesReplaceTheCachedImage() {
        CountingTile tile = tile();
        PanelFboManager.Panel original = manager.visible(tile);
        NBTTagCompound synchronizedData = new NBTTagCompound();
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setString("recipeId", "Updated recipe");
        synchronizedData.setTag("snapshot", snapshot);

        tile.readFromNBT(synchronizedData);

        assertNotSame(original, manager.visible(tile));
        assertEquals(2, tile.snapshotReads);
    }

    @Test
    void identicalPanelsShareOneImage() {
        CountingTile first = tile();
        CountingTile second = tile();

        assertSame(manager.visible(first), manager.visible(second));
    }

    private static CountingTile tile() {
        CountingTile tile = new CountingTile();
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setString("recipeId", "Fixture recipe");
        tile.setSnapshot(snapshot);
        return tile;
    }

    private static final class CountingTile extends RecipePanelTile {

        private int snapshotReads;
        private int settingsReads;

        @Override
        public NBTTagCompound getSnapshot() {
            snapshotReads++;
            return super.getSnapshot();
        }

        @Override
        public NBTTagCompound getSettings() {
            settingsReads++;
            return super.getSettings();
        }
    }
}
