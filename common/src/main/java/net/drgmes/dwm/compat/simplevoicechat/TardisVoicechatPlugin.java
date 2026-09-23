package net.drgmes.dwm.compat.simplevoicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import net.drgmes.dwm.DWM;
import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockEntity;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.phone.TardisPhoneCall;
import net.drgmes.dwm.common.tardis.phone.TardisPhoneManager;
import net.drgmes.dwm.enums.TardisConsoleUnitControlRole;
import net.drgmes.dwm.utils.helpers.DimensionHelper;
import net.drgmes.dwm.utils.helpers.TardisHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.UUID;

/**
 * Bridges a connected TardisPhoneCall's two console rooms: whatever a player in one room says, everyone in the
 * other hears too, on top of (not instead of) Simple Voice Chat's own proximity chat. Registered as a Fabric
 * entrypoint ("voicechat" in fabric.mod.json) and, for Forge/NeoForge, by the @ForgeVoicechatPlugin annotation
 * below - Simple Voice Chat discovers plugins differently per loader, but both point at this one class.
 */
@ForgeVoicechatPlugin
public class TardisVoicechatPlugin implements VoicechatPlugin {
    public static final String PLUGIN_ID = DWM.MODID + "_phone";

    // How close a speaker needs to be to their own phone to be picked up at all - without this, anyone anywhere
    // in the room (or the whole interior) got relayed to the other side regardless of distance from the phone.
    private static final double MIC_RANGE = 4.0;

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        DWM.LOGGER.info("Simple Voice Chat found - TARDIS phone calls will carry voice");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, TardisVoicechatPlugin::onMicrophonePacket);
    }

    private static void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null) return;

        ServerPlayer apiSpeaker = senderConnection.getPlayer();
        if (!(apiSpeaker.getPlayer() instanceof ServerPlayerEntity speaker)) return;
        if (!(speaker.getWorld() instanceof ServerWorld speakerWorld) || !TardisHelper.isTardisDimension(speakerWorld)) return;

        String speakerTardisId = DimensionHelper.getWorldId(speakerWorld);
        Optional<TardisPhoneCall> callHolder = TardisPhoneManager.getCall(speakerTardisId);
        if (callHolder.isEmpty() || !callHolder.get().isConnected()) return;
        TardisPhoneCall call = callHolder.get();

        MinecraftServer server = speaker.getServer();

        // Simple Voice Chat dispatches this event from its own packet-processing thread, not the server thread -
        // TardisStateManager.get() below (via getPhonePosition) rebinds the tardis's chunk loaders as a side
        // effect, which mutates vanilla chunk-ticket state that is only safe to touch from the server thread.
        // Doing that off-thread corrupted it and crashed the server, so hop onto the server thread first.
        server.execute(() -> {
            BaseTardisConsoleUnitBlockEntity speakerConsoleUnit = getConsoleUnit(speakerWorld);
            if (speakerConsoleUnit == null) return;

            Vec3d speakerPhonePosition = speakerConsoleUnit.getControlPosition(TardisConsoleUnitControlRole.PHONE);
            if (speakerPhonePosition == null || speaker.getPos().squaredDistanceTo(speakerPhonePosition) > MIC_RANGE * MIC_RANGE) return;

            String listenerTardisId = call.otherPartyOf(speakerTardisId);
            ServerWorld otherWorld = DimensionHelper.getModWorld(listenerTardisId, server);
            if (otherWorld == null) return;

            BaseTardisConsoleUnitBlockEntity consoleUnit = getConsoleUnit(otherWorld);
            if (consoleUnit == null) return;

            // Each listener hears it as coming from their own room's phone, not the speaker's real (differently
            // dimensioned) position - so it fades exactly like proximity chat does as they walk away from it.
            Vec3d phonePosition = consoleUnit.getControlPosition(TardisConsoleUnitControlRole.PHONE);
            if (phonePosition == null) return;

            VoicechatServerApi api = event.getVoicechat();
            Position position = api.createPosition(phonePosition.x, phonePosition.y, phonePosition.z);

            LocationalSoundPacket soundPacket = event.getPacket().locationalSoundPacketBuilder()
                .position(position)
                .distance((float) api.getVoiceChatDistance())
                .opusEncodedData(event.getPacket().getOpusEncodedData())
                .sender(speaker.getUuid())
                .channelId(call.getChannelId(speakerTardisId))
                .sequenceNumber(call.nextSequenceNumber(speakerTardisId))
                .build();

            // The listening side's own privacy switch (not the speaker's) decides whether its room hears this
            // normally or only the one player on the phone there - flipping it never affects what the OTHER
            // room can hear of this side, only what this side can hear of the other.
            boolean privacyOn = consoleUnit.isPhonePrivacyEnabled();
            UUID restrictTo = privacyOn ? call.phoneUserOf(listenerTardisId) : null;

            for (ServerPlayerEntity listener : otherWorld.getPlayers()) {
                if (restrictTo != null && !listener.getUuid().equals(restrictTo)) continue;

                VoicechatConnection listenerConnection = api.getConnectionOf(listener.getUuid());
                if (listenerConnection != null && listenerConnection.isConnected()) {
                    api.sendLocationalSoundPacketTo(listenerConnection, soundPacket);
                }
            }
        });
    }

    private static BaseTardisConsoleUnitBlockEntity getConsoleUnit(ServerWorld world) {
        Optional<TardisStateManager> tardisHolder = TardisStateManager.get(world);
        if (tardisHolder.isEmpty()) return null;

        return world.getBlockEntity(tardisHolder.get().getMainConsolePosition()) instanceof BaseTardisConsoleUnitBlockEntity consoleUnit ? consoleUnit : null;
    }
}
