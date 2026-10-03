package com.gtnewhorizons.neirecipepanel.client.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.neirecipepanel.recipe.ChoiceMatcher;
import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;

public final class ResolvedRecipe {

    public final IRecipeHandler handler;
    public final int index;
    private final RecipeSnapshot snapshot;
    private List<PositionedStack> inputs = Collections.emptyList();
    private List<PositionedStack> others = Collections.emptyList();
    private PositionedStack result;
    private boolean choicesAdjusted;

    ResolvedRecipe(IRecipeHandler handler, int index, RecipeSnapshot snapshot) {
        this.handler = handler;
        this.index = index;
        this.snapshot = snapshot;
        refresh();
    }

    public IRecipeHandler pinnedHandler() {
        return new PinnedRecipeHandler(this);
    }

    public void refresh() {
        choicesAdjusted = snapshot.version() == 3;
        inputs = reconcile(handler.getIngredientStacks(index), snapshot.ingredients());
        List<PositionedStack> otherStacks = handler.getOtherStacks(index);
        if (handler instanceof FurnaceRecipeHandler && FurnaceRecipeHandler.afuels != null
            && !FurnaceRecipeHandler.afuels.isEmpty()
            && otherStacks != null
            && otherStacks.size() == 1) {
            PositionedStack fuel = otherStacks.get(0)
                .copy();
            fuel.items = FurnaceRecipeHandler.afuels.stream()
                .map(pair -> pair.stack.item)
                .toArray(ItemStack[]::new);
            otherStacks = Collections.singletonList(fuel);
        }
        others = reconcile(otherStacks, snapshot.others());
        PositionedStack liveResult = handler.getResultStack(index);
        List<PositionedStack> results = reconcile(
            liveResult == null ? Collections.emptyList() : Collections.singletonList(liveResult),
            snapshot.result() == null ? Collections.emptyList() : Collections.singletonList(snapshot.result()));
        result = results.isEmpty() ? null : results.get(0);
    }

    public boolean choicesAdjusted() {
        return choicesAdjusted;
    }

    public String name() {
        return handler.getRecipeName();
    }

    public List<PositionedStack> inputs() {
        return inputs;
    }

    public List<PositionedStack> others() {
        return others;
    }

    public PositionedStack result() {
        return result;
    }

    public List<PositionedStack> outputs() {
        return result == null ? others : Collections.singletonList(result);
    }

    public List<PositionedStack> cycling() {
        if (result == null) return inputs;
        List<PositionedStack> cycling = new ArrayList<>(inputs);
        cycling.addAll(others);
        return cycling;
    }

    public List<PositionedStack> allStacks() {
        List<PositionedStack> stacks = new ArrayList<>(inputs);
        stacks.addAll(others);
        if (result != null) stacks.add(result);
        return stacks;
    }

    public ItemStack displayResult() {
        for (PositionedStack output : outputs()) if (output.item != null) return output.item.copy();
        return null;
    }

    private List<PositionedStack> reconcile(List<PositionedStack> live, List<RecipeSnapshot.Selection> saved) {
        if (live == null) live = Collections.emptyList();
        List<RecipeSnapshot.Selection> choices = new ArrayList<>();
        for (RecipeSnapshot.Selection choice : saved) {
            if (!choice.legacyItemStack) choices.add(choice);
            else {
                NBTTagCompound identity = StackIdentity.of(ItemStack.loadItemStackFromNBT(choice.identity()));
                if (identity == null) choicesAdjusted = true;
                else choices.add(new RecipeSnapshot.Selection(choice.x, choice.y, identity));
            }
        }
        List<ChoiceMatcher.Slot> slots = new ArrayList<>();
        for (PositionedStack slot : live) {
            List<NBTTagCompound> identities = new ArrayList<>();
            for (ItemStack item : slot.items) identities.add(StackIdentity.of(item));
            slots.add(new ChoiceMatcher.Slot(slot.relx, slot.rely, identities));
        }
        int[] owners = ChoiceMatcher.match(choices, slots);
        List<PositionedStack> selected = new ArrayList<>();
        int matched = 0;
        for (int slotIndex = 0; slotIndex < live.size(); slotIndex++) {
            PositionedStack slot = live.get(slotIndex)
                .copy();
            int owner = owners[slotIndex];
            int alternative = 0;
            if (owner >= 0) {
                matched++;
                NBTTagCompound identity = choices.get(owner)
                    .identity();
                for (int i = 0; i < slot.items.length; i++) {
                    if (identity.equals(StackIdentity.of(slot.items[i]))) {
                        alternative = i;
                        break;
                    }
                }
            }
            if (slot.items.length > 0) slot.setPermutationToRender(alternative);
            selected.add(slot);
        }
        if (matched != choices.size()
            || (snapshot.version() == RecipeSnapshot.VERSION && live.size() != saved.size())) {
            choicesAdjusted = true;
        }
        return Collections.unmodifiableList(selected);
    }
}
