package net.drgmes.dwm.compat.immersiveportals;

import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockEntity;
import net.drgmes.dwm.blocks.tardis.consoleunits.screens.TardisConsoleUnitMonitorExteriorViewScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

// Kept out of ClientPackets so that class never has to resolve the screen (and with it Immersive Portals' classes)
// on a client without Immersive Portals - only reached after a ModCompats.immersivePortals() check.
@Environment(EnvType.CLIENT)
public final class ExteriorViewClient {
    private ExteriorViewClient() {
    }

    public static void open(BaseTardisConsoleUnitBlockEntity consoleUnit, Identifier dimension, BlockPos exteriorPos, int facingId) {
        MinecraftClient.getInstance().setScreen(new TardisConsoleUnitMonitorExteriorViewScreen(
            consoleUnit,
            RegistryKey.of(RegistryKeys.WORLD, dimension),
            exteriorPos,
            Direction.byId(facingId)
        ));
    }
}
