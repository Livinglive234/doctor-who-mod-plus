package net.drgmes.dwm.compat.xaero;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.items.tardis.keys.TardisKeyItem;
import net.drgmes.dwm.network.server.TardisMapDestinationApplyPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * "Set TARDIS destination here" in the right-click menu of Xaero's World Map. Xaero's has no API for adding to it, so
 * MixinXaeroGuiMap calls {@link #addTo} from the map screen. Everything here is built to fail soft: a Xaero update that
 * renames what it reaches for takes the option away (or does nothing when picked) and logs why, instead of crashing.
 */
@Environment(EnvType.CLIENT)
public class XaeroMapDestinationOption extends RightClickOption {
    // The translation key of the option's name: Xaero's shows it as it does its own.
    private static final String NAME = "gui.dwm.map.set_tardis_destination";

    public XaeroMapDestinationOption(int index, IRightClickableElement map) {
        super(NAME, index, map);
    }

    /** Adds the option to the menu, if the player has a TARDIS key to set the destination of. */
    public static void addTo(ArrayList<RightClickOption> options, IRightClickableElement map) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (options == null || player == null || TardisKeyItem.findTardisId(player) == null) return;

        options.add(new XaeroMapDestinationOption(options.size(), map));
    }

    // Where was clicked, and in which dimension: the fields Xaero's own options (its teleport) go by.
    @Override
    public void onAction(Screen screen) {
        try {
            int x = (int) read("rightClickX");
            int z = (int) read("rightClickZ");
            @SuppressWarnings("unchecked") RegistryKey<World> dimension = (RegistryKey<World>) read("rightClickDim");

            new TardisMapDestinationApplyPacket(dimension, x, z).sendToServer();
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
            DWM.LOGGER.warn("Could not read where Xaero's World Map was clicked, so the TARDIS destination was not set. Its map screen has changed?", e);
        }
    }

    private Object read(String name) throws ReflectiveOperationException {
        Field field = this.target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(this.target);
    }
}
