package net.drgmes.dwm.network.server;

import dev.architectury.networking.NetworkManager;
import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.systems.TardisSystemMaterialization;
import net.drgmes.dwm.compat.immersiveportals.ExteriorViewCompat;
import net.drgmes.dwm.network.IPacket;
import net.drgmes.dwm.network.client.TardisExteriorViewOpenPacket;
import net.drgmes.dwm.setup.ModCompats;
import net.drgmes.dwm.utils.helpers.TardisHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

// Sent from the monitor's Exterior View button (active) and when its screen closes (not active).
public record TardisExteriorViewPacket(
    boolean active
) implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_exterior_view");
    public static final CustomPayload.Id<TardisExteriorViewPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisExteriorViewPacket> PACKET_CODEC = PacketCodec.tuple(
        PacketCodecs.BOOL, TardisExteriorViewPacket::active,
        TardisExteriorViewPacket::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }

    public static void handle(TardisExteriorViewPacket payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return;
            if (!ModCompats.immersivePortals()) return;

            if (!payload.active) {
                ExteriorViewCompat.stopViewing(player);
                return;
            }

            if (!(player.getWorld() instanceof ServerWorld tardisWorld) || !TardisHelper.isTardisDimension(tardisWorld)) return;

            TardisStateManager.get(tardisWorld).ifPresent((tardis) -> {
                TardisSystemMaterialization materializationSystem = tardis.getSystem(TardisSystemMaterialization.class);
                if (!materializationSystem.isMaterialized() || materializationSystem.isInMaterializationProcess()) {
                    player.sendMessage(DWM.TEXTS.EXTERIOR_VIEW_UNAVAILABLE, true);
                    return;
                }

                ExteriorViewCompat.startViewing(player, tardis.getCurrentExteriorDimension(), tardis.getCurrentExteriorPosition());

                new TardisExteriorViewOpenPacket(
                    tardis.getMainConsolePosition(),
                    tardis.getCurrentExteriorDimension().getValue(),
                    tardis.getCurrentExteriorPosition(),
                    tardis.getCurrentExteriorFacing().getId()
                ).sendTo(player);
            });
        });
    }
}
