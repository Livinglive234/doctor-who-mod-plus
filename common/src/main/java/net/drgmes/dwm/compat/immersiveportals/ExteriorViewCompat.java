package net.drgmes.dwm.compat.immersiveportals;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The server half of the console monitor's exterior view: the view is rendered by the player's own client, which
 * only has the chunks of the world it is in - so while it is open, Immersive Portals is asked to stream the chunks
 * around the exterior (in whatever dimension that is) to that one player.
 */
public final class ExteriorViewCompat {
    // Matches the render distance the view caps itself at (see TardisConsoleUnitMonitorExteriorViewScreen).
    public static final int RADIUS = 8;

    private static final Map<ServerPlayerEntity, ChunkLoader> LOADERS = new WeakHashMap<>();

    private ExteriorViewCompat() {
    }

    public static void startViewing(ServerPlayerEntity player, RegistryKey<World> dimension, BlockPos position) {
        stopViewing(player);

        ChunkLoader loader = new ChunkLoader(new DimensionalChunkPos(dimension, new ChunkPos(position)), RADIUS);
        PortalAPI.addChunkLoaderForPlayer(player, loader);
        LOADERS.put(player, loader);
    }

    public static void stopViewing(ServerPlayerEntity player) {
        ChunkLoader loader = LOADERS.remove(player);
        if (loader != null) PortalAPI.removeChunkLoaderForPlayer(player, loader);
    }
}
