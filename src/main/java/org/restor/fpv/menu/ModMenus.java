package org.restor.fpv.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.restor.fpv.Fpv;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Fpv.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CarrierMenu>> CARRIER = MENUS.register("carrier",
        () -> IMenuTypeExtension.create(CarrierMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CargoBayMenu>> CARGO_BAY = MENUS.register("cargo_bay",
        () -> IMenuTypeExtension.create(CargoBayMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ChargingStationMenu>> CHARGING_STATION = MENUS.register("charging_station",
        () -> IMenuTypeExtension.create(ChargingStationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<GuardStationMenu>> GUARD_STATION = MENUS.register("guard_station",
        () -> IMenuTypeExtension.create(GuardStationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<org.restor.fpv.assembly.AssemblyStationMenu>> ASSEMBLY_STATION = MENUS.register("assembly_station",
        () -> IMenuTypeExtension.create(org.restor.fpv.assembly.AssemblyStationMenu::new));
}
