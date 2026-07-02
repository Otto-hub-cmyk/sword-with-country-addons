package com.minecolonies.traveleraddon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class TravelerDemandPurchasePacket {
    private final int travelerEntityId;

    public TravelerDemandPurchasePacket(final int travelerEntityId) {
        this.travelerEntityId = travelerEntityId;
    }

    public static TravelerDemandPurchasePacket decode(final FriendlyByteBuf buffer) {
        return new TravelerDemandPurchasePacket(buffer.readInt());
    }

    public static void encode(final TravelerDemandPurchasePacket packet, final FriendlyByteBuf buffer) {
        buffer.writeInt(packet.travelerEntityId);
    }

    public static void handle(final TravelerDemandPurchasePacket packet, final Supplier<NetworkEvent.Context> contextSupplier) {
        final NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            final ServerPlayer player = context.getSender();
            if (player != null) {
                TravelerTradeService.handleDemandPurchase(player, packet.travelerEntityId);
            }
        });
        context.setPacketHandled(true);
    }
}
