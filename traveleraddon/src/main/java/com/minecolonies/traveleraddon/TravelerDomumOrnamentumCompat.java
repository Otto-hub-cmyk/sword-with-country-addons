package com.minecolonies.traveleraddon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class TravelerDomumOrnamentumCompat {
    private static final String DO_NAMESPACE = "domum_ornamentum";
    private static final String TEXTURE_DATA = "textureData";

    private TravelerDomumOrnamentumCompat() {
    }

    public static boolean isDomumOrnamentumItem(final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && DO_NAMESPACE.equals(id.getNamespace());
    }

    public static boolean canSanitizeForMarket(final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !DO_NAMESPACE.equals(id.getNamespace())) {
            return true;
        }
        return !needsProfile(id.getPath()) || defaultProfile(id.getPath()) != null;
    }

    public static boolean sanitizeForMarket(final ItemStack stack) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !DO_NAMESPACE.equals(id.getNamespace())) {
            return true;
        }

        final DomumProfile profile = defaultProfile(id.getPath());
        if (profile == null) {
            return !needsProfile(id.getPath());
        }

        applyProfile(stack, profile, true);
        return true;
    }

    public static boolean sanitizeDemandStack(final ItemStack stack) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !DO_NAMESPACE.equals(id.getNamespace())) {
            return true;
        }

        final DomumProfile profile = defaultProfile(id.getPath());
        if (profile == null) {
            return !needsProfile(id.getPath());
        }

        applyProfile(stack, profile, false);
        return hasUsableTextureData(stack);
    }

    private static void applyProfile(final ItemStack stack, final DomumProfile profile, final boolean overwriteEmptyTextureData) {
        final CompoundTag tag = stack.getOrCreateTag();
        if (profile.type() != null && tag.getString("type").isBlank()) {
            tag.putString("type", profile.type());
        }

        if (!tag.contains(TEXTURE_DATA) || (overwriteEmptyTextureData && tag.getCompound(TEXTURE_DATA).isEmpty())) {
            tag.put(TEXTURE_DATA, profile.textureData().copy());
        }
    }

    private static boolean hasUsableTextureData(final ItemStack stack) {
        final CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TEXTURE_DATA) && !tag.getCompound(TEXTURE_DATA).isEmpty();
    }

    private static boolean needsProfile(final String path) {
        return switch (path) {
            case "panel",
                 "vanilla_trapdoors_compat",
                 "vanilla_doors_compat",
                 "post",
                 "fancy_door",
                 "fancy_trapdoors",
                 "vanilla_fence_compat",
                 "vanilla_fence_gate_compat",
                 "vanilla_slab_compat",
                 "vanilla_stairs_compat",
                 "vanilla_wall_compat",
                 "blockpaperwall",
                 "blocktiledpaperwall",
                 "dynamic_timberframe",
                 "shingle",
                 "shingle_flat",
                 "shingle_flat_lower",
                 "shingle_steep",
                 "shingle_steep_lower",
                 "shingle_slab" -> true;
            default -> false;
        };
    }

    private static DomumProfile defaultProfile(final String path) {
        return switch (path) {
            case "panel" -> new DomumProfile("FULL", textureData(entry("minecraft:block/oak_planks", "minecraft:oak_planks")));
            case "vanilla_trapdoors_compat" -> new DomumProfile("WAFFLE", textureData(entry("minecraft:block/oak_planks", "minecraft:oak_planks")));
            case "vanilla_doors_compat" -> new DomumProfile("WAFFLE", textureData(entry("minecraft:block/oak_planks", "minecraft:oak_planks")));
            case "post" -> new DomumProfile("PLAIN", textureData(entry("minecraft:block/oak_planks", "minecraft:oak_planks")));
            case "fancy_door" -> new DomumProfile("FULL", textureData(
                entry("minecraft:block/oak_planks", "minecraft:oak_planks"),
                entry("minecraft:block/acacia_planks", "minecraft:acacia_planks")
            ));
            case "fancy_trapdoors" -> new DomumProfile("FULL", textureData(
                entry("minecraft:block/oak_planks", "minecraft:oak_planks"),
                entry("minecraft:block/acacia_planks", "minecraft:acacia_planks")
            ));
            case "vanilla_fence_compat",
                 "vanilla_fence_gate_compat",
                 "vanilla_slab_compat",
                 "vanilla_stairs_compat",
                 "vanilla_wall_compat" -> new DomumProfile(null, textureData(entry("minecraft:block/oak_planks", "minecraft:oak_planks")));
            case "blockpaperwall",
                 "blocktiledpaperwall" -> new DomumProfile(null, textureData(
                entry("block/oak_planks", "minecraft:oak_planks"),
                entry("block/dark_oak_planks", "minecraft:dark_oak_planks")
            ));
            case "dynamic_timberframe" -> new DomumProfile(null, textureData(
                entry("block/oak_planks", "minecraft:oak_planks"),
                entry("block/dark_oak_planks", "minecraft:dark_oak_planks")
            ));
            case "shingle",
                 "shingle_flat",
                 "shingle_flat_lower",
                 "shingle_steep",
                 "shingle_steep_lower" -> new DomumProfile(null, textureData(
                entry("block/clay", "minecraft:clay"),
                entry("block/oak_planks", "minecraft:oak_planks")
            ));
            case "shingle_slab" -> new DomumProfile(null, textureData(
                entry("block/oak_planks", "minecraft:oak_planks"),
                entry("block/dark_oak_planks", "minecraft:dark_oak_planks")
            ));
            default -> null;
        };
    }

    private static CompoundTag textureData(final TextureEntry... entries) {
        final CompoundTag textureData = new CompoundTag();
        for (final TextureEntry entry : entries) {
            textureData.putString(entry.componentId(), entry.blockId());
        }
        return textureData;
    }

    private static TextureEntry entry(final String componentId, final String blockId) {
        return new TextureEntry(componentId, blockId);
    }

    private record DomumProfile(String type, CompoundTag textureData) {
    }

    private record TextureEntry(String componentId, String blockId) {
    }
}
