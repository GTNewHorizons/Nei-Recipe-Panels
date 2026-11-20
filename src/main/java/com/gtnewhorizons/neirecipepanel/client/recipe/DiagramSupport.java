package com.gtnewhorizons.neirecipepanel.client.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.github.dcysteine.neicustomdiagram.api.diagram.Diagram;
import com.github.dcysteine.neicustomdiagram.api.diagram.DiagramGroup;
import com.github.dcysteine.neicustomdiagram.api.diagram.component.DisplayComponent;
import com.github.dcysteine.neicustomdiagram.api.diagram.interactable.Interactable;
import com.github.dcysteine.neicustomdiagram.api.diagram.interactable.InteractiveComponentGroup;
import com.github.dcysteine.neicustomdiagram.api.draw.Point;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeHandlerRef;

/** Only loaded through {@link DiagramHandlers} when NEI Custom Diagram is present. */
final class DiagramSupport {

    private DiagramSupport() {}

    static boolean isDiagram(IRecipeHandler handler) {
        return handler instanceof DiagramGroup;
    }

    static RecipeSnapshot capture(IRecipeHandler handler, int index) {
        DiagramGroup group = (DiagramGroup) handler;
        if (index < 0 || index >= group.diagrams.size()) return null;
        Content content = Content.of(group, group.diagrams.get(index));
        if (content.anchors.isEmpty()) return null;
        return RecipeSnapshot.createDiagram(group.getHandlerId(), content.anchors, content.hash);
    }

    static DiagramHandlers.Match resolve(RecipeSnapshot snapshot) {
        DiagramGroup prototype = prototype(snapshot.handlerId());
        if (prototype == null) return null;
        List<NBTTagCompound> saved = new ArrayList<>();
        for (RecipeSnapshot.Selection anchor : snapshot.diagramAnchors()) saved.add(anchor.identity());

        // Anchor lookups are cheap and find generated-on-demand diagrams; the full list is the fallback.
        Map<Diagram, Content> seen = new IdentityHashMap<>();
        List<Content> partial = new ArrayList<>();
        for (NBTTagCompound identity : saved) {
            ItemStack query = StackIdentity.queryStack(identity);
            if (query == null) continue;
            for (Interactable.RecipeType type : Interactable.RecipeType.values()) {
                DiagramHandlers.Match match = scan(
                    prototype,
                    prototype.loadDiagrams("item", type, query),
                    snapshot,
                    saved,
                    seen,
                    partial);
                if (match != null) return match;
            }
        }
        DiagramHandlers.Match match = scan(
            prototype,
            prototype.loadDiagrams(prototype.getHandlerId(), Interactable.RecipeType.CRAFTING),
            snapshot,
            saved,
            seen,
            partial);
        if (match != null) return match;

        // Changed diagram: accept it only when a single distinct candidate still shows every saved anchor.
        Set<Integer> hashes = new HashSet<>();
        for (Content content : partial) hashes.add(content.hash);
        return hashes.size() == 1 ? single(prototype, partial.get(0).diagram, false) : null;
    }

    private static DiagramHandlers.Match scan(DiagramGroup prototype, DiagramGroup group, RecipeSnapshot snapshot,
        List<NBTTagCompound> saved, Map<Diagram, Content> seen, List<Content> partial) {
        for (Diagram diagram : group.diagrams) {
            if (seen.containsKey(diagram)) continue;
            Content content = Content.of(group, diagram);
            seen.put(diagram, content);
            if (content.hash == snapshot.diagramHash() && content.identities.containsAll(saved)) {
                return single(prototype, diagram, true);
            }
            if (content.identities.containsAll(saved)) partial.add(content);
        }
        return null;
    }

    /** A group holding just this diagram, so index 0 is valid everywhere (NEI hover maths included). */
    private static DiagramHandlers.Match single(DiagramGroup prototype, Diagram diagram, boolean exact) {
        DiagramGroup group = prototype.newInstance(ImmutableList.of(diagram));
        return new DiagramHandlers.Match(RecipeHandlerRef.of(group, 0), exact);
    }

    private static DiagramGroup prototype(String groupId) {
        for (List<ICraftingHandler> handlers : Arrays
            .asList(GuiCraftingRecipe.craftinghandlers, GuiCraftingRecipe.serialCraftingHandlers)) {
            for (ICraftingHandler handler : handlers) {
                if (handler instanceof DiagramGroup && groupId.equals(handler.getHandlerId())) {
                    return (DiagramGroup) handler;
                }
            }
        }
        return null;
    }

    private static final class Content {

        private final Diagram diagram;
        private final List<RecipeSnapshot.Selection> anchors = new ArrayList<>();
        private final Set<NBTTagCompound> identities = new HashSet<>();
        private int hash = 1;

        private Content(Diagram diagram) {
            this.diagram = diagram;
        }

        private static Content of(DiagramGroup group, Diagram diagram) {
            Content content = new Content(diagram);
            for (Interactable interactable : diagram.interactables(group.diagramState)) {
                if (!(interactable instanceof InteractiveComponentGroup)) continue;
                InteractiveComponentGroup slot = (InteractiveComponentGroup) interactable;
                Point position = slot.position();
                content.hash = 31 * (31 * content.hash + position.x()) + position.y();
                for (DisplayComponent component : slot.components) {
                    NBTTagCompound identity = identity(
                        component.component()
                            .stack());
                    if (identity == null) continue;
                    content.hash = 31 * content.hash + identity.hashCode();
                    if (content.identities.add(identity)
                        && content.anchors.size() < RecipeSnapshot.MAX_DIAGRAM_ANCHORS) {
                        content.anchors.add(new RecipeSnapshot.Selection(position.x(), position.y(), identity));
                    }
                }
            }
            return content;
        }

        private static NBTTagCompound identity(Object stack) {
            if (stack instanceof ItemStack) return StackIdentity.of((ItemStack) stack);
            if (stack instanceof FluidStack) return StackIdentity.of((FluidStack) stack);
            return null;
        }
    }
}
