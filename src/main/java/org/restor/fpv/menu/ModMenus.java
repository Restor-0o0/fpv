package org.restor.fpv.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Fpv.MODID);

    public static final RegistryObject<MenuType<WorkbenchMenu>> WORKBENCH = MENUS.register("workbench",
        () -> IForgeMenuType.create(WorkbenchMenu::new));

    public static final RegistryObject<MenuType<org.restor.fpv.assembly.AssemblyStationMenu>> ASSEMBLY_STATION = MENUS.register("assembly_station",
        () -> IForgeMenuType.create(org.restor.fpv.assembly.AssemblyStationMenu::new));
}
