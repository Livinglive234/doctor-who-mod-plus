package net.drgmes.dwm.common.tardis.systems.flight;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.systems.TardisSystemMaterialization;
import net.drgmes.dwm.utils.helpers.WorldHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Decides what the flyover lever changes about a trip: which flyover it gets, how long the flight takes, where
 * the TARDIS lands and who gets a fly-by. Holds no state - everything is read from the TARDIS.
 */
public class TardisFlyoverPlanner {
    public enum Mode { NONE, FULL_ROUTE, DEPARTURE_STREAK }

    /** The straight line of a flight, on the ground plane. */
    public record FlightLine(double originX, double originZ, double dirX, double dirZ, double length) {
    }

    /** A fly-by: where along the line it is centred, and how far it reaches either side of that. */
    public record Flyby(UUID player, double along, double half) {
    }

    private final TardisStateManager tardis;

    public TardisFlyoverPlanner(TardisStateManager tardis) {
        this.tardis = tardis;
    }

    // The ticks a flight spends travelling: all of it except the last ARRIVAL_DURATION, which belong to the arrival flyover.
    public static int travelTicks(int flightDuration) {
        return Math.max(1, flightDuration - DWM.FLYOVER.ARRIVAL_DURATION);
    }

    // How many ticks longer than the flight takes to cross a stretch, a fly-by takes to pass it.
    public static double flybyExtraTicks(double half, double normalSpeed) {
        return Math.max(0D, DWM.FLYOVER.FLYBY_DURATION - 2D * half / normalSpeed);
    }

    private static boolean hasCeiling(ServerWorld world) {
        return world == null || world.getDimension().hasCeiling();
    }

    public boolean isInterdimensional() {
        return !this.tardis.getCurrentExteriorDimension().equals(this.tardis.getDestinationExteriorDimension());
    }

    // The lever does nothing on a trip where both ends have a ceiling (a hop within the Nether): it just stays on.
    public boolean affectsTrip() {
        return this.tardis.isFlyoverEnabled() && !(hasCeiling(this.tardis.getExteriorWorld()) && hasCeiling(this.tardis.getDestinationExteriorWorld()));
    }

    public double distance() {
        return Math.sqrt(this.tardis.getCurrentExteriorPosition().getSquaredDistance(this.tardis.getDestinationExteriorPosition()));
    }

    // Only same-dimension hops fly a route; leaving for or arriving from another dimension has its own flyover.
    public Mode mode() {
        if (!this.tardis.isFlyoverEnabled() || this.isInterdimensional() || hasCeiling(this.tardis.getExteriorWorld())) return Mode.NONE;
        return this.distance() <= DWM.FLYOVER.FULL_ROUTE_MAX_DISTANCE ? Mode.FULL_ROUTE : Mode.DEPARTURE_STREAK;
    }

    public int padding() {
        return this.mode() == Mode.DEPARTURE_STREAK ? DWM.FLYOVER.LONG_HOP_PADDING : 0;
    }

    /**
     * The flight time with the lever on: what the flyover needs, within the trip's cap minus the demat and remat
     * either side of the flight (instant ones cost almost nothing).
     */
    public int adjustDuration(int baseDuration, int takeoffTicks, int landingTicks) {
        int budget = Math.max(DWM.FLYOVER.FLIGHT_MIN_DURATION, this.tripCap() - takeoffTicks - landingTicks);

        return switch (this.mode()) {
            case FULL_ROUTE -> Math.min(budget, Math.max(DWM.FLYOVER.FLIGHT_MIN_DURATION, DWM.FLYOVER.FLIGHT_LEAD + (int) Math.ceil(this.distance() / DWM.FLYOVER.COMFORT_SPEED)));
            case DEPARTURE_STREAK -> {
                int limit = Math.max(DWM.FLYOVER.LONG_HOP_MIN_DURATION, budget - DWM.FLYOVER.LONG_HOP_PADDING);
                yield Math.min(limit, Math.max(DWM.FLYOVER.LONG_HOP_MIN_DURATION, DWM.FLYOVER.LONG_HOP_LEAD + (int) Math.ceil(this.distance() / DWM.FLYOVER.LONG_HOP_SPEED)));
            }
            case NONE -> Math.min(budget, baseDuration);
        };
    }

    // 45 seconds, or 60 for a trip that is both interdimensional and long distance (not just one of the two).
    private int tripCap() {
        boolean longInterdimensional = this.isInterdimensional() && this.distance() > DWM.FLYOVER.FULL_ROUTE_MAX_DISTANCE;
        return longInterdimensional ? DWM.FLYOVER.TRIP_CAP_LONG_INTERDIMENSIONAL : DWM.FLYOVER.TRIP_CAP;
    }

