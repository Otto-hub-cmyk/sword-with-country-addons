package com.minecolonies.traveleraddon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class TravelerMarketPurchasePacket {
    private final int travelerEntityId;
    private final String itemId;

    public TravelerMarketPurchasePacket(final int travelerEntityId, final String itemId) {
        this.travelerEntityId = travelerEntityId;
        this.itemId = itemId;
    }

    public static TravelerMarketPurchasePacket decode(final FriendlyByteBuf buffer) {
        return new TravelerMarketPurchasePacket(buffer.readInt(), buffer.readUtf());
    }

    public static void encode(final TravelerMarketPurchasePacket packet, final FriendlyByteBuf buffer) {
        buffer.writeInt(packet.travelerEntityId);
        buffer.writeUtf(packet.itemId);
    }

    public static void handle(final TravelerMarketPurchasePacket packet, final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            final ServerPlayer player = context.getSender();
            if (player != null) {
                TravelerTradeService.handleMarketPurchase(player, packet.travelerEntityId, packet.itemId);
            }
        });
        context.setPacketHandled(true);
    }
}
