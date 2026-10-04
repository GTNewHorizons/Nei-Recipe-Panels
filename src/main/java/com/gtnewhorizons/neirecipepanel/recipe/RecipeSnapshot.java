package com.gtnewhorizons.neirecipepanel.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.github.bsideup.jabel.Desugar;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizons.neirecipepanel.network.BoundedNbt;

/** Durable recipe locator and selected identities; quantities and presentation remain live NEI data. */
public final class RecipeSnapshot {

    public static final int VERSION = 1;
    public static final int MAX_SLOTS = 256;
    private static final int MAX_RECIPE_ID = 12000;

    private final NBTTagCompound document;
    private final List<Selection> ingredients;
    private final List<Selection> others;
    private final Selection result;

    private RecipeSnapshot(NBTTagCompound document) {
        this.document = (NBTTagCompound) document.copy();
        ingredients = readSelections(document, "inputs");
        others = readSelections(document, "other");
        result = document.hasKey("result", 10) ? Selection.read(document.getCompoundTag("result")) : null;
    }

    public static RecipeSnapshot create(String handlerId, String recipeId, List<Selection> ingredients,
        Selection result, List<Selection> others, List<NBTTagCompound> outputs) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("ver", VERSION);
        tag.setString("handlerId", handlerId);
        tag.setString("recipeId", recipeId);
        tag.setTag("inputs", writeSelections(ingredients));
        tag.setTag("other", writeSelections(others));
        if (result != null) tag.setTag("result", result.write());
        NBTTagList outputTags = new NBTTagList();
        for (NBTTagCompound output : outputs) outputTags.appendTag(output.copy());
        tag.setTag("outputs", outputTags);
        return readFromNBT(tag);
    }

    public static RecipeSnapshot readFromNBT(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("ver", 3) || !tag.hasKey("recipeId", 8)) return null;
        if (tag.getInteger("ver") != VERSION || !validRecipeId(tag.getString("recipeId"))) return null;
        if (!BoundedNbt.isWithinLimit(tag, BoundedNbt.MAX_BYTES)) return null;
        if (!tag.hasKey("handlerId", 8) || tag.getString("handlerId")
            .isEmpty()
            || tag.getString("handlerId")
                .length() > 256
            || !validSelections(tag, "inputs", MAX_SLOTS)
            || !validSelections(tag, "other", MAX_SLOTS)
            || !validOutputs(tag)
            || (tag.hasKey("result")
                && (!tag.hasKey("result", 10) || !Selection.isValid(tag.getCompoundTag("result")))))
            return null;
        return new RecipeSnapshot(tag);
    }

    public static NBTTagCompound sanitize(NBTTagCompound raw, int maxSlots, int maxBytes) {
        if (raw == null || raw.getInteger("ver") != VERSION) return null;
        if (!validSelections(raw, "inputs", maxSlots) || !validSelections(raw, "other", maxSlots)) return null;
        if (!BoundedNbt.isWithinLimit(raw, Math.min(maxBytes, BoundedNbt.MAX_BYTES))) return null;
        RecipeSnapshot snapshot = readFromNBT(raw);
        if (snapshot == null) return null;
        return create(
            snapshot.handlerId(),
            snapshot.recipeIdJson(),
            snapshot.ingredients,
            snapshot.result,
            snapshot.others,
            snapshot.outputs()).writeToNBT();
    }

    public String recipeIdJson() {
        return document.getString("recipeId");
    }

    public String handlerId() {
        return document.getString("handlerId");
    }

    public List<Selection> ingredients() {
        return ingredients;
    }

    public List<Selection> others() {
        return others;
    }

    public Selection result() {
        return result;
    }

    public List<NBTTagCompound> outputs() {
        NBTTagList tags = document.getTagList("outputs", 10);
        List<NBTTagCompound> outputs = new ArrayList<>();
        for (int i = 0; i < tags.tagCount(); i++) outputs.add(
            (NBTTagCompound) tags.getCompoundTagAt(i)
                .copy());
        return outputs;
    }

    public NBTTagCompound writeToNBT() {
        return (NBTTagCompound) document.copy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecipeSnapshot && document.equals(((RecipeSnapshot) other).document);
    }

    @Override
    public int hashCode() {
        return document.hashCode();
    }

    private static boolean validRecipeId(String json) {
        if (json.isEmpty() || json.length() > MAX_RECIPE_ID) return false;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 16) return false;
            } else if (c == '}' || c == ']') {
                if (--depth < 0) return false;
            }
        }
        if (depth != 0 || quoted) return false;
        try {
            JsonObject id = new JsonParser().parse(json)
                .getAsJsonObject();
            if (!id.has("handlerName") || !id.get("handlerName")
                .isJsonPrimitive()
                || id.get("handlerName")
                    .getAsString()
                    .isEmpty()
                || id.get("handlerName")
                    .getAsString()
                    .length() > 256
                || !id.has("result")
                || !id.get("result")
                    .isJsonObject()
                || id.getAsJsonObject("result")
                    .entrySet()
                    .isEmpty()
                || !id.has("ingredients")
                || !id.get("ingredients")
                    .isJsonArray()
                || id.getAsJsonArray("ingredients")
                    .size() > MAX_SLOTS)
                return false;
            for (JsonElement ingredient : id.getAsJsonArray("ingredients")) {
                if (!ingredient.isJsonObject() || ingredient.getAsJsonObject()
                    .entrySet()
                    .isEmpty()) return false;
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean compoundList(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, 9)) return false;
        NBTTagList list = (NBTTagList) tag.getTag(key);
        return list.tagCount() == 0 || list.func_150303_d() == 10;
    }

    private static boolean validSelections(NBTTagCompound tag, String key, int limit) {
        if (!compoundList(tag, key)) return false;
        NBTTagList list = tag.getTagList(key, 10);
        if (list.tagCount() > limit) return false;
        for (int i = 0; i < list.tagCount(); i++) if (!Selection.isValid(list.getCompoundTagAt(i))) return false;
        return true;
    }

    private static boolean validOutputs(NBTTagCompound tag) {
        if (!compoundList(tag, "outputs")) return false;
        NBTTagList list = tag.getTagList("outputs", 10);
        if (list.tagCount() == 0 || list.tagCount() > MAX_SLOTS) return false;
        for (int i = 0; i < list.tagCount(); i++) if (list.getCompoundTagAt(i)
            .hasNoTags()) return false;
        return true;
    }

    private static List<Selection> readSelections(NBTTagCompound tag, String key) {
        List<Selection> selections = new ArrayList<>();
        NBTTagList list = tag.getTagList(key, 10);
        for (int i = 0; i < list.tagCount(); i++) selections.add(Selection.read(list.getCompoundTagAt(i)));
        return Collections.unmodifiableList(selections);
    }

    private static NBTTagList writeSelections(List<Selection> selections) {
        NBTTagList list = new NBTTagList();
        for (Selection selection : selections) list.appendTag(selection.write());
        return list;
    }

    @Desugar
    public record Selection(int x, int y, NBTTagCompound identity) {

        public Selection(int x, int y, NBTTagCompound identity) {
            this.x = x;
            this.y = y;
            this.identity = (NBTTagCompound) identity.copy();
        }

        @Override
        public NBTTagCompound identity() {
            return (NBTTagCompound) identity.copy();
        }

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setTag("s", identity.copy());
            return tag;
        }

        private static Selection read(NBTTagCompound tag) {
            return new Selection(tag.getInteger("x"), tag.getInteger("y"), tag.getCompoundTag("s"));
        }

        private static boolean isValid(NBTTagCompound tag) {
            return tag.hasKey("x", 3) && tag.hasKey("y", 3)
                && Math.abs((long) tag.getInteger("x")) <= 4096
                && Math.abs((long) tag.getInteger("y")) <= 4096
                && tag.hasKey("s", 10)
                && !tag.getCompoundTag("s")
                    .hasNoTags();
        }
    }
}
