package com.gtnewhorizons.neirecipepanel.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.IChunkProvider;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.neirecipepanel.config.PanelSettings;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import sun.misc.Unsafe;

class ConfigurePanelMessageTest {

    @Test
    void preservesCoordinatesTransparencyAndMaximumLengthUnicodeName() {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf reencoded = Unpooled.buffer();
        try {
            new ConfigurePanelMessage(-17, 255, 31, repeat('\u754c', PanelSettings.MAX_NAME), true).toBytes(encoded);
            byte[] expected = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), expected);

            ConfigurePanelMessage decoded = new ConfigurePanelMessage();
            decoded.fromBytes(encoded);
            assertEquals(0, encoded.readableBytes());
            decoded.toBytes(reencoded);

            byte[] actual = new byte[reencoded.readableBytes()];
            reencoded.readBytes(actual);
            assertArrayEquals(expected, actual);
        } finally {
            encoded.release();
            reencoded.release();
        }
    }

    @Test
    void trimsLocallyProvidedNamesBeforeEncoding() {
        ByteBuf encoded = Unpooled.buffer();
        try {
            new ConfigurePanelMessage(0, 0, 0, repeat('x', PanelSettings.MAX_NAME + 1), false).toBytes(encoded);
            encoded.skipBytes(12);
            assertEquals(repeat('x', PanelSettings.MAX_NAME), ByteBufUtils.readUTF8String(encoded));
        } finally {
            encoded.release();
        }
    }

    @Test
    void rejectsNamesWithExcessiveEncodedLength() {
        ByteBuf encoded = coordinates();
        ByteBufUtils.writeVarInt(encoded, 193, 2);
        encoded.writeZero(194);
        assertInvalid(encoded);
    }

    @Test
    void rejectsNamesWithExcessiveCharacterCount() {
        ByteBuf encoded = coordinates();
        ByteBufUtils.writeUTF8String(encoded, repeat('\u754c', PanelSettings.MAX_NAME + 1));
        encoded.writeBoolean(false);
        assertInvalid(encoded);
    }

    @Test
    void rejectsMalformedUtf8InsteadOfReplacingCharacters() {
        ByteBuf encoded = coordinates();
        ByteBufUtils.writeVarInt(encoded, 2, 2);
        encoded.writeByte(0xc3)
            .writeByte(0x28)
            .writeBoolean(false);
        assertInvalid(encoded);
    }

    @Test
    void rejectsTruncatedNames() {
        ByteBuf encoded = coordinates();
        ByteBufUtils.writeVarInt(encoded, 3, 2);
        encoded.writeByte('x');
        assertInvalid(encoded);
    }

    @Test
    void rejectsPacketsWithoutTransparencyFlag() {
        ByteBuf encoded = coordinates();
        ByteBufUtils.writeUTF8String(encoded, "");
        assertInvalid(encoded);
    }

    @Test
    void rejectsTrailingFields() {
        ByteBuf encoded = coordinates();
        ByteBufUtils.writeUTF8String(encoded, "name");
        encoded.writeBoolean(false)
            .writeByte(0);
        assertInvalid(encoded);
    }

    @Test
    void rejectsInvalidHeightWithoutQueryingChunks() throws Exception {
        ConfigurationFixture fixture = new ConfigurationFixture();
        fixture.player.posY = -1;
        fixture.apply(0, -1, 0);
        fixture.player.posY = 256;
        fixture.apply(0, 256, 0);
        assertEquals(0, fixture.world.chunkQueries);
    }

    @Test
    void rejectsCoordinatesOutsideWorldBoundsWithoutQueryingChunks() throws Exception {
        ConfigurationFixture fixture = new ConfigurationFixture();
        fixture.player.posX = 30_000_000;
        fixture.apply(30_000_000, 0, 0);
        fixture.player.posX = -30_000_001;
        fixture.apply(-30_000_001, 0, 0);
        fixture.player.posX = 0;
        fixture.player.posZ = 30_000_000;
        fixture.apply(0, 0, 30_000_000);
        fixture.player.posZ = -30_000_001;
        fixture.apply(0, 0, -30_000_001);
        assertEquals(0, fixture.world.chunkQueries);
    }

    @Test
    void rejectsDistantCoordinatesWithoutQueryingChunks() throws Exception {
        ConfigurationFixture fixture = new ConfigurationFixture();
        fixture.apply(1_000_000, 0, 0);
        assertEquals(0, fixture.world.chunkQueries);
    }

    @Test
    void rejectsNonFinitePlayerDistanceWithoutQueryingChunks() throws Exception {
        ConfigurationFixture fixture = new ConfigurationFixture();
        fixture.player.posX = Double.NaN;
        fixture.apply(0, 0, 0);
        assertEquals(0, fixture.world.chunkQueries);
    }

    @Test
    void rejectsUnloadedChunksBeforeBlockTileOrPermissionLookups() throws Exception {
        ConfigurationFixture fixture = new ConfigurationFixture();
        fixture.apply(0, 0, 0);
        assertEquals(1, fixture.world.chunkQueries);
    }

    private static ByteBuf coordinates() {
        return Unpooled.buffer()
            .writeInt(1)
            .writeInt(2)
            .writeInt(3);
    }

    private static void assertInvalid(ByteBuf encoded) {
        try {
            assertThrows(DecoderException.class, () -> new ConfigurePanelMessage().fromBytes(encoded));
        } finally {
            encoded.release();
        }
    }

    private static String repeat(char character, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, character);
        return new String(characters);
    }

    private static final class ConfigurationFixture {

        private final GuardedWorld world;
        private final EntityPlayerMP player;
        private final Method apply;

        private ConfigurationFixture() throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Unsafe unsafe = (Unsafe) field.get(null);
            world = (GuardedWorld) unsafe.allocateInstance(GuardedWorld.class);
            world.chunks = (IChunkProvider) Proxy.newProxyInstance(
                IChunkProvider.class.getClassLoader(),
                new Class<?>[] { IChunkProvider.class },
                (proxy, method, arguments) -> {
                    if (!method.getName()
                        .equals("chunkExists")) {
                        throw new AssertionError("Unexpected chunk operation: " + method);
                    }
                    world.chunkQueries++;
                    return false;
                });
            player = (EntityPlayerMP) unsafe.allocateInstance(EntityPlayerMP.class);
            player.worldObj = world;
            apply = ConfigurePanelMessage.Handler.class
                .getDeclaredMethod("apply", EntityPlayerMP.class, ConfigurePanelMessage.class);
            apply.setAccessible(true);
        }

        private void apply(int x, int y, int z) throws Exception {
            apply.invoke(null, player, new ConfigurePanelMessage(x, y, z, "name", false));
        }
    }

    private static final class GuardedWorld extends World {

        private int chunkQueries;
        private IChunkProvider chunks;

        private GuardedWorld() {
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
        public int getActualHeight() {
            return 256;
        }

        @Override
        public IChunkProvider getChunkProvider() {
            return chunks;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            throw new AssertionError("Unexpected block lookup");
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            throw new AssertionError("Unexpected metadata lookup");
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            throw new AssertionError("Unexpected tile lookup");
        }

        @Override
        public boolean canMineBlock(EntityPlayer player, int x, int y, int z) {
            throw new AssertionError("Unexpected permission lookup");
        }
    }
}
