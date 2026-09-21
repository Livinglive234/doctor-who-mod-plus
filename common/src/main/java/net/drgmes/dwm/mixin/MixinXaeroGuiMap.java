package net.drgmes.dwm.mixin;

import net.drgmes.dwm.compat.xaero.XaeroMapDestinationOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/**
 * Adds an option to the right-click menu of Xaero's World Map screen. Xaero's has no API for it. Pseudo, so the mixin
 * is skipped when Xaero's isn't installed, and not required, so a Xaero without that method costs the option, not the game.
 */
@Pseudo
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public class MixinXaeroGuiMap {
    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false, require = 0)
    private void dwm$addTardisDestinationOption(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        XaeroMapDestinationOption.addTo(cir.getReturnValue(), (IRightClickableElement) this);
    }
}
