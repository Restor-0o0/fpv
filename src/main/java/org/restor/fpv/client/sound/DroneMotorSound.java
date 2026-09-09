package org.restor.fpv.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.restor.fpv.assembly.ModularDroneEntity;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.sound.ModSounds;

/** Motor note that follows an aircraft; loudness and pitch track throttle alone. */
public class DroneMotorSound extends AbstractTickableSoundInstance {

    /**
     * Blade-pass frequency scales roughly with 1/diameter, far beyond the [0.5, 2.0] pitch
     * window the engine allows, so each size class gets its own sample and spends the whole
     * window on throttle. Frequencies below are what the sample reaches at that pitch.
     */
    public enum Tier {
        SMALL(2.5f, 0.62f, 1.50f, 0.72f),   // 1104 Hz sample:  685 Hz idle -> 1660 Hz flat out
        MEDIUM(5.0f, 0.58f, 1.42f, 1.00f),  //  620 Hz sample:  360 Hz idle ->  880 Hz flat out
        LARGE(7.0f, 0.56f, 1.34f, 1.22f);   //  420 Hz sample:  235 Hz idle ->  565 Hz flat out

        final float inches;
        final float idlePitch;
        final float fullPitch;
        final float gain;

        Tier(float inches, float idlePitch, float fullPitch, float gain) {
            this.inches = inches;
            this.idlePitch = idlePitch;
            this.fullPitch = fullPitch;
            this.gain = gain;
        }

        SoundEvent event() {
            return switch (this) {
                case SMALL -> ModSounds.MOTOR_LOOP_SMALL.get();
                case LARGE -> ModSounds.MOTOR_LOOP_LARGE.get();
                default -> ModSounds.MOTOR_LOOP.get();
            };
        }
    }

    private final Entity craft;
    private final Tier tier;
    private final boolean firstPerson;
    private float loudness;

    public DroneMotorSound(Entity craft, boolean firstPerson, float initialLoudness) {
        super(tierOf(craft).event(), SoundSource.NEUTRAL, craft.level().getRandom());
        this.craft = craft;
        this.tier = tierOf(craft);
        this.firstPerson = firstPerson;
        this.loudness = initialLoudness;
        this.looping = true;
        this.delay = 0;
        this.volume = Math.max(0.01f, initialLoudness);
        this.pitch = tier.idlePitch;
        if (firstPerson) {
            // Pinned to the listener instead of the craft: from inside the goggles the motors
            // are on your own airframe, so they must not swing across the stereo field
            // every time the quad yaws.
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        } else {
            this.x = craft.getX();
            this.y = craft.getY();
            this.z = craft.getZ();
        }
    }

    public static Tier tierOf(Entity craft) {
        float inches = propInches(craft);
        if (inches < 3.2f) return Tier.SMALL;
        if (inches < 6.0f) return Tier.MEDIUM;
        return Tier.LARGE;
    }

    private static float propInches(Entity craft) {
        if (craft instanceof ModularDroneEntity modular) {
            return modular.getAssembly().propDiameterInches();
        }
        if (craft instanceof org.restor.fpv.entity.NpcDroneEntity npc) {
            return npc.getAssembly().propDiameterInches();
        }
        if (craft instanceof DroneEntity drone) {
            return switch (drone.droneData.type) {
                case TINY_WHOOP -> 2.5f;
                case CINEWHOOP -> 3.5f;
                default -> 5.0f;
            };
        }
        return 7.0f;
    }

    public Entity craft() { return craft; }

    public float loudness() { return loudness; }

    public Tier tier() { return tier; }

    public boolean isFirstPerson() { return firstPerson; }

    private boolean running() {
        if (craft instanceof DroneEntity drone) return drone.isArmed();
        if (craft instanceof PlaneEntity plane) return plane.isArmed();
        return craft instanceof org.restor.fpv.entity.NpcDroneEntity;
    }

    private float throttle() {
        if (craft instanceof DroneEntity drone) return drone.getVisualThrottle();
        if (craft instanceof PlaneEntity plane) return plane.getVisualThrottle();
        if (craft instanceof org.restor.fpv.entity.NpcDroneEntity) {
            // No throttle model on the AI craft, so hover plus whatever it is asking of itself.
            return 0.45f + (float) Math.min(0.45, craft.getDeltaMovement().length() * 1.1);
        }
        return 0f;
    }

    // The engine drops a sound whose volume is zero when play() is called, and a
    // parked drone is silent until armed — so this instance must be allowed to start
    // quiet and fade in later.
    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        if (craft.isRemoved() || !craft.isAlive()) {
            stop();
            return;
        }
        if (!firstPerson) {
            x = craft.getX();
            y = craft.getY();
            z = craft.getZ();
        }

        float thr = Math.max(0f, Math.min(1f, throttle()));
        // Thrust goes as rpm^2, so the blade-pass rate flattens off near the stops instead
        // of climbing straight to a scream.
        float rpm = (float) Math.pow(thr, 0.8);

        float target = running() ? (0.26f + rpm * 0.62f) * tier.gain : 0.0f;
        if (firstPerson) target *= 0.55f;
        loudness += (target - loudness) * 0.12f;
        volume = Math.min(1.0f, loudness);

        float wanted = tier.idlePitch + (tier.fullPitch - tier.idlePitch) * rpm;
        pitch += (wanted - pitch) * 0.35f;
        pitch = Math.max(0.5f, Math.min(2.0f, pitch));
    }

    @Override
    public boolean canPlaySound() {
        return !craft.isSilent();
    }
}
