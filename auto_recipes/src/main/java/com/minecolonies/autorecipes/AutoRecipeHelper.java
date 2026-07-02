package com.minecolonies.autorecipes;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.IRecipeManager;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.ModRecipeTypes;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.api.util.OptionalPredicate;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.AbstractDOCraftingBuildingModule;
import com.minecolonies.core.colony.crafting.CustomRecipe;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import com.minecolonies.core.colony.crafting.RecipeAnalyzer;
import com.minecolonies.core.util.DomumOrnamentumUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class AutoRecipeHelper {
    public static final String AUTO_RECIPES_DISCOVERED_TAG = "auto_recipes_discovered";
    public static final String AUTO_RECIPES_DO_INPUTS_TAG = "auto_recipes_do_inputs";
    public static final int DO_RECIPE_HARD_LIMIT = 16384;
    private static final int DO_IMPORT_BATCH_SIZE = 1;
    private static final int DO_IMPORT_MAX_COMPONENTS = 2;
    private static final long DO_IMPORT_INTERVAL_TICKS = 20L;

    private AutoRecipeHelper() {
    }

    public static int getDoRecipeHardLimit() {
        return DO_RECIPE_HARD_LIMIT;
    }

    public static void tickDoAutoDiscovery(final com.minecolonies.api.colony.IColony colony) {
        if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide()) {
            return;
        }

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            if (building == null || !isEligibleBuilding(building)) {
                continue;
            }

            for (final ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class)) {
                if (module instanceof final AbstractCraftingBuildingModule craftingModule
                    && module instanceof AbstractDOCraftingBuildingModule) {
                    discoverAllDoRecipes(craftingModule, building);
                }
            }
        }
    }

    public static void discoverIfEligible(final AbstractCraftingBuildingModule module, final IBuilding building) {
        if (module instanceof AbstractDOCraftingBuildingModule) {
            discoverAllDoRecipes(module, building);
            return;
        }

        if (!isEligibleBuilding(building) || hasDiscoveryMarker(module) || !isModuleReadyForDiscovery(module)) {
            return;
        }

        try {
            if (building == null || building.getColony() == null) {
                return;
            }

            final Level level = building.getColony().getWorld();
            if (level == null || level.isClientSide()) {
                return;
            }

            final MinecraftServer server = level.getServer();
            if (server == null) {
                return;
            }

            final RecipeManager recipeManager = server.getRecipeManager();
            if (recipeManager == null) {
                return;
            }

            final Map vanillaRecipes = RecipeAnalyzer.buildVanillaRecipesMap(recipeManager, level);
            final List<IGenericRecipe> recipes = RecipeAnalyzer.findRecipes(vanillaRecipes, module, level);
            final IRecipeManager colonyRecipeManager = IColonyManager.getInstance().getRecipeManager();
            boolean addedAny = false;

            for (final IGenericRecipe genericRecipe : recipes) {
                final IRecipeStorage storage = toStorage(genericRecipe);
                if (storage == null || storage.getPrimaryOutput() == null || storage.getPrimaryOutput().isEmpty()) {
                    continue;
                }

                final var token = colonyRecipeManager.checkOrAddRecipe(storage);
                if (token != null && !module.getRecipes().contains(token)) {
                    module.addRecipeToList(token, false);
                    module.handleRecipeUpdate(token);
                    addedAny = true;
                }
            }

            if (addedAny || !recipes.isEmpty()) {
                markDiscoveryComplete(module);
            }
        } catch (final Exception ignored) {
        }
    }

    public static void teachDoRecipesForRequest(final IBuilding building, final IDeliverable deliverable) {
        if (building == null || deliverable == null || !isEligibleBuilding(building)) {
            return;
        }

        try {
            if (deliverable instanceof IConcreteDeliverable concreteDeliverable) {
                for (final ItemStack requestedStack : concreteDeliverable.getRequestedItems()) {
                    teachDoRecipeForStack(building, requestedStack);
                }
                return;
            }

            final ItemStack result = deliverable.getResult();
            if (result != null && !result.isEmpty()) {
                teachDoRecipeForStack(building, result);
            }
        } catch (final Exception ignored) {
        }
    }

    public static void teachDoRecipeForStack(final IBuilding building, final ItemStack requestedStack) {
        if (building == null || requestedStack == null || requestedStack.isEmpty() || !isEligibleBuilding(building)) {
            return;
        }

        try {
            for (final ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class)) {
                if (module instanceof AbstractCraftingBuildingModule craftingModule
                    && module instanceof AbstractDOCraftingBuildingModule) {
                    teachDoRecipeForStack(craftingModule, building, requestedStack);
                }
            }
        } catch (final Exception ignored) {
        }
    }

    public static void teachDoRecipeForStack(
        final AbstractCraftingBuildingModule module,
        final IBuilding building,
        final ItemStack requestedStack
    ) {
        if (!(module instanceof AbstractDOCraftingBuildingModule)
            || building == null
            || requestedStack == null
            || requestedStack.isEmpty()
            || !isEligibleBuilding(building)
            || !isModuleReadyForDiscovery(module)) {
            return;
        }

        try {
            if (building.getColony() == null) {
                return;
            }

            final Level level = building.getColony().getWorld();
            if (level == null || level.isClientSide() || level.getServer() == null) {
                return;
            }

            final IRecipeStorage storage = createDoRequestRecipe(requestedStack, module, level.getServer().getRecipeManager(), level);
            if (storage == null) {
                return;
            }

            final var token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
            if (token != null && !module.getRecipes().contains(token)) {
                module.addRecipeToList(token, false);
                module.handleRecipeUpdate(token);
                module.markDirty();
            }
        } catch (final Exception ignored) {
        }
    }

    public static void discoverAllDoRecipes(final AbstractCraftingBuildingModule module, final IBuilding building) {
        if (module == null
            || building == null
            || building.getColony() == null
            || !isEligibleBuilding(building)
            || !isModuleReadyForDiscovery(module)) {
            return;
        }

        try {
            final Level level = building.getColony().getWorld();
            if (level == null || level.isClientSide()) {
                return;
            }
            if (level.getGameTime() % DO_IMPORT_INTERVAL_TICKS != 0L) {
                return;
            }

            AutoRecipesMod.LOGGER.info(
                "[auto_recipes] DO import entry: module={} buildingLevel={} discovered={} knownRecipes={} gameTime={}",
                module.getId(),
                building.getBuildingLevelEquivalent(),
                hasDiscoveryMarker(module),
                module.getRecipes().size(),
                level.getGameTime()
            );

            final int[] addedThisRound = {0};
            final boolean[] stoppedForBatch = {false};
            final int[] scannedPatterns = {0};
            final RecipeManager recipeManager = level.getServer() != null ? level.getServer().getRecipeManager() : null;
            final CustomRecipeManager customRecipeManager = CustomRecipeManager.getInstance();
            if (recipeManager == null) {
                AutoRecipesMod.LOGGER.warn("[auto_recipes] DO import skipped: recipe manager missing for module={}", module.getId());
                return;
            }

            restorePersistedDoRecipes(module, recipeManager, level, customRecipeManager);

            final int knownCustomRecipes = customRecipeManager.getRecipes(module.getCustomRecipeKey()).size();
            if (knownCustomRecipes >= DO_RECIPE_HARD_LIMIT) {
                module.checkForWorkerSpecificRecipes();
                AutoRecipesMod.LOGGER.info(
                    "[auto_recipes] DO import hard-limit reached: module={} learnedRecipes={} customRecipes={} limit={}",
                    module.getId(),
                    module.getRecipes().size(),
                    knownCustomRecipes,
                    DO_RECIPE_HARD_LIMIT
                );
                markDiscoveryComplete(module);
                return;
            }

            AutoRecipesMod.LOGGER.info(
                "[auto_recipes] DO import tick: module={} buildingLevel={} learnedRecipes={} customRecipes={} maxTarget={} gameTime={}",
                module.getId(),
                building.getBuildingLevelEquivalent(),
                module.getRecipes().size(),
                knownCustomRecipes,
                DO_RECIPE_HARD_LIMIT,
                level.getGameTime()
            );

            final Predicate<ItemStack> validator = module.getIngredientValidator().orElse(false);
            final List<DoInputPattern> patterns = new ArrayList<>();
            final Set<String> processedPatterns = new HashSet<>();
            for (final com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe recipe :
                recipeManager.getAllRecipesFor(com.ldtteam.domumornamentum.recipe.ModRecipeTypes.ARCHITECTS_CUTTER.get())) {
                final DoInputPattern pattern = createDoInputPattern(recipe, validator);
                if (pattern == null || !processedPatterns.add(pattern.signature())) {
                    continue;
                }

                patterns.add(pattern);
            }

            patterns.sort(
                Comparator.comparingInt((DoInputPattern pattern) -> pattern.candidateBlocks().size())
                    .reversed()
                    .thenComparing(DoInputPattern::signature)
            );
            scannedPatterns[0] = patterns.size();

            for (final DoInputPattern pattern : patterns) {
                registerDoPatternRecipes(pattern, recipeManager, level, customRecipeManager, module, addedThisRound, stoppedForBatch);
                if (stoppedForBatch[0]) {
                    break;
                }
            }

            AutoRecipesMod.LOGGER.info(
                "[auto_recipes] DO import summary: module={} scannedPatterns={} addedThisRound={} learnedRecipes={} customRecipes={} stoppedForBatch={}",
                module.getId(),
                scannedPatterns[0],
                addedThisRound[0],
                module.getRecipes().size(),
                customRecipeManager.getRecipes(module.getCustomRecipeKey()).size(),
                stoppedForBatch[0]
            );

            module.checkForWorkerSpecificRecipes();

            if (customRecipeManager.getRecipes(module.getCustomRecipeKey()).size() >= DO_RECIPE_HARD_LIMIT
                || (!stoppedForBatch[0] && addedThisRound[0] == 0)) {
                markDiscoveryComplete(module);
            }

            if (addedThisRound[0] > 0) {
                module.markDirty();
            }
        } catch (final Exception ex) {
            AutoRecipesMod.LOGGER.error("[auto_recipes] DO import crashed for module={}", module.getId(), ex);
        }
    }

    public static void serializeMarker(final CompoundTag tag, final boolean discovered) {
        tag.putBoolean(AUTO_RECIPES_DISCOVERED_TAG, discovered);
    }

    public static void serializeState(final CompoundTag tag, final boolean discovered, final Set<String> persistedDoInputs) {
        serializeMarker(tag, discovered);
        final ListTag inputList = new ListTag();
        for (final String key : persistedDoInputs) {
            inputList.add(StringTag.valueOf(key));
        }
        tag.put(AUTO_RECIPES_DO_INPUTS_TAG, inputList);
    }

    public static boolean deserializeMarker(final CompoundTag tag) {
        return tag.contains(AUTO_RECIPES_DISCOVERED_TAG) && tag.getBoolean(AUTO_RECIPES_DISCOVERED_TAG);
    }

    public static Set<String> deserializePersistedDoInputs(final CompoundTag tag) {
        final Set<String> values = new LinkedHashSet<>();
        if (!tag.contains(AUTO_RECIPES_DO_INPUTS_TAG, Tag.TAG_LIST)) {
            return values;
        }

        final ListTag list = tag.getList(AUTO_RECIPES_DO_INPUTS_TAG, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            final String entry = list.getString(i);
            if (!entry.isBlank()) {
                values.add(entry);
            }
        }
        return values;
    }

    private static boolean isEligibleBuilding(final IBuilding building) {
        if (building == null) {
            return false;
        }
        return building.getBuildingLevelEquivalent() >= AutoRecipesConfig.UNLOCK_LEVEL.get();
    }

    private static boolean hasDiscoveryMarker(final AbstractCraftingBuildingModule module) {
        return module instanceof AutoRecipeState state && state.autoRecipes$isDiscovered();
    }

    private static boolean isModuleReadyForDiscovery(final AbstractCraftingBuildingModule module) {
        return module != null && !module.getSupportedCraftingTypes().isEmpty();
    }

    private static void markDiscoveryComplete(final AbstractCraftingBuildingModule module) {
        if (module instanceof AutoRecipeState state) {
            state.autoRecipes$setDiscovered(true);
            module.markDirty();
        }
    }

    private static IRecipeStorage toStorage(final IGenericRecipe recipe) {
        final var builder = RecipeStorage.builder();
        builder.withGridSize(recipe.getGridSize());
        builder.withPrimaryOutput(recipe.getPrimaryOutput());
        builder.withIntermediate(recipe.getIntermediate());
        builder.withLootTable(recipe.getLootTable());

        final List<com.minecolonies.api.crafting.ItemStorage> inputs = new ArrayList<>();
        for (final List<ItemStack> optionList : recipe.getInputs()) {
            if (optionList == null || optionList.isEmpty()) {
                continue;
            }
            inputs.add(new com.minecolonies.api.crafting.ItemStorage(optionList.get(0)));
        }

        builder.withInputs(inputs);
        return builder.build();
    }

    private static Collection<IRequest<?>> collectOpenRequests(final IBuilding building) {
        final Set<IRequest<?>> requests = new LinkedHashSet<>(building.getOpenRequests(-1));
        for (final ICitizenData citizen : building.getAllAssignedCitizen()) {
            requests.addAll(building.getOpenRequests(citizen.getId()));
        }
        return requests;
    }

    private static IRecipeStorage createDoRequestRecipe(
        final ItemStack requestedStack,
        final AbstractCraftingBuildingModule module,
        final RecipeManager recipeManager,
        final Level level
    ) {
        final IMateriallyTexturedBlock doBlock = DomumOrnamentumUtils.getBlock(requestedStack);
        if (doBlock == null) {
            AutoRecipesMod.LOGGER.info("[auto_recipes] DO request recipe skipped: no DO block for {}", describeStack(requestedStack));
            return null;
        }

        final MaterialTextureData textureData = DomumOrnamentumUtils.getTextureData(requestedStack);
        if (textureData.isEmpty()) {
            AutoRecipesMod.LOGGER.info("[auto_recipes] DO request recipe skipped: empty textureData for {}", describeStack(requestedStack));
            return null;
        }

        final Predicate<ItemStack> validator = module.getIngredientValidator().orElse(false);
        final List<Block> currentInputs = new ArrayList<>();
        boolean hasCompatibleIngredient = false;
        for (final IMateriallyTexturedBlockComponent component : doBlock.getComponents()) {
            final Block block = textureData.getTexturedComponents().get(component.getId());
            if (block == null || block.asItem().getDefaultInstance().isEmpty()) {
                if (component.isOptional()) {
                    continue;
                }
                AutoRecipesMod.LOGGER.info(
                    "[auto_recipes] DO request recipe skipped: missing required component {} for {}",
                    component.getId(),
                    describeStack(requestedStack)
                );
                return null;
            }

            final ItemStack input = new ItemStack(block);
            input.setCount(1);
            if (validator.test(input)) {
                hasCompatibleIngredient = true;
            }
            currentInputs.add(block);
        }

        if (currentInputs.isEmpty() || !hasCompatibleIngredient) {
            AutoRecipesMod.LOGGER.info(
                "[auto_recipes] DO request recipe skipped: no compatible ingredient for {} inputs={}",
                describeStack(requestedStack),
                describeBlocks(currentInputs)
            );
            return null;
        }

        return createDoConcreteRecipe(currentInputs, recipeManager, level);
    }

    private static DoInputPattern createDoInputPattern(
        final com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe recipe,
        final Predicate<ItemStack> validator
    ) {
        final Block generatedBlock = ForgeRegistries.BLOCKS.getValue(recipe.getBlockName());
        if (!(generatedBlock instanceof final IMateriallyTexturedBlock doBlock)) {
            return null;
        }

        final List<List<Block>> candidateBlocks = new ArrayList<>();
        final StringBuilder signature = new StringBuilder();
        for (final IMateriallyTexturedBlockComponent component : doBlock.getComponents()) {
            final List<Block> validBlocks = ForgeRegistries.BLOCKS.tags()
                .getTag(component.getValidSkins())
                .stream()
                .filter(block -> !block.asItem().getDefaultInstance().isEmpty())
                .filter(block -> validator.test(new ItemStack(block)))
                .sorted(Comparator.comparing(AutoRecipeHelper::blockSortKey))
                .toList();

            if (validBlocks.isEmpty()) {
                AutoRecipesMod.LOGGER.info(
                    "[auto_recipes] DO pattern rejected: block={} componentTag={} has no valid blocks for this module",
                    recipe.getBlockName(),
                    component.getValidSkins().location()
                );
                return null;
            }

            candidateBlocks.add(validBlocks);
            signature
                .append(component.getValidSkins().location())
                .append('|')
                .append(component.isOptional())
                .append(';');
        }

        if (candidateBlocks.isEmpty()) {
            return null;
        }

        if (candidateBlocks.size() > DO_IMPORT_MAX_COMPONENTS) {
            return null;
        }

        return new DoInputPattern(candidateBlocks, signature.toString());
    }

    private static void registerDoPatternRecipes(
        final DoInputPattern pattern,
        final RecipeManager recipeManager,
        final Level level,
        final CustomRecipeManager customRecipeManager,
        final AbstractCraftingBuildingModule module,
        final int[] addedThisRound,
        final boolean[] stoppedForBatch
    ) {
        registerDoPatternRecipes(pattern, recipeManager, level, customRecipeManager, module, 0, new ArrayList<>(), addedThisRound, stoppedForBatch);
    }

    private static void registerDoPatternRecipes(
        final DoInputPattern pattern,
        final RecipeManager recipeManager,
        final Level level,
        final CustomRecipeManager customRecipeManager,
        final AbstractCraftingBuildingModule module,
        final int index,
        final List<Block> currentInputs,
        final int[] addedThisRound,
        final boolean[] stoppedForBatch
    ) {
        if (stoppedForBatch[0]
            || addedThisRound[0] >= DO_IMPORT_BATCH_SIZE
            || CustomRecipeManager.getInstance().getRecipes(module.getCustomRecipeKey()).size() >= DO_RECIPE_HARD_LIMIT) {
            stoppedForBatch[0] = true;
            return;
        }

        if (index >= pattern.candidateBlocks().size()) {
            final IRecipeStorage storage = createDoConcreteRecipe(currentInputs, recipeManager, level);
            if (storage == null) {
                AutoRecipesMod.LOGGER.info(
                    "[auto_recipes] DO concrete recipe rejected: inputs={}",
                    describeBlocks(currentInputs)
                );
                return;
            }

            final CustomRecipe customRecipe = toDoCustomRecipe(module, storage);
            final Map<ResourceLocation, CustomRecipe> existing = customRecipeManager.getAllRecipes()
                .computeIfAbsent(module.getCustomRecipeKey(), key -> new java.util.HashMap<>());
            final ResourceLocation recipeId = customRecipe.getRecipeId();
            if (recipeId != null && !existing.containsKey(recipeId)) {
                customRecipeManager.addRecipe(customRecipe);
                rememberPersistedDoInputs(module, storage);
                addedThisRound[0]++;
                AutoRecipesMod.LOGGER.info(
                    "[auto_recipes] DO custom recipe registered: module={} recipeId={} output={} inputs={} customRecipes={}",
                    module.getId(),
                    recipeId,
                    describeStack(customRecipe.getPrimaryOutput()),
                    describeItemStorages(storage.getCleanedInput()),
                    customRecipeManager.getRecipes(module.getCustomRecipeKey()).size()
                );
                if (addedThisRound[0] >= DO_IMPORT_BATCH_SIZE
                    || customRecipeManager.getRecipes(module.getCustomRecipeKey()).size() >= DO_RECIPE_HARD_LIMIT) {
                    stoppedForBatch[0] = true;
                }
            }
            return;
        }

        for (final Block block : pattern.candidateBlocks().get(index)) {
            if (stoppedForBatch[0]) {
                return;
            }
            currentInputs.add(block);
            registerDoPatternRecipes(pattern, recipeManager, level, customRecipeManager, module, index + 1, currentInputs, addedThisRound, stoppedForBatch);
            currentInputs.remove(currentInputs.size() - 1);
        }
    }

    private static IRecipeStorage createDoConcreteRecipe(
        final List<Block> currentInputs,
        final RecipeManager recipeManager,
        final Level level
    ) {
        final List<ItemStorage> inputs = new ArrayList<>();
        final Container inputInventory = new SimpleContainer(Math.max(3, currentInputs.size()));
        for (int i = 0; i < currentInputs.size(); i++) {
            final ItemStack inputStack = new ItemStack(currentInputs.get(i));
            inputStack.setCount(1);
            inputInventory.setItem(i, inputStack.copy());
            inputs.add(new ItemStorage(inputStack));
        }

        final List<? extends net.minecraft.world.item.crafting.Recipe<Container>> matchedRecipesRaw =
            recipeManager.getRecipesFor(com.ldtteam.domumornamentum.recipe.ModRecipeTypes.ARCHITECTS_CUTTER.get(), inputInventory, level);
        if (matchedRecipesRaw.isEmpty()) {
            AutoRecipesMod.LOGGER.info(
                "[auto_recipes] DO concrete recipe found no architects_cutter match for inputs={}",
                describeBlocks(currentInputs)
            );
        }

        final List<ItemStack> groupedOutputs = new ArrayList<>();
        for (final net.minecraft.world.item.crafting.Recipe<Container> rawRecipe : matchedRecipesRaw) {
            if (!(rawRecipe instanceof com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe recipe)) {
                continue;
            }

            final ItemStack assembled = recipe.assemble(inputInventory, level.registryAccess()).copy();
            final IMateriallyTexturedBlock assembledBlock = DomumOrnamentumUtils.getBlock(assembled);
            if (assembledBlock != null && assembledBlock.getComponents().size() == inputs.size()) {
                groupedOutputs.add(assembled);
            }
        }

        if (groupedOutputs.isEmpty()) {
            AutoRecipesMod.LOGGER.info(
                "[auto_recipes] DO concrete recipe produced no grouped outputs for inputs={}",
                describeBlocks(currentInputs)
            );
            return null;
        }

        final List<ItemStack> normalizedOutputs = normalizeDoOutputs(groupedOutputs);
        if (normalizedOutputs.isEmpty()) {
            return null;
        }

        final ItemStack output = normalizedOutputs.get(0).copy();

        final List<ItemStack> alternateOutputs = new ArrayList<>();
        for (int i = 1; i < normalizedOutputs.size(); i++) {
            alternateOutputs.add(normalizedOutputs.get(i).copy());
        }

        return RecipeStorage.builder()
            .withInputs(inputs)
            .withPrimaryOutput(output)
            .withAlternateOutputs(alternateOutputs)
            .withGridSize(3)
            .withRecipeType(ModRecipeTypes.MULTI_OUTPUT_ID)
            .withRecipeId(new ResourceLocation(AutoRecipesMod.MOD_ID, "do_request/" + sanitizeRecipePathFromInputs(inputs)))
            .build();
    }

    private static void restorePersistedDoRecipes(
        final AbstractCraftingBuildingModule module,
        final RecipeManager recipeManager,
        final Level level,
        final CustomRecipeManager customRecipeManager
    ) {
        if (!(module instanceof AutoRecipeState state)) {
            return;
        }

        final Set<String> persistedInputs = state.autoRecipes$getPersistedDoInputs();
        if (persistedInputs.isEmpty()) {
            return;
        }

        final Map<ResourceLocation, CustomRecipe> existing = customRecipeManager.getAllRecipes()
            .computeIfAbsent(module.getCustomRecipeKey(), key -> new java.util.HashMap<>());

        boolean restoredAny = false;
        for (final String inputKey : persistedInputs) {
            final List<Block> blocks = parsePersistedInputKey(inputKey);
            if (blocks.isEmpty()) {
                continue;
            }

            final IRecipeStorage storage = createDoConcreteRecipe(blocks, recipeManager, level);
            if (storage == null || storage.getRecipeSource() == null) {
                continue;
            }

            if (existing.containsKey(storage.getRecipeSource())) {
                continue;
            }

            customRecipeManager.addRecipe(toDoCustomRecipe(module, storage));
            restoredAny = true;
        }

        if (restoredAny) {
            module.checkForWorkerSpecificRecipes();
            module.markDirty();
        }
    }

    private static CustomRecipe toDoCustomRecipe(final AbstractCraftingBuildingModule module, final IRecipeStorage storage) {
        final List<ItemStorage> inputs = new ArrayList<>();
        for (final ItemStorage input : storage.getCleanedInput()) {
            inputs.add(input.copy().toImmutable());
        }

        final List<ItemStack> secondaryOutputs = new ArrayList<>();
        for (final ItemStack stack : storage.getSecondaryOutputs()) {
            secondaryOutputs.add(stack.copy());
        }

        final List<ItemStack> alternateOutputs = new ArrayList<>();
        for (final ItemStack stack : storage.getAlternateOutputs()) {
            alternateOutputs.add(stack.copy());
        }

        return new CustomRecipe(
            module.getCustomRecipeKey(),
            AutoRecipesConfig.UNLOCK_LEVEL.get(),
            5,
            false,
            false,
            Objects.requireNonNull(storage.getRecipeSource()),
            Set.of(),
            Set.of(),
            storage.getLootTable(),
            storage.getRequiredTool(),
            inputs,
            storage.getPrimaryOutput().copy(),
            secondaryOutputs,
            alternateOutputs,
            storage.getIntermediate()
        );
    }

    private static String sanitizeRecipePathFromInputs(final List<ItemStorage> inputs) {
        final StringBuilder builder = new StringBuilder("inputs");
        for (final ItemStorage input : inputs) {
            final ResourceLocation key = ForgeRegistries.ITEMS.getKey(input.getItem());
            final String part = key == null ? "unknown" : key.getNamespace() + "_" + key.getPath();
            builder.append('_').append(part.replace(':', '_').replace('/', '_'));
        }
        return builder.toString();
    }

    private static void rememberPersistedDoInputs(final AbstractCraftingBuildingModule module, final IRecipeStorage storage) {
        if (!(module instanceof AutoRecipeState state)) {
            return;
        }

        final Set<String> persisted = new LinkedHashSet<>(state.autoRecipes$getPersistedDoInputs());
        final String key = toPersistedInputKey(storage.getCleanedInput());
        if (!key.isBlank() && persisted.add(key)) {
            state.autoRecipes$setPersistedDoInputs(persisted);
            module.markDirty();
        }
    }

    private static String toPersistedInputKey(final List<ItemStorage> inputs) {
        return inputs.stream()
            .map(ItemStorage::getItem)
            .map(ForgeRegistries.ITEMS::getKey)
            .filter(Objects::nonNull)
            .map(id -> id.toString())
            .collect(Collectors.joining("|"));
    }

    private static List<Block> parsePersistedInputKey(final String key) {
        final List<Block> blocks = new ArrayList<>();
        if (key == null || key.isBlank()) {
            return blocks;
        }

        for (final String part : key.split("\\|")) {
            if (part.isBlank()) {
                return List.of();
            }
            final ResourceLocation itemId = ResourceLocation.tryParse(part);
            if (itemId == null) {
                return List.of();
            }
            final var item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item.getDefaultInstance().isEmpty()) {
                return List.of();
            }
            final Block block = Block.byItem(item);
            if (block == null || block.asItem().getDefaultInstance().isEmpty()) {
                return List.of();
            }
            blocks.add(block);
        }

        return blocks;
    }

    private static boolean sameItemAndNbt(final ItemStack first, final ItemStack second) {
        if (first.getItem() != second.getItem()) {
            return false;
        }
        if (first.getCount() != second.getCount()) {
            return false;
        }
        if (!first.hasTag() && !second.hasTag()) {
            return true;
        }
        return java.util.Objects.equals(first.getTag(), second.getTag());
    }

    private static List<ItemStack> normalizeDoOutputs(final List<ItemStack> groupedOutputs) {
        final List<ItemStack> uniqueOutputs = new ArrayList<>();
        for (final ItemStack rawOutput : groupedOutputs) {
            if (rawOutput == null || rawOutput.isEmpty()) {
                continue;
            }

            final ItemStack normalized = rawOutput.copy();
            normalized.setCount(Math.max(1, normalized.getCount()));

            boolean duplicate = false;
            for (final ItemStack existing : uniqueOutputs) {
                if (sameItemAndNbt(existing, normalized)) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                uniqueOutputs.add(normalized);
            }
        }

        uniqueOutputs.sort(Comparator
            .comparing(AutoRecipeHelper::stackSortKey)
            .thenComparingInt(ItemStack::getCount));
        return uniqueOutputs;
    }

    private static String stackSortKey(final ItemStack stack) {
        final ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        final String itemKey = key == null ? "unknown:unknown" : key.toString();
        final String tagKey = stack.hasTag() ? stack.getTag().toString() : "";
        return itemKey + "|" + tagKey;
    }

    private static String blockSortKey(final Block block) {
        final ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? "unknown:unknown" : key.toString();
    }

    private static String describeStack(final ItemStack stack) {
        final ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        final String itemKey = key == null ? "unknown:unknown" : key.toString();
        final String tag = stack.hasTag() ? stack.getTag().toString() : "";
        return itemKey + " x" + stack.getCount() + (tag.isEmpty() ? "" : " " + tag);
    }

    private static String describeBlocks(final List<Block> blocks) {
        final List<String> keys = new ArrayList<>();
        for (final Block block : blocks) {
            keys.add(blockSortKey(block));
        }
        return keys.toString();
    }

    private static String describeItemStorages(final List<ItemStorage> storages) {
        final List<String> parts = new ArrayList<>();
        for (final ItemStorage storage : storages) {
            parts.add(describeStack(storage.getItemStack()));
        }
        return parts.toString();
    }

    private record DoInputPattern(List<List<Block>> candidateBlocks, String signature) {
    }
}
