package com.minecolonies.traveleraddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TravelerMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, TravelerAddonMod.MOD_ID);

    public static final RegistryObject<MenuType<TravelerMenu>> TRAVELER_MENU =
        MENUS.register("traveler_menu", () -> IForgeMenuType.create(TravelerMenu::new));

    private TravelerMenus() {
    }
}
