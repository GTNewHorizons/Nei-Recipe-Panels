package com.gtnewhorizons.neirecipepanel.client.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.gson.JsonParser;
import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;
import com.gtnewhorizons.neirecipepanel.recipe.ChoiceMatcher;
import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.StackInfo;

public final class RecipeResolver {

    public static final RecipeResolver INSTANCE = new RecipeResolver(
        (id, output) -> GuiCraftingRecipe.getCraftingHandlers("recipeId", output, id));
    private static final int MAX_CACHED_RECIPES = 128;
    private static final int MAX_CANDIDATES = 16384;
    private static final int MAX_OUTPUT_ANCHORS = 16;
    private final Map<NBTTagCompound, Resolution> cache = new LinkedHashMap<>(16, 0.75F, true);
    private final BiFunction<Recipe.RecipeId, ItemStack, List<ICraftingHandler>> craftingLookup;

    RecipeResolver(BiFunction<Recipe.RecipeId, ItemStack, List<ICraftingHandler>> craftingLookup) {
        this.craftingLookup = craftingLookup;
    }

    public void clear() {
        cache.clear();
    }

    public Resolution resolve(NBTTagCompound document) {
        if (document == null) return Resolution.failed(Status.INVALID);
        Resolution result = cache.get(document);
        if (result != null) return result;
        result = resolveFresh(document);
        cache.put((NBTTagCompound) document.copy(), result);
        if (cache.size() > MAX_CACHED_RECIPES) cache.remove(
            cache.keySet()
                .iterator()
                .next());
        return result;
    }

    public Resolution resolveFresh(NBTTagCompound document) {
        RecipeSnapshot snapshot = RecipeSnapshot.readFromNBT(document);
        if (snapshot == null) return Resolution.failed(Status.INVALID);
        try {
            Recipe.RecipeId id = Recipe.RecipeId.of(
                new JsonParser().parse(snapshot.recipeIdJson())
                    .getAsJsonObject());
            if (id.getHandlerName() == null) return Resolution.failed(Status.MISSING);
            List<RecipeHandlerRef> candidates = new ArrayList<>();
            List<RecipeHandlerRef> exact = new ArrayList<>();
            for (RecipeHandlerRef candidate : discover(snapshot, id)) {
                IRecipeHandler handler = candidate.handler;
                int index = candidate.recipeIndex;
                List<PositionedStack> outputs = outputs(handler, index);
                candidates.add(candidate);
                if (matchesInputs(handler, index, id) && matchesOutputs(outputs, snapshot.outputs()))
                    exact.add(candidate);
            }
            List<RecipeHandlerRef> matches = exact.isEmpty() ? candidates : exact;
            if (matches.isEmpty()) return Resolution.failed(Status.MISSING);
            if (matches.size() != 1) return Resolution.failed(Status.AMBIGUOUS);
            RecipeHandlerRef ref = matches.get(0);
            return new Resolution(new ResolvedRecipe(ref.handler, ref.recipeIndex, snapshot), Status.READY);
        } catch (RuntimeException | LinkageError e) {
            NEIRecipePanelsMod.LOG.warn("Could not resolve recipe panel", e);
            return Resolution.failed(Status.UNSUPPORTED);
        }
    }

    private List<RecipeHandlerRef> discover(RecipeSnapshot snapshot, Recipe.RecipeId id) {
        Map<NBTTagCompound, ItemStack> anchors = new LinkedHashMap<>();
        if (id.getResult() != null) anchors.put(StackIdentity.of(id.getResult()), id.getResult());
        for (NBTTagCompound output : snapshot.outputs()) {
            ItemStack query = StackIdentity.queryStack(output);
            if (query != null) anchors.put(output, query);
        }
        if (anchors.size() > MAX_OUTPUT_ANCHORS) throw new IllegalArgumentException("Too many recipe output anchors");
        Map<NBTTagCompound, List<RecipeHandlerRef>> discovered = new LinkedHashMap<>();
        int checked = 0;
        for (Map.Entry<NBTTagCompound, ItemStack> anchor : anchors.entrySet()) {
            Map<NBTTagCompound, List<RecipeHandlerRef>> queryMatches = new LinkedHashMap<>();
            for (ICraftingHandler handler : craftingLookup.apply(id, anchor.getValue())) {
                if (!id.getHandlerName()
                    .equals(
                        GuiRecipeTab.getHandlerInfo(handler)
                            .getHandlerName()))
                    continue;
                if (!snapshot.handlerId()
                    .equals(handler.getHandlerId())) continue;
                for (int index = 0; index < handler.numRecipes(); index++) {
                    if (++checked > MAX_CANDIDATES) throw new IllegalArgumentException("Too many recipe candidates");
                    List<PositionedStack> outputs = outputs(handler, index);
                    if (!accepts(outputs, anchor.getKey())) continue;
                    queryMatches.computeIfAbsent(fingerprint(handler, index), key -> new ArrayList<>())
                        .add(RecipeHandlerRef.of(handler, index));
                }
            }
            for (Map.Entry<NBTTagCompound, List<RecipeHandlerRef>> match : queryMatches.entrySet()) {
                List<RecipeHandlerRef> previous = discovered.get(match.getKey());
                // Deduplicate overlapping output queries while retaining duplicates within a query as ambiguous.
                if (previous == null || previous.size() < match.getValue()
                    .size()) discovered.put(match.getKey(), match.getValue());
            }
        }
        List<RecipeHandlerRef> candidates = new ArrayList<>();
        for (List<RecipeHandlerRef> matches : discovered.values()) candidates.addAll(matches);
        return candidates;
    }

