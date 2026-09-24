package net.drgmes.dwm.common.tardis.phone;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockEntity;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.systems.TardisSystemMaterialization;
import net.drgmes.dwm.enums.TardisConsoleUnitControlRole;
import net.drgmes.dwm.setup.ModDimensions;
import net.drgmes.dwm.setup.ModSounds;
import net.drgmes.dwm.utils.helpers.DimensionHelper;
import net.drgmes.dwm.utils.helpers.TardisHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The TARDIS phone: a server-global, in-memory registry of who's calling whom. One TardisPhoneCall per pair, keyed
 * by both tardis ids so either side finds it by its own. Nothing here is saved (see TardisPhoneCall).
 */
public class TardisPhoneManager {
    public record DialEntry(String tardisId, String ownerName) {
    }

    private static final Map<String, TardisPhoneCall> CALLS = new HashMap<>();

    public static void clear() {
        CALLS.clear();
    }

    public static Optional<TardisPhoneCall> getCall(String tardisId) {
        return Optional.ofNullable(CALLS.get(tardisId));
    }

    // Every other TARDIS on the server with an owner, for the dial screen - a TARDIS nobody has claimed has no one
    // to answer it. Excludes only the TARDIS the dialer is currently standing in (whether or not it's theirs, e.g.
    // a guest); any other TARDIS they own themselves is still a valid, if unusual, thing to call.
    public static List<DialEntry> listCallableTardises(MinecraftServer server, String excludingTardisId) {
        List<DialEntry> entries = new ArrayList<>();

        for (RegistryKey<World> worldKey : ModDimensions.WORLDS) {
            ServerWorld world = DimensionHelper.getWorld(worldKey, server);
            if (world == null || !TardisHelper.isTardisDimension(world)) continue;

            String tardisId = DimensionHelper.getWorldId(world);
            if (tardisId.equals(excludingTardisId)) continue;

            TardisStateManager.get(world).ifPresent((tardis) -> {
                UUID ownerId = tardis.getOwner();
                if (ownerId == null) return;
                entries.add(new DialEntry(tardisId, TardisHelper.getOwnerDisplayName(ownerId, server)));
            });
        }

        entries.sort((a, b) -> a.ownerName.compareToIgnoreCase(b.ownerName));
        return entries;
    }

    public static void startCall(MinecraftServer server, String callerId, String calleeId, ServerPlayerEntity dialer) {
        if (callerId.equals(calleeId)) return;

        if (CALLS.containsKey(callerId)) {
            dialer.sendMessage(DWM.TEXTS.PHONE_LINE_BUSY, true);
            return;
        }

        Optional<TardisStateManager> callerHolder = getTardis(server, callerId);
        Optional<TardisStateManager> calleeHolder = getTardis(server, calleeId);
        if (callerHolder.isEmpty() || calleeHolder.isEmpty()) {
            dialer.sendMessage(DWM.TEXTS.PHONE_UNREACHABLE, true);
            return;
        }

        if (CALLS.containsKey(calleeId)) {
            dialer.sendMessage(DWM.TEXTS.PHONE_LINE_BUSY, true);
            return;
        }

        TardisPhoneCall call = new TardisPhoneCall(callerId, calleeId, dialer.getUuid());
        CALLS.put(callerId, call);
        CALLS.put(calleeId, call);

        TardisStateManager callerTardis = callerHolder.get();
        TardisStateManager calleeTardis = calleeHolder.get();

        announceMessage(callerTardis, DWM.TEXTS.PHONE_CALLING);
        // Whoever's actually dialing, not the tardis owner - a guest or key-holder placing the call from someone
        // else's TARDIS shouldn't show up to the callee as if the owner were the one calling.
        announceMessage(calleeTardis, DWM.TEXTS.PHONE_RINGING.apply(dialer.getName().getString()));

        playRingback(callerTardis);
        playRing(calleeTardis);
    }

    public static void answer(MinecraftServer server, String tardisId, ServerPlayerEntity answerer) {
        TardisPhoneCall call = CALLS.get(tardisId);
        if (call == null || call.isConnected() || !call.calleeId.equals(tardisId)) return;

        call.connect(answerer.getUuid());
        stopRinging(server, call);

        getTardis(server, call.callerId).ifPresent((tardis) -> announceMessage(tardis, DWM.TEXTS.PHONE_CONNECTED));
        getTardis(server, call.calleeId).ifPresent((tardis) -> announceMessage(tardis, DWM.TEXTS.PHONE_CONNECTED));
    }

