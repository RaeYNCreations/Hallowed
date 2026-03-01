package com.raeynd.hallowed.gui;

import com.raeynd.hallowed.Hallowed;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers all {@link MenuType} instances for the Hallowed mod.
 */
public final class HallowedMenuTypes {

    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Hallowed.MOD_ID);

    /**
     * Container menu for the Resurrection GUI (other-player resurrection at a Bonfire).
     * Created client-side with extra data written by {@link ResurrectionMenuProvider}.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ResurrectionMenu>> RESURRECTION_MENU =
            MENU_TYPES.register("resurrection_menu",
                    () -> IMenuTypeExtension.create(ResurrectionMenu::new));

    private HallowedMenuTypes() {}

    public static void register(IEventBus modBus) {
        MENU_TYPES.register(modBus);
    }
}
