package net.drgmes.dwm.utils.sounds;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.entities.tardis.exteriors.TardisFlyoverEntity;
import net.drgmes.dwm.setup.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

/** Kept apart from the entity so a dedicated server never loads the client sound classes. */
@Environment(EnvType.CLIENT)
public final class FlyoverSounds {
    private FlyoverSounds() {
    }

    public static void play(TardisFlyoverEntity entity) {
        TardisFlyoverEntity.Sound sound = entity.getSound();
        boolean takeoff = sound == TardisFlyoverEntity.Sound.TAKEOFF;

        MinecraftClient.getInstance().getSoundManager().play(new FlyoverSoundInstance(
            takeoff ? ModSounds.TARDIS_TAKEOFF.get() : ModSounds.TARDIS_FLIGHT.get(),
            entity,
            takeoff ? DWM.FLYOVER.TAKEOFF_SOUND_VOLUME : DWM.FLYOVER.FLIGHT_SOUND_VOLUME,
            takeoff ? DWM.FLYOVER.TAKEOFF_SOUND_RANGE : DWM.FLYOVER.FLIGHT_SOUND_RANGE,
            sound == TardisFlyoverEntity.Sound.FLIGHT
        ));
    }
}