    public static void end(MinecraftServer server, String tardisId, Text reasonForOtherParty) {
        TardisPhoneCall call = CALLS.remove(tardisId);
        if (call == null) return;
        CALLS.remove(call.otherPartyOf(tardisId));
        if (!call.isConnected()) stopRinging(server, call);

        getTardis(server, tardisId).ifPresent((tardis) -> announce(tardis, DWM.TEXTS.PHONE_ENDED, ModSounds::playTardisPhoneEndSound));
        getTardis(server, call.otherPartyOf(tardisId)).ifPresent((tardis) -> announce(tardis, reasonForOtherParty, ModSounds::playTardisPhoneEndSound));
    }

    public static void tick(MinecraftServer server) {
        if (CALLS.isEmpty()) return;

        // Both ids of a call point at the same instance - collect the distinct calls first so each is only ticked once.
        // end()'s first tardisId gets the generic "ended" message, its other party gets the specific reason - here
        // that's the callee (never answered) getting "ended" and the caller getting "No answer".
        for (TardisPhoneCall call : new ArrayList<>(CALLS.values()).stream().distinct().toList()) {
            if (call.tickRinging()) {
                end(server, call.calleeId, DWM.TEXTS.PHONE_NO_ANSWER);
                continue;
            }

            if (call.tickCallerRingback()) getTardis(server, call.callerId).ifPresent(TardisPhoneManager::playRingback);
            if (call.tickCalleeRing()) getTardis(server, call.calleeId).ifPresent(TardisPhoneManager::playRing);
        }
    }

    private static Optional<TardisStateManager> getTardis(MinecraftServer server, String tardisId) {
        ServerWorld world = DimensionHelper.getModWorld(tardisId, server);
        return world == null ? Optional.empty() : TardisStateManager.get(world);
    }

    private static void announce(TardisStateManager tardis, Text message, BiConsumer<World, BlockPos> sound) {
        sound.accept(tardis.getWorld(), tardis.getMainConsolePosition());
        announceMessage(tardis, message);
    }

    private static void announceMessage(TardisStateManager tardis, Text message) {
        for (ServerPlayerEntity player : tardis.getWorld().getPlayers()) {
            player.sendMessage(message, true);
        }
    }

    // The ring and ringback are one-shot plays of a few seconds each (looped by re-playing them), so on their own
    // they'd finish out after the call is answered or dropped - this cuts whatever's mid-play for everyone in
    // earshot: both TARDISes' interiors and, for the ring, outside the callee's exterior.
    private static void stopRinging(MinecraftServer server, TardisPhoneCall call) {
        List<StopSoundS2CPacket> packets = List.of(
            new StopSoundS2CPacket(ModSounds.TARDIS_PHONE_RING.get().getId(), SoundCategory.BLOCKS),
            new StopSoundS2CPacket(ModSounds.TARDIS_PHONE_RINGBACK.get().getId(), SoundCategory.BLOCKS)
        );

        for (String tardisId : List.of(call.callerId, call.calleeId)) {
            getTardis(server, tardisId).ifPresent((tardis) -> {
                List<ServerPlayerEntity> players = new ArrayList<>(tardis.getWorld().getPlayers());

                ServerWorld exteriorWorld = tardis.getExteriorWorld();
                if (exteriorWorld != null && exteriorWorld != tardis.getWorld()) players.addAll(exteriorWorld.getPlayers());

                for (ServerPlayerEntity player : players) {
                    packets.forEach(player.networkHandler::sendPacket);
                }
            });
        }
    }

    private static void playRingback(TardisStateManager tardis) {
        ModSounds.playTardisPhoneRingbackSound(tardis.getWorld(), tardis.getMainConsolePosition());
    }

    // Interior: anchored to the phone control's own position (not the console's) so it fades and pans the way
    // any other positional sound does as a player walks away from it or around a corner. Exterior: an ordinary,
    // un-anchored-to-a-prop ring, since there's no exterior phone model to tie it to - only while an exterior is
    // actually standing there materialized, not while it's off flying somewhere or mid-demat/remat.
    private static void playRing(TardisStateManager tardis) {
        if (tardis.getWorld().getBlockEntity(tardis.getMainConsolePosition()) instanceof BaseTardisConsoleUnitBlockEntity consoleUnit) {
            Vec3d phonePosition = consoleUnit.getControlPosition(TardisConsoleUnitControlRole.PHONE);
            if (phonePosition != null) ModSounds.playTardisPhoneRingSound(tardis.getWorld(), phonePosition);
        }

        TardisSystemMaterialization materializationSystem = tardis.getSystem(TardisSystemMaterialization.class);
        if (!materializationSystem.isMaterialized() || materializationSystem.isInMaterializationProcess()) return;

        ServerWorld exteriorWorld = tardis.getExteriorWorld();
        if (exteriorWorld != null) ModSounds.playTardisPhoneRingSound(exteriorWorld, tardis.getCurrentExteriorPosition());
    }
}
