package com.gtnewhorizons.neirecipepanel.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.jupiter.api.Test;

class RecipeSnapshotTest {

    private static final String RECIPE_ID = "{\"handlerName\":\"test.Handler\",\"ingredients\":[{\"strId\":\"minecraft:iron_ingot\"}],\"result\":{\"strId\":\"minecraft:iron_block\"}}";

    @Test
    void roundTripsCurrentDocumentWithoutSharingMutableTags() {
        NBTTagCompound identity = identity("minecraft:iron_ingot");
        RecipeSnapshot snapshot = RecipeSnapshot.create(
            "test.Handler",
            RECIPE_ID,
            Collections.singletonList(new RecipeSnapshot.Selection(4, 7, identity)),
            null,
            Collections.emptyList(),
            Collections.singletonList(identity("minecraft:iron_block")));
        assertNotNull(snapshot);
        identity.setString("strId", "changed");
        assertEquals(
            "minecraft:iron_ingot",
            snapshot.ingredients()
                .get(0)
                .identity()
                .getString("strId"));
        NBTTagCompound saved = snapshot.writeToNBT();
        assertEquals(1, saved.getInteger("ver"));
        assertEquals(snapshot, RecipeSnapshot.readFromNBT(saved));
        saved.setString("recipeId", "changed");
        snapshot.ingredients()
            .get(0)
            .identity()
            .setString("strId", "changed");
        snapshot.outputs()
            .get(0)
            .setString("strId", "changed");
        assertEquals(RECIPE_ID, snapshot.recipeIdJson());
        assertEquals(
            "minecraft:iron_ingot",
            snapshot.ingredients()
                .get(0)
                .identity()
                .getString("strId"));
        assertEquals(
            "minecraft:iron_block",
            snapshot.outputs()
                .get(0)
                .getString("strId"));
    }

    @Test
    void rejectsUnsupportedVersionsWithOtherwiseValidData() {
        for (int version : new int[] { -1, 0, 2, 3, 4, Integer.MAX_VALUE }) {
            NBTTagCompound tag = current();
            tag.setInteger("ver", version);
            assertNull(RecipeSnapshot.readFromNBT(tag));
            assertNull(RecipeSnapshot.sanitize(tag, 64, 16384));
        }
    }

    @Test
    void rejectsOldSchemaEvenWithVersionOne() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("ver", 1);
        tag.setString("recipeId", RECIPE_ID);
        tag.setTag("ingredients", new NBTTagList());
        tag.setTag("others", new NBTTagList());
        assertNull(RecipeSnapshot.readFromNBT(tag));
        assertNull(RecipeSnapshot.sanitize(tag, 64, 16384));
    }

    @Test
    void rejectsMissingAndMalformedVersions() {
        NBTTagCompound missing = current();
        missing.removeTag("ver");
        assertNull(RecipeSnapshot.readFromNBT(missing));
        missing.setString("ver", "1");
        assertNull(RecipeSnapshot.readFromNBT(missing));
        assertNull(RecipeSnapshot.readFromNBT(new NBTTagCompound()));
    }

    @Test
    void rejectsEmptyAndDeepRecipeLocatorsBeforeTheyCanConsumeBlueprints() {
        NBTTagCompound tag = current();
        tag.setString("recipeId", "{}");
        assertNull(RecipeSnapshot.sanitize(tag, 64, 16384));
        tag.setString("recipeId", "{\"handlerName\":\"test.Handler\",\"ingredients\":[],\"result\":{}}");
        assertNull(RecipeSnapshot.sanitize(tag, 64, 16384));
        tag.setString("recipeId", repeat("[", 1000) + "0" + repeat("]", 1000));
        assertNull(RecipeSnapshot.sanitize(tag, 64, 16384));
    }

    @Test
    void rejectsExcessiveSlotsAndWrongListTypes() {
        NBTTagCompound tag = current();
        assertNull(RecipeSnapshot.sanitize(tag, 0, 16384));
        NBTTagList invalid = new NBTTagList();
        invalid.appendTag(new NBTTagString("item"));
        tag.setTag("inputs", invalid);
        assertNull(RecipeSnapshot.readFromNBT(tag));
    }

    @Test
    void canonicalizesOnlyNewDocumentsAndEnforcesUncompressedSize() {
        NBTTagCompound tag = current();
        tag.setString("unused", "extra");
        NBTTagCompound clean = RecipeSnapshot.sanitize(tag, 64, 16384);
        assertNotNull(clean);
        assertFalse(clean.hasKey("unused"));
        tag.setIntArray("large", new int[400000]);
        assertNull(RecipeSnapshot.sanitize(tag, 64, 16384));
    }

    private static NBTTagCompound current() {
        return RecipeSnapshot
            .create(
                "test.Handler",
                RECIPE_ID,
                Collections.singletonList(new RecipeSnapshot.Selection(0, 0, identity("minecraft:iron_ingot"))),
                null,
                Collections.emptyList(),
                Collections.singletonList(identity("minecraft:iron_block")))
            .writeToNBT();
    }

    private static NBTTagCompound identity(String name) {
        NBTTagCompound identity = new NBTTagCompound();
        identity.setString("strId", name);
        return identity;
    }

    private static String repeat(String text, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(text);
        return result.toString();
    }
}
