package net.drgmes.dwm.network.client;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.network.IPacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// The server has let go of the exterior view (it can no longer stream the chunks it needs), so the screen closes.
public record TardisExteriorViewClosePacket() implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_exterior_view_close");
    public static final CustomPayload.Id<TardisExteriorViewClosePacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisExteriorViewClosePacket> PACKET_CODEC = PacketCodec.unit(new TardisExteriorViewClosePacket());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
