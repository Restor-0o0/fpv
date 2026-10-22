package org.restor.fpv.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.restor.fpv.Fpv;

/**
 * Every sound the mod plays goes through here. The entries are declared in
 * assets/fpv/sounds.json as aliases onto vanilla events, so swapping in recorded
 * audio later is a resource change and touches no code.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, Fpv.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MOTOR_LOOP = register("drone.motor_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTOR_LOOP_SMALL = register("drone.motor_loop_small");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTOR_LOOP_LARGE = register("drone.motor_loop_large");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARM = register("drone.arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISARM = register("drone.disarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH = register("drone.crash");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH = register("drone.launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> DART = register("drone.dart");
    public static final DeferredHolder<SoundEvent, SoundEvent> PAYLOAD_RELEASE = register("drone.payload_release");

    public static final DeferredHolder<SoundEvent, SoundEvent> TX_BIND = register("tx.bind");
    public static final DeferredHolder<SoundEvent, SoundEvent> TX_UNBIND = register("tx.unbind");
    public static final DeferredHolder<SoundEvent, SoundEvent> TX_LINK = register("tx.link");

    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_CHARGE = register("station.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEST_DEPLOY = register("station.nest_deploy");

    public static final DeferredHolder<SoundEvent, SoundEvent> WARHEAD_ARM = register("warhead.arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARHEAD_DETONATE = register("warhead.detonate");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARHEAD_INCENDIARY = register("warhead.incendiary");

    public static final DeferredHolder<SoundEvent, SoundEvent> DETECTOR_ALERT = register("antidrone.detector_alert");
    public static final DeferredHolder<SoundEvent, SoundEvent> JAMMER_LOOP = register("antidrone.jammer_loop");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Fpv.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
