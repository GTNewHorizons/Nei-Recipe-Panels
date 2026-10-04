package com.gtnewhorizons.neirecipepanel.client.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;

public final class RecipeCapture {

    private RecipeCapture() {}

    public static RecipeSnapshot capture(IRecipeHandler handler, int index) {
        try {
            RecipeHandlerRef source = PinnedRecipeHandler.unwrap(handler, index);
            handler = source.handler;
            index = source.recipeIndex;
            Recipe.RecipeId id = Recipe.RecipeId.of(handler, index);
            if (id == null || id.getResult() == null) return null;
            PositionedStack result = handler.getResultStack(index);
            List<PositionedStack> others = orEmpty(handler.getOtherStacks(index));
            List<NBTTagCompound> outputs = new ArrayList<>();
            for (PositionedStack output : result == null ? others : Collections.singletonList(result)) {
                NBTTagCompound identity = StackIdentity.of(output.item);
                if (identity == null) return null;
                outputs.add(identity);
            }
            return RecipeSnapshot.create(
                handler.getHandlerId(),
                id.toJsonObject()
                    .toString(),
                selections(handler.getIngredientStacks(index)),
                result == null ? null : selection(result),
                selections(others),
                outputs);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static List<RecipeSnapshot.Selection> selections(List<PositionedStack> slots) {
        List<RecipeSnapshot.Selection> choices = new ArrayList<>();
        for (PositionedStack slot : orEmpty(slots)) choices.add(selection(slot));
        return choices;
    }

    private static RecipeSnapshot.Selection selection(PositionedStack slot) {
        NBTTagCompound identity = StackIdentity.of(slot.item);
        if (identity == null) throw new IllegalArgumentException("Recipe slot has no selected identity");
        return new RecipeSnapshot.Selection(slot.relx, slot.rely, identity);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
