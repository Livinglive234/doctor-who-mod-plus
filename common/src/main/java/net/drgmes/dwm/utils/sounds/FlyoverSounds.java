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
        boolean flight = entity.getSound() == TardisFlyoverEntity.Sound.FLIGHT;

        MinecraftClient.getInstance().getSoundManager().play(new FlyoverSoundInstance(
            flight ? ModSounds.TARDIS_FLIGHT.get() : ModSounds.TARDIS_TAKEOFF.get(),
            entity,
            flight ? DWM.FLYOVER.FLYBY_SOUND_VOLUME : DWM.FLYOVER.TAKEOFF_SOUND_VOLUME,
            flight ? DWM.FLYOVER.FLYBY_SOUND_RANGE : DWM.FLYOVER.TAKEOFF_SOUND_RANGE,
            flight
        ));
    }
}
