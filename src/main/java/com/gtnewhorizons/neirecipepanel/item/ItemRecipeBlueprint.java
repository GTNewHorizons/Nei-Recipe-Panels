package com.gtnewhorizons.neirecipepanel.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Blank consumable. The NEI recipe-screen button spends one to imprint a {@link ItemRecipePanel}. */
public class ItemRecipeBlueprint extends Item {

    public ItemRecipeBlueprint() {
        setUnlocalizedName("recipeBlueprint");
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabMisc);
        setTextureName(NEIRecipePanelsMod.MODID + ":recipe_blueprint");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.nei-recipe-panels.blueprint.1"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.nei-recipe-panels.blueprint.2"));
    }
}
