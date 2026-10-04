package com.gtnewhorizons.neirecipepanel.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.neirecipepanel.CommonProxy;
import com.gtnewhorizons.neirecipepanel.ModItems;
import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;
import com.gtnewhorizons.neirecipepanel.client.gui.GuiRecipeButtonHandler;
import com.gtnewhorizons.neirecipepanel.client.gui.GuiRecipePanelConfig;
import com.gtnewhorizons.neirecipepanel.client.gui.PanelInputHandler;
import com.gtnewhorizons.neirecipepanel.client.gui.PanelRecipeOpener;
import com.gtnewhorizons.neirecipepanel.client.recipe.RecipeResolver;
import com.gtnewhorizons.neirecipepanel.client.render.PanelDrawBatch;
import com.gtnewhorizons.neirecipepanel.client.render.PanelFboManager;
import com.gtnewhorizons.neirecipepanel.client.render.RecipePanelItemRenderer;
import com.gtnewhorizons.neirecipepanel.client.render.RecipePanelRenderer;

import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new GuiRecipeButtonHandler());
        FMLCommonHandler.instance()
            .bus()
            .register(PanelFboManager.INSTANCE);
        FMLInterModComms.sendMessage(
            "Waila",
            "register",
            "com.gtnewhorizons.neirecipepanel.client.RecipePanelWaila.callbackRegister");
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientRegistry.bindTileEntitySpecialRenderer(RecipePanelTile.class, new RecipePanelRenderer());
        MinecraftForge.EVENT_BUS.register(PanelDrawBatch.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(PanelDrawBatch.INSTANCE);
        MinecraftForgeClient.registerItemRenderer(ModItems.recipePanel, new RecipePanelItemRenderer());
        GuiContainerManager.addInputHandler(new PanelInputHandler());
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager manager) {
                    PanelFboManager.INSTANCE.reload();
                }
            });
    }

    @Override
    public void openPanelRecipe(NBTTagCompound snapshot, boolean usage) {
        PanelRecipeOpener.open(snapshot, usage);
    }

    @Override
    public ItemStack getPanelResult(NBTTagCompound snapshot) {
        return RecipeResolver.INSTANCE.resolve(snapshot)
            .displayResult();
    }

    @Override
    public void addPanelInformation(NBTTagCompound snapshot, List<String> tooltip) {
        RecipeResolver.Status status = RecipeResolver.INSTANCE.resolve(snapshot)
            .status();
        if (status != RecipeResolver.Status.READY)
            tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal(status.translationKey()));
    }

    @Override
    public void openPanelConfig(int x, int y, int z) {
        TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(x, y, z);
        if (te instanceof RecipePanelTile) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new GuiRecipePanelConfig((RecipePanelTile) te));
        }
    }
}
