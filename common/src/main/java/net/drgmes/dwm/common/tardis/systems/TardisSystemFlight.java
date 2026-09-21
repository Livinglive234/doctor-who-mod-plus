package net.drgmes.dwm.common.tardis.systems;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.systems.flight.TardisFlightHistoryEntry;
import net.drgmes.dwm.common.tardis.systems.flight.TardisFlightWaypointEntry;
import net.drgmes.dwm.common.tardis.systems.flight.TardisFlyoverPlanner;
import net.drgmes.dwm.common.tardis.systems.flight.TardisFlyoverSession;
import net.drgmes.dwm.setup.ModSounds;
import net.drgmes.dwm.utils.helpers.CommonHelper;
import net.minecraft.nbt.NbtCompound;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class TardisSystemFlight extends TardisBaseSystem {
    public static final int HISTORY_SIZE = 100;

    private enum EStep {
        NONE,
        INITED,
        WAIT_FOR_DEMAT,
        PROCESSING,
    }

    private final List<Consumer<Boolean>> callbacks = new ArrayList<>();
    private final TardisFlyoverPlanner flyoverPlanner;
    private final TardisFlyoverSession flyover;
    private List<TardisFlightHistoryEntry> history = new ArrayList<>();
    private List<TardisFlightWaypointEntry> waypoints = new ArrayList<>();

    private EStep step = EStep.NONE;
    private UUID initiatorId;

    private int tick = -1;
    private int soundTick = -1;

    // Recorded when the flight starts and synced with the rest of this system: the console screen shows the progress
    // on the client, which has no world to work the flight time out from.
    private int duration = 0;
    private int padding = 0;
    private int paddingRemaining = 0;
    private boolean takeoffWasInstant = false;

    public TardisSystemFlight(TardisStateManager tardis) {
        super(tardis);

        this.flyoverPlanner = new TardisFlyoverPlanner(tardis);
        this.flyover = new TardisFlyoverSession(tardis, this.flyoverPlanner);
    }

    @Override
    public boolean inProgress() {
        return this.step != EStep.NONE;
    }

    @Override
    public void readNbt(NbtCompound tag) {
        if (tag.contains("step")) this.step = EStep.valueOf(tag.getString("step"));
        if (tag.contains("initiatorId")) this.initiatorId = tag.getUuid("initiatorId");
        if (tag.contains("tick")) this.tick = tag.getInt("tick");
        if (tag.contains("soundTick")) this.soundTick = tag.getInt("soundTick");
        if (tag.contains("duration")) this.duration = tag.getInt("duration");
        if (tag.contains("padding")) this.padding = tag.getInt("padding");
        if (tag.contains("paddingRemaining")) this.paddingRemaining = tag.getInt("paddingRemaining");

        if (tag.contains("history")) {
            this.history.clear();

            NbtCompound historyTag = tag.getCompound("history");
            List<String> keys = new ArrayList<>(historyTag.getKeys());
            keys.sort(Comparator.comparing((key) -> key));

            keys.forEach((key) -> {
                TardisFlightHistoryEntry entry = TardisFlightHistoryEntry.createFromNbt(historyTag.getCompound(key));
                if (!this.history.contains(entry)) this.history.add(entry);
            });
        }

        if (tag.contains("waypoints")) {
            this.waypoints.clear();

            NbtCompound waypointsTag = tag.getCompound("waypoints");
            List<String> keys = new ArrayList<>(waypointsTag.getKeys());
            keys.sort(Comparator.comparing((key) -> key));

            keys.forEach((key) -> {
                TardisFlightWaypointEntry entry = TardisFlightWaypointEntry.createFromNbt(waypointsTag.getCompound(key));
                if (!this.waypoints.contains(entry)) this.waypoints.add(entry);
            });
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        if (this.initiatorId != null) tag.putUuid("initiatorId", this.initiatorId);

        tag.putString("step", this.step.name());
        tag.putInt("tick", this.tick);
        tag.putInt("soundTick", this.soundTick);
        tag.putInt("duration", this.duration);
        tag.putInt("padding", this.padding);
        tag.putInt("paddingRemaining", this.paddingRemaining);

        AtomicInteger i1 = new AtomicInteger();
        NbtCompound historyTag = new NbtCompound();
        this.history.forEach((entry) -> historyTag.put(CommonHelper.formatIndexString(i1.incrementAndGet()), entry.writeNbt(new NbtCompound())));
        tag.put("history", historyTag);

        AtomicInteger i2 = new AtomicInteger();
        NbtCompound waypointsTag = new NbtCompound();
        this.waypoints.forEach((entry) -> waypointsTag.put(CommonHelper.formatIndexString(i2.incrementAndGet()), entry.writeNbt(new NbtCompound())));
        tag.put("waypoints", waypointsTag);

        return tag;
    }

    @Override
    public void tick() {
        if (!this.isEnabled() || !this.inProgress()) return;
        boolean advanced = this.advanceCountdown();

        switch (this.step) {
            case INITED -> {
                if (!this.takeoff()) {
                    this.reset();
                    this.applyCallbacks(false);
                    this.tardis.markConsoleTilesUpdated();
                }
            }

            case PROCESSING -> {
                this.playFlightSound();
                this.paddingRemaining = Math.max(0, this.paddingRemaining - this.flyover.updateFlybys(this.tick, this.duration));

                // The countdown can hold still, so these are keyed to it reaching a value, not to the tick.
                if (advanced) {
                    if (this.tick == DWM.FLYOVER.ARRIVAL_DURATION) this.flyover.spawnArrival(this.tick);
                    if (this.tick == DWM.FLYOVER.DROP_DURATION) this.flyover.spawnDrop(this.tick);
                    if (this.tick == DWM.FLYOVER.SLAM_THUD_LEAD) this.flyover.playLandingThud();
                    if (this.tick % 3 == 0) this.tardis.markConsoleTilesUpdated();
                }

                if (this.tick == 0) {
                    boolean isSuccessful = this.land();

                    this.reset();
                    this.applyCallbacks(isSuccessful);
                    this.tardis.markConsoleTilesUpdated();
                }
            }
        }
    }

    public boolean init(boolean flag, UUID initiatorId) {
        if (!this.isEnabled() || this.isInFlight() || flag == this.inProgress()) return false;

        if (!flag) {
            this.reset();
            this.resetCallbacks();
            return true;
        }

        TardisSystemMaterialization materializationSystem = this.tardis.getSystem(TardisSystemMaterialization.class);
        if (materializationSystem.inProgress()) return false;

        this.step = EStep.INITED;
        this.initiatorId = initiatorId;
        this.tick = 0;
        this.soundTick = 0;
        return true;
    }

    public boolean takeoff() {
        if (!this.isEnabled() || !this.inProgress() || this.tick > 0) return false;

        TardisSystemMaterialization materializationSystem = this.tardis.getSystem(TardisSystemMaterialization.class);
        if (!materializationSystem.isEnabled() || materializationSystem.inProgress()) return false;

        materializationSystem.putCallback((flag) -> {
            if (!flag || this.step == EStep.NONE) return;

            this.step = EStep.PROCESSING;
            this.duration = this.getFlightDuration();
            this.padding = this.flyoverPlanner.padding();
            this.paddingRemaining = this.padding;
            this.tick = this.duration;
            this.tardis.markConsoleTilesUpdated();
            this.flyover.spawnDeparture(this.tick, this.takeoffWasInstant);
        });

        this.takeoffWasInstant = materializationSystem.isMaterialized() && this.flyoverPlanner.shouldTakeOffInstantly();

        if (!materializationSystem.isMaterialized()) {
            materializationSystem.applyCallbacks(true);
        }
        else if (materializationSystem.init(false, this.initiatorId, this.takeoffWasInstant)) {
            this.step = EStep.WAIT_FOR_DEMAT;
        }
        else {
            materializationSystem.reset();
            materializationSystem.applyCallbacks(false);
            return false;
        }

        return true;
    }

    public boolean land() {
        if (!this.isEnabled() || !this.inProgress() || this.tick > 0) return false;

        TardisSystemMaterialization materializationSystem = this.tardis.getSystem(TardisSystemMaterialization.class);
        if (!materializationSystem.isEnabled() || materializationSystem.inProgress()) return false;

        boolean landInstantly = this.flyover.landsInstantly();
        this.flyover.playLandingThud();
        this.flyover.finishLanding();

        this.tardis.setDimension(this.tardis.getDestinationExteriorDimension(), true);
        this.tardis.setPosition(this.tardis.getDestinationExteriorPosition(), true);
        this.tardis.setFacing(this.tardis.getDestinationExteriorFacing(), true);

        materializationSystem.putCallback((flag) -> {
            if (flag) {
                this.tardis.raiseLandingShields();

                // Silent travel makes no sound at all but this: the thud of arriving, inside, whatever the landing.
                if (this.tardis.isSilentTravelEnabled()) ModSounds.playTardisGroundLandingSound(this.tardis.getWorld(), this.tardis.getMainConsolePosition());
            }

            this.reset();
            this.applyCallbacks(flag);

            TardisSystemResearch researchSystem = this.tardis.getSystem(TardisSystemResearch.class);
            researchSystem.updateVisitedStructures(this.initiatorId);
            researchSystem.updateVisitedBiomes(this.initiatorId);
            researchSystem.updateVisitedWorlds(this.initiatorId);

            this.addHistoryEntry(new TardisFlightHistoryEntry(
                this.tardis.getCurrentExteriorDimension(),
                this.tardis.getCurrentExteriorPosition(),
                this.tardis.getCurrentExteriorFacing()
            ));
        });

        if (!materializationSystem.init(true, this.initiatorId, landInstantly)) {
            materializationSystem.reset();
            materializationSystem.applyCallbacks(false);
            return false;
        }

        return true;
    }

    public void reset() {
        this.step = EStep.NONE;
        this.initiatorId = null;
        this.tick = -1;
        this.soundTick = -1;
        this.padding = 0;
        this.paddingRemaining = 0;
        this.takeoffWasInstant = false;
        this.flyover.reset();
    }

    public void putCallback(Consumer<Boolean> callback) {
        this.callbacks.add(callback);
    }

    public void applyCallbacks(boolean isSuccessful) {
        this.callbacks.forEach((callback) -> callback.accept(isSuccessful));
        this.resetCallbacks();
    }

    public void resetCallbacks() {
        this.callbacks.clear();
    }

    public boolean isInFlight() {
        return this.inProgress() && this.tick > 0;
    }

    // Runs on the client too (the console screen shows it), so it must not need a world.
    public int getProgressPercent() {
        int flightDuration = (this.duration > 0 ? this.duration : DWM.TIMINGS.FLIGHT_DURATION_BASE) + this.padding;
        return 100 - (int) Math.ceil((float) (this.tick + this.paddingRemaining) / flightDuration * 100);
    }

    /** Time from setting off to being landed: demat, flight, padding and remat, plus an estimate of what fly-bys add. */
    public int getEstimatedTripDuration() {
        int takeoffTicks = this.getTakeoffTicks();
        int landingTicks = this.getLandingTicks();
        int flightDuration = this.getFlightDuration(takeoffTicks, landingTicks);

        return takeoffTicks + flightDuration + this.flyoverPlanner.padding() + this.flyoverPlanner.estimateFlybyExtraTicks(flightDuration) + landingTicks;
    }

    private int getFlightDuration() {
        return this.getFlightDuration(this.getTakeoffTicks(), this.getLandingTicks());
    }

    private int getFlightDuration(int takeoffTicks, int landingTicks) {
        int distance = this.tardis.getCurrentExteriorPosition().getManhattanDistance(this.tardis.getDestinationExteriorPosition());
        float distanceProgress = Math.min(1F, (float) distance / DWM.TIMINGS.FLIGHT_DISTANCE_FOR_MAX_DURATION);
        int distanceDuration = DWM.TIMINGS.FLIGHT_DURATION_BASE + Math.round(distanceProgress * (DWM.TIMINGS.FLIGHT_DURATION_MAX - DWM.TIMINGS.FLIGHT_DURATION_BASE));

        int duration = distanceDuration + (this.flyoverPlanner.isInterdimensional() ? DWM.TIMINGS.FLIGHT_DIMENSION_CROSSING_BONUS : 0);
        return this.flyoverPlanner.affectsTrip() ? this.flyoverPlanner.adjustDuration(duration, takeoffTicks, landingTicks) : duration;
    }

    // Once the flight has begun the exterior is gone, so what takeoff() decided is what counts.
    private int getTakeoffTicks() {
        boolean instant = this.step == EStep.WAIT_FOR_DEMAT || this.step == EStep.PROCESSING ? this.takeoffWasInstant : this.flyoverPlanner.shouldTakeOffInstantly();
        return instant ? DWM.TIMINGS.INSTANT_MATERIALIZATION_DURATION : DWM.TIMINGS.DEMAT_DURATION;
    }

    private int getLandingTicks() {
        return this.flyoverPlanner.findInstantLandingSpot(false) != null ? DWM.TIMINGS.INSTANT_MATERIALIZATION_DURATION : DWM.TIMINGS.REMAT_DURATION;
    }

    // Counts the flight down a tick, unless it holds still for the padding or a fly-by. Returns whether it did.
    private boolean advanceCountdown() {
        if (this.tick <= 0 || (this.step == EStep.PROCESSING && this.duration > 0 && this.holdsCountdown())) return false;

        this.tick -= 1;
        return true;
    }

    // The padding is idle time at the end of the travel, just before the arrival flyover begins.
    private boolean holdsCountdown() {
        if (this.tick == DWM.FLYOVER.ARRIVAL_DURATION + 1 && this.paddingRemaining > 0) {
            this.paddingRemaining -= 1;
            return true;
        }

        return this.flyover.slowsCountdown(this.tick, this.duration);
    }

    // ////////////////////// //
    // Flight History methods //
    // ////////////////////// //

    public void addHistoryEntry(TardisFlightHistoryEntry historyEntry) {
        this.history.addFirst(historyEntry);
        if (this.history.size() > HISTORY_SIZE) this.history = this.history.subList(0, HISTORY_SIZE);

        this.tardis.markConsoleTilesUpdated();
        this.tardis.markDirty();
    }

    public boolean deleteHistoryEntry(TardisFlightHistoryEntry historyEntry) {
        Optional<TardisFlightHistoryEntry> foundEntryHolder = this.history.stream().filter((entry) -> entry.equals(historyEntry)).findFirst();
        if (foundEntryHolder.isEmpty()) return false;

        this.history.remove(foundEntryHolder.get());
        this.tardis.markConsoleTilesUpdated();
        this.tardis.markDirty();
        return true;
    }

    public void clearHistory() {
        this.history.clear();
        this.tardis.markConsoleTilesUpdated();
        this.tardis.markDirty();
    }

    // ///////////////// //
    // Waypoints methods //
    // ///////////////// //

    public void addWaypointEntry(TardisFlightWaypointEntry waypointEntry) {
        this.waypoints.add(waypointEntry);
        this.tardis.markConsoleTilesUpdated();
        this.tardis.markDirty();
    }

    public boolean updateWaypointEntry(TardisFlightWaypointEntry oldWaypointEntry, TardisFlightWaypointEntry newWaypointEntry) {
        int index = this.waypoints.indexOf(oldWaypointEntry);
        if (index < 0) return false;

        this.waypoints.set(index, newWaypointEntry);
        this.tardis.markConsoleTilesUpdated();
        this.tardis.markDirty();
        return true;
    }

    public boolean deleteWaypointEntry(TardisFlightWaypointEntry waypointEntry) {
        Optional<TardisFlightWaypointEntry> foundEntryHolder = this.waypoints.stream().filter((entry) -> entry.equals(waypointEntry)).findFirst();
        if (foundEntryHolder.isEmpty()) return false;

        this.waypoints.remove(foundEntryHolder.get());
        this.tardis.markConsoleTilesUpdated();
        this.tardis.markDirty();
        return true;
    }

    private void playFlightSound() {
        if (this.soundTick > 0) {
            this.soundTick -= 1;
        }
        else if (this.soundTick == 0) {
            this.soundTick = DWM.TIMINGS.FLIGHT_LOOP;

            // A loop that would run past the end of the flight would play over the landing.
            if (this.tick >= DWM.TIMINGS.FLIGHT_LOOP && !this.tardis.isSilentTravelEnabled()) ModSounds.playTardisFlightSound(this.tardis.getWorld(), this.tardis.getMainConsolePosition(), 0.6F); // quieter in-room, like the takeoff and landing sounds
        }
    }
}
