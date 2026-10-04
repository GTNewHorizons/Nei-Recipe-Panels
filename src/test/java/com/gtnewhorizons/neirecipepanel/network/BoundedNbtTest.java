package com.gtnewhorizons.neirecipepanel.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class BoundedNbtTest {

    @Test
    void roundTripsNestedDataWithAByteBound() {
        NBTTagCompound document = new NBTTagCompound();
        document.setString("unicode", "\u754c\u754c");
        document.setIntArray("values", new int[] { 1, 2, 3 });
        NBTTagList nested = new NBTTagList();
        nested.appendTag(document.copy());
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("nested", nested);
        ByteBuf buffer = Unpooled.buffer();
        try {
            BoundedNbt.write(buffer, root, 4096);
            assertEquals(root, BoundedNbt.read(buffer, 4096));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsIntegerArrayLengthsThatOverflowVanillaAllocationAccounting() {
        ByteBuf body = root().writeByte(11)
            .writeShort(1)
            .writeByte('a')
            .writeInt(536_870_912)
            .writeByte(0);
        assertRejected(body);
    }

    @Test
    void rejectsNegativeArraysAndEndTagListsWithDeclaredElements() {
        assertRejected(
            root().writeByte(7)
                .writeShort(1)
                .writeByte('a')
                .writeInt(-1)
                .writeByte(0));
        assertRejected(
            root().writeByte(9)
                .writeShort(1)
                .writeByte('a')
                .writeByte(0)
                .writeInt(1000000)
                .writeByte(0));
    }

    @Test
    void rejectsDeepTreesBeforeSerializationOrAllocation() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound cursor = root;
        for (int i = 0; i < 1000; i++) {
            NBTTagCompound child = new NBTTagCompound();
            cursor.setTag("nested", child);
            cursor = child;
        }
        assertFalse(BoundedNbt.isWithinLimit(root, 16384));
        ByteBuf body = root();
        for (int i = 0; i < 20; i++) body.writeByte(10)
            .writeShort(1)
            .writeByte('a');
        body.writeZero(21);
        assertRejected(body);
    }

    @Test
    void rejectsTruncatedOversizedAndTrailingPackets() {
        ByteBuf truncated = Unpooled.buffer()
            .writeInt(100)
            .writeByte(10);
        ByteBuf oversized = Unpooled.buffer()
            .writeInt(BoundedNbt.MAX_BYTES + 1);
        ByteBuf trailing = root().writeByte(0)
            .writeByte(1);
        try {
            assertNull(BoundedNbt.read(truncated, 4096));
            assertNull(BoundedNbt.read(oversized, 4096));
            assertRejected(trailing);
        } finally {
            truncated.release();
            oversized.release();
        }
    }

    @Test
    void rejectsLargeUncompressedArraysEvenWhenTheyWouldCompressWell() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray("zeros", new int[400000]);
        assertFalse(BoundedNbt.isWithinLimit(tag, 16384));
    }

    @Test
    void malformedPacketsNeverEscapeAsDecoderExceptions() {
        Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            byte[] bytes = new byte[random.nextInt(64)];
            random.nextBytes(bytes);
            ByteBuf buffer = Unpooled.buffer()
                .writeInt(bytes.length)
                .writeBytes(bytes);
            try {
                assertNull(BoundedNbt.read(buffer, 4096));
            } finally {
                buffer.release();
            }
        }
    }

    private static ByteBuf root() {
        return Unpooled.buffer()
            .writeByte(10)
            .writeShort(0);
    }

    private static void assertRejected(ByteBuf body) {
        ByteBuf packet = Unpooled.buffer()
            .writeInt(body.readableBytes())
            .writeBytes(body);
        try {
            assertNull(BoundedNbt.read(packet, 4096));
        } finally {
            packet.release();
            body.release();
        }
    }
}
