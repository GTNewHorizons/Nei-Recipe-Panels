package com.gtnewhorizons.neirecipepanel.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;
import com.gtnewhorizons.neirecipepanel.client.recipe.ResolvedRecipe;

import sun.misc.Unsafe;

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

    @Test
    void animationVisibilityPreservesTheCameraForAdjacentPanels() throws ReflectiveOperationException {
        Field singleton = Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Unsafe unsafe = (Unsafe) singleton.get(null);
        EmptyWorld world = (EmptyWorld) unsafe.allocateInstance(EmptyWorld.class);
        CountingTile north = tile();
        CountingTile east = tile();
        Field recipe = PanelFboManager.Panel.class.getDeclaredField("recipe");
        recipe.setAccessible(true);
        recipe.set(manager.visible(north), unsafe.allocateInstance(ResolvedRecipe.class));
        manager.visible(east);
        north.setWorldObj(world);
        east.setWorldObj(world);
        Vec3 eye = Vec3.createVectorHelper(1.35, 1.75, 1.65);

        assertTrue(manager.requestAnimation(north, eye, 0.5, 1.5, 1.99));
        assertEquals(1.35, eye.xCoord);
        assertEquals(1.75, eye.yCoord);
        assertEquals(1.65, eye.zCoord);
        assertTrue(manager.requestAnimation(east, eye, 1.01, 1.5, 2.5));
        assertEquals(1.35, eye.xCoord);
        assertEquals(1.75, eye.yCoord);
        assertEquals(1.65, eye.zCoord);
    }

    private static final class EmptyWorld extends World {

        private static final Block AIR = new Block(Material.air) {

            @Override
            public boolean canCollideCheck(int metadata, boolean hitLiquids) {
                return false;
            }
        };

        private EmptyWorld() {
            super(null, "fixture", (WorldProvider) null, null, null);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return AIR;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return 0;
        }
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
