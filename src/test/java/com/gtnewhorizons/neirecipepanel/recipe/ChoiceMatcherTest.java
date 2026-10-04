package com.gtnewhorizons.neirecipepanel.recipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

class ChoiceMatcherTest {

    @Test
    void preservesIdentitiesWhenSlotsAndAlternativesAreReordered() {
        assertArrayEquals(
            new int[] { 1, 0 },
            ChoiceMatcher.match(
                Arrays.asList(choice(0, "copper"), choice(18, "tin")),
                Arrays.asList(slot(0, "tin"), slot(18, "tin", "copper"))));
    }

    @Test
    void reassignsAnEarlierChoiceToPreserveBothChoices() {
        assertArrayEquals(
            new int[] { 1, 0 },
            ChoiceMatcher.match(
                Arrays.asList(choice(0, "copper"), choice(18, "tin")),
                Arrays.asList(slot(0, "copper", "tin"), slot(18, "copper"))));
    }

    @Test
    void doesNotMatchTwoSavedInputsToOneCurrentSlot() {
        assertArrayEquals(
            new int[] { 0, -1 },
            ChoiceMatcher.match(
                Arrays.asList(choice(0, "copper"), choice(18, "copper")),
                Arrays.asList(slot(0, "copper"), slot(18, "tin"))));
    }

    @Test
    void prefersPreviousPositionsForRepeatedInputs() {
        assertArrayEquals(
            new int[] { 0, 1 },
            ChoiceMatcher.match(
                Arrays.asList(choice(0, "copper"), choice(18, "copper")),
                Arrays.asList(slot(0, "copper"), slot(18, "copper"))));
    }

    @Test
    void leavesRemovedChoicesAndNewSlotsUnassigned() {
        assertArrayEquals(
            new int[] { 1, -1 },
            ChoiceMatcher.match(
                Arrays.asList(choice(0, "removed"), choice(18, "tin")),
                Arrays.asList(slot(0, "tin"), slot(18, "new"))));
    }

    @Test
    void requiresMatchingMetadataInsteadOfOnlyTheItemName() {
        NBTTagCompound tagged = identity("copper");
        tagged.setInteger("Damage", 1);
        assertArrayEquals(
            new int[] { -1 },
            ChoiceMatcher.match(
                Collections.singletonList(new RecipeSnapshot.Selection(0, 0, tagged)),
                Collections.singletonList(slot(0, "copper"))));
    }

    private static RecipeSnapshot.Selection choice(int x, String item) {
        return new RecipeSnapshot.Selection(x, 0, identity(item));
    }

    private static ChoiceMatcher.Slot slot(int x, String... items) {
        NBTTagCompound[] identities = Arrays.stream(items)
            .map(ChoiceMatcherTest::identity)
            .toArray(NBTTagCompound[]::new);
        return new ChoiceMatcher.Slot(x, 0, Arrays.asList(identities));
    }

    private static NBTTagCompound identity(String item) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("strId", item);
        return tag;
    }
}
