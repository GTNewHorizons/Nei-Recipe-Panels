package com.gtnewhorizons.neirecipepanel.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import cpw.mods.fml.relauncher.ReflectionHelper;
import io.netty.buffer.ByteBuf;

/** Uncompressed, bounded NBT transport; validates lengths before Minecraft allocates arrays or lists. */
public final class BoundedNbt {

    public static final int MAX_BYTES = 262144;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_NODES = 4096;
    private static final Field LIST_VALUES = ReflectionHelper.findField(NBTTagList.class, "tagList", "field_74747_a");

    private BoundedNbt() {}

    public static void write(ByteBuf buffer, NBTTagCompound tag, int limit) {
        try {
            byte[] bytes = encode(tag, limit);
            buffer.writeInt(bytes.length);
            buffer.writeBytes(bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Panel document exceeds transport limits", e);
        }
    }

    public static NBTTagCompound read(ByteBuf buffer, int limit) {
        if (buffer.readableBytes() < 4) return null;
        int length = buffer.readInt();
        if (length <= 0 || length > limit || length != buffer.readableBytes()) return null;
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        try {
            DataInputStream validation = new DataInputStream(new ByteArrayInputStream(bytes));
            if (validation.readUnsignedByte() != 10) return null;
            validation.readUTF();
            validatePayload(validation, 10, 0, new int[] { MAX_NODES });
            if (validation.available() != 0) return null;
            return CompressedStreamTools
                .func_152456_a(new DataInputStream(new ByteArrayInputStream(bytes)), new NBTSizeTracker(limit * 16L));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static boolean isWithinLimit(NBTTagCompound tag, int limit) {
        try {
            encode(tag, limit);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static byte[] encode(NBTTagCompound tag, int limit) throws IOException {
        if (tag == null || limit <= 0 || limit > MAX_BYTES) throw new IOException("Invalid NBT limit");
        validateTree(tag, 0, new int[] { MAX_NODES });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStream bounded = new OutputStream() {

            @Override
            public void write(int value) throws IOException {
                if (bytes.size() >= limit) throw new IOException("NBT byte limit exceeded");
                bytes.write(value);
            }

            @Override
            public void write(byte[] values, int offset, int length) throws IOException {
                if (length > limit - bytes.size()) throw new IOException("NBT byte limit exceeded");
                bytes.write(values, offset, length);
            }
        };
        CompressedStreamTools.write(tag, new DataOutputStream(bounded));
        return bytes.toByteArray();
    }

    private static void validateTree(NBTBase tag, int depth, int[] nodes) throws IOException {
        if (depth > MAX_DEPTH || --nodes[0] < 0) throw new IOException("NBT complexity limit exceeded");
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            for (String key : compound.func_150296_c()) validateTree(compound.getTag(key), depth + 1, nodes);
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            if (list.tagCount() > nodes[0]) throw new IOException("NBT list limit exceeded");
            for (NBTBase element : elements(list)) validateTree(element, depth + 1, nodes);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<NBTBase> elements(NBTTagList list) {
        // Minecraft 1.7.10 exposes no generic list-element accessor.
        try {
            return (List<NBTBase>) LIST_VALUES.get(list);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot inspect NBT list", e);
        }
    }

    private static void validatePayload(DataInputStream input, int type, int depth, int[] nodes) throws IOException {
        if (depth > MAX_DEPTH || --nodes[0] < 0) throw new IOException("NBT complexity limit exceeded");
        switch (type) {
            case 1:
                skip(input, 1);
                break;
            case 2:
                skip(input, 2);
                break;
            case 3:
            case 5:
                skip(input, 4);
                break;
            case 4:
            case 6:
                skip(input, 8);
                break;
            case 7:
                skipArray(input, 1);
                break;
            case 8:
                input.readUTF();
                break;
            case 9:
                int elementType = input.readUnsignedByte();
                int count = input.readInt();
                if (count < 0 || count > nodes[0] || (count > 0 && (elementType < 1 || elementType > 11))) {
                    throw new IOException("Invalid NBT list");
                }
                for (int i = 0; i < count; i++) validatePayload(input, elementType, depth + 1, nodes);
                break;
            case 10:
                int childType;
                while ((childType = input.readUnsignedByte()) != 0) {
                    input.readUTF();
                    validatePayload(input, childType, depth + 1, nodes);
                }
                break;
            case 11:
                skipArray(input, 4);
                break;
            default:
                throw new IOException("Unknown NBT type");
        }
    }

    private static void skipArray(DataInputStream input, int elementBytes) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available() / elementBytes) throw new IOException("Invalid NBT array");
        skip(input, length * elementBytes);
    }

    private static void skip(DataInputStream input, int bytes) throws IOException {
        if (bytes > input.available() || input.skipBytes(bytes) != bytes) throw new IOException("Truncated NBT");
    }
}
