package net.drgmes.dwm.network.server;

import dev.architectury.networking.NetworkManager;
import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.phone.TardisPhoneManager;
import net.drgmes.dwm.network.IPacket;
import net.drgmes.dwm.utils.helpers.TardisHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public record TardisPhoneDialPacket(
    String targetTardisId
) implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_phone_dial");
    public static final CustomPayload.Id<TardisPhoneDialPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisPhoneDialPacket> PACKET_CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, TardisPhoneDialPacket::targetTardisId,
        TardisPhoneDialPacket::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }

    public static void handle(TardisPhoneDialPacket payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return;
            if (!(player.getWorld() instanceof ServerWorld tardisWorld) || !TardisHelper.isTardisDimension(tardisWorld)) return;

            TardisStateManager.get(tardisWorld).ifPresent((tardis) -> {
                TardisPhoneManager.startCall(player.getServer(), tardis.getId(), payload.targetTardisId, player);
            });
        });
    }
}
