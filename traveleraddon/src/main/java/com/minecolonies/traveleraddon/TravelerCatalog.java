package com.minecolonies.traveleraddon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TravelerCatalog {
    private static final int LOW_PRICE = 16;
    private static final int MEDIUM_PRICE = 48;
    private static final int HIGH_PRICE = 128;
    private static final int EXTREME_PRICE = 328;

    private static final Set<String> BLOCKED_IDS = Set.of(
        "minecraft:air",
        "minecraft:amethyst_shard",
        "minecraft:bedrock",
        "minecraft:barrier",
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",
        "minecraft:command_block_minecart",
        "minecraft:jigsaw",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:debug_stick",
        "minecraft:knowledge_book",
        "minecraft:light",
        "minecraft:spawner",
        "minecraft:trial_spawner",
        "minecraft:end_portal_frame"
    );

    private static final Set<String> EXTREME_VALUE_IDS = Set.of(
        "minecraft:netherite_ingot",
        "minecraft:netherite_scrap",
        "minecraft:ancient_debris",
        "minecraft:netherite_block"
    );

    private static final Set<String> HIGH_VALUE_BLOCK_IDS = Set.of(
        "minecraft:diamond_block",
        "minecraft:emerald_block",
        "minecraft:netherite_block"
    );

    private static final Set<String> HIGH_VALUE_IDS = Set.of(
        "minecraft:diamond",
        "minecraft:diamond_block",
        "minecraft:emerald",
        "minecraft:emerald_block",
        "minecraft:elytra",
        "minecraft:totem_of_undying",
        "minecraft:beacon"
    );

    private static final Set<String> MEDIUM_VALUE_IDS = Set.of(
        "minecraft:iron_ingot",
        "minecraft:gold_ingot",
        "minecraft:copper_ingot",
        "minecraft:redstone",
        "minecraft:lapis_lazuli",
        "minecraft:quartz",
        "minecraft:coal",
        "minecraft:charcoal",
        "minecraft:glowstone",
        "minecraft:glowstone_dust",
        "minecraft:obsidian",
        "minecraft:crying_obsidian",
        "minecraft:ender_pearl",
        "minecraft:blaze_rod",
        "minecraft:slime_ball",
        "minecraft:string",
        "minecraft:leather",
        "minecraft:paper"
    );

    private static final Set<String> LOW_TAGS = Set.of(
        "minecraft:logs",
        "minecraft:planks",
        "minecraft:wooden_slabs",
        "minecraft:wooden_stairs",
        "minecraft:wool",
        "minecraft:leaves",
        "minecraft:sand",
        "minecraft:terracotta",
        "minecraft:stairs",
        "minecraft:slabs"
    );

    private static final Set<String> MEDIUM_TAGS = Set.of(
        "minecraft:stone_crafting_materials",
        "minecraft:coals",
        "minecraft:beacon_payment_items"
    );

    private static final List<BundleEntry> BASE_BUNDLE = List.of(
        new BundleEntry(Items.OAK_LOG, 48, BundleCategory.STRUCTURE),
        new BundleEntry(Items.SPRUCE_LOG, 32, BundleCategory.STRUCTURE),
        new BundleEntry(Items.BIRCH_LOG, 32, BundleCategory.STRUCTURE),
        new BundleEntry(Items.COBBLESTONE, 64, BundleCategory.STRUCTURE),
        new BundleEntry(Items.STONE, 48, BundleCategory.STRUCTURE),
        new BundleEntry(Items.WHITE_WOOL, 24, BundleCategory.STRUCTURE),
        new BundleEntry(Items.DIRT, 64, BundleCategory.NATURAL),
        new BundleEntry(Items.SAND, 48, BundleCategory.NATURAL),
        new BundleEntry(Items.GRAVEL, 32, BundleCategory.NATURAL),
        new BundleEntry(Items.CLAY_BALL, 24, BundleCategory.NATURAL),
        new BundleEntry(Items.GLASS, 32, BundleCategory.PROCESSED),
        new BundleEntry(Items.COAL, 32, BundleCategory.PROCESSED),
        new BundleEntry(Items.IRON_INGOT, 24, BundleCategory.PROCESSED),
        new BundleEntry(Items.COPPER_INGOT, 24, BundleCategory.PROCESSED),
        new BundleEntry(Items.REDSTONE, 16, BundleCategory.INDUSTRIAL),
        new BundleEntry(Items.LAPIS_LAZULI, 12, BundleCategory.INDUSTRIAL),
        new BundleEntry(Items.QUARTZ, 16, BundleCategory.INDUSTRIAL)
    );

    private TravelerCatalog() {
    }

    public static void bootstrap() {
        TravelerAddonMod.LOGGER.info(
            "Traveler catalog loaded with {} base bundle entries and {} market items",
            BASE_BUNDLE.size(),
            BuiltInRegistries.ITEM.size()
        );
    }

    public static List<BundleEntry> baseBundle() {
        return BASE_BUNDLE;
    }

    public static List<MarketEntry> searchMarket(final String rawQuery) {
        final String query = normalize(rawQuery);
        final List<MarketEntry> results = new ArrayList<>();

        for (final Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            if (TravelerItemSafety.isBlocked(item)) {
                continue;
            }

            final ItemStack stack = item.getDefaultInstance();
            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }

            final String englishId = id.toString().toLowerCase(Locale.ROOT);
            final String displayName;
            final String translatedName;
            try {
                displayName = stack.getHoverName().getString();
                translatedName = normalize(displayName);
            } catch (final Exception exception) {
                TravelerAddonMod.LOGGER.warn("Traveler skipped market item {} during display-name lookup", id, exception);
                continue;
            }
            final String descriptionId = item.getDescriptionId(stack).toLowerCase(Locale.ROOT);

            if (!query.isEmpty()
                && !englishId.contains(query)
                && !translatedName.contains(query)
                && !descriptionId.contains(query)) {
                continue;
            }

            final PurchaseRule rule = purchaseRuleFor(item);
            results.add(new MarketEntry(
                item,
                displayName,
                englishId,
                rule,
                marketCountFor(item),
                isPurchasable(item)
            ));
        }

        results.sort(
            Comparator.comparing((MarketEntry entry) -> !entry.purchasable())
                .thenComparing(entry -> entry.rule().sortOrder())
                .thenComparing(MarketEntry::displayName)
        );
        return results;
    }

    public static int priceFor(final Item item) {
        final PurchaseRule rule = purchaseRuleFor(item);
        if (rule != PurchaseRule.BLOCKED) {
            return rule.price();
        }

        final ItemStack stack = item.getDefaultInstance();
        final Rarity rarity = stack.getRarity();
        if (rarity == Rarity.EPIC) {
            return EXTREME_PRICE;
        }
        if (rarity == Rarity.RARE) {
            return HIGH_PRICE;
        }
        return LOW_PRICE;
    }

    public static int marketCountFor(final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null && HIGH_VALUE_BLOCK_IDS.contains(id.toString())) {
            return 4;
        }

        final ItemStack stack = item.getDefaultInstance();
        final int maxStackSize = Math.max(1, stack.getMaxStackSize());
        final PurchaseRule rule = purchaseRuleFor(item);
        return switch (rule) {
            case LOW, MEDIUM -> maxStackSize;
            case HIGH -> Math.max(1, maxStackSize / 2);
            case EXTREME -> Math.max(1, (maxStackSize + 1) / 2);
            case BLOCKED -> 0;
        };
    }

    public static boolean isPurchasable(final Item item) {
        return !TravelerItemSafety.isBlocked(item) && purchaseRuleFor(item) != PurchaseRule.BLOCKED;
    }

    public static PurchaseRule purchaseRuleFor(final Item item) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return PurchaseRule.BLOCKED;
        }

        final String idString = id.toString();
        if (!TravelerDomumOrnamentumCompat.canSanitizeForMarket(item)) {
            return PurchaseRule.BLOCKED;
        }

        if (item instanceof SpawnEggItem || BLOCKED_IDS.contains(idString)) {
            return PurchaseRule.BLOCKED;
        }

        final ItemStack stack = item.getDefaultInstance();
        final Rarity rarity = stack.getRarity();
        if (rarity == Rarity.EPIC) {
            return PurchaseRule.EXTREME;
        }
        if (rarity == Rarity.RARE) {
            return PurchaseRule.HIGH;
        }

        if (EXTREME_VALUE_IDS.contains(idString)) {
            return PurchaseRule.EXTREME;
        }

        if (HIGH_VALUE_IDS.contains(idString)) {
            return PurchaseRule.HIGH;
        }

        if (MEDIUM_VALUE_IDS.contains(idString)) {
            return PurchaseRule.MEDIUM;
        }

        if (matchesAnyTag(item, MEDIUM_TAGS)) {
            return PurchaseRule.MEDIUM;
        }

        if (matchesAnyTag(item, LOW_TAGS)) {
            return PurchaseRule.LOW;
        }

        if (idString.endsWith("_pickaxe")
            || idString.endsWith("_axe")
            || idString.endsWith("_shovel")
            || idString.endsWith("_hoe")
            || idString.endsWith("_sword")
            || idString.endsWith("_helmet")
            || idString.endsWith("_chestplate")
            || idString.endsWith("_leggings")
            || idString.endsWith("_boots")
            || idString.endsWith("_horse_armor")
            || idString.contains("music_disc")
            || idString.contains("smithing_template")
            || idString.contains("enchanted_golden_apple")) {
            return PurchaseRule.EXTREME;
        }

        if (idString.endsWith("_block")
            && (idString.contains("diamond") || idString.contains("emerald") || idString.contains("netherite"))) {
            return PurchaseRule.EXTREME;
        }

        if (idString.contains("diamond") || idString.contains("emerald") || idString.contains("netherite")) {
            return PurchaseRule.HIGH;
        }

        if (idString.contains("iron")
            || idString.contains("gold")
            || idString.contains("copper")
            || idString.contains("redstone")
            || idString.contains("lapis")
            || idString.contains("quartz")
            || idString.contains("coal")) {
            return PurchaseRule.MEDIUM;
        }

        return PurchaseRule.LOW;
    }

    private static boolean matchesAnyTag(final Item item, final Set<String> tagIds) {
        final ItemStack stack = item.getDefaultInstance();
        for (final String tagId : tagIds) {
            if (stack.is(tag(tagId))) {
                return true;
            }
        }
        return false;
    }

    private static TagKey<Item> tag(final String path) {
        return TagKey.create(BuiltInRegistries.ITEM.key(), new ResourceLocation(path));
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    public enum BundleCategory {
        STRUCTURE,
        NATURAL,
        PROCESSED,
        INDUSTRIAL
    }

    public enum PurchaseRule {
        BLOCKED(99, 0),
        LOW(0, LOW_PRICE),
        MEDIUM(1, MEDIUM_PRICE),
        HIGH(2, HIGH_PRICE),
        EXTREME(3, EXTREME_PRICE);

        private final int sortOrder;
        private final int price;

        PurchaseRule(final int sortOrder, final int price) {
            this.sortOrder = sortOrder;
            this.price = price;
        }

        public int sortOrder() {
            return sortOrder;
        }

        public int price() {
            return price;
        }
    }

    public record BundleEntry(Item item, int count, BundleCategory category) {
    }

    public record MarketEntry(
        Item item,
        String displayName,
        String registryName,
        PurchaseRule rule,
        int saleCount,
        boolean purchasable
    ) {
    }
}