    private static NBTTagCompound fingerprint(IRecipeHandler handler, int index) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("handler", handler.getHandlerId());
        tag.setString(
            "id",
            Recipe.RecipeId.of(handler, index)
                .toJsonObject()
                .toString());
        List<PositionedStack> stacks = new ArrayList<>(handler.getIngredientStacks(index));
        stacks.addAll(handler.getOtherStacks(index));
        PositionedStack result = handler.getResultStack(index);
        if (result != null) stacks.add(result);
        NBTTagList slots = new NBTTagList();
        for (PositionedStack stack : stacks) {
            NBTTagCompound slot = new NBTTagCompound();
            slot.setInteger("x", stack.relx);
            slot.setInteger("y", stack.rely);
            NBTTagList items = new NBTTagList();
            for (ItemStack item : stack.items) {
                NBTTagCompound data = StackInfo.itemStackToNBT(item);
                if (data != null) items.appendTag(data);
            }
            slot.setTag("items", items);
            slots.appendTag(slot);
        }
        tag.setTag("slots", slots);
        return tag;
    }

    static boolean matchesInputs(IRecipeHandler handler, int index, Recipe.RecipeId id) {
        List<PositionedStack> slots = handler.getIngredientStacks(index);
        if (slots == null) slots = Collections.emptyList();
        List<ItemStack> ingredients = id.getIngredients();
        if (slots.size() != ingredients.size()) return false;
        List<RecipeSnapshot.Selection> choices = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            NBTTagCompound identity = StackIdentity.of(ingredients.get(i));
            if (identity == null) return false;
            if (id.isShapedRecipe()) {
                if (!accepts(Collections.singletonList(slots.get(i)), identity)) return false;
            } else choices.add(new RecipeSnapshot.Selection(0, 0, identity));
        }
        return id.isShapedRecipe() || allMatched(ChoiceMatcher.match(choices, optionSlots(slots)));
    }

    private static boolean matchesOutputs(List<PositionedStack> outputs, List<NBTTagCompound> identities) {
        if (identities.size() != outputs.size()) return false;
        List<RecipeSnapshot.Selection> choices = new ArrayList<>();
        for (NBTTagCompound identity : identities) choices.add(new RecipeSnapshot.Selection(0, 0, identity));
        return allMatched(ChoiceMatcher.match(choices, optionSlots(outputs)));
    }

    private static boolean allMatched(int[] owners) {
        for (int owner : owners) if (owner < 0) return false;
        return true;
    }

    private static List<ChoiceMatcher.Slot> optionSlots(List<PositionedStack> stacks) {
        List<ChoiceMatcher.Slot> slots = new ArrayList<>();
        for (PositionedStack stack : stacks) {
            List<NBTTagCompound> identities = new ArrayList<>();
            for (ItemStack item : stack.items) identities.add(StackIdentity.of(item));
            slots.add(new ChoiceMatcher.Slot(stack.relx, stack.rely, identities));
        }
        return slots;
    }

    private static boolean accepts(List<PositionedStack> outputs, NBTTagCompound identity) {
        if (identity == null) return false;
        for (PositionedStack output : outputs) {
            for (ItemStack item : output.items) if (identity.equals(StackIdentity.of(item))) return true;
        }
        return false;
    }

    private static List<PositionedStack> outputs(IRecipeHandler handler, int index) {
        PositionedStack result = handler.getResultStack(index);
        return result == null ? handler.getOtherStacks(index) : Collections.singletonList(result);
    }

    public enum Status {

        READY,
        CHOICES_ADJUSTED,
        MISSING,
        AMBIGUOUS,
        INVALID,
        UNSUPPORTED;

        public String translationKey() {
            return "nei-recipe-panels.recipe." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final class Resolution {

        private final ResolvedRecipe recipe;
        private final Status status;

        private Resolution(ResolvedRecipe recipe, Status status) {
            this.recipe = recipe;
            this.status = status;
        }

        private static Resolution failed(Status status) {
            return new Resolution(null, status);
        }

        public ResolvedRecipe recipe() {
            return recipe;
        }

        public Status status() {
            return recipe != null && recipe.choicesAdjusted() ? Status.CHOICES_ADJUSTED : status;
        }

        public ItemStack displayResult() {
            return recipe == null ? null : recipe.displayResult();
        }
    }
}
