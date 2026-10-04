package com.gtnewhorizons.neirecipepanel.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

import com.gtnewhorizons.neirecipepanel.ModItems;
import com.gtnewhorizons.neirecipepanel.config.Config;
import com.gtnewhorizons.neirecipepanel.item.ItemRecipePanel;
import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class MakeRecipePanelMessage implements IMessage {

    private NBTTagCompound snapshot;

    public MakeRecipePanelMessage() {}

    public MakeRecipePanelMessage(NBTTagCompound snapshot) {
        this.snapshot = snapshot == null ? null : (NBTTagCompound) snapshot.copy();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound raw = BoundedNbt.read(buf, Math.min(Config.maxSnapshotBytes, BoundedNbt.MAX_BYTES));
        snapshot = RecipeSnapshot.sanitize(raw, Config.maxIngredients, Config.maxSnapshotBytes);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        BoundedNbt.write(buf, snapshot, BoundedNbt.MAX_BYTES);
    }

    public static class Handler implements IMessageHandler<MakeRecipePanelMessage, IMessage> {

        @Override
        public IMessage onMessage(MakeRecipePanelMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            NBTTagCompound raw = message.snapshot;
            ServerTasks.submit(player, () -> {
                if (player.isDead) return;
                if (raw == null) {
                    player.addChatMessage(new ChatComponentTranslation("nei-recipe-panels.chat.badSnapshot"));
                } else {
                    grant(player, raw);
                }
            });
            return null;
        }

        private static void grant(EntityPlayerMP player, NBTTagCompound raw) {
            if (player == null || player.isDead || raw == null) {
                return;
            }

            Config.PanelMode mode = Config.panelMode;
            if (mode == Config.PanelMode.DISABLED) {
                return;
            }
            boolean creative = player.capabilities.isCreativeMode;
            if (mode == Config.PanelMode.CREATIVE_ONLY && !creative) {
                deny(player);
                return;
            }
            if (mode == Config.PanelMode.OP_ONLY && !isOp(player)) {
                deny(player);
                return;
            }

            boolean spend = !creative || Config.consumeInCreative;
            if (spend && !consumeBlueprint(player)) {
                player.addChatMessage(new ChatComponentTranslation("nei-recipe-panels.chat.needBlueprint"));
                return;
            }

            ItemStack panel = ItemRecipePanel.withSnapshot(raw);
            if (!player.inventory.addItemStackToInventory(panel)) {
                player.entityDropItem(panel, 0.5F);
            }
            // Full window resync: the recipe GUI is the open container client-side, so per-slot
            // updates for the player inventory outside the hotbar would be dropped by the client.
            player.sendContainerToPlayer(player.inventoryContainer);
        }

        private static boolean consumeBlueprint(EntityPlayerMP player) {
            if (player.inventory.consumeInventoryItem(ModItems.recipeBlueprint)) {
                return true;
            }
            ItemStack held = player.inventory.getItemStack();
            if (held == null || held.stackSize <= 0 || held.getItem() != ModItems.recipeBlueprint) {
                return false;
            }
            if (--held.stackSize <= 0) {
                player.inventory.setItemStack(null);
            }
            return true;
        }

        private static void deny(EntityPlayerMP player) {
            player.addChatMessage(new ChatComponentTranslation("nei-recipe-panels.chat.notAllowed"));
        }

        private static boolean isOp(EntityPlayerMP player) {
            MinecraftServer server = MinecraftServer.getServer();
            return server != null && server.getConfigurationManager()
                .func_152596_g(player.getGameProfile());
        }
    }
}
