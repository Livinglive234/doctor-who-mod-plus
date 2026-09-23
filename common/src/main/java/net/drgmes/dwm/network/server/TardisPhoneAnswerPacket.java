package net.drgmes.dwm.network.server;

import dev.architectury.networking.NetworkManager;
import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.phone.TardisPhoneManager;
import net.drgmes.dwm.network.IPacket;
import net.drgmes.dwm.utils.helpers.TardisHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

// Sent from TardisConsoleUnitPhoneIncomingCallScreen's Answer button - the screen's own opening (rather than
// answering straight from the right-click) is what makes answering a deliberate choice instead of a reflex.
public record TardisPhoneAnswerPacket() implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_phone_answer");
    public static final CustomPayload.Id<TardisPhoneAnswerPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisPhoneAnswerPacket> PACKET_CODEC = PacketCodec.unit(new TardisPhoneAnswerPacket());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }

    public static void handle(TardisPhoneAnswerPacket payload, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return;
            if (!(player.getWorld() instanceof ServerWorld tardisWorld) || !TardisHelper.isTardisDimension(tardisWorld)) return;

            TardisStateManager.get(tardisWorld).ifPresent((tardis) -> {
                TardisPhoneManager.answer(player.getServer(), tardis.getId(), player);
            });
        });
    }
}
