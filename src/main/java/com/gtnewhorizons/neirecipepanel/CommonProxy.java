package com.gtnewhorizons.neirecipepanel;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.neirecipepanel.config.Config;
import com.gtnewhorizons.neirecipepanel.network.ConfigurePanelMessage;
import com.gtnewhorizons.neirecipepanel.network.MakeRecipePanelMessage;
import com.gtnewhorizons.neirecipepanel.network.ServerTasks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        ModItems.register();
        ModBlocks.register();
        NEIRecipePanelsMod.NETWORK
            .registerMessage(MakeRecipePanelMessage.Handler.class, MakeRecipePanelMessage.class, 0, Side.SERVER);
        NEIRecipePanelsMod.NETWORK
            .registerMessage(ConfigurePanelMessage.Handler.class, ConfigurePanelMessage.class, 1, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(ServerTasks.INSTANCE);
    }

    public void init(FMLInitializationEvent event) {
        ModItems.registerRecipes();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    /** Client-side; no-op on a dedicated server. */
    public void openPanelRecipe(NBTTagCompound snapshot, boolean usage) {}

    /** Client-side; no-op on a dedicated server. */
    public void openPanelConfig(int x, int y, int z) {}

    public ItemStack getPanelResult(NBTTagCompound snapshot) {
        return null;
    }

    public void addPanelInformation(NBTTagCompound snapshot, List<String> tooltip) {}

    public void serverStarting(FMLServerStartingEvent event) {
        ServerTasks.clear();
    }
}
