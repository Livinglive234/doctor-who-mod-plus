package net.drgmes.dwm.utils.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/** A sound that follows an entity, getting quieter as it moves away, and stops when the entity is gone. */
@Environment(EnvType.CLIENT)
public class FlyoverSoundInstance extends MovingSoundInstance {
    private final Entity entity;

    /**
     * @param volume above 1 it doesn't get any louder, but carries further: the range is 16 blocks times the volume
     * @param loop   whether it repeats for as long as the entity is there
     */
    public FlyoverSoundInstance(SoundEvent sound, Entity entity, float volume, boolean loop) {
        super(sound, SoundCategory.BLOCKS, SoundInstance.createRandom());
        this.entity = entity;
        this.volume = volume;
        this.repeat = loop;
        this.follow();
    }

    @Override
    public void tick() {
        if (this.entity.isRemoved()) this.setDone();
        else this.follow();
    }

    private void follow() {
        this.x = this.entity.getX();
        this.y = this.entity.getY();
        this.z = this.entity.getZ();
    }
}