    // With open sky overhead the demat is instant and the flying copy takes over, the mirror of a slam landing.
    public boolean shouldTakeOffInstantly() {
        if (!this.tardis.isFlyoverEnabled()) return false;

        ServerWorld world = this.tardis.getExteriorWorld();
        // The TARDIS's own two blocks are standing there, so they mustn't count as cover.
        return !hasCeiling(world) && WorldHelper.isOpenToSky(world, this.tardis.getCurrentExteriorPosition(), 2);
    }

    /**
     * Where the TARDIS lands if that is a spot with open sky - null otherwise (lever off, ceiling, or covered).
     * Judged on the spot the landing scan resolves, not the requested one.
     *
     * @param loadChunks whether the destination chunk may be loaded to look. Estimates and plans say no, so a far-off
     *                   destination isn't loaded just to look at it; the landing itself says yes, since it loads the
     *                   chunk to place the exterior anyway.
     */
    public TardisSystemMaterialization.LandingSpot findInstantLandingSpot(boolean loadChunks) {
        if (!this.tardis.isFlyoverEnabled()) return null;

        ServerWorld world = this.tardis.getDestinationExteriorWorld();
        BlockPos destination = this.tardis.getDestinationExteriorPosition();
        if (hasCeiling(world) || (!loadChunks && !world.isChunkLoaded(destination))) return null;

        TardisSystemMaterialization.LandingSpot spot = this.tardis.getSystem(TardisSystemMaterialization.class).findLandingSpot(world, destination, this.tardis.getDestinationExteriorFacing());
        return spot != null && WorldHelper.isOpenToSky(world, spot.pos(), 0) ? spot : null;
    }

    public FlightLine flightLine() {
        BlockPos from = this.tardis.getCurrentExteriorPosition();
        BlockPos to = this.tardis.getDestinationExteriorPosition();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3) return null;

        return new FlightLine(from.getX() + 0.5D, from.getZ() + 0.5D, dx / length, dz / length, length);
    }

    /**
     * The fly-by a player gets on this route, or null if they don't: they are too far off the line, or the departure
     * streak or the arrival flyover covers them. The pass stops where the streak's reach ends and where the arrival
     * begins, so the TARDIS is seen to carry on into them.
     */
    public Flyby flybyFor(ServerPlayerEntity player, FlightLine line) {
        double relX = player.getX() - line.originX();
        double relZ = player.getZ() - line.originZ();
        double along = relX * line.dirX() + relZ * line.dirZ();
        double lateral = Math.abs(relX * line.dirZ() - relZ * line.dirX());
        if (along <= 0 || along >= line.length() || lateral > DWM.FLYOVER.FLYBY_MAX_OFFSET) return null;

        double half = Math.min(DWM.FLYOVER.FLYBY_HALF_LENGTH, Math.min(along - DWM.FLYOVER.STREAK_DISTANCE, line.length() - along - DWM.FLYOVER.ARRIVAL_DISTANCE));
        return half < DWM.FLYOVER.FLYBY_MIN_HALF_LENGTH ? null : new Flyby(player.getUuid(), along, half);
    }

    /**
     * How many ticks past the padding fly-bys are expected to add, from who is near the route right now: one pass over
     * the players, made when the key is used. An estimate - people move.
     */
    public int estimateFlybyExtraTicks(int flightDuration) {
        if (this.mode() != Mode.DEPARTURE_STREAK) return 0;

        ServerWorld world = this.tardis.getExteriorWorld();
        FlightLine line = this.flightLine();
        if (world == null || line == null || world.getPlayers().isEmpty()) return 0;

        double normalSpeed = line.length() / travelTicks(flightDuration);
        List<Flyby> flybys = world.getPlayers().stream()
            .map((player) -> this.flybyFor(player, line))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingDouble(Flyby::along))
            .toList();

        // One fly-by per group of players close enough together to share it, in the order the TARDIS reaches them.
        Set<UUID> served = new HashSet<>();
        double extraTicks = 0;
        for (Flyby flyby : flybys) {
            if (served.contains(flyby.player())) continue;

            Vec3d pass = new Vec3d(line.originX() + line.dirX() * flyby.along(), 0, line.originZ() + line.dirZ() * flyby.along());
            for (ServerPlayerEntity other : world.getPlayers()) {
                if (new Vec3d(other.getX(), 0, other.getZ()).squaredDistanceTo(pass) <= DWM.FLYOVER.FLYBY_HALF_LENGTH * DWM.FLYOVER.FLYBY_HALF_LENGTH) served.add(other.getUuid());
            }

            served.add(flyby.player());
            extraTicks += flybyExtraTicks(flyby.half(), normalSpeed);
        }

        return Math.max(0, (int) Math.ceil(extraTicks) - this.padding());
    }
}
