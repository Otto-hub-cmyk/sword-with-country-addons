package com.minecolonies.traveleraddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TravelerNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(TravelerAddonMod.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private TravelerNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
            0,
            TravelerDemandPurchasePacket.class,
            TravelerDemandPurchasePacket::encode,
            TravelerDemandPurchasePacket::decode,
            TravelerDemandPurchasePacket::handle
        );
        CHANNEL.registerMessage(
            1,
            TravelerMarketPurchasePacket.class,
            TravelerMarketPurchasePacket::encode,
            TravelerMarketPurchasePacket::decode,
            TravelerMarketPurchasePacket::handle
        );
    }
}
