package com.minecolonies.autorecipes;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AutoRecipesConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue UNLOCK_LEVEL = BUILDER
        .comment("Building level at which MineColonies crafting workers automatically learn compatible recipes.")
        .defineInRange("unlockLevel", 2, 1, 5);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private AutoRecipesConfig() {
    }
}
