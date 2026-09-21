package net.drgmes.dwm.common.tardis.systems.flight;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.exteriors.TardisExteriorEntry;
import net.drgmes.dwm.common.tardis.exteriors.TardisExteriors;
import net.drgmes.dwm.common.tardis.systems.TardisSystemMaterialization;
import net.drgmes.dwm.entities.tardis.exteriors.TardisFlyoverEntity;
import net.drgmes.dwm.setup.ModEntities;
import net.drgmes.dwm.setup.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.function.Consumer;

/**
 * The flyover of one flight: the entities it has spawned, the fly-bys it is queueing, and the slowdown they cause.
 * Server-only and never saved - it is purely cosmetic.
 */
public class TardisFlyoverSession {
    private record LandingPlan(TardisFlyoverEntity.Arrival arrival, Vec3d target, float yaw) {
    }

    private final TardisStateManager tardis;
    private final TardisFlyoverPlanner planner;

    private final List<TardisFlyoverEntity> entities = new ArrayList<>();
    private final Set<UUID> flybyServed = new HashSet<>();
    private final List<TardisFlyoverPlanner.Flyby> pendingFlybys = new ArrayList<>();
    private final List<TardisFlyoverPlanner.Flyby> flybyZones = new ArrayList<>();

    private Boolean plannedInstantLanding = null;
    private boolean thudPlayed = false;
    private boolean flybysEnabled = false;
    private double countdownAccumulator = 0;

    public TardisFlyoverSession(TardisStateManager tardis, TardisFlyoverPlanner planner) {
        this.tardis = tardis;
        this.planner = planner;
    }

    /**
     * Whether the landing is a slam: what the flyover planned, or else decided now. That may load the chunk, which
     * placing the exterior is about to do anyway - so a slam is a slam, and the interior hears it, whether or not
     * anyone is outside to see it.
     */
    public boolean landsInstantly() {
        if (this.plannedInstantLanding == null) this.plannedInstantLanding = this.planner.findInstantLandingSpot(true) != null;
        return this.plannedInstantLanding;
    }

    public void reset() {
        this.plannedInstantLanding = null;
        this.thudPlayed = false;
        this.flybysEnabled = false;
        this.flybyServed.clear();
        this.pendingFlybys.clear();
        this.flybyZones.clear();
        this.countdownAccumulator = 0;
        this.discardAll();
    }

    // A slam lingers on the landing spot for a moment to overlap the exterior appearing, then removes itself.
    public void finishLanding() {
        this.entities.removeIf(TardisFlyoverEntity::isLingeringSlam);
        this.discardAll();
    }

    // ///////////////////// //
    // Take-off and landing  //
    // ///////////////////// //

    /** Once demat is done: the flyover that carries the TARDIS away. */
    public void spawnDeparture(int flightTicks, boolean takeoffWasInstant) {
        TardisFlyoverEntity flyover = this.spawnDepartureFlyover(flightTicks, takeoffWasInstant);

        // An instant takeoff has no demat to carry the takeoff sound, so the flyover makes it, outside, following it
        // away; inside it fades out before the flight ends, or it would drown out the landing.
        if (takeoffWasInstant) {
            if (flyover != null) flyover.setSound(TardisFlyoverEntity.Sound.TAKEOFF);
            this.playInteriorTakeoffSound(flightTicks);
        }
    }

    private TardisFlyoverEntity spawnDepartureFlyover(int flightTicks, boolean takeoffWasInstant) {
        boolean interdimensional = this.planner.isInterdimensional();
        TardisFlyoverPlanner.Mode mode = this.planner.mode();

        // Leaving for another dimension lifts off and flies straight up, if there was a take-off to lift off from.
        if (interdimensional ? !takeoffWasInstant : mode == TardisFlyoverPlanner.Mode.NONE) return null;

        ServerWorld world = this.tardis.getExteriorWorld();
        if (world == null) return null;

        Vec3d start = Vec3d.ofBottomCenter(this.tardis.getCurrentExteriorPosition());
        float startYaw = this.tardis.getCurrentExteriorFacing().asRotation();
        String exteriorType = this.exteriorTypeName();

        if (interdimensional) return this.spawn(world, (entity) -> entity.configureAscent(start, exteriorType, startYaw));

        // After a normal demat (underground) there is nothing to lift off from, so it fades in at flying height.
        TardisFlyoverEntity.Departure departure = takeoffWasInstant ? TardisFlyoverEntity.Departure.GROUND : TardisFlyoverEntity.Departure.FADE_IN;

        if (mode == TardisFlyoverPlanner.Mode.FULL_ROUTE) {
            LandingPlan plan = this.planLanding();
            return this.spawn(world, (entity) -> entity.configureRoute(start, plan.target(), exteriorType, flightTicks, departure, startYaw, plan.arrival(), plan.yaw()));
        }

        Vec3d destination = Vec3d.ofBottomCenter(this.tardis.getDestinationExteriorPosition());
        this.flybysEnabled = true;
        return this.spawn(world, (entity) -> entity.configureDepartureStreak(start, destination, exteriorType, departure, startYaw));
    }

