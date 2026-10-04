package com.gtnewhorizons.neirecipepanel.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.common.io.ByteStreams;
import com.gtnewhorizons.neirecipepanel.config.Config;
import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class MakeRecipePanelMessageTest {

    private final int originalSlots = Config.maxIngredients;
    private final int originalBytes = Config.maxSnapshotBytes;

    @AfterEach
    void restoreLimits() {
        Config.maxIngredients = originalSlots;
        Config.maxSnapshotBytes = originalBytes;
    }

    @Test
    void defaultLimitsAcceptARealNineByNineExtremeCraftingRecipe() throws Exception {
        NBTTagCompound snapshot = extremeRecipe();

        NBTTagCompound accepted = decode(snapshot);

        assertNotNull(accepted);
        assertEquals(1, accepted.getInteger("ver"));
        assertEquals(
            81,
            RecipeSnapshot.readFromNBT(accepted)
                .ingredients()
                .size());
        assertEquals(snapshot, accepted);
    }

    @Test
    void explicitLowerSlotLimitsStillRejectAnExtremeRecipe() throws Exception {
        Config.maxIngredients = 64;

        assertNull(decode(extremeRecipe()));
    }

    @Test
    void configuredByteLimitsRemainEnforced() throws Exception {
        Config.maxSnapshotBytes = 512;

        assertNull(decode(extremeRecipe()));
    }

    private static NBTTagCompound extremeRecipe() throws IOException {
        ByteBuf encoded = Unpooled.buffer();
        try (InputStream fixture = MakeRecipePanelMessageTest.class
            .getResourceAsStream("/recipes/extreme-crafting-81-slots.nbt")) {
            assertNotNull(fixture);
            byte[] payload = ByteStreams.toByteArray(fixture);
            encoded.writeInt(payload.length)
                .writeBytes(payload);
            NBTTagCompound snapshot = BoundedNbt.read(encoded, BoundedNbt.MAX_BYTES);
            assertNotNull(snapshot);
            return snapshot;
        } finally {
            encoded.release();
        }
    }

    private static NBTTagCompound decode(NBTTagCompound snapshot) throws ReflectiveOperationException {
        ByteBuf encoded = Unpooled.buffer();
        try {
            new MakeRecipePanelMessage(snapshot).toBytes(encoded);
            MakeRecipePanelMessage decoded = new MakeRecipePanelMessage();
            decoded.fromBytes(encoded);
            Field field = MakeRecipePanelMessage.class.getDeclaredField("snapshot");
            field.setAccessible(true);
            return (NBTTagCompound) field.get(decoded);
        } finally {
            encoded.release();
        }
    }
}
