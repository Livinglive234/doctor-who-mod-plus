package net.drgmes.dwm.network.server;

import dev.architectury.networking.NetworkManager;
import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.systems.TardisSystemFlight;
import net.drgmes.dwm.common.tardis.systems.TardisSystemResearch;
import net.drgmes.dwm.items.tardis.keys.TardisKeyItem;
import net.drgmes.dwm.network.IPacket;
import net.drgmes.dwm.utils.helpers.DimensionHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Sets the destination of the TARDIS whose key the player holds to a spot they picked on a map (Xaero's, see
 * {@code compat.xaero}). A map gives an x and a z, so the height is the ground there if it is loaded, and otherwise
 * what the console already has, for the landing scan to sort out.
 */
public record TardisMapDestinationApplyPacket(
    RegistryKey<World> dimension,
    int x,
    int z
) implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_map_destination_apply");
    public static final CustomPayload.Id<TardisMapDestinationApplyPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisMapDestinationApplyPacket> PACKET_CODEC = PacketCodec.tuple(
        RegistryKey.createPacketCodec(RegistryKeys.WORLD), TardisMapDestinationApplyPacket::dimension,
        PacketCodecs.INTEGER, TardisMapDestinationApplyPacket::x,
        PacketCodecs.INTEGER, TardisMapDestinationApplyPacket::z,
        TardisMapDestinationApplyPacket::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }

    public static void handle(TardisMapDestinationApplyPacket payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            PlayerEntity player = context.getPlayer();
            String tardisId = TardisKeyItem.findTardisId(player);
            ServerWorld tardisWorld = tardisId != null ? DimensionHelper.getModWorld(tardisId, player.getServer()) : null;
            if (tardisWorld == null) {
                player.sendMessage(Text.translatable("message.dwm.tardis.map_destination.no_key"), true);
                return;
            }

            TardisStateManager.get(tardisWorld).ifPresent((tardis) -> apply(payload, player, tardis));
        });
    }

    private static void apply(TardisMapDestinationApplyPacket payload, PlayerEntity player, TardisStateManager tardis) {
        TardisSystemFlight flightSystem = tardis.getSystem(TardisSystemFlight.class);
        if (!flightSystem.isEnabled()) {
            player.sendMessage(DWM.TEXTS.FLIGHT_SYSTEM_NOT_INSTALLED, true);
            return;
        }

        // The same rule as the console's destination controls: not while it is under way
        if (tardis.isTravelInProgress()) {
            player.sendMessage(DWM.TEXTS.TARDIS_MUST_BE_LANDED, true);
            return;
        }

        RegistryKey<World> dimension = payload.dimension;
        ServerWorld world = DimensionHelper.getWorld(dimension, player.getServer());

        // The dimensions the console can be set to, as its dimension controls do
        boolean available = world != null && tardis.getSystem(TardisSystemResearch.class).getAvailableDimensions().contains(dimension);
        if (!available || !world.getWorldBorder().contains(payload.x, payload.z)) {
            player.sendMessage(Text.translatable("message.dwm.tardis.map_destination.unavailable"), true);
            return;
        }

        BlockPos destination = new BlockPos(payload.x, height(world, payload.x, payload.z, tardis.getDestinationExteriorPosition().getY()), payload.z);

        tardis.setDestinationDimension(dimension);
        tardis.setDestinationPosition(destination);
        tardis.markConsoleTilesUpdated();
        player.sendMessage(Text.translatable("message.dwm.tardis.map_destination.set", destination.getX(), destination.getZ(), dimension.getValue().getPath().replace("_", " ").toUpperCase()), true);
    }

    // The ground where it is loaded (looking at it must not load it), and where there is no ceiling to mistake for it.
    private static int height(ServerWorld world, int x, int z, int currentY) {
        if (!world.getDimension().hasCeiling() && world.isChunkLoaded(x >> 4, z >> 4)) return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

        return MathHelper.clamp(currentY, world.getBottomY(), world.getTopY() - 2);
    }
}
