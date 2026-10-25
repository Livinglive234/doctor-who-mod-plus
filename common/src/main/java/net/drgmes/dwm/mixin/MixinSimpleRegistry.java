package net.drgmes.dwm.mixin;

import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Keeps the game from crashing on the next save after a TARDIS is deleted. Removing a dimension while the server runs
 * (DimLib, bundled with Immersive Portals) leaves an empty slot where it was in the dimension registry's id list so the
 * other dimensions keep their numbers, but the game's save code walks that list with streamEntries() and does not expect
 * an empty slot: the next autosave threw a NullPointerException in the server tick loop and took the server down. The
 * stream now skips empty slots, which also leaves the removed dimension out of the saved world settings.
 */
@Mixin(SimpleRegistry.class)
public class MixinSimpleRegistry<T> {
    @Inject(method = "streamEntries", at = @At("RETURN"), cancellable = true)
    private void dwm$skipRemovedEntries(CallbackInfoReturnable<Stream<RegistryEntry.Reference<T>>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(Objects::nonNull));
    }
}
