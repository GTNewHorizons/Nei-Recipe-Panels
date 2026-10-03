package com.gtnewhorizons.neirecipepanel.client.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.stackinfo.FluidDisplayStackStringifyHandler;

final class StackIdentity {

    private StackIdentity() {}

    static ItemStack queryStack(NBTTagCompound identity) {
        NBTTagCompound query = (NBTTagCompound) identity.copy();
        if (query.hasKey("fluid", 8)) {
            query.setString(FluidDisplayStackStringifyHandler.NBT_FLUID_NAME, query.getString("fluid"));
            query.removeTag("fluid");
        }
        return StackInfo.loadFromNBT(query, 1);
    }

    static NBTTagCompound of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        if (StackInfo.isFluidDisplayItem(stack)) {
            FluidStack fluid = StackInfo.getFluid(stack);
            if (fluid == null) return null;
            NBTTagCompound identity = new NBTTagCompound();
            identity.setString(
                "fluid",
                fluid.getFluid()
                    .getName());
            if (fluid.tag != null) identity.setTag("tag", fluid.tag.copy());
            return identity;
        }
        NBTTagCompound identity = StackInfo.itemStackToNBT(stack, false);
        if (identity != null) {
            identity = (NBTTagCompound) identity.copy();
            identity.removeTag("Count");
        }
        return identity;
    }
}
