package com.minecolonies.autorecipes;

import com.mojang.logging.LogUtils;
import com.minecolonies.api.colony.IColony;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(AutoRecipesMod.MOD_ID)
public final class AutoRecipesMod {
    public static final String MOD_ID = "auto_recipes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AutoRecipesMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AutoRecipesConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }

        for (final IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
            if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide()) {
                continue;
            }

            AutoRecipeHelper.tickDoAutoDiscovery(colony);
        }
    }
}
