package net.drgmes.dwm.network.client;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.phone.TardisPhoneManager;
import net.drgmes.dwm.network.IPacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record TardisPhoneDialOpenPacket(
    BlockPos blockPos,
    List<TardisPhoneManager.DialEntry> entries
) implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_phone_dial_open");
    public static final CustomPayload.Id<TardisPhoneDialOpenPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    private static final PacketCodec<PacketByteBuf, TardisPhoneManager.DialEntry> ENTRY_CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, TardisPhoneManager.DialEntry::tardisId,
        PacketCodecs.STRING, TardisPhoneManager.DialEntry::ownerName,
        TardisPhoneManager.DialEntry::new
    );

    public static final PacketCodec<PacketByteBuf, TardisPhoneDialOpenPacket> PACKET_CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC, TardisPhoneDialOpenPacket::blockPos,
        ENTRY_CODEC.collect(PacketCodecs.toList()), TardisPhoneDialOpenPacket::entries,
        TardisPhoneDialOpenPacket::new
    );

    public TardisPhoneDialOpenPacket(BlockPos blockPos, MinecraftServer server, String excludingTardisId) {
        this(blockPos, new ArrayList<>(TardisPhoneManager.listCallableTardises(server, excludingTardisId)));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
