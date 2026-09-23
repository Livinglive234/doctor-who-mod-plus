package net.drgmes.dwm.common.tardis.phone;

import net.drgmes.dwm.DWM;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A call between two TARDISes, by tardis id. Purely in-memory and server-side, like TardisEmergencyReturn's own
 * sequence - nothing here is saved, so a restart mid-call just drops it rather than leaving a stuck line.
 */
public class TardisPhoneCall {
    public final String callerId;
    public final String calleeId;

    // The specific player on each side the privacy switch (see TardisVoicechatPlugin) restricts relayed audio
    // to, if that side's switch is on - the one who dialed, and whoever answers (not known until they do).
    public final UUID callerPlayerId;
    private UUID calleePlayerId;

    // Each direction gets its own channel id and sequence counter, for TardisVoicechatPlugin to relay mic packets
    // under, once connected() is true.
    public final UUID callerToCalleeChannelId = UUID.randomUUID();
    public final UUID calleeToCallerChannelId = UUID.randomUUID();
    public final AtomicLong callerToCalleeSequence = new AtomicLong();
    public final AtomicLong calleeToCallerSequence = new AtomicLong();

    private boolean connected;
    private int ringTicks = DWM.TIMINGS.PHONE_RING_TIMEOUT;

    // Separate timers, since the caller's ringback tone and the callee's ring are different recordings with
    // different natural lengths - each loops on its own cadence rather than sharing one interval.
    private int callerRingbackTicks = DWM.TIMINGS.PHONE_RINGBACK_INTERVAL;
    private int calleeRingTicks = DWM.TIMINGS.PHONE_RING_INTERVAL;

    public TardisPhoneCall(String callerId, String calleeId, UUID callerPlayerId) {
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callerPlayerId = callerPlayerId;
    }

    public boolean involves(String tardisId) {
        return this.callerId.equals(tardisId) || this.calleeId.equals(tardisId);
    }

    public String otherPartyOf(String tardisId) {
        return this.callerId.equals(tardisId) ? this.calleeId : this.callerId;
    }

    // The player the privacy switch on tardisId's own side should restrict listeners to, once connected.
    public UUID phoneUserOf(String tardisId) {
        return tardisId.equals(this.callerId) ? this.callerPlayerId : this.calleePlayerId;
    }

    public boolean isConnected() {
        return this.connected;
    }

    public void connect(UUID calleePlayerId) {
        this.connected = true;
        this.calleePlayerId = calleePlayerId;
    }

    // The channel id and sequence counter for audio travelling away from tardisId, towards its other party.
    public UUID getChannelId(String fromTardisId) {
        return fromTardisId.equals(this.callerId) ? this.callerToCalleeChannelId : this.calleeToCallerChannelId;
    }

    public long nextSequenceNumber(String fromTardisId) {
        AtomicLong sequence = fromTardisId.equals(this.callerId) ? this.callerToCalleeSequence : this.calleeToCallerSequence;
        return sequence.getAndIncrement();
    }

    // Ticks down while ringing; irrelevant once connected. Returns true the instant it runs out.
    public boolean tickRinging() {
        if (this.connected) return false;
        return --this.ringTicks <= 0;
    }

    // Tick down independently of tickRinging's timeout, each resetting itself on its own interval so the sound
    // already played once when the call started (see TardisPhoneManager.startCall) can be replayed to loop it
    // while still ringing.
    public boolean tickCallerRingback() {
        if (this.connected) return false;
        if (--this.callerRingbackTicks > 0) return false;
        this.callerRingbackTicks = DWM.TIMINGS.PHONE_RINGBACK_INTERVAL;
        return true;
    }

    public boolean tickCalleeRing() {
        if (this.connected) return false;
        if (--this.calleeRingTicks > 0) return false;
        this.calleeRingTicks = DWM.TIMINGS.PHONE_RING_INTERVAL;
        return true;
    }
}
