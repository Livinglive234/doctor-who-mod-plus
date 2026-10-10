package net.drgmes.dwm.network.client;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.network.IPacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record TardisExteriorViewOpenPacket(
    BlockPos consolePos,
    Identifier dimension,
    BlockPos exteriorPos,
    int facingId
) implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_exterior_view_open");
    public static final CustomPayload.Id<TardisExteriorViewOpenPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisExteriorViewOpenPacket> PACKET_CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC, TardisExteriorViewOpenPacket::consolePos,
        Identifier.PACKET_CODEC, TardisExteriorViewOpenPacket::dimension,
        BlockPos.PACKET_CODEC, TardisExteriorViewOpenPacket::exteriorPos,
        PacketCodecs.VAR_INT, TardisExteriorViewOpenPacket::facingId,
        TardisExteriorViewOpenPacket::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
