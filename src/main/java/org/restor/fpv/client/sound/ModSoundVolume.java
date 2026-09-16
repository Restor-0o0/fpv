package org.restor.fpv.client.sound;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.restor.fpv.Config;
import org.restor.fpv.Fpv;

@EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public final class ModSoundVolume {
    private ModSoundVolume() {}

    public static float motor() {
        return Config.SOUND_MOTOR_VOLUME.get().floatValue();
    }

    public static float effects() {
        return Config.SOUND_EFFECTS_VOLUME.get().floatValue();
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof TickableSoundInstance) return;
        if (!Fpv.MODID.equals(sound.getLocation().getNamespace())) return;
        float scale = effects();
        if (scale >= 0.999f) return;
        event.setSound(scale <= 0.001f ? null : new Scaled(sound, scale));
    }

    private record Scaled(SoundInstance inner, float scale) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return inner.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { return inner.resolve(manager); }
        @Override public Sound getSound() { return inner.getSound(); }
        @Override public SoundSource getSource() { return inner.getSource(); }
        @Override public boolean isLooping() { return inner.isLooping(); }
        @Override public boolean isRelative() { return inner.isRelative(); }
        @Override public int getDelay() { return inner.getDelay(); }
        @Override public float getVolume() { return inner.getVolume() * scale; }
        @Override public float getPitch() { return inner.getPitch(); }
        @Override public double getX() { return inner.getX(); }
        @Override public double getY() { return inner.getY(); }
        @Override public double getZ() { return inner.getZ(); }
        @Override public Attenuation getAttenuation() { return inner.getAttenuation(); }
        @Override public boolean canStartSilent() { return inner.canStartSilent(); }
        @Override public boolean canPlaySound() { return inner.canPlaySound(); }
    }
}
