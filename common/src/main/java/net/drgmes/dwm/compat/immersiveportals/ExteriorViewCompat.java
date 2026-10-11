package net.drgmes.dwm.compat.immersiveportals;

import dev.architectury.event.events.common.TickEvent;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.systems.TardisSystemMaterialization;
import net.drgmes.dwm.entities.tardis.exteriors.TardisFlyoverEntity;
import net.drgmes.dwm.network.client.TardisExteriorViewOpenPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The server half of the console monitor's exterior view: the view is rendered by the player's own client, which
 * only has the chunks of the world it is in - so while it is open, Immersive Portals is asked to stream the chunks
 * around the exterior (in whatever dimension that is) to that one player. For a flying TARDIS the stream follows
 * the flyover as it moves.
 */
public final class ExteriorViewCompat {
    // Matches the render distance the view caps itself at (see TardisConsoleUnitMonitorExteriorViewScreen).
    public static final int RADIUS = 8;

    // How far the flyover may get from the middle of the streamed area before the stream is moved to it.
    private static final int FOLLOW_SLACK_CHUNKS = 3;

    // How long after the flyover is gone to wait for the exterior to finish materializing.
    private static final int HAND_OVER_WAIT_TICKS = 200;

    private static final class Following {
        private final TardisFlyoverEntity entity;
        private final RegistryKey<World> dimension;
        private final TardisStateManager tardis;
        private ChunkPos center;
        private int waiting;

        private Following(TardisStateManager tardis, TardisFlyoverEntity entity, RegistryKey<World> dimension, ChunkPos center) {
            this.tardis = tardis;
            this.entity = entity;
            this.dimension = dimension;
            this.center = center;
        }
    }

    private static final Map<ServerPlayerEntity, ChunkLoader> LOADERS = new WeakHashMap<>();
    private static final Map<ServerPlayerEntity, Following> FOLLOWING = new WeakHashMap<>();
    private static boolean tickHooked;

    private ExteriorViewCompat() {
    }

    public static void startViewing(ServerPlayerEntity player, RegistryKey<World> dimension, BlockPos position) {
        stopViewing(player);
        install(player, dimension, new ChunkPos(position));
    }

    public static void startFollowing(ServerPlayerEntity player, TardisStateManager tardis, TardisFlyoverEntity entity) {
        stopViewing(player);

        RegistryKey<World> dimension = entity.getWorld().getRegistryKey();
        ChunkPos center = entity.getChunkPos();
        install(player, dimension, center);
        FOLLOWING.put(player, new Following(tardis, entity, dimension, center));

        if (!tickHooked) {
            tickHooked = true;
            TickEvent.SERVER_POST.register((server) -> tickFollowing());
        }
    }

    public static void stopViewing(ServerPlayerEntity player) {
        FOLLOWING.remove(player);

        ChunkLoader loader = LOADERS.remove(player);
        if (loader != null) PortalAPI.removeChunkLoaderForPlayer(player, loader);
    }

    private static void install(ServerPlayerEntity player, RegistryKey<World> dimension, ChunkPos center) {
        ChunkLoader loader = new ChunkLoader(new DimensionalChunkPos(dimension, center), RADIUS);
        PortalAPI.addChunkLoaderForPlayer(player, loader);
        LOADERS.put(player, loader);
    }

    // The flight is over: if the TARDIS has landed, the view carries on as the ordinary view of its exterior; if it is
    // still materializing, wait a little for that; otherwise there is nothing to look at and the screen closes itself.
    private static void handOverOrStop(ServerPlayerEntity player, Following following) {
        TardisSystemMaterialization materialization = following.tardis.getSystem(TardisSystemMaterialization.class);

        if (materialization.isMaterialized() && !materialization.isInMaterializationProcess()) {
            RegistryKey<World> dimension = following.tardis.getCurrentExteriorDimension();
            BlockPos position = following.tardis.getCurrentExteriorPosition();

            startViewing(player, dimension, position);

            new TardisExteriorViewOpenPacket(
                following.tardis.getMainConsolePosition(),
                dimension.getValue(),
                position,
                following.tardis.getCurrentExteriorFacing().getId(),
                -1
            ).sendTo(player);

            return;
        }

        if (++following.waiting > HAND_OVER_WAIT_TICKS) stopViewing(player);
    }

    // Moves the stream along with each followed flyover, and lets go once it is gone or has left its dimension.
    private static void tickFollowing() {
        for (Map.Entry<ServerPlayerEntity, Following> entry : new ArrayList<>(FOLLOWING.entrySet())) {
            ServerPlayerEntity player = entry.getKey();
            Following following = entry.getValue();
            TardisFlyoverEntity entity = following.entity;

            if (player == null || player.isRemoved()) {
                if (player != null) stopViewing(player);
                continue;
            }

            if (entity.isRemoved() || !entity.getWorld().getRegistryKey().equals(following.dimension)) {
                handOverOrStop(player, following);
                continue;
            }

            ChunkPos now = entity.getChunkPos();
            if (Math.max(Math.abs(now.x - following.center.x), Math.abs(now.z - following.center.z)) < FOLLOW_SLACK_CHUNKS) continue;

            ChunkLoader old = LOADERS.get(player);
            if (old != null) PortalAPI.removeChunkLoaderForPlayer(player, old);

            install(player, following.dimension, now);
            following.center = now;
        }
    }
}
