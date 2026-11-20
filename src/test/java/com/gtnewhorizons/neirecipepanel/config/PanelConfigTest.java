package com.gtnewhorizons.neirecipepanel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.config.Config;

class PanelConfigTest {

    private static final Class<?>[] CATEGORIES = { PanelConfig.Recipe.class, PanelConfig.Server.class,
        PanelConfig.Client.class };

    /** Code outside FML (and anything reading a field before GTNHLib loads) must see the configured defaults. */
    @Test
    void javaInitialisersMatchTheConfigDefaults() throws IllegalAccessException {
        for (Class<?> category : CATEGORIES) {
            for (Field field : configFields(category)) {
                Object value = field.get(null);
                String name = category.getSimpleName() + "." + field.getName();
                if (field.isAnnotationPresent(Config.DefaultInt.class)) {
                    assertEquals(
                        field.getAnnotation(Config.DefaultInt.class)
                            .value(),
                        value,
                        name);
                } else if (field.isAnnotationPresent(Config.DefaultBoolean.class)) {
                    assertEquals(
                        field.getAnnotation(Config.DefaultBoolean.class)
                            .value(),
                        value,
                        name);
                } else if (field.isAnnotationPresent(Config.DefaultEnum.class)) {
                    assertEquals(
                        field.getAnnotation(Config.DefaultEnum.class)
                            .value(),
                        ((Enum<?>) value).name(),
                        name);
                } else {
                    throw new AssertionError("No default annotation on " + name);
                }
            }
        }
    }

    /** GTNHLib lowercases every part of generated keys: %mod.cfg.%cat.%field. */
    @Test
    void everyConfigEntryHasALangKey() throws IOException {
        Properties lang = new Properties();
        try (InputStream in = PanelConfigTest.class.getResourceAsStream("/assets/nei-recipe-panels/lang/en_US.lang")) {
            assertNotNull(in);
            lang.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        for (Class<?> category : CATEGORIES) {
            String prefix = "nei-recipe-panels.cfg." + category.getAnnotation(Config.class)
                .category()
                .toLowerCase(Locale.ROOT);
            assertTrue(lang.containsKey(prefix), prefix);
            for (Field field : configFields(category)) {
                String key = prefix + "."
                    + field.getName()
                        .toLowerCase(Locale.ROOT);
                assertTrue(lang.containsKey(key), key);
            }
        }
    }

    private static Iterable<Field> configFields(Class<?> category) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Field field : category.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) fields.add(field);
        }
        return fields;
    }
}
