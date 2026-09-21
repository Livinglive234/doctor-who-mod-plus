package net.drgmes.dwm.network.client;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.network.IPacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record TardisTakeoffSoundPacket(
    BlockPos blockPos,
    float volume,
    int fadeDelay,
    int fadeTicks
) implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_takeoff_sound");
    public static final CustomPayload.Id<TardisTakeoffSoundPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisTakeoffSoundPacket> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, TardisTakeoffSoundPacket payload) {
            buf.writeBlockPos(payload.blockPos);
            buf.writeFloat(payload.volume);
            buf.writeVarInt(payload.fadeDelay);
            buf.writeVarInt(payload.fadeTicks);
        }

        @Override
        public TardisTakeoffSoundPacket decode(PacketByteBuf buf) {
            return new TardisTakeoffSoundPacket(
                buf.readBlockPos(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt()
            );
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
