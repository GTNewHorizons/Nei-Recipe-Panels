package com.gtnewhorizons.neirecipepanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.neirecipepanel.network.ServerTasks;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@Mod(
    modid = NEIRecipePanelsMod.MODID,
    version = Tags.VERSION,
    name = NEIRecipePanelsMod.NAME,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:NotEnoughItems@[2.8.152,)")
public class NEIRecipePanelsMod {

    public static final String MODID = "nei-recipe-panels";
    public static final String NAME = "NEI Recipe Panels";
    public static final Logger LOG = LogManager.getLogger(MODID);

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @SidedProxy(
        clientSide = "com.gtnewhorizons.neirecipepanel.client.ClientProxy",
        serverSide = "com.gtnewhorizons.neirecipepanel.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerTasks.clear();
    }
}
