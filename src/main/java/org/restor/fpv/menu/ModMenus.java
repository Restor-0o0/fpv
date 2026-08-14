package org.restor.fpv.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.restor.fpv.Fpv;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, Fpv.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> WORKBENCH = MENUS.register("workbench",
        () -> IMenuTypeExtension.create(WorkbenchMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<org.restor.fpv.assembly.AssemblyStationMenu>> ASSEMBLY_STATION = MENUS.register("assembly_station",
        () -> IMenuTypeExtension.create(org.restor.fpv.assembly.AssemblyStationMenu::new));
}
