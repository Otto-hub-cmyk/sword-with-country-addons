package com.minecolonies.autorecipes.mixin;

import com.minecolonies.autorecipes.AutoRecipeHelper;
import com.minecolonies.autorecipes.AutoRecipesConfig;
import com.minecolonies.autorecipes.AutoRecipeState;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.AbstractDOCraftingBuildingModule;
import net.minecraft.nbt.CompoundTag;
import java.util.LinkedHashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractCraftingBuildingModule.class)
public abstract class AbstractCraftingBuildingModuleMixin implements AutoRecipeState {
    @Shadow protected AbstractBuilding building;

    @Unique
    private boolean autoRecipes$discovered;

    @Unique
    private Set<String> autoRecipes$persistedDoInputs = new LinkedHashSet<>();

    @Inject(method = "checkForWorkerSpecificRecipes", at = @At("TAIL"))
    private void autoRecipes$discoverAllAtConfiguredLevel(final CallbackInfo ci) {
        final AbstractCraftingBuildingModule module = (AbstractCraftingBuildingModule) (Object) this;
        if (!(module instanceof AbstractDOCraftingBuildingModule)) {
            AutoRecipeHelper.discoverIfEligible(module, this.building);
        }
    }

    @Inject(method = "onColonyTick", at = @At("TAIL"))
    private void autoRecipes$discoverOnColonyTick(final CallbackInfo ci) {
        final AbstractCraftingBuildingModule module = (AbstractCraftingBuildingModule) (Object) this;
        if (!(module instanceof AbstractDOCraftingBuildingModule)) {
            AutoRecipeHelper.discoverIfEligible(module, this.building);
        }
    }

    @Inject(method = "serializeNBT", at = @At("TAIL"))
    private void autoRecipes$writeMarker(final CompoundTag tag, final CallbackInfo ci) {
        AutoRecipeHelper.serializeState(tag, this.autoRecipes$discovered, this.autoRecipes$persistedDoInputs);
    }

    @Inject(method = "deserializeNBT", at = @At("TAIL"))
    private void autoRecipes$readMarker(final CompoundTag tag, final CallbackInfo ci) {
        this.autoRecipes$discovered = AutoRecipeHelper.deserializeMarker(tag);
        this.autoRecipes$persistedDoInputs = AutoRecipeHelper.deserializePersistedDoInputs(tag);
    }

    @Inject(method = "getMaxRecipes", at = @At("RETURN"), cancellable = true)
    private void autoRecipes$raiseRecipeCap(final CallbackInfoReturnable<Integer> cir) {
        final AbstractCraftingBuildingModule module = (AbstractCraftingBuildingModule) (Object) this;
        final int original = cir.getReturnValue();
        if (module instanceof AbstractDOCraftingBuildingModule
            && this.building != null
            && this.building.getBuildingLevelEquivalent() >= AutoRecipesConfig.UNLOCK_LEVEL.get()) {
            cir.setReturnValue(Math.max(original, AutoRecipeHelper.getDoRecipeHardLimit()));
            return;
        }

        if (!this.autoRecipes$discovered) {
            return;
        }

        final int learnedRecipes = module.getRecipes().size();
        if (learnedRecipes <= original) {
            return;
        }

        cir.setReturnValue(Math.max(original, learnedRecipes));
    }

    @Override
    public boolean autoRecipes$isDiscovered() {
        return this.autoRecipes$discovered;
    }

    @Override
    public void autoRecipes$setDiscovered(final boolean discovered) {
        this.autoRecipes$discovered = discovered;
    }

    @Override
    public Set<String> autoRecipes$getPersistedDoInputs() {
        return this.autoRecipes$persistedDoInputs;
    }

    @Override
    public void autoRecipes$setPersistedDoInputs(final Set<String> persistedDoInputs) {
        this.autoRecipes$persistedDoInputs = new LinkedHashSet<>(persistedDoInputs);
    }
}
