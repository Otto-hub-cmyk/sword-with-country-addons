package com.minecolonies.autorecipes.mixin;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.autorecipes.AutoRecipeHelper;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractCraftingProductionResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractCraftingProductionResolver.class)
public abstract class AbstractCraftingProductionResolverMixin {
    @Inject(method = "attemptResolveForBuildingAndStack", at = @At("HEAD"))
    private void autoRecipes$teachDoForProductionAttempt(
        final IRequestManager manager,
        final AbstractBuilding building,
        final ItemStack stack,
        final int count,
        final int minCount,
        final IToken<?> recipeId,
        final CallbackInfoReturnable<List<?>> cir
    ) {
        AutoRecipeHelper.teachDoRecipeForStack(building, stack);
    }
}
