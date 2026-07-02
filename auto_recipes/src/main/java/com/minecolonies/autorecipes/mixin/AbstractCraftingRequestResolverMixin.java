package com.minecolonies.autorecipes.mixin;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.autorecipes.AutoRecipeHelper;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.requestsystem.resolvers.core.AbstractCraftingRequestResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractCraftingRequestResolver.class)
public abstract class AbstractCraftingRequestResolverMixin {
    @Inject(method = "canResolveForBuilding", at = @At("HEAD"))
    private void autoRecipes$teachDoForCanResolve(
        final IRequestManager manager,
        final IRequest<? extends IDeliverable> request,
        final AbstractBuilding building,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        AutoRecipeHelper.teachDoRecipesForRequest(building, request.getRequest());
    }

    @Inject(method = "attemptResolveForBuilding", at = @At("HEAD"))
    private void autoRecipes$teachDoForAttemptResolve(
        final IRequestManager manager,
        final IRequest<? extends IDeliverable> request,
        final AbstractBuilding building,
        final CallbackInfoReturnable<List<?>> cir
    ) {
        AutoRecipeHelper.teachDoRecipesForRequest(building, request.getRequest());
    }
}
