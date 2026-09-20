package net.drgmes.dwm.entities.tardis.exteriors;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.utils.builders.EntityBuilder;
import net.minecraft.entity.SpawnGroup;

public class TardisFlyoverEntityBuilder extends EntityBuilder<TardisFlyoverEntity> {
    public TardisFlyoverEntityBuilder(String name) {
        // Tracking range is in chunks (the game multiplies it by 16), sized to the render distance. Synced every
        // tick, since this entity actually moves. The 1x2.5 box is a rough stand-in: purely cosmetic, no collision.
        super(name, TardisFlyoverEntity::new, SpawnGroup.MISC, 1.0F, 2.5F, (int) Math.ceil(DWM.FLYOVER.RENDER_DISTANCE / 16F), 1);
    }
}
