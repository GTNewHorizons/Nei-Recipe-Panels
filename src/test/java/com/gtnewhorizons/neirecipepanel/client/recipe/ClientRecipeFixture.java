package com.gtnewhorizons.neirecipepanel.client.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.RegistrySimple;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;

import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.common.Loader;
import sun.misc.Unsafe;

final class ClientRecipeFixture implements AutoCloseable {

    private final Unsafe unsafe;
    private final Map<Field, Object> originalFields = new LinkedHashMap<>();
    private List<IStackStringifyHandler> originalStringifiers;
    private HashMap<String, HandlerInfo> originalHandlerInfo;
    private ArrayList<FurnaceRecipeHandler.FuelPair> originalFuels;
    private boolean capturedFuels;
    private Map<Object, Object> registryNames;
    private final Map<String, Object> originalRegistryNames = new LinkedHashMap<>();
    private final Map<Item, String> names = new IdentityHashMap<>();
    private final Map<String, Item> items = new HashMap<>();
    private final Map<Item, Integer> damages = new IdentityHashMap<>();

    ClientRecipeFixture() throws ReflectiveOperationException {
        Field singleton = Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        unsafe = (Unsafe) singleton.get(null);
        try {
            replace(Loader.class, "instance", unsafe.allocateInstance(Loader.class));
            Class.forName(Blocks.class.getName());
            replace(Blocks.class, "water", new Block(Material.water) {});
            replace(Blocks.class, "lava", new Block(Material.lava) {});
            Class.forName(Items.class.getName());
            for (String name : new String[] { "bucket", "water_bucket", "lava_bucket", "glass_bottle", "diamond" }) {
                replace(Items.class, name, new Item());
            }
            replace(Items.class, "potionitem", new ItemPotion());
            Class.forName(FluidRegistry.class.getName());
            Class.forName(FluidContainerRegistry.class.getName());
            originalStringifiers = new ArrayList<>(StackInfo.stackStringifyHandlers);
            StackInfo.stackStringifyHandlers.clear();
            StackInfo.stackStringifyHandlers.add(new FixtureStringifier());
            originalHandlerInfo = GuiRecipeTab.handlerMap;
            GuiRecipeTab.handlerMap = new HashMap<>();
            originalFuels = FurnaceRecipeHandler.afuels;
            capturedFuels = true;
            registryNames = registryNames();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            close();
            throw failure;
        }
    }

    Item item(String name) {
        Item item = new Item();
        names.put(item, "fixture:" + name);
        items.put("fixture:" + name, item);
        damages.put(item, damages.size() + 1);
        String registryName = "fixture:" + name;
        if (!originalRegistryNames.containsKey(registryName)) {
            originalRegistryNames.put(registryName, registryNames.get(registryName));
        }
        registryNames.put(registryName, item);
        return item;
    }

    ItemStack stack(Item item, int count) {
        return new ItemStack(item, count, damages.get(item));
    }

    void forget(Item item) {
        items.remove(names.get(item));
    }

    void registerHandler(String handlerId, String category) {
        GuiRecipeTab.handlerMap.put(handlerId, new HandlerInfo(category, "Fixture", "fixture", false, ""));
    }

    <T> T allocate(Class<T> type) throws InstantiationException {
        return type.cast(unsafe.allocateInstance(type));
    }

    @Override
    public void close() {
        if (registryNames != null) {
            originalRegistryNames.keySet()
                .forEach(registryNames::remove);
            originalRegistryNames.forEach((name, item) -> { if (item != null) registryNames.put(name, item); });
        }
        if (capturedFuels) FurnaceRecipeHandler.afuels = originalFuels;
        if (originalHandlerInfo != null) GuiRecipeTab.handlerMap = originalHandlerInfo;
        if (originalStringifiers != null) {
            StackInfo.stackStringifyHandlers.clear();
            StackInfo.stackStringifyHandlers.addAll(originalStringifiers);
        }
        for (Map.Entry<Field, Object> entry : originalFields.entrySet()) {
            Field field = entry.getKey();
            unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), entry.getValue());
        }
    }

    private void replace(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        originalFields.put(field, field.get(null));
        unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> registryNames() throws ReflectiveOperationException {
        Field field = RegistrySimple.class.getDeclaredField("registryObjects");
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(Item.itemRegistry);
    }

    private final class FixtureStringifier implements IStackStringifyHandler {

        @Override
        public NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize) {
            if (stack == null || !names.containsKey(stack.getItem())) return null;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("strId", names.get(stack.getItem()));
            tag.setInteger("Damage", stack.getItemDamage());
            tag.setInteger("Count", saveStackSize ? stack.stackSize : 1);
            if (stack.hasTagCompound()) tag.setTag(
                "tag",
                stack.getTagCompound()
                    .copy());
            return tag;
        }

        @Override
        public ItemStack convertNBTToItemStack(NBTTagCompound tag) {
            Item item = items.get(tag.getString("strId"));
            if (item == null) return null;
            ItemStack stack = new ItemStack(item, tag.getInteger("Count"), tag.getInteger("Damage"));
            if (tag.hasKey("tag", 10)) stack.setTagCompound(
                (NBTTagCompound) tag.getCompoundTag("tag")
                    .copy());
            return stack;
        }
    }
}
