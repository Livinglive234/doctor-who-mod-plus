package net.drgmes.dwm.utils.helpers;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.blocks.tardis.exteriors.BaseTardisExteriorBlock;
import net.drgmes.dwm.blocks.tardis.exteriors.BaseTardisExteriorBlockEntity;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.items.tardis.keys.TardisKeyItem;
import net.drgmes.dwm.setup.ModDimensions.ModDimensionTypes;
import net.drgmes.dwm.world.generator.TardisChunkGenerator;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;

import java.util.Optional;
import java.util.UUID;

public class TardisHelper {
    public static final BlockPos TARDIS_POS = new BlockPos(0, 128, 0).toImmutable();

    // The same rule as TardisStateManager.checkAccess(player, true, false), but by owner id and tardisId directly
    // instead of a TardisStateManager instance: monitor screens only ever have a client-side mirror of one, which
    // never has its world set (only readNbt is ever called on it client-side), and checkAccess's key-holder scan
    // needs the TARDIS's id, which it otherwise derives from that world.
    public static boolean hasOwnerOrKeyAccess(UUID ownerId, String tardisId, PlayerEntity player) {
        if (player == null) return false;
        if (ownerId == null || ownerId.equals(player.getUuid())) return true;

        return tardisId.equals(TardisKeyItem.findTardisId(player));
    }

    // The same rule as TardisStateManager.checkAccess(player, false, true): the owner, and only the owner - no
    // key-holder fallback, and (unlike hasOwnerOrKeyAccess) no free pass for an unclaimed TARDIS either.
    public static boolean isOwner(UUID ownerId, PlayerEntity player) {
        return player != null && ownerId != null && ownerId.equals(player.getUuid());
    }

    public static BlockPos getTardisFarPos(int index) {
        return TARDIS_POS.add(DWM.COMMON.TARDIS_ROOMS_OFFSET, 0, DWM.COMMON.TARDIS_ROOMS_OFFSET).multiply(index).withY(TARDIS_POS.getY()).toImmutable();
    }

    public static boolean isTardisDimension(World world) {
        if (world == null) return false;

        Optional<RegistryKey<DimensionType>> dimensionTypeRegistryKey = world.getDimensionEntry().getKey();
        return dimensionTypeRegistryKey.isPresent() && dimensionTypeRegistryKey.get().equals(ModDimensionTypes.TARDIS);
    }

    public static boolean isTardisDimension(RegistryKey<World> worldKey) {
        if (worldKey == null) return false;
        return worldKey.getValue().getNamespace().equals(DWM.MODID);
    }

    public static ServerWorld getOrCreateTardisWorld(String id, RegistryKey<World> dimension, BlockPos blockPos, Direction direction, MinecraftServer server) {
        ServerWorld tardisWorld = DimensionHelper.getOrCreateWorld(id, server, TardisHelper::tardisDimensionBuilder);

        TardisStateManager.get(tardisWorld).ifPresent((tardis) -> {
            tardis.setDimension(dimension, false);
            tardis.setFacing(direction, false);
            tardis.setPosition(blockPos, false);
        });

        return tardisWorld;
    }

    public static ServerWorld getOrCreateTardisWorld(BaseTardisExteriorBlockEntity tile) {
        if (tile.getWorld() == null || tile.getWorld().isClient) return null;

        return TardisHelper.getOrCreateTardisWorld(
            tile.getOrCreateTardisId(),
            tile.getWorld().getRegistryKey(),
            tile.getPos(),
            tile.getCachedState().get(BaseTardisExteriorBlock.FACING),
            tile.getWorld().getServer()
        );
    }

    public static DimensionOptions tardisDimensionBuilder(MinecraftServer server) {
        return new DimensionOptions(
            server.getRegistryManager().get(RegistryKeys.DIMENSION_TYPE).getEntry(ModDimensionTypes.TARDIS).orElseThrow(),
            new TardisChunkGenerator(server)
        );
    }
}
