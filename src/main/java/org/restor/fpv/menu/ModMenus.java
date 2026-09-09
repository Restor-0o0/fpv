package org.restor.fpv.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Fpv.MODID);

    public static final RegistryObject<MenuType<CarrierMenu>> CARRIER = MENUS.register("carrier",
        () -> IForgeMenuType.create(CarrierMenu::new));

    public static final RegistryObject<MenuType<CargoBayMenu>> CARGO_BAY = MENUS.register("cargo_bay",
        () -> IForgeMenuType.create(CargoBayMenu::new));

    public static final RegistryObject<MenuType<ChargingStationMenu>> CHARGING_STATION = MENUS.register("charging_station",
        () -> IForgeMenuType.create(ChargingStationMenu::new));

    public static final RegistryObject<MenuType<org.restor.fpv.assembly.AssemblyStationMenu>> ASSEMBLY_STATION = MENUS.register("assembly_station",
        () -> IForgeMenuType.create(org.restor.fpv.assembly.AssemblyStationMenu::new));
}
