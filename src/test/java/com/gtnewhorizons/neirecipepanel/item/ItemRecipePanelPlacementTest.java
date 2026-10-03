package com.gtnewhorizons.neirecipepanel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.neirecipepanel.ModItems;

import sun.misc.Unsafe;

class ItemRecipePanelPlacementTest {

    private ItemRecipePanel item;
    private Item previousPanelItem;
    private FixtureWorld world;
    private EntityPlayerMP player;
    private ItemStack stack;

    @BeforeEach
    void createFixture() throws ReflectiveOperationException {
        Unsafe unsafe = unsafe();
        world = (FixtureWorld) unsafe.allocateInstance(FixtureWorld.class);
        world.reportedHeight = 256;
        world.targetLoaded = true;
        Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        player = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
        player.capabilities = new PlayerCapabilities();
        previousPanelItem = ModItems.recipePanel;
        item = new ItemRecipePanel();
        ModItems.recipePanel = item;
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setInteger("ver", 100);
        stack = ItemRecipePanel.withSnapshot(snapshot);
    }

    @AfterEach
    void restorePanelItem() {
        ModItems.recipePanel = previousPanelItem;
    }

    @Test
    void downwardPlacementAtBottomDoesNotConsumePanel() {
        assertFalse(place(0, 0, 0, 0));
        assertEquals(0, world.placementAttempts);
        assertEquals(1, stack.stackSize);
    }

    @Test
    void upwardPlacementAtTopDoesNotConsumePanel() {
        assertFalse(place(0, 255, 0, 1));
        assertEquals(0, world.placementAttempts);
        assertEquals(1, stack.stackSize);
    }

    @Test
    void invalidFacesDoNotConsumePanel() {
        for (int side : new int[] { -1, 6, Integer.MAX_VALUE }) {
            assertFalse(place(0, 64, 0, side), "side=" + side);
        }
        assertEquals(0, world.placementAttempts);
        assertEquals(1, stack.stackSize);
    }

    @Test
    void unloadedTargetDoesNotConsumePanel() {
        world.targetLoaded = false;

        assertFalse(place(0, 64, 0, 5));
        assertEquals(0, world.placementAttempts);
        assertEquals(1, stack.stackSize);
    }

    @Test
    void minecraftPlacementFailureDoesNotConsumePanel() {
        world.reportedHeight = 512;

        assertFalse(place(0, 255, 0, 1));
        assertEquals(1, world.placementAttempts);
        assertEquals(256, world.targetY);
        assertFalse(world.placed);
        assertEquals(1, stack.stackSize);
    }

    @Test
    void itemDataDoesNotShareMutableTags() {
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setInteger("ver", 100);
        NBTTagCompound settings = new NBTTagCompound();
        settings.setString("name", "Original");
        ItemStack panel = ItemRecipePanel.withData(snapshot, settings);
        snapshot.setInteger("ver", 200);
        settings.setString("name", "Changed");
        ItemRecipePanel.getSnapshot(panel)
            .setInteger("ver", 300);
        ItemRecipePanel.getSettings(panel)
            .setString("name", "Changed again");

        assertEquals(
            100,
            ItemRecipePanel.getSnapshot(panel)
                .getInteger("ver"));
        assertEquals(
            "Original",
            ItemRecipePanel.getSettings(panel)
                .getString("name"));
    }

    private boolean place(int x, int y, int z, int side) {
        return item.onItemUse(stack, player, world, x, y, z, side, 0.5F, 0.5F, 0.5F);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class FixtureWorld extends World {

        private int reportedHeight;
        private boolean targetLoaded;
        private int placementAttempts;
        private int targetY;
        private boolean placed;

        private FixtureWorld() {
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
        public int getHeight() {
            return reportedHeight;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return targetLoaded || x != 1;
        }

        @Override
        public boolean canMineBlock(EntityPlayer player, int x, int y, int z) {
            return true;
        }

        @Override
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side) {
            return true;
        }

        @Override
        public boolean canPlaceEntityOnSide(Block block, int x, int y, int z, boolean ignoreCollision, int side,
            Entity entity, ItemStack stack) {
            return true;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return null;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return 0;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Block block, int meta, int flags) {
            placementAttempts++;
            targetY = y;
            placed = super.setBlock(x, y, z, block, meta, flags);
            return placed;
        }
    }
}
