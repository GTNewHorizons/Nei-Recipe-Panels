package com.gtnewhorizons.neirecipepanel.block;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.gtnewhorizons.neirecipepanel.config.PanelSettings;

public class RecipePanelTile extends TileEntity {

    private static final String TAG_SNAPSHOT = "snapshot";
    private static final String TAG_SETTINGS = "cfg";

    private NBTTagCompound snapshot;
    private NBTTagCompound settings;
    private long contentVersion;

    public boolean hasSnapshot() {
        return snapshot != null;
    }

    public long contentVersion() {
        return contentVersion;
    }

    public NBTTagCompound getSnapshot() {
        return copy(snapshot);
    }

    public void setSnapshot(NBTTagCompound snapshot) {
        this.snapshot = copy(snapshot);
        markChanged();
    }

    public NBTTagCompound getSettings() {
        return copy(settings);
    }

    public PanelSettings settings() {
        return PanelSettings.fromNBT(settings);
    }

    public void setSettings(NBTTagCompound settings) {
        this.settings = copySettings(settings);
        markChanged();
    }

    public void setData(NBTTagCompound snapshot, NBTTagCompound settings) {
        this.snapshot = copy(snapshot);
        this.settings = copySettings(settings);
        markChanged();
    }

    private void markChanged() {
        contentVersion++;
        if (worldObj == null || !worldObj.isRemote) {
            markDirty();
        }
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    private static NBTTagCompound copy(NBTTagCompound tag) {
        return tag == null ? null : (NBTTagCompound) tag.copy();
    }

    private static NBTTagCompound copySettings(NBTTagCompound settings) {
        return settings == null || settings.hasNoTags() ? null : copy(settings);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        snapshot = tag.hasKey(TAG_SNAPSHOT, 10) ? copy(tag.getCompoundTag(TAG_SNAPSHOT)) : null;
        settings = tag.hasKey(TAG_SETTINGS, 10) ? copySettings(tag.getCompoundTag(TAG_SETTINGS)) : null;
        contentVersion++;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.removeTag(TAG_SNAPSHOT);
        tag.removeTag(TAG_SETTINGS);
        if (snapshot != null) {
            tag.setTag(TAG_SNAPSHOT, snapshot.copy());
        }
        if (settings != null) {
            tag.setTag(TAG_SETTINGS, settings.copy());
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, getBlockMetadata(), tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1);
    }
}
