package com.gtnewhorizons.neirecipepanel.network;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.gtnewhorizons.neirecipepanel.ModBlocks;
import com.gtnewhorizons.neirecipepanel.block.RecipePanelTile;
import com.gtnewhorizons.neirecipepanel.config.PanelSettings;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

public class ConfigurePanelMessage implements IMessage {

    private static final int MAX_NAME_BYTES = PanelSettings.MAX_NAME * 4;
    private static final int WORLD_COORDINATE_LIMIT = 30_000_000;
    private static final double MAX_DISTANCE_SQUARED = 64D;

    private int x;
    private int y;
    private int z;
    private String name;
    private boolean transparent;

    public ConfigurePanelMessage() {}

    public ConfigurePanelMessage(int x, int y, int z, String name, boolean transparent) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = PanelSettings.trim(name);
        this.transparent = transparent;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        name = readName(buf);
        transparent = buf.readBoolean();
        if (buf.isReadable()) {
            throw new DecoderException("Unexpected panel configuration data");
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, PanelSettings.trim(name));
        buf.writeBoolean(transparent);
    }

    private static String readName(ByteBuf buf) {
        int length = ByteBufUtils.readVarInt(buf, 2);
        if (length > MAX_NAME_BYTES || length > buf.readableBytes() - 1) {
            throw new DecoderException("Invalid panel name length");
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        String name;
        try {
            name = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new DecoderException("Invalid panel name encoding", exception);
        }
        if (name.length() > PanelSettings.MAX_NAME) {
            throw new DecoderException("Panel name is too long");
        }
        return name;
    }

    public static class Handler implements IMessageHandler<ConfigurePanelMessage, IMessage> {

        @Override
        public IMessage onMessage(ConfigurePanelMessage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerTasks.submit(player, () -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, ConfigurePanelMessage message) {
            if (player == null || player.isDead) {
                return;
            }
            World world = player.worldObj;
            if (world == null || world.isRemote
                || message.y < 0
                || message.y >= world.getActualHeight()
                || message.x < -WORLD_COORDINATE_LIMIT
                || message.x >= WORLD_COORDINATE_LIMIT
                || message.z < -WORLD_COORDINATE_LIMIT
                || message.z >= WORLD_COORDINATE_LIMIT
                || !(player.getDistanceSq(message.x + 0.5D, message.y + 0.5D, message.z + 0.5D) <= MAX_DISTANCE_SQUARED)
                || !world.getChunkProvider()
                    .chunkExists(message.x >> 4, message.z >> 4)) {
                return;
            }
            if (world.getBlock(message.x, message.y, message.z) != ModBlocks.recipePanel
                || !world.canMineBlock(player, message.x, message.y, message.z)) {
                return;
            }
            int face = world.getBlockMetadata(message.x, message.y, message.z);
            if (face < 0 || face > 5
                || !player.canPlayerEdit(message.x, message.y, message.z, face, player.getHeldItem())) {
                return;
            }
            PlayerInteractEvent interaction = ForgeEventFactory.onPlayerInteract(
                player,
                PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,
                message.x,
                message.y,
                message.z,
                face,
                world);
            if (interaction.isCanceled() || interaction.useBlock == Event.Result.DENY
                || player.worldObj != world
                || player.isDead) {
                return;
            }
            TileEntity te = world.getTileEntity(message.x, message.y, message.z);
            if (!(te instanceof RecipePanelTile)) {
                return;
            }
            PanelSettings settings = new PanelSettings();
            settings.customName = PanelSettings.trim(message.name);
            settings.transparent = message.transparent;
            ((RecipePanelTile) te).setSettings(settings.isEmpty() ? null : settings.toNBT());
        }
    }
}
