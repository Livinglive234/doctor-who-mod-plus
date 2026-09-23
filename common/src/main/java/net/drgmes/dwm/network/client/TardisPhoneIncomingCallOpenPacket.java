package net.drgmes.dwm.network.client;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.network.IPacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record TardisPhoneIncomingCallOpenPacket(
    BlockPos blockPos,
    String callerName
) implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_phone_incoming_call_open");
    public static final CustomPayload.Id<TardisPhoneIncomingCallOpenPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisPhoneIncomingCallOpenPacket> PACKET_CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC, TardisPhoneIncomingCallOpenPacket::blockPos,
        PacketCodecs.STRING, TardisPhoneIncomingCallOpenPacket::callerName,
        TardisPhoneIncomingCallOpenPacket::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
