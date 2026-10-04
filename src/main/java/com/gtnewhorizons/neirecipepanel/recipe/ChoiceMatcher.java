package com.gtnewhorizons.neirecipepanel.recipe;

import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

/** Assigns saved choices to distinct accepting slots, preferring their previous positions. */
public final class ChoiceMatcher {

    private ChoiceMatcher() {}

    public static int[] match(List<RecipeSnapshot.Selection> choices, List<Slot> slots) {
        int[] owners = new int[slots.size()];
        Arrays.fill(owners, -1);
        for (int choice = 0; choice < choices.size(); choice++) {
            assign(choice, choices, slots, owners, new boolean[slots.size()]);
        }
        return owners;
    }

    private static boolean assign(int choiceIndex, List<RecipeSnapshot.Selection> choices, List<Slot> slots,
        int[] owners, boolean[] visited) {
        RecipeSnapshot.Selection choice = choices.get(choiceIndex);
        NBTTagCompound identity = choice.identity();
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < slots.size(); slot++) {
                Slot target = slots.get(slot);
                boolean samePosition = target.x == choice.x() && target.y == choice.y();
                if (visited[slot] || samePosition != (pass == 0) || !target.identities.contains(identity)) continue;
                visited[slot] = true;
                if (owners[slot] == -1 || assign(owners[slot], choices, slots, owners, visited)) {
                    owners[slot] = choiceIndex;
                    return true;
                }
            }
        }
        return false;
    }

    public static final class Slot {

        public final int x;
        public final int y;
        private final List<NBTTagCompound> identities;

        public Slot(int x, int y, List<NBTTagCompound> identities) {
            this.x = x;
            this.y = y;
            this.identities = identities;
        }
    }
}
