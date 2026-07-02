package com.minecolonies.traveleraddon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TravelerItemSafety {
    private static final Set<String> RUNTIME_BLOCKED_ITEMS = ConcurrentHashMap.newKeySet();
    private static final Set<String> RUNTIME_BLOCKED_NAMESPACES = ConcurrentHashMap.newKeySet();

    private TravelerItemSafety() {
    }

    public static boolean isBlocked(final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return true;
        }
        return RUNTIME_BLOCKED_ITEMS.contains(id.toString()) || RUNTIME_BLOCKED_NAMESPACES.contains(id.getNamespace());
    }

    public static ItemStack createSafeMarketStack(final Item item, final int count) {
        if (item == null || count <= 0 || isBlocked(item)) {
            return ItemStack.EMPTY;
        }

        final ItemStack stack;
        try {
            stack = new ItemStack(item, count);
        } catch (final Exception exception) {
            blockItem(item, "market-construction", exception);
            return ItemStack.EMPTY;
        }

        if (!TravelerDomumOrnamentumCompat.sanitizeForMarket(stack)) {
            blockItem(item, "market-sanitize", null);
            return ItemStack.EMPTY;
        }

        return validateStack(stack, "market-validate");
    }

    public static ItemStack sanitizeDemandStack(final ItemStack source) {
        if (source == null || source.isEmpty() || isBlocked(source.getItem())) {
            return ItemStack.EMPTY;
        }

        final ItemStack safeStack = source.copy();
        if (!TravelerDomumOrnamentumCompat.sanitizeDemandStack(safeStack)) {
            blockItem(source.getItem(), "demand-sanitize", null);
            return ItemStack.EMPTY;
        }

        return validateStack(safeStack, "demand-validate");
    }

    public static ItemStack sanitizePreviewStack(final Item item, final int count) {
        final ItemStack stack = createSafeMarketStack(item, count);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private static ItemStack validateStack(final ItemStack stack, final String phase) {
        try {
            final CompoundTag serialized = stack.save(new CompoundTag());
            if (serialized == null) {
                blockItem(stack.getItem(), phase + "-serialize-null", null);
                return ItemStack.EMPTY;
            }

            final ItemStack roundTrip = ItemStack.of(serialized);
            if (roundTrip.isEmpty()) {
                blockItem(stack.getItem(), phase + "-roundtrip-empty", null);
                return ItemStack.EMPTY;
            }

            roundTrip.getHoverName().getString();
            roundTrip.getRarity();
            return roundTrip;
        } catch (final Exception exception) {
            blockItem(stack.getItem(), phase, exception);
            return ItemStack.EMPTY;
        }
    }

    private static void blockItem(final Item item, final String phase, final Exception exception) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return;
        }

        RUNTIME_BLOCKED_ITEMS.add(id.toString());
        if (exception == null) {
            TravelerAddonMod.LOGGER.warn("Traveler runtime-blocked item {} during {}", id, phase);
        } else {
            TravelerAddonMod.LOGGER.warn("Traveler runtime-blocked item {} during {}", id, phase, exception);
        }
    }
}
