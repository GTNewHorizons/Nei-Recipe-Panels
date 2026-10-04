package com.gtnewhorizons.neirecipepanel.client.render;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

final class PanelStatusText {

    private static final int MARGIN = 8;

    private PanelStatusText() {}

    static void draw(FontRenderer font, String key, int width, int height, int color) {
        List<String> lines = font.listFormattedStringToWidth(StatCollector.translateToLocal(key), width - 2 * MARGIN);
        int y = (height - lines.size() * font.FONT_HEIGHT) / 2;
        for (String line : lines) {
            font.drawString(line, (width - font.getStringWidth(line)) / 2, y, color);
            y += font.FONT_HEIGHT;
        }
    }
}
