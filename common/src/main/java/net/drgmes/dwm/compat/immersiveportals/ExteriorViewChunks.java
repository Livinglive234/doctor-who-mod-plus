package net.drgmes.dwm.compat.immersiveportals;

import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether the exterior view may stream the chunks around a flying TARDIS. Streaming asks the server to load
 * them, which generates any that don't exist yet - so the view only locks on where the terrain has already been
 * generated, or near the destination (where the TARDIS is about to land and generate it anyway).
 */
public final class ExteriorViewChunks {
    // Within this many chunks of the destination the view may lock on regardless.
    public static final int DESTINATION_RANGE = 30;

    public enum Result { OK, PENDING, NO }

    private record Key(RegistryKey<World> dimension, long chunk) {
    }

    private static final int MAX_KNOWN = 100_000;
    private static final Set<Key> GENERATED = ConcurrentHashMap.newKeySet();
    private static final Map<Key, CompletableFuture<Optional<NbtCompound>>> PENDING = new ConcurrentHashMap<>();

    private ExteriorViewChunks() {
    }

    // Whether the view may stream the area of the given radius around a chunk of this world. A blocking check waits for
    // the chunk files to be read; otherwise unread ones come back as PENDING and the caller asks again next tick.
    public static Result check(ServerWorld world, ChunkPos center, int radius, TardisStateManager tardis, boolean blocking) {
        if (isNearDestination(world, center, tardis)) return Result.OK;

        Result result = Result.OK;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Boolean generated = isGenerated(world, new ChunkPos(center.x + dx, center.z + dz), blocking);
                if (generated == null) result = Result.PENDING;
                else if (!generated) return Result.NO;
            }
        }

        return result;
    }

    private static boolean isNearDestination(ServerWorld world, ChunkPos center, TardisStateManager tardis) {
        if (!tardis.getDestinationExteriorDimension().equals(world.getRegistryKey())) return false;

        ChunkPos destination = new ChunkPos(tardis.getDestinationExteriorPosition());
        return Math.max(Math.abs(destination.x - center.x), Math.abs(destination.z - center.z)) <= DESTINATION_RANGE;
    }

    // null while the chunk file is still being read.
    private static Boolean isGenerated(ServerWorld world, ChunkPos pos, boolean blocking) {
        // Looking for a loaded chunk neither loads nor generates anything.
        if (world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false) != null) return true;

        Key key = new Key(world.getRegistryKey(), pos.toLong());
        if (GENERATED.contains(key)) return true;

        CompletableFuture<Optional<NbtCompound>> read = PENDING.computeIfAbsent(key, (k) -> ((ServerChunkManager) world.getChunkManager()).chunkLoadingManager.getNbt(pos));
        if (!blocking && !read.isDone()) return null;

        PENDING.remove(key);
        Optional<NbtCompound> nbt = read.join();
        boolean generated = nbt.isPresent() && nbt.get().getString("Status").endsWith("full");

        if (generated) {
            if (GENERATED.size() > MAX_KNOWN) GENERATED.clear();
            GENERATED.add(key);
        }

        return generated;
    }
}
