package com.minecolonies.traveleraddon;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TravelerAddonMod.MOD_ID)
public final class TravelerAddonMod {
    public static final String MOD_ID = "traveleraddon";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public TravelerAddonMod() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        TravelerMenus.MENUS.register(modBus);
        TravelerNetwork.register();
        TravelerCatalog.bootstrap();
        MinecraftForge.EVENT_BUS.register(new TravelerEvents());
        LOGGER.info("Traveler Addon bootstrap complete");
    }
}
