package net.drgmes.dwm.utils.sounds;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.entities.tardis.exteriors.TardisFlyoverEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

/**
 * A sound that follows a flyover, getting quieter as it moves away from the listener, and stops when the flyover is
 * gone or the sound's time is up. The sound engine only fades mono sounds with distance, and these are stereo, so
 * the fall-off is done here.
 */
@Environment(EnvType.CLIENT)
public class FlyoverSoundInstance extends MovingSoundInstance {
    private final TardisFlyoverEntity entity;
    private final float maxVolume;
    private final float range;
    private int age = 0;

    /**
     * @param maxVolume the volume right next to the entity
     * @param range     how far away it can be heard, in blocks; the volume falls off in a straight line to nothing
     * @param loop      whether it repeats for as long as the entity is there
     */
    public FlyoverSoundInstance(SoundEvent sound, TardisFlyoverEntity entity, float maxVolume, float range, boolean loop) {
        super(sound, SoundCategory.BLOCKS, SoundInstance.createRandom());
        this.entity = entity;
        this.maxVolume = maxVolume;
        this.range = range;
        this.repeat = loop;
        this.attenuationType = SoundInstance.AttenuationType.NONE;
        this.follow();
    }

    // It may start out of earshot, and silent.
    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }

    @Override
    public void tick() {
        this.age++;

        int duration = this.entity.getSoundTicks();
        if (this.entity.isRemoved() || (duration > 0 && this.age >= duration)) this.setDone();
        else this.follow();
    }

    // Volume: the distance to the listener, and for a sound with a set time, a fade to its end.
    private void follow() {
        this.x = this.entity.getX();
        this.y = this.entity.getY();
        this.z = this.entity.getZ();

        double distance = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(this.entity.getPos());
        float volume = this.maxVolume * MathHelper.clamp(1F - (float) (distance / this.range), 0F, 1F);

        int duration = this.entity.getSoundTicks();
        if (duration > 0) volume *= MathHelper.clamp((float) (duration - this.age) / DWM.FLYOVER.TAKEOFF_SOUND_END_FADE, 0F, 1F);

        this.volume = volume;
    }
}
