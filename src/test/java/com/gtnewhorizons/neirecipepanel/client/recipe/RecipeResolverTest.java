package com.gtnewhorizons.neirecipepanel.client.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.neirecipepanel.recipe.RecipeSnapshot;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.recipe.Badge;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;

class RecipeResolverTest {

    private static final String HANDLER_ID = "fixture.Handler";
    private static final String CATEGORY = "fixture.category";
    private ClientRecipeFixture fixture;
    private Item first;
    private Item second;
    private Item output;
    private Item byproduct;

    @BeforeEach
    void createFixture() throws ReflectiveOperationException {
        fixture = new ClientRecipeFixture();
        first = fixture.item("first");
        second = fixture.item("second");
        output = fixture.item("output");
        byproduct = fixture.item("byproduct");
        fixture.registerHandler(HANDLER_ID, CATEGORY);
    }

    @AfterEach
    void restoreGlobals() {
        if (fixture != null) fixture.close();
    }

    @Test
    void reorderedAlternativesPreserveSelectedItemWithCurrentCounts() {
        PositionedStack oldInput = slot(fixture.stack(first, 1), fixture.stack(second, 2));
        oldInput.setPermutationToRender(1);
        Handler original = handler(recipe(oldInput, slot(fixture.stack(output, 1))));
        PositionedStack currentInput = slot(fixture.stack(second, 7), fixture.stack(first, 11));
        Handler current = handler(recipe(currentInput, slot(fixture.stack(output, 42))));

        RecipeResolver.Resolution resolution = resolver(current).resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.READY, resolution.status());
        assertStack(
            second,
            7,
            resolution.recipe()
                .inputs()
                .get(0).item);
        assertStack(output, 42, resolution.displayResult());
        assertNotSame(
            currentInput,
            resolution.recipe()
                .inputs()
                .get(0));
        resolution.recipe()
            .inputs()
            .get(0).item.stackSize = 100;
        assertEquals(7, currentInput.item.stackSize);
    }

    @Test
    void reopenedRecipeCanBeImprintedWithItsOriginalHandlerAndRecipeIndex() {
        PositionedStack input = slot(fixture.stack(first, 3), fixture.stack(second, 5));
        input.setPermutationToRender(1);
        Handler original = handler(
            recipe(slot(fixture.stack(first, 1)), slot(fixture.stack(byproduct, 1))),
            recipe(input, slot(fixture.stack(output, 2))));
        RecipeSnapshot captured = RecipeCapture.capture(original, 1);
        assertNotNull(captured);
        ResolvedRecipe resolved = resolver(original).resolveFresh(captured.writeToNBT())
            .recipe();
        assertNotNull(resolved);
        assertEquals(1, resolved.index);

        IRecipeHandler reopened = resolved.pinnedHandler();
        assertEquals(1, reopened.numRecipes());
        assertEquals(
            1,
            reopened.getIngredientStacks(0)
                .get(0).items.length);
        assertStack(
            second,
            5,
            reopened.getIngredientStacks(0)
                .get(0).item);

        RecipeSnapshot recaptured = RecipeCapture.capture(reopened, 0);
        assertNotNull(recaptured);
        assertEquals(HANDLER_ID, recaptured.handlerId());
        assertEquals(captured.writeToNBT(), recaptured.writeToNBT());
    }

    @Test
    void uniqueCurrentRecipeCanReplaceItsInputAndReportAdjustedChoice() {
        Handler original = handler(recipe(slot(fixture.stack(first, 1)), slot(fixture.stack(output, 1))));
        Handler current = handler(recipe(slot(fixture.stack(second, 4)), slot(fixture.stack(output, 9))));

        RecipeResolver.Resolution resolution = resolver(current).resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.CHOICES_ADJUSTED, resolution.status());
        assertStack(
            second,
            4,
            resolution.recipe()
                .inputs()
                .get(0).item);
        assertStack(output, 9, resolution.displayResult());
    }

    @Test
    void absentRecipeHasNoInventedDisplayResult() {
        Handler original = handler(recipe(slot(fixture.stack(first, 1)), slot(fixture.stack(output, 1))));

        RecipeResolver.Resolution resolution = resolver().resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.MISSING, resolution.status());
        assertNull(resolution.recipe());
        assertNull(resolution.displayResult());
    }

    @Test
    void duplicateRecipesWithinOneQueryRemainAmbiguous() {
        FixtureRecipe sameRecipe = recipe(slot(fixture.stack(first, 1)), slot(fixture.stack(output, 1)));
        Handler original = handler(sameRecipe);
        Handler duplicates = handler(sameRecipe, sameRecipe);

        RecipeResolver.Resolution resolution = resolver(duplicates).resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.AMBIGUOUS, resolution.status());
        assertNull(resolution.recipe());
    }

    @Test
    void distinctRecipesWithTheSameOutputRemainAmbiguousAfterInputsChange() {
        Handler original = handler(recipe(slot(fixture.stack(first, 1)), slot(fixture.stack(output, 1))));
        Item third = fixture.item("third");
        Handler current = handler(
            recipe(slot(fixture.stack(second, 1)), slot(fixture.stack(output, 1))),
            recipe(slot(fixture.stack(third, 1)), slot(fixture.stack(output, 1))));

        RecipeResolver.Resolution resolution = resolver(current).resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.AMBIGUOUS, resolution.status());
        assertNull(resolution.recipe());
    }

    @Test
    void fullOutputSetDisambiguatesRecipesWithIdenticalInputsAndFirstOutput() {
        FixtureRecipe wanted = multiOutput(
            slot(fixture.stack(first, 1)),
            slot(fixture.stack(output, 1)),
            slot(fixture.stack(byproduct, 1)));
        Handler original = handler(wanted);
        Handler current = handler(
            wanted,
            multiOutput(slot(fixture.stack(first, 1)), slot(fixture.stack(output, 1)), slot(fixture.stack(second, 1))));

        RecipeResolver.Resolution resolution = resolver(current).resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.READY, resolution.status());
        assertEquals(0, resolution.recipe().index);
        assertStack(
            byproduct,
            1,
            resolution.recipe()
                .others()
                .get(1).item);
    }

    @Test
    void survivingByproductFindsCurrentRecipeWhenRepresentativeOutputDisappears() {
        Handler original = handler(
            multiOutput(
                slot(fixture.stack(first, 1)),
                slot(fixture.stack(output, 1)),
                slot(fixture.stack(byproduct, 1))));
        Handler current = handler(multiOutput(slot(fixture.stack(first, 2)), slot(fixture.stack(byproduct, 6))));
        List<Item> queried = new ArrayList<>();
        RecipeResolver resolver = new RecipeResolver((id, anchor) -> {
            queried.add(anchor.getItem());
            return anchor.getItem() == byproduct ? Collections.singletonList(current) : Collections.emptyList();
        });

        RecipeResolver.Resolution resolution = resolver.resolveFresh(document(original));

        assertEquals(Arrays.asList(output, byproduct), queried);
        assertEquals(RecipeResolver.Status.CHOICES_ADJUSTED, resolution.status());
        assertStack(byproduct, 6, resolution.displayResult());
        assertStack(
            first,
            2,
            resolution.recipe()
                .inputs()
                .get(0).item);
    }

    @Test
    void survivingByproductStillResolvesWhenOldRepresentativeItemIsRemovedFromThePack() {
        Handler original = handler(
            multiOutput(
                slot(fixture.stack(first, 1)),
                slot(fixture.stack(output, 1)),
                slot(fixture.stack(byproduct, 1))));
        NBTTagCompound saved = document(original);
        fixture.forget(output);
        Handler current = handler(multiOutput(slot(fixture.stack(first, 2)), slot(fixture.stack(byproduct, 6))));
        List<Item> queried = new ArrayList<>();
        RecipeResolver resolver = new RecipeResolver((id, anchor) -> {
            queried.add(anchor.getItem());
            return Collections.singletonList(current);
        });

        RecipeResolver.Resolution resolution = resolver.resolveFresh(saved);

        assertEquals(Collections.singletonList(byproduct), queried);
        assertEquals(RecipeResolver.Status.CHOICES_ADJUSTED, resolution.status());
        assertStack(byproduct, 6, resolution.displayResult());
    }

    @Test
    void overlappingOutputQueriesDoNotCreateArtificialAmbiguity() {
        FixtureRecipe recipe = multiOutput(
            slot(fixture.stack(first, 1)),
            slot(fixture.stack(output, 1)),
            slot(fixture.stack(byproduct, 1)));
        Handler original = handler(recipe);
        RecipeResolver resolver = new RecipeResolver((id, anchor) -> Collections.singletonList(handler(recipe)));

        RecipeResolver.Resolution resolution = resolver.resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.READY, resolution.status());
        assertNotNull(resolution.recipe());
    }

    @Test
    void differentActualHandlerCannotClaimTheSavedCategory() {
        FixtureRecipe recipe = recipe(slot(fixture.stack(first, 1)), slot(fixture.stack(output, 1)));
        Handler original = handler(recipe);
        String wrongId = "fixture.UnrelatedHandler";
        fixture.registerHandler(wrongId, CATEGORY);
        Handler unrelated = new Handler(wrongId, Collections.singletonList(recipe));

        RecipeResolver.Resolution resolution = resolver(unrelated).resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.MISSING, resolution.status());
        assertNull(resolution.recipe());
    }

    @Test
    void legacyPermutationIndexesDoNotPretendToRecoverSelectedIdentities() {
        Handler current = handler(recipe(slot(fixture.stack(first, 3)), slot(fixture.stack(output, 8))));
        NBTTagCompound legacy = document(current);
        legacy.setInteger("ver", 3);
        legacy.removeTag("inputs");
        legacy.removeTag("result");
        legacy.removeTag("other");
        legacy.removeTag("outputs");
        legacy.setIntArray("inPerms", new int[] { 7 });

        RecipeResolver.Resolution resolution = resolver(current).resolveFresh(legacy);

        assertEquals(RecipeResolver.Status.CHOICES_ADJUSTED, resolution.status());
        assertStack(
            first,
            3,
            resolution.recipe()
                .inputs()
                .get(0).item);
        assertStack(output, 8, resolution.displayResult());
    }

    @Test
    void reconciliationPreservesCustomStackPresentationWithoutSharingMutableItems() {
        CustomStack originalStack = new CustomStack(fixture.stack(first, 1));
        CustomStack liveStack = new CustomStack(fixture.stack(first, 17));
        Handler original = handler(recipe(originalStack, slot(fixture.stack(output, 1))));
        Handler current = handler(recipe(liveStack, slot(fixture.stack(output, 1))));

        RecipeResolver.Resolution resolution = resolver(current).resolveFresh(document(original));

        CustomStack selected = assertInstanceOf(
            CustomStack.class,
            resolution.recipe()
                .inputs()
                .get(0));
        assertEquals(24, selected.width);
        assertEquals(40, selected.height);
        assertEquals(2500, selected.getChance());
        assertEquals(
            1,
            selected.getBadges()
                .size());
        assertEquals(
            "custom",
            selected.getBadges()
                .get(0)
                .getText());
        assertNotSame(liveStack, selected);
        assertNotSame(liveStack.items, selected.items);
        assertNotSame(liveStack.getBadges(), selected.getBadges());
        assertStack(first, 17, selected.item);
    }

    @Test
    void furnaceFuelChoiceSurvivesTheHandlersDynamicFuelReplacement() throws InstantiationException {
        FurnaceRecipeHandler.afuels = new ArrayList<>(
            Arrays.asList(
                new FurnaceRecipeHandler.FuelPair(fixture.stack(first, 1), 1600),
                new FurnaceRecipeHandler.FuelPair(fixture.stack(second, 1), 1600)));
        FixtureFurnace original = furnace(FurnaceRecipeHandler.afuels.get(1).stack);
        FixtureFurnace current = furnace(FurnaceRecipeHandler.afuels.get(0).stack);

        RecipeResolver.Resolution resolution = resolver(current).resolveFresh(document(original));

        assertEquals(RecipeResolver.Status.READY, resolution.status());
        assertStack(
            second,
            1,
            resolution.recipe()
                .others()
                .get(0).item);
        current.fuel = FurnaceRecipeHandler.afuels.get(0).stack;
        resolution.recipe()
            .refresh();
        assertStack(
            second,
            1,
            resolution.recipe()
                .others()
                .get(0).item);
        assertStack(first, 1, current.fuel.item);
    }

    private FixtureFurnace furnace(PositionedStack fuel) throws InstantiationException {
        FixtureFurnace furnace = fixture.allocate(FixtureFurnace.class);
        furnace.input = slot(fixture.stack(output, 1));
        furnace.result = slot(fixture.stack(byproduct, 1));
        furnace.fuel = fuel;
        fixture.registerHandler(furnace.getHandlerId(), CATEGORY);
        return furnace;
    }

    private static RecipeResolver resolver(ICraftingHandler... handlers) {
        return new RecipeResolver((id, anchor) -> Arrays.asList(handlers));
    }

    private static Handler handler(FixtureRecipe... recipes) {
        return new Handler(HANDLER_ID, Arrays.asList(recipes));
    }

    private static FixtureRecipe recipe(PositionedStack input, PositionedStack result) {
        return new FixtureRecipe(Collections.singletonList(input), result, Collections.emptyList());
    }

    private static FixtureRecipe multiOutput(PositionedStack input, PositionedStack... outputs) {
        return new FixtureRecipe(Collections.singletonList(input), null, Arrays.asList(outputs));
    }

    private static PositionedStack slot(ItemStack... alternatives) {
        return new PositionedStack(alternatives, 8, 12, false);
    }

    private static NBTTagCompound document(IRecipeHandler handler) {
        PositionedStack result = handler.getResultStack(0);
        List<PositionedStack> others = handler.getOtherStacks(0);
        List<PositionedStack> outputs = result == null ? others : Collections.singletonList(result);
        RecipeSnapshot snapshot = RecipeSnapshot.create(
            handler.getHandlerId(),
            Recipe.RecipeId.of(handler, 0)
                .toJsonObject()
                .toString(),
            selections(handler.getIngredientStacks(0)),
            result == null ? null : selection(result),
            selections(others),
            outputs.stream()
                .map(stack -> StackIdentity.of(stack.item))
                .collect(Collectors.toList()));
        assertNotNull(snapshot);
        return snapshot.writeToNBT();
    }

    private static List<RecipeSnapshot.Selection> selections(List<PositionedStack> stacks) {
        return stacks.stream()
            .map(RecipeResolverTest::selection)
            .collect(Collectors.toList());
    }

    private static RecipeSnapshot.Selection selection(PositionedStack stack) {
        return new RecipeSnapshot.Selection(stack.relx, stack.rely, StackIdentity.of(stack.item));
    }

    private static void assertStack(Item item, int count, ItemStack stack) {
        assertNotNull(stack);
        assertSame(item, stack.getItem());
        assertEquals(count, stack.stackSize);
    }

    private static final class FixtureRecipe {

        private final List<PositionedStack> inputs;
        private final PositionedStack result;
        private final List<PositionedStack> others;

        private FixtureRecipe(List<PositionedStack> inputs, PositionedStack result, List<PositionedStack> others) {
            this.inputs = inputs;
            this.result = result;
            this.others = others;
        }
    }

    private static final class Handler implements ICraftingHandler {

        private final String id;
        private final List<FixtureRecipe> recipes;

        private Handler(String id, List<FixtureRecipe> recipes) {
            this.id = id;
            this.recipes = recipes;
        }

        @Override
        public String getHandlerId() {
            return id;
        }

        @Override
        public String getRecipeName() {
            return "Fixture";
        }

        @Override
        public int numRecipes() {
            return recipes.size();
        }

        @Override
        public List<PositionedStack> getIngredientStacks(int index) {
            return recipes.get(index).inputs;
        }

        @Override
        public List<PositionedStack> getOtherStacks(int index) {
            return recipes.get(index).others;
        }

        @Override
        public PositionedStack getResultStack(int index) {
            return recipes.get(index).result;
        }

        @Override
        public ICraftingHandler getRecipeHandler(String outputId, Object... results) {
            return this;
        }

        @Override
        public void drawBackground(int index) {}

        @Override
        public void drawForeground(int index) {}

        @Override
        public void onUpdate() {}

        @Override
        public boolean hasOverlay(GuiContainer gui, Container container, int index) {
            return false;
        }

        @Override
        public IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int index) {
            return null;
        }

        @Override
        public IOverlayHandler getOverlayHandler(GuiContainer gui, int index) {
            return null;
        }

        @Override
        public List<String> handleTooltip(GuiRecipe<?> gui, List<String> tooltip, int index) {
            return tooltip;
        }

        @Override
        public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> tooltip, int index) {
            return tooltip;
        }

        @Override
        public boolean keyTyped(GuiRecipe<?> gui, char key, int code, int index) {
            return false;
        }

        @Override
        public boolean mouseClicked(GuiRecipe<?> gui, int button, int index) {
            return false;
        }
    }

    private static final class CustomStack extends PositionedStack {

        private CustomStack(ItemStack stack) {
            super(stack, 8, 12, false);
            width = 24;
            height = 40;
            setChance(2500);
            setBadges(Collections.singletonList(new Badge("custom")));
        }
    }

    private static final class FixtureFurnace extends FurnaceRecipeHandler {

        private PositionedStack input;
        private PositionedStack result;
        private PositionedStack fuel;

        @Override
        public String getRecipeName() {
            return "Furnace fixture";
        }

        @Override
        public int numRecipes() {
            return 1;
        }

        @Override
        public List<PositionedStack> getIngredientStacks(int ignored) {
            return Collections.singletonList(input);
        }

        @Override
        public List<PositionedStack> getOtherStacks(int ignored) {
            return Collections.singletonList(fuel);
        }

        @Override
        public PositionedStack getResultStack(int ignored) {
            return result;
        }
    }
}