    private void playInteriorTakeoffSound(int flightTicks) {
        int fade = DWM.FLYOVER.TAKEOFF_SOUND_FADE;
        int end = flightTicks - DWM.FLYOVER.TAKEOFF_SOUND_LANDING_MARGIN;
        ModSounds.playTardisTakeoffSound(this.tardis.getWorld(), this.tardis.getMainConsolePosition(), 0.6F, Math.max(0, end - fade), fade); // quieter in-room
    }

    /**
     * The thud of a slam landing outside, a little ahead of the exterior appearing so it lands with the touchdown
     * (the interior's is played with the landing itself). Played once: the landing asks again, in case the flight
     * never passed that moment.
     */
    public void playLandingThud() {
        if (this.thudPlayed || !this.landsInstantly()) return;
        this.thudPlayed = true;

        ServerWorld world = this.tardis.getDestinationExteriorWorld();
        if (world == null) return;

        TardisSystemMaterialization.LandingSpot spot = this.planner.findInstantLandingSpot(true);
        ModSounds.playTardisGroundLandingSound(world, spot != null ? spot.pos() : this.tardis.getDestinationExteriorPosition());
    }

    /** The far end of a long hop, when someone is there to see it and its chunks are loaded (entities only tick in loaded chunks). */
    public void spawnArrival(int flightTicks) {
        if (this.planner.mode() != TardisFlyoverPlanner.Mode.DEPARTURE_STREAK) return;

        ServerWorld world = this.tardis.getExteriorWorld();
        if (world == null) return;

        BlockPos destination = this.tardis.getDestinationExteriorPosition();
        Vec3d origin = Vec3d.ofBottomCenter(this.tardis.getCurrentExteriorPosition());
        Vec3d target = Vec3d.ofBottomCenter(destination);
        if (!this.isWatched(world, target, DWM.FLYOVER.ARRIVAL_PLAYER_RADIUS)) return;
        if (!world.isChunkLoaded(destination) || !world.isChunkLoaded(BlockPos.ofFloored(TardisFlyoverEntity.approachStart(origin, target)))) return;

        LandingPlan plan = this.planLanding();
        String exteriorType = this.exteriorTypeName();
        this.spawn(world, (entity) -> entity.configureArrival(origin, plan.target(), exteriorType, flightTicks, plan.arrival(), plan.yaw()));
    }

    /** Arriving from another dimension onto a spot with open sky: dropped straight down from the sky. */
    public void spawnDrop(int flightTicks) {
        if (!this.planner.isInterdimensional()) return;

        TardisSystemMaterialization.LandingSpot spot = this.planner.findInstantLandingSpot(false);
        ServerWorld world = this.tardis.getDestinationExteriorWorld();
        if (spot == null || world == null) return;

        Vec3d landing = Vec3d.ofBottomCenter(spot.pos());
        if (!this.isWatched(world, landing, DWM.FLYOVER.DROP_PLAYER_RADIUS)) return;

        String exteriorType = this.exteriorTypeName();
        this.spawn(world, (entity) -> entity.configureDrop(landing, exteriorType, flightTicks, spot.facing().asRotation()));
        this.plannedInstantLanding = true;
    }

    // Slam onto the landing spot if it has open sky, otherwise fade out over the spot before a normal remat.
    private LandingPlan planLanding() {
        TardisSystemMaterialization.LandingSpot spot = this.planner.findInstantLandingSpot(false);
        this.plannedInstantLanding = spot != null;

        if (spot != null) return new LandingPlan(TardisFlyoverEntity.Arrival.SLAM, Vec3d.ofBottomCenter(spot.pos()), spot.facing().asRotation());
        return new LandingPlan(TardisFlyoverEntity.Arrival.FADE_OUT, Vec3d.ofBottomCenter(this.tardis.getDestinationExteriorPosition()), this.tardis.getDestinationExteriorFacing().asRotation());
    }

    // ///////// //
    // Fly-bys   //
    // ///////// //

    // How far along its line the TARDIS "is", in blocks. It moves at a steady pace through the travel part of the
    // flight and has arrived when the arrival flyover begins, so no slowdown can happen while that is playing.
    private static double virtualAlong(double length, int tick, int duration) {
        double travelTicksLeft = Math.max(0, tick - DWM.FLYOVER.ARRIVAL_DURATION);
        return length * (1D - travelTicksLeft / TardisFlyoverPlanner.travelTicks(duration));
    }

