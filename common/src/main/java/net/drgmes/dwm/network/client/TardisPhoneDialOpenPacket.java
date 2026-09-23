package net.drgmes.dwm.network.client;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.phone.TardisPhoneManager;
import net.drgmes.dwm.network.IPacket;
import net.drgmes.dwm.utils.helpers.CommonHelper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public record TardisPhoneDialOpenPacket(
    BlockPos blockPos,
    NbtCompound tag
) implements IPacket {
    public static final Identifier ID = DWM.getIdentifier("tardis_phone_dial_open");
    public static final CustomPayload.Id<TardisPhoneDialOpenPacket> PACKET_ID = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, TardisPhoneDialOpenPacket> PACKET_CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC, TardisPhoneDialOpenPacket::blockPos,
        PacketCodecs.NBT_COMPOUND, TardisPhoneDialOpenPacket::tag,
        TardisPhoneDialOpenPacket::new
    );

    public TardisPhoneDialOpenPacket(BlockPos blockPos, MinecraftServer server, String excludingTardisId) {
        this(blockPos, createEntriesTag(server, excludingTardisId));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }

    public static List<TardisPhoneManager.DialEntry> readEntries(NbtCompound tag) {
        List<TardisPhoneManager.DialEntry> entries = new ArrayList<>();
        for (String key : tag.getKeys()) {
            NbtCompound entryTag = tag.getCompound(key);
            entries.add(new TardisPhoneManager.DialEntry(entryTag.getString("tardisId"), entryTag.getString("ownerName")));
        }
        return entries;
    }

    private static NbtCompound createEntriesTag(MinecraftServer server, String excludingTardisId) {
        NbtCompound tag = new NbtCompound();
        AtomicInteger i = new AtomicInteger();

        TardisPhoneManager.listCallableTardises(server, excludingTardisId).forEach((entry) -> {
            NbtCompound entryTag = new NbtCompound();
            entryTag.putString("tardisId", entry.tardisId());
            entryTag.putString("ownerName", entry.ownerName());
            tag.put(CommonHelper.formatIndexString(i.incrementAndGet()), entryTag);
        });

        return tag;
    }
}
