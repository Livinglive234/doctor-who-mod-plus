package net.drgmes.dwm.utils.sounds;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;

/**
 * A sound at a position that fades out and stops. A sound the server plays can't be faded, so one that has to end at
 * a given moment is played by the client instead.
 */
@Environment(EnvType.CLIENT)
public class FadingSoundInstance extends MovingSoundInstance {
    private final float baseVolume;
    private final int fadeDelay;
    private final int fadeTicks;
    private int age = 0;

    /**
     * @param fadeDelay ticks at full volume before the fade starts
     * @param fadeTicks how long the fade takes; 0 for none, which lets the sound play out
     */
    public FadingSoundInstance(SoundEvent sound, BlockPos blockPos, float volume, int fadeDelay, int fadeTicks) {
        super(sound, SoundCategory.BLOCKS, SoundInstance.createRandom());
        this.x = blockPos.getX() + 0.5D;
        this.y = blockPos.getY() + 0.5D;
        this.z = blockPos.getZ() + 0.5D;
        this.volume = volume;
        this.baseVolume = volume;
        this.fadeDelay = fadeDelay;
        this.fadeTicks = fadeTicks;
    }

    @Override
    public void tick() {
        if (this.fadeTicks <= 0 || ++this.age <= this.fadeDelay) return;

        float remaining = 1F - (float) (this.age - this.fadeDelay) / this.fadeTicks;
        if (remaining <= 0F) this.setDone();
        else this.volume = this.baseVolume * remaining;
    }
}
