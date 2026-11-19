package com.gtnewhorizons.neirecipepanel.client.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class RecipeFootprintTest {

    private static final int WIDTH = 166;
    private static final int HEIGHT = 100;

    @Test
    void buttonFlushWithTheRightEdgeIsFreeWhenNothingIsDrawn() {
        RecipeFootprint footprint = footprint(new boolean[WIDTH * WIDTH]);

        assertTrue(footprint.isFree(WIDTH - 12, HEIGHT - 18, 12, 12, 1));
    }

    @Test
    void drawnPixelInsideTheMarginBlocksTheSpot() {
        boolean[] drawn = new boolean[WIDTH * WIDTH];
        drawn[(HEIGHT - 19) * WIDTH + WIDTH - 6] = true;

        assertFalse(footprint(drawn).isFree(WIDTH - 12, HEIGHT - 18, 12, 12, 1));
    }

    @Test
    void buttonMustStayInsideTheWidget() {
        assertFalse(footprint(new boolean[WIDTH * WIDTH]).isFree(WIDTH - 11, HEIGHT - 18, 12, 12, 1));
    }

    private static RecipeFootprint footprint(boolean[] drawn) {
        return new RecipeFootprint(WIDTH, HEIGHT, WIDTH, drawn, Collections.emptyList());
    }
}
