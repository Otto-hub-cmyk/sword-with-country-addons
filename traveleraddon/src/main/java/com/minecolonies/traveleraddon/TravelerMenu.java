package com.minecolonies.traveleraddon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class TravelerMenu extends AbstractContainerMenu {
    private final int travelerEntityId;
    private final int remainingMarketPurchases;
    private final int remainingDemandPurchases;
    private final int townHallLevel;

    public TravelerMenu(final int containerId, final Inventory inventory, final FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    public TravelerMenu(
        final int containerId,
        final Inventory inventory,
        final int travelerEntityId,
        final int remainingMarketPurchases,
        final int remainingDemandPurchases,
        final int townHallLevel
    ) {
        super(TravelerMenus.TRAVELER_MENU.get(), containerId);
        this.travelerEntityId = travelerEntityId;
        this.remainingMarketPurchases = remainingMarketPurchases;
        this.remainingDemandPurchases = remainingDemandPurchases;
        this.townHallLevel = townHallLevel;
    }

    public int travelerEntityId() {
        return travelerEntityId;
    }

    public int remainingMarketPurchases() {
        return remainingMarketPurchases;
    }

    public int remainingDemandPurchases() {
        return remainingDemandPurchases;
    }

    public int townHallLevel() {
        return townHallLevel;
    }

    @Override
    public boolean stillValid(final Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY;
    }
}
