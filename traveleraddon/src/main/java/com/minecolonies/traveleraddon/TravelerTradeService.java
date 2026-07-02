package com.minecolonies.traveleraddon;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.core.tileentities.TileEntityWareHouse;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TravelerTradeService {
    public static final int DEMAND_BUNDLE_PRICE = 35;

    private TravelerTradeService() {
    }

    public static void handleDemandPurchase(final ServerPlayer player, final int travelerEntityId) {
        final TradeContext context = resolveContext(player, travelerEntityId);
        if (context == null) {
            return;
        }

        final int remainingDemandPurchases = context.traveler().getPersistentData().getInt("RemainingDemandPurchases");
        if (remainingDemandPurchases <= 0) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.demand_already_used"), false);
            return;
        }

        final List<ItemStack> demandBundle = collectDemandBundle(context.colony());
        if (demandBundle.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.no_demands"), false);
            return;
        }

        if (!canWarehouseAccept(context.warehouse(), demandBundle)) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.warehouse_full"), false);
            return;
        }

        if (!tryTakeAmethyst(player, DEMAND_BUNDLE_PRICE)) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.not_enough_shards_demand"), false);
            return;
        }

        deliverToWarehouse(context.warehouse(), demandBundle);
        context.traveler().getPersistentData().putInt("RemainingDemandPurchases", remainingDemandPurchases - 1);
        player.displayClientMessage(Component.translatable("message.traveleraddon.demand_purchase_success"), false);
    }

    public static void handleMarketPurchase(final ServerPlayer player, final int travelerEntityId, final String itemId) {
        final TradeContext context = resolveContext(player, travelerEntityId);
        if (context == null) {
            return;
        }

        final int remainingPurchases = context.traveler().getPersistentData().getInt("RemainingMarketPurchases");
        if (remainingPurchases <= 0) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.market_limit_reached"), false);
            return;
        }

        final ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        if (resourceLocation == null || !BuiltInRegistries.ITEM.containsKey(resourceLocation)) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.market_item_missing"), false);
            return;
        }

        final Item item = BuiltInRegistries.ITEM.get(resourceLocation);
        if (!TravelerCatalog.isPurchasable(item)) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.market_item_blocked"), false);
            return;
        }

        final int orderCount = TravelerCatalog.marketCountFor(item);
        if (orderCount <= 0) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.market_item_blocked"), false);
            return;
        }

        final ItemStack order = TravelerItemSafety.createSafeMarketStack(item, orderCount);
        if (order.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.market_item_blocked"), false);
            return;
        }
        final int price = TravelerCatalog.priceFor(item);

        if (!canWarehouseAccept(context.warehouse(), List.of(order))) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.warehouse_full"), false);
            return;
        }

        if (!tryTakeAmethyst(player, price)) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.not_enough_shards_market", price), false);
            return;
        }

        deliverToWarehouse(context.warehouse(), List.of(order));
        context.traveler().getPersistentData().putInt("RemainingMarketPurchases", remainingPurchases - 1);
        player.displayClientMessage(
            Component.translatable("message.traveleraddon.market_purchase_success", order.getHoverName(), price),
            false
        );
    }

    public static List<ItemStack> collectDemandBundle(final IColony colony) {
        final Map<String, ItemStack> merged = new LinkedHashMap<>();
        final IRequestManager requestManager = colony.getRequestManager();
        if (requestManager == null) {
            return List.of();
        }

        for (final IRequest<?> request : collectTopLevelRequests(requestManager)) {
            if (!isActiveRequest(request)) {
                continue;
            }

            final Object requestable = request.getRequest();
            if (!(requestable instanceof IDeliverable deliverable)) {
                continue;
            }

            final Map<String, ItemStack> targetStacks = new LinkedHashMap<>();
            if (requestable instanceof IConcreteDeliverable concrete) {
                for (final ItemStack stack : concrete.getRequestedItems()) {
                    mergeTargetStack(targetStacks, stack);
                }
            } else {
                final ItemStack result = deliverable.getResult();
                mergeTargetStack(targetStacks, result);
            }

            if (targetStacks.isEmpty()) {
                final ItemStack result = deliverable.getResult();
                if (!result.isEmpty()) {
                    mergeTargetStack(targetStacks, result);
                }
            }

            for (final Map.Entry<String, ItemStack> entry : targetStacks.entrySet()) {
                final ItemStack requested = entry.getValue();
                if (requested.isEmpty()) {
                    continue;
                }
                final ItemStack copy = requested.copy();
                copy.setCount(resolveRequestedCount(request, deliverable, copy));
                merged.merge(entry.getKey(), copy, TravelerTradeService::mergeStacks);
            }
        }

        return new ArrayList<>(merged.values());
    }

    private static ItemStack mergeStacks(final ItemStack existing, final ItemStack incoming) {
        existing.grow(incoming.getCount());
        return existing;
    }

    private static void mergeTargetStack(final Map<String, ItemStack> targetStacks, final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        final ItemStack safeStack = TravelerItemSafety.sanitizeDemandStack(stack);
        if (safeStack.isEmpty()) {
            return;
        }

        final String key = stackKey(safeStack);
        targetStacks.merge(key, safeStack, TravelerTradeService::mergeTargetStack);
    }

    private static ItemStack mergeTargetStack(final ItemStack existing, final ItemStack incoming) {
        existing.setCount(Math.max(existing.getCount(), incoming.getCount()));
        return existing;
    }

    private static String stackKey(final ItemStack stack) {
        final String tag = stack.hasTag() ? stack.getTag().toString() : "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "#" + stack.getDamageValue() + "#" + tag;
    }

    private static int resolveRequestedCount(final IRequest<?> request, final IDeliverable deliverable, final ItemStack stack) {
        return Math.max(1, Math.max(stack.getCount(), deliverable.getCount()));
    }

    private static List<IRequest<?>> collectTopLevelRequests(final IRequestManager requestManager) {
        final List<IRequest<?>> requests = new ArrayList<>();
        final List<com.minecolonies.api.colony.requestsystem.token.IToken<?>> assignedTokens = new ArrayList<>();
        assignedTokens.addAll(requestManager.getPlayerResolver().getAllAssignedRequests());
        assignedTokens.addAll(requestManager.getRetryingRequestResolver().getAllAssignedRequests());
        final IColony colony = requestManager.getColony();
        if (colony != null && colony.getCitizenManager() != null) {
            for (final ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                final IJob<?> job = citizen.getJob();
                if (job != null) {
                    assignedTokens.addAll(job.getAsyncRequests());
                }
            }
        }

        for (final com.minecolonies.api.colony.requestsystem.token.IToken<?> token : assignedTokens) {
            IRequest<?> current = requestManager.getRequestForToken(token);
            if (current == null) {
                continue;
            }
            while (current != null && current.hasParent()) {
                current = requestManager.getRequestForToken(current.getParent());
            }
            if (current != null && !requests.contains(current)) {
                requests.add(current);
            }
        }

        return requests;
    }

    private static boolean isActiveRequest(final IRequest<?> request) {
        return switch (request.getState()) {
            case CREATED, REPORTED, ASSIGNING, ASSIGNED, IN_PROGRESS, FOLLOWUP_IN_PROGRESS, FINALIZING -> true;
            default -> false;
        };
    }

    private static boolean canWarehouseAccept(final IWareHouse warehouse, final List<ItemStack> stacks) {
        if (!(warehouse.getTileEntity() instanceof TileEntityWareHouse tileEntityWareHouse)) {
            return false;
        }

        for (final ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            final BlockEntity rackEntity = tileEntityWareHouse.getRackForStack(stack);
            if (!(rackEntity instanceof AbstractTileEntityRack rack)) {
                return false;
            }

            ItemStack remaining = stack.copy();
            final IItemHandler rackInventory = rack.getInventory();
            for (int slot = 0; slot < rackInventory.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = rackInventory.insertItem(slot, remaining, true);
            }

            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void deliverToWarehouse(final IWareHouse warehouse, final List<ItemStack> stacks) {
        final InventoryCitizen delivery = new InventoryCitizen("traveler_delivery", false);
        for (final ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < delivery.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = delivery.insertItem(slot, remaining, false);
            }
        }
        warehouse.getTileEntity().dumpInventoryIntoWareHouse(delivery);
    }

    private static boolean tryTakeAmethyst(final ServerPlayer player, final int amount) {
        final int current = player.getInventory().clearOrCountMatchingItems(
            stack -> stack.is(Items.AMETHYST_SHARD),
            0,
            player.inventoryMenu.getCraftSlots()
        );
        if (current < amount) {
            return false;
        }

        player.getInventory().clearOrCountMatchingItems(
            stack -> stack.is(Items.AMETHYST_SHARD),
            amount,
            player.inventoryMenu.getCraftSlots()
        );
        return true;
    }

    private static TradeContext resolveContext(final ServerPlayer player, final int travelerEntityId) {
        final Level level = player.level();
        final Entity entity = level.getEntity(travelerEntityId);
        if (entity == null || !TravelerUtils.isTravelerEntity(entity)) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.traveler_missing"), false);
            return null;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, entity.blockPosition());
        if (colony == null) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.colony_missing"), false);
            return null;
        }

        final IWareHouse warehouse = TravelerUtils.findWarehouse(colony);
        if (warehouse == null || warehouse.getTileEntity() == null) {
            player.displayClientMessage(Component.translatable("message.traveleraddon.warehouse_missing"), false);
            return null;
        }

        final ITownHall townHall = colony.getServerBuildingManager().getTownHall();
        return new TradeContext(colony, warehouse, entity, townHall);
    }

    private record TradeContext(IColony colony, IWareHouse warehouse, Entity traveler, ITownHall townHall) {
    }
}
