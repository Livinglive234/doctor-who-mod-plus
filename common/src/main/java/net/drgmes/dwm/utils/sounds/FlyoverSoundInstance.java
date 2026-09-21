package net.drgmes.dwm.utils.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

/**
 * A sound that follows an entity, getting quieter as it moves away from the listener, and stops when the entity is
 * gone. The sound engine only fades mono sounds with distance, and these are stereo, so the fall-off is done here.
 */
@Environment(EnvType.CLIENT)
public class FlyoverSoundInstance extends MovingSoundInstance {
    private final Entity entity;
    private final float maxVolume;
    private final float range;

    /**
     * @param maxVolume the volume right next to the entity
     * @param range     how far away it can be heard, in blocks; the volume falls off in a straight line to nothing
     * @param loop      whether it repeats for as long as the entity is there
     */
    public FlyoverSoundInstance(SoundEvent sound, Entity entity, float maxVolume, float range, boolean loop) {
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
        if (this.entity.isRemoved()) this.setDone();
        else this.follow();
    }

    private void follow() {
        this.x = this.entity.getX();
        this.y = this.entity.getY();
        this.z = this.entity.getZ();

        double distance = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(this.entity.getPos());
        this.volume = this.maxVolume * MathHelper.clamp(1F - (float) (distance / this.range), 0F, 1F);
    }
}
