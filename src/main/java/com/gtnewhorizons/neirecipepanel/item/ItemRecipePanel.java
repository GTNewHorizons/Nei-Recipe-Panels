package com.gtnewhorizons.neirecipepanel.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;

import com.gtnewhorizons.neirecipepanel.ModBlocks;
import com.gtnewhorizons.neirecipepanel.ModItems;
import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemRecipePanel extends Item {

    public static final String TAG_SNAPSHOT = "snapshot";
    public static final String TAG_SETTINGS = "cfg";

    public ItemRecipePanel() {
        setUnlocalizedName("recipePanel");
        setMaxStackSize(16);
        setCreativeTab(CreativeTabs.tabMisc);
        setTextureName(NEIRecipePanelsMod.MODID + ":recipe_blueprint");
    }

    public static ItemStack withSnapshot(NBTTagCompound snapshot) {
        return withData(snapshot, null);
    }

    public static ItemStack withData(NBTTagCompound snapshot, NBTTagCompound settings) {
        ItemStack stack = new ItemStack(ModItems.recipePanel);
        NBTTagCompound tag = new NBTTagCompound();
        if (snapshot != null) {
            tag.setTag(TAG_SNAPSHOT, snapshot.copy());
        }
        if (settings != null && !settings.hasNoTags()) {
            tag.setTag(TAG_SETTINGS, settings.copy());
        }
        stack.setTagCompound(tag);
        return stack;
    }

    public static NBTTagCompound getSnapshot(ItemStack stack) {
        return getCompound(stack, TAG_SNAPSHOT);
    }

    public static NBTTagCompound getSettings(ItemStack stack) {
        return getCompound(stack, TAG_SETTINGS);
    }

    public static ItemStack getResult(ItemStack stack) {
        NBTTagCompound snapshot = getSnapshot(stack);
        return snapshot == null ? null : NEIRecipePanelsMod.proxy.getPanelResult(snapshot);
    }

    private static NBTTagCompound getCompound(ItemStack stack, String key) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag != null && tag.hasKey(key, 10) ? (NBTTagCompound) tag.getCompoundTag(key)
            .copy() : null;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        NBTTagCompound snapshot = getSnapshot(stack);
        if (snapshot == null || stack.stackSize <= 0
            || side < 0
            || side >= ForgeDirection.VALID_DIRECTIONS.length
            || !isLoadedPosition(world, x, y, z)) {
            return false;
        }

        ForgeDirection dir = ForgeDirection.getOrientation(side);
        int px = x + dir.offsetX;
        int py = y + dir.offsetY;
        int pz = z + dir.offsetZ;

        if (!isLoadedPosition(world, px, py, pz) || !player.canPlayerEdit(px, py, pz, side, stack)
            || !world.canMineBlock(player, px, py, pz)
            || !world.isSideSolid(x, y, z, dir)) {
            return false;
        }
        if (!world.canPlaceEntityOnSide(ModBlocks.recipePanel, px, py, pz, false, side, player, stack)) {
            return false;
        }

        if (world.isRemote) {
            return true;
        }

        boolean forgeCapturesPlacement = world.captureBlockSnapshots;
        BlockSnapshot replaced = BlockSnapshot.getBlockSnapshot(world, px, py, pz);
        if (!world.setBlock(px, py, pz, ModBlocks.recipePanel, side, 3)) {
            return false;
        }
        TileEntity tile = world.getTileEntity(px, py, pz);
        if (!(tile instanceof RecipePanelTile) || world.getBlock(px, py, pz) != ModBlocks.recipePanel) {
            restorePlacement(replaced);
            return false;
        }
        ((RecipePanelTile) tile).setData(snapshot, getSettings(stack));
        if (!forgeCapturesPlacement && ForgeEventFactory.onPlayerBlockPlace(player, replaced, dir)
            .isCanceled()) {
            restorePlacement(replaced);
            return false;
        }
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
        }
        return true;
    }

    private static boolean isLoadedPosition(World world, int x, int y, int z) {
        return x >= -30000000 && x < 30000000
            && z >= -30000000
            && z < 30000000
            && y >= 0
            && y < world.getHeight()
            && world.blockExists(x, y, z);
    }

    private static void restorePlacement(BlockSnapshot snapshot) {
        World world = snapshot.world;
        boolean wasRestoring = world.restoringBlockSnapshots;
        boolean wasCapturing = world.captureBlockSnapshots;
        world.restoringBlockSnapshots = true;
        world.captureBlockSnapshots = false;
        try {
            snapshot.restore(true, false);
        } finally {
            world.captureBlockSnapshots = wasCapturing;
            world.restoringBlockSnapshots = wasRestoring;
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (getSnapshot(stack) != null) {
            return StatCollector.translateToLocal("item.recipePanel.encoded.name");
        }
        return super.getItemStackDisplayName(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        if (getSnapshot(stack) == null) {
            tooltip.add(
                EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.nei-recipe-panels.panel.blank"));
            return;
        }
        ItemStack result = getResult(stack);
        if (result != null) {
            tooltip.add(
                EnumChatFormatting.GRAY + StatCollector
                    .translateToLocalFormatted("tooltip.nei-recipe-panels.panel.recipe", result.getDisplayName()));
        }
        tooltip
            .add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("tooltip.nei-recipe-panels.panel.hang"));
        NEIRecipePanelsMod.proxy.addPanelInformation(getSnapshot(stack), tooltip);
    }
}
