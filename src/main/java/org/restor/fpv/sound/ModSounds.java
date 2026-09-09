package org.restor.fpv.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import org.restor.fpv.Fpv;

/**
 * Every sound the mod plays goes through here. The entries are declared in
 * assets/fpv/sounds.json as aliases onto vanilla events, so swapping in recorded
 * audio later is a resource change and touches no code.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, Fpv.MODID);

    public static final RegistryObject<SoundEvent> MOTOR_LOOP = register("drone.motor_loop");
    public static final RegistryObject<SoundEvent> MOTOR_LOOP_SMALL = register("drone.motor_loop_small");
    public static final RegistryObject<SoundEvent> MOTOR_LOOP_LARGE = register("drone.motor_loop_large");
    public static final RegistryObject<SoundEvent> ARM = register("drone.arm");
    public static final RegistryObject<SoundEvent> DISARM = register("drone.disarm");
    public static final RegistryObject<SoundEvent> CRASH = register("drone.crash");
    public static final RegistryObject<SoundEvent> LAUNCH = register("drone.launch");
    public static final RegistryObject<SoundEvent> DART = register("drone.dart");
    public static final RegistryObject<SoundEvent> PAYLOAD_RELEASE = register("drone.payload_release");

    public static final RegistryObject<SoundEvent> TX_BIND = register("tx.bind");
    public static final RegistryObject<SoundEvent> TX_UNBIND = register("tx.unbind");
    public static final RegistryObject<SoundEvent> TX_LINK = register("tx.link");

    public static final RegistryObject<SoundEvent> STATION_CHARGE = register("station.charge");
    public static final RegistryObject<SoundEvent> NEST_DEPLOY = register("station.nest_deploy");

    public static final RegistryObject<SoundEvent> WARHEAD_ARM = register("warhead.arm");
    public static final RegistryObject<SoundEvent> WARHEAD_DETONATE = register("warhead.detonate");
    public static final RegistryObject<SoundEvent> WARHEAD_INCENDIARY = register("warhead.incendiary");

    public static final RegistryObject<SoundEvent> DETECTOR_ALERT = register("antidrone.detector_alert");
    public static final RegistryObject<SoundEvent> JAMMER_LOOP = register("antidrone.jammer_loop");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(Fpv.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