    /** Whether the countdown should hold still this tick: it runs slower while the TARDIS passes a fly-by, and only there. */
    public boolean slowsCountdown(int tick, int duration) {
        if (this.flybyZones.isEmpty() || tick <= DWM.FLYOVER.ARRIVAL_DURATION) return false;

        TardisFlyoverPlanner.FlightLine line = this.planner.flightLine();
        if (line == null) return false;

        double virtualAlong = virtualAlong(line.length(), tick, duration);
        TardisFlyoverPlanner.Flyby zone = null;
        for (TardisFlyoverPlanner.Flyby candidate : this.flybyZones) {
            if (Math.abs(virtualAlong - candidate.along()) <= candidate.half()) {
                zone = candidate;
                break;
            }
        }

        if (zone == null) return false;

        double normalSpeed = line.length() / TardisFlyoverPlanner.travelTicks(duration);
        double zoneSpeed = 2D * zone.half() / DWM.FLYOVER.FLYBY_DURATION; // for the pass to last as long as the fly-by
        double rate = Math.min(1D, zoneSpeed / normalSpeed);
        if (rate >= 1D) return false;

        this.countdownAccumulator += rate;
        if (this.countdownAccumulator < 1D) return true;

        this.countdownAccumulator -= 1D;
        return false;
    }

    /**
     * Queues a fly-by for players near the route and starts the ones whose stretch the TARDIS has reached, when it
     * reaches it. Returns the ticks those add to the flight, which come out of its padding first.
     */
    public int updateFlybys(int tick, int duration) {
        if (!this.flybysEnabled || duration <= 0) return 0;

        ServerWorld world = this.tardis.getExteriorWorld();
        if (world == null) return 0;

        boolean scan = world.getTime() % DWM.FLYOVER.FLYBY_CHECK_INTERVAL == 0;
        if (!scan && this.pendingFlybys.isEmpty() && this.flybyZones.isEmpty()) return 0;

        TardisFlyoverPlanner.FlightLine line = this.planner.flightLine();
        if (line == null) return 0;

        double virtualAlong = virtualAlong(line.length(), tick, duration);
        this.flybyZones.removeIf((zone) -> virtualAlong > zone.along() + zone.half());

        if (scan) this.queueBystanders(world, line, virtualAlong);
        return this.startDueFlybys(world, line, virtualAlong, duration);
    }

    private void queueBystanders(ServerWorld world, TardisFlyoverPlanner.FlightLine line, double virtualAlong) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            UUID id = player.getUuid();
            if (this.flybyServed.contains(id) || this.pendingFlybys.stream().anyMatch((pending) -> pending.player().equals(id))) continue;

            TardisFlyoverPlanner.Flyby flyby = this.planner.flybyFor(player, line);
            if (flyby != null && virtualAlong <= flyby.along()) this.pendingFlybys.add(flyby);
        }
    }

    private int startDueFlybys(ServerWorld world, TardisFlyoverPlanner.FlightLine line, double virtualAlong, int duration) {
        List<TardisFlyoverPlanner.Flyby> due = this.pendingFlybys.stream().filter((pending) -> virtualAlong >= pending.along() - pending.half()).toList();
        this.pendingFlybys.removeAll(due);

        String exteriorType = this.exteriorTypeName();
        Vec3d direction = new Vec3d(line.dirX(), 0, line.dirZ());
        double normalSpeed = line.length() / TardisFlyoverPlanner.travelTicks(duration);
        int extraTicks = 0;

        for (TardisFlyoverPlanner.Flyby flyby : due) {
            PlayerEntity player = world.getPlayerByUuid(flyby.player());
            if (player == null || this.flybyServed.contains(flyby.player())) continue;

            Vec3d pass = new Vec3d(line.originX() + line.dirX() * flyby.along(), player.getY(), line.originZ() + line.dirZ() * flyby.along());
            if (this.spawn(world, (entity) -> entity.configureFlyby(pass, direction, exteriorType, DWM.FLYOVER.FLYBY_DURATION, flyby.half())) == null) continue;

            this.flybyZones.add(flyby);
            extraTicks += (int) Math.ceil(TardisFlyoverPlanner.flybyExtraTicks(flyby.half(), normalSpeed));

            // One fly-by covers everyone standing near enough to see it.
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (other.squaredDistanceTo(pass) <= DWM.FLYOVER.FLYBY_HALF_LENGTH * DWM.FLYOVER.FLYBY_HALF_LENGTH) this.flybyServed.add(other.getUuid());
            }

            this.flybyServed.add(flyby.player());
        }

        this.pendingFlybys.removeIf((pending) -> this.flybyServed.contains(pending.player()));
        return extraTicks;
    }

    // //////// //
    // Entities //
    // //////// //

    private TardisFlyoverEntity spawn(ServerWorld world, Consumer<TardisFlyoverEntity> configure) {
        TardisFlyoverEntity entity = ModEntities.TARDIS_FLYOVER.getEntityType().create(world);
        if (entity == null) return null;

        configure.accept(entity);
        world.spawnEntity(entity);
        this.entities.add(entity);
        return entity;
    }

    private String exteriorTypeName() {
        TardisExteriorEntry exteriorType = this.tardis.getExteriorType();
        return (exteriorType != null ? exteriorType : TardisExteriors.CAPSULE).name;
    }

    private boolean isWatched(ServerWorld world, Vec3d position, double radius) {
        return !world.getPlayers((player) -> player.squaredDistanceTo(position) <= radius * radius).isEmpty();
    }

    private void discardAll() {
        this.entities.forEach((entity) -> {
            if (!entity.isRemoved()) entity.discard();
        });

        this.entities.clear();
    }
}
