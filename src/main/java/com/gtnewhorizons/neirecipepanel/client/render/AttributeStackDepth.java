package com.gtnewhorizons.neirecipepanel.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.IntSupplier;

import org.lwjgl.opengl.GL11;

/** Angelica emulates attribute stacks without reporting their depths through glGetInteger. */
final class AttributeStackDepth {

    private static final AttributeStackDepth INSTANCE = create();
    private final IntSupplier attributes;
    private final IntSupplier clientAttributes;

    private AttributeStackDepth(IntSupplier attributes, IntSupplier clientAttributes) {
        this.attributes = attributes;
        this.clientAttributes = clientAttributes;
    }

    static int attributes() {
        return INSTANCE.attributes.getAsInt();
    }

    static int clientAttributes() {
        return INSTANCE.clientAttributes.getAsInt();
    }

    private static AttributeStackDepth create() {
        Class<?> manager;
        try {
            manager = Class.forName(
                "com.gtnewhorizons.angelica.glsm.GLStateManager",
                false,
                AttributeStackDepth.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return new AttributeStackDepth(
                () -> GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH),
                () -> GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH));
        }
        try {
            Method depth = manager.getMethod("getAttribDepth");
            Method context = manager.getMethod("ctx");
            Field clientDepth = context.getReturnType()
                .getField("clientAttribStackPointer");
            return new AttributeStackDepth(() -> {
                try {
                    return (Integer) depth.invoke(null);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read Angelica attribute stack depth", e);
                }
            }, () -> {
                try {
                    return clientDepth.getInt(context.invoke(null));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read Angelica client attribute stack depth", e);
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported Angelica attribute stack API", e);
        }
    }
}
