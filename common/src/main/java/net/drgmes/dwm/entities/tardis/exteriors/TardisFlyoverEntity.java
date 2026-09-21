package net.drgmes.dwm.entities.tardis.exteriors;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.exteriors.TardisExteriors;
import net.drgmes.dwm.utils.sounds.FlyoverSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Purely cosmetic flying copy of a TARDIS, shown while its real exterior doesn't exist in the world (see
 * TardisFlyoverSession, which spawns it; it removes itself when its timeline ends).
 *
 * <p>A timeline, driven by ticksElapsed, is up to three phases:
 * <ul>
 *   <li>departure - a heavy lift-off from the ground, or a fade-in at flying height when the takeoff was underground;</li>
 *   <li>travel - a route, a streak, an approach or a fly-by (see {@link Kind});</li>
 *   <li>tail - how a route or drop ends over its target: a slam, or a fade-out.</li>
 * </ul>
 * Speed is shaped to be slow wherever someone can see it well and fast out of sight.
 *
 * <p>It only rotates while travelling horizontally. A flyover that ends over a target winds its spin down over the
 * last stretch of travel, so it is at rest, facing its landing direction, by the time it hovers above the spot.
 */
public class TardisFlyoverEntity extends Entity {
    /**
     * How a flyover begins: GROUND lifts off from the exterior's spot, matching its facing;
     * FADE_IN fades in at flying height above the spot (the takeoff was underground).
     */
    public enum Departure { GROUND, FADE_IN }

    /**
     * How a flyover ends over its target: SLAM plunges onto the landing spot, coming to rest facing the way the
     * exterior will; FADE_OUT fades away over the spot before a normal materialization.
     */
    public enum Arrival { SLAM, FADE_OUT }

    /**
     * ROUTE: glide from the takeoff spot to the target. STREAK: accelerate away toward the destination and vanish.
     * APPROACH: the arrival end of a long hop, already at speed when it appears, easing to a hover over the target.
     * FLYBY: one pass across the sky in front of a bystander. DROP: fall straight from the sky onto the landing spot.
     * ASCENT: lift off straight up, out of sight (leaving for another dimension).
     */
    private enum Kind { ROUTE, STREAK, APPROACH, FLYBY, DROP, ASCENT }

    /** What clients near it hear it make, following it around until it is gone: TAKEOFF for a takeoff, FLIGHT (a loop) for a fly-by. */
    public enum Sound { NONE, TAKEOFF, FLIGHT }

    private static final TrackedData<String> EXTERIOR_TYPE = DataTracker.registerData(
        TardisFlyoverEntity.class,
        TrackedDataHandlerRegistry.STRING
    );

    // Its own unbounded value rather than the entity's yaw, which is synced as a wrapped byte and would
    // visibly glitch every time a continuously spinning entity crossed 0/360.
    private static final TrackedData<Float> SPIN_ANGLE = DataTracker.registerData(
        TardisFlyoverEntity.class,
        TrackedDataHandlerRegistry.FLOAT
    );

    private static final TrackedData<Integer> SOUND = DataTracker.registerData(
        TardisFlyoverEntity.class,
        TrackedDataHandlerRegistry.INTEGER
    );

    // 0 is invisible, 1 fully opaque: how far along a fade-in or fade-out it is.
    private static final TrackedData<Float> ALPHA = DataTracker.registerData(
        TardisFlyoverEntity.class,
        TrackedDataHandlerRegistry.FLOAT
    );

    // ---- server-side timeline ----
    private Kind kind = Kind.ROUTE;
    private Departure departure = Departure.FADE_IN;
    private Arrival arrival = null;

    private Vec3d startPos = Vec3d.ZERO;
    private Vec3d targetPos = Vec3d.ZERO;
    private Vec3d direction = Vec3d.ZERO;
    private double flybyHalf = 0;
    private double cruiseY = 0;
    private double ascentTopY = 0;

    private int descentTicks = 0;
    private int travelTicks = 0;
    private int tailTicks = 0;
    private int totalTicks = 1;
    private int ticksElapsed = 0;

    private boolean tailStarted = false;
    private double tailStartY = 0;

    // The spin over the travel phase is planned up front: ramp up, cruise, then (over a target) slow to stop
    // exactly on the landing facing, by adjusting the cruise rate a little so it never has to speed up to make it.
    private float landingSpin = 0;
    private int spinRampTicks = 0;
    private int settleTicks = 0;
    private float spinPlanStart = 0;
    private float spinPlanTotal = 0;
    private float spinPlanRate = DWM.FLYOVER.SPIN_DEGREES_PER_TICK;

    // ---- client-side interpolation of the spin ----
    private float clientSpin = 0;
    private float clientPrevSpin = 0;
    private boolean clientSpinInited = false;

    public TardisFlyoverEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
    }

    /** Where an arrival flyover appears: out in the distance, on the side the TARDIS is coming from. */
    public static Vec3d approachStart(Vec3d origin, Vec3d target) {
        Vec3d direction = horizontalDirection(origin.subtract(target));
        return new Vec3d(
            target.x + direction.x * DWM.FLYOVER.ARRIVAL_DISTANCE,
            target.y,
            target.z + direction.z * DWM.FLYOVER.ARRIVAL_DISTANCE
        );
    }

    private static Vec3d horizontalDirection(Vec3d vector) {
        Vec3d horizontal = new Vec3d(vector.x, 0, vector.z);
        return horizontal.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : horizontal.normalize();
    }

    @Override
    public void initDataTracker(DataTracker.Builder builder) {
        builder.add(EXTERIOR_TYPE, TardisExteriors.CAPSULE.name);
        builder.add(SPIN_ANGLE, 0F);
        builder.add(ALPHA, 1F);
        builder.add(SOUND, Sound.NONE.ordinal());
    }

    // Cosmetic and short-lived: not worth persisting across a chunk unload or restart mid-flight.
    @Override
    public void readCustomDataFromNbt(NbtCompound tag) {
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound tag) {
    }

    // ////////////// //
    // Configuration  //
    // ////////////// //

    /**
     * A flight that is one continuous flyover: [fade-in], glide to the target, then slam or fade out.
     * The timeline is sized to end when the real flight does.
     *
     * @param start       where the TARDIS took off (bottom-centre of its block)
     * @param target      where it lands - the resolved landing spot for a slam
     * @param flightTicks how many ticks the real flight has left
     * @param startYaw    the exterior's facing, so a ground takeoff hands over seamlessly
     * @param landingYaw  the facing the exterior will land with, so a slam hands over seamlessly
     */
    public void configureRoute(Vec3d start, Vec3d target, String exteriorTypeName, int flightTicks, Departure departure, float startYaw, Arrival arrival, float landingYaw) {
        this.kind = Kind.ROUTE;
        this.arrival = arrival;
        this.configureDeparture(start, target, exteriorTypeName, departure, startYaw);

        this.landingSpin = landingYaw;
        this.planEndOverTarget(flightTicks);
        this.planSpin(departure == Departure.FADE_IN);
    }

    /** The start of a long hop: [fade-in], then accelerate away toward the destination and vanish. */
    public void configureDepartureStreak(Vec3d start, Vec3d destination, String exteriorTypeName, Departure departure, float startYaw) {
        this.kind = Kind.STREAK;
        this.configureDeparture(start, destination, exteriorTypeName, departure, startYaw);

        this.direction = horizontalDirection(destination.subtract(start));
        this.travelTicks = DWM.FLYOVER.STREAK_DURATION;
        this.totalTicks = this.descentTicks + this.travelTicks;
    }

    /**
     * The far end of a long hop: it starts out in the distance, streaks in and eases to a hover over the target,
     * then slams down or fades out.
     *
     * @param origin where the TARDIS is flying in from - only used for the approach direction
     */
    public void configureArrival(Vec3d origin, Vec3d target, String exteriorTypeName, int flightTicks, Arrival arrival, float landingYaw) {
        this.kind = Kind.APPROACH;
        this.arrival = arrival;

        Vec3d start = approachStart(origin, target);
        this.startPos = start;
        this.targetPos = target;
        this.dataTracker.set(EXTERIOR_TYPE, exteriorTypeName);
        this.cruiseY = this.cruiseAltitude(start, target);

        // It appears already flying and already spinning, so no lift-off and no ramp-up.
        this.landingSpin = landingYaw;
        this.launch(new Vec3d(start.x, this.cruiseY, start.z), landingYaw);
        this.planEndOverTarget(flightTicks);
        this.planSpin(true);
    }

    /**
     * The departure of an interdimensional hop: the flying copy takes over from the exterior on its spot, hardly
     * moves at first - it is heavy - then accelerates straight up through the clouds and out of sight. Purely
     * vertical, so it keeps the exterior's facing all the way.
     *
     * @param start    where the exterior stood (bottom-centre of its block)
     * @param startYaw the exterior's facing
     */
    public void configureAscent(Vec3d start, String exteriorTypeName, float startYaw) {
        this.kind = Kind.ASCENT;
        this.totalTicks = DWM.FLYOVER.DEPARTURE_ASCENT_DURATION;

        this.dataTracker.set(EXTERIOR_TYPE, exteriorTypeName);
        this.startPos = start;
        this.ascentTopY = this.cloudTop(start.y);
        this.launch(start, startYaw);
    }

    /**
     * One pass across the sky in front of a bystander, along the direction of a long flight. It is centred on
     * {@code pass} - the point of the flight's line closest to the bystander - and is slowest right there.
     *
     * @param pass      the closest point of the flight's line to the bystander (x and z matter; y is the bystander's)
     * @param direction the horizontal direction of the flight
     * @param half      blocks either side of {@code pass} that it covers - shorter than usual when it would
     *                  otherwise run into the departure streak or the arrival flyover
     */
    public void configureFlyby(Vec3d pass, Vec3d direction, String exteriorTypeName, int ticks, double half) {
        this.kind = Kind.FLYBY;
        this.travelTicks = Math.max(2, ticks);
        this.totalTicks = this.travelTicks;

        this.dataTracker.set(EXTERIOR_TYPE, exteriorTypeName);
        this.setSound(Sound.FLIGHT);
        this.startPos = pass;
        this.direction = horizontalDirection(direction);
        this.cruiseY = Math.max(pass.y, this.surfaceY(pass)) + DWM.FLYOVER.TERRAIN_CLEARANCE;
        this.flybyHalf = half;

        this.launch(new Vec3d(pass.x - this.direction.x * half, this.cruiseY, pass.z - this.direction.z * half), this.random.nextFloat() * 360F);
    }

    /**
     * An arrival from another dimension onto an above-ground spot: appears high in the sky right above the
     * landing spot and drops straight onto it, already facing the way the exterior will.
     *
     * @param landing the resolved landing spot (bottom-centre of its block)
     */
    public void configureDrop(Vec3d landing, String exteriorTypeName, int flightTicks, float landingYaw) {
        this.kind = Kind.DROP;
        this.arrival = Arrival.SLAM;
        this.totalTicks = Math.max(2, flightTicks) + DWM.FLYOVER.SLAM_HOLD;
        this.tailTicks = this.totalTicks;

        this.dataTracker.set(EXTERIOR_TYPE, exteriorTypeName);
        this.targetPos = landing;

        double dropY = Math.max(landing.y + DWM.FLYOVER.DROP_HEIGHT, DWM.FLYOVER.CLOUD_LEVEL + 32);
        this.launch(new Vec3d(landing.x, Math.min(dropY, this.getWorld().getTopY() + 64), landing.z), landingYaw);
    }

    // What the takeoff-shaped launches (routes and streaks) have in common.
    private void configureDeparture(Vec3d start, Vec3d target, String exteriorTypeName, Departure departure, float startYaw) {
        this.departure = departure;
        this.descentTicks = departure == Departure.GROUND ? DWM.FLYOVER.LIFTOFF_DURATION : DWM.FLYOVER.FADE_IN_DURATION;
        this.spinRampTicks = DWM.FLYOVER.SPIN_RAMP;

        this.dataTracker.set(EXTERIOR_TYPE, exteriorTypeName);
        this.startPos = start;
        this.targetPos = target;
        this.cruiseY = this.cruiseAltitude(start, target);

        if (departure == Departure.FADE_IN) this.setAlpha(0F);
        this.launch(departure == Departure.FADE_IN ? new Vec3d(start.x, this.cruiseY, start.z) : start, startYaw);
    }

    // Sizes the timeline of a route or an arrival, which end over the target: travel, then a slam or a fade-out.
    private void planEndOverTarget(int flightTicks) {
        // A slam lingers on the landing spot for a moment after the flight ends, to overlap the exterior appearing.
        int slamHold = this.arrival == Arrival.SLAM ? DWM.FLYOVER.SLAM_HOLD : 0;
        int fullTail = this.arrival == Arrival.SLAM ? DWM.FLYOVER.SLAM_DURATION + DWM.FLYOVER.SLAM_HOLD : DWM.FLYOVER.FADE_OUT_DURATION;

        this.totalTicks = Math.max(2, flightTicks) + slamHold;
        this.tailTicks = Math.min(fullTail, Math.max(1, this.totalTicks - this.descentTicks - 1));
        this.travelTicks = Math.max(1, this.totalTicks - this.descentTicks - this.tailTicks);
        this.settleTicks = Math.min(DWM.FLYOVER.SPIN_SETTLE_DURATION, Math.max(1, this.travelTicks / 2));
    }

    private void launch(Vec3d position, float spin) {
        this.setPosition(position.x, position.y, position.z);
        this.setSpinAngle(spin);
    }

    private int surfaceY(Vec3d position) {
        return this.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, MathHelper.floor(position.x), MathHelper.floor(position.z));
    }

    private double cruiseAltitude(Vec3d start, Vec3d target) {
        return Math.max(Math.max(start.y, this.surfaceY(start)), target.y) + DWM.FLYOVER.TERRAIN_CLEARANCE;
    }

    private double cloudTop(double y) {
        return Math.max(DWM.FLYOVER.CLOUD_LEVEL, y) + DWM.FLYOVER.ASCENT_CLOUD_CLEARANCE;
    }

    // ///////// //
    // Accessors //
    // ///////// //

    public String getExteriorTypeName() {
        return this.dataTracker.get(EXTERIOR_TYPE);
    }

    private void setSpinAngle(float angle) {
        this.dataTracker.set(SPIN_ANGLE, angle);
    }

    private float getSpinAngle() {
        return this.dataTracker.get(SPIN_ANGLE);
    }

    private void setAlpha(float alpha) {
        this.dataTracker.set(ALPHA, alpha);
    }

    public void setSound(Sound sound) {
        this.dataTracker.set(SOUND, sound.ordinal());
    }

    public Sound getSound() {
        return Sound.values()[this.dataTracker.get(SOUND)];
    }

    // Clients start the sound when they learn what it is, which is when it comes into view.
    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (SOUND.equals(data) && this.getWorld().isClient && this.getSound() != Sound.NONE) FlyoverSounds.play(this);
    }

    /** How opaque to draw it: below 1 while it fades in or out. */
    public float getAlpha() {
        return this.dataTracker.get(ALPHA);
    }

    /** The spin to render with, smoothed between ticks. Only meaningful on the client. */
    public float getSpinAngle(float tickDelta) {
        if (!this.clientSpinInited) return this.getSpinAngle();
        return MathHelper.lerp(tickDelta, this.clientPrevSpin, this.clientSpin);
    }

    /** A slam that keeps resting on its spot after the flight ends, to overlap the exterior appearing. */
    public boolean isLingeringSlam() {
        return this.arrival == Arrival.SLAM;
    }

    // ////// //
    // Ticking //
    // ////// //

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            this.clientPrevSpin = this.clientSpinInited ? this.clientSpin : this.getSpinAngle();
            this.clientSpin = this.getSpinAngle();
            this.clientSpinInited = true;
            return;
        }

        this.ticksElapsed++;
        if (this.ticksElapsed >= this.totalTicks) {
            this.discard();
            return;
        }

        int t = this.ticksElapsed;
        if (this.kind == Kind.ASCENT) this.tickAscent(t);
        else if (t <= this.descentTicks) this.tickDescent(t);
        else if (t <= this.descentTicks + this.travelTicks) this.tickTravel(t - this.descentTicks);
        else this.tickTail(t - this.descentTicks - this.travelTicks);
    }

    // The phase before travel - no rotation, it holds the facing it started with.
    private void tickDescent(int i) {
        float progress = (float) i / (float) this.descentTicks;

        if (this.departure == Departure.FADE_IN) {
            this.setAlpha(progress);
            return;
        }

        // Heavy: hardly moves at first - about a second to get a block clear of the ground - then gets going and
        // settles smoothly at flying height (a smootherstep of the squared progress).
        float heavy = progress * progress;
        float eased = heavy * heavy * heavy * (heavy * (heavy * 6F - 15F) + 10F);
        this.setPositionStraining(MathHelper.lerp(eased, this.startPos.y, this.cruiseY), progress, 0.6F);
    }

    // Straight up, accelerating: cubic, so it is nearly motionless for the first moments (a block clear after
    // about 0.6 seconds).
    private void tickAscent(int i) {
        float progress = (float) i / (float) this.totalTicks;
        this.setPositionStraining(MathHelper.lerp(progress * progress * progress, this.startPos.y, this.ascentTopY), progress, 0.4F);
    }

    // Puts it at the given height over its start, with a little strain-shake that dies away by `shakeUntil` progress.
    private void setPositionStraining(double y, float progress, float shakeUntil) {
        double shake = progress < shakeUntil ? DWM.FLYOVER.LIFTOFF_SHAKE * (1F - progress / shakeUntil) : 0;
        double shakeX = shake * (this.random.nextDouble() * 2 - 1);
        double shakeZ = shake * (this.random.nextDouble() * 2 - 1);
        this.setPosition(this.startPos.x + shakeX, y, this.startPos.z + shakeZ);
    }

    private void tickTravel(int i) {
        float progress = (float) i / (float) this.travelTicks;

        switch (this.kind) {
            case FLYBY -> {
                // u runs -1..1; a linear plus a cubic term makes the pass slowest in the middle, in front of the bystander.
                float u = -1F + 2F * progress;
                float bias = DWM.FLYOVER.FLYBY_SLOW_BIAS;
                double offset = this.flybyHalf * (bias * u + (1F - bias) * u * u * u);

                double x = this.startPos.x + this.direction.x * offset;
                double z = this.startPos.z + this.direction.z * offset;
                this.setPosition(x, this.glideAltitude(x, z, 0), z);
            }
            case STREAK -> {
                // Cubic ease-in: starts slow and close, where someone can see it, then rockets off toward the horizon.
                float accelerated = progress * progress * progress;
                double x = this.startPos.x + this.direction.x * DWM.FLYOVER.STREAK_DISTANCE * accelerated;
                double z = this.startPos.z + this.direction.z * DWM.FLYOVER.STREAK_DISTANCE * accelerated;
                this.setPosition(x, this.glideAltitude(x, z, DWM.FLYOVER.STREAK_CLIMB * accelerated), z);
            }
            default -> {
                // A route eases in and out. An approach is already at speed, so it only eases out - cubically,
                // spending most of its time slow and close to the landing spot, where it can be seen.
                float eased = this.kind == Kind.APPROACH
                    ? 1F - (1F - progress) * (1F - progress) * (1F - progress)
                    : MathHelper.clamp(progress * progress * (3F - 2F * progress), 0F, 1F);

                double x = MathHelper.lerp(eased, this.startPos.x, this.targetPos.x);
                double z = MathHelper.lerp(eased, this.startPos.z, this.targetPos.z);

                // Ahead of a slam it also climbs to the height the plunge starts from.
                double climb = this.arrival == Arrival.SLAM ? (DWM.FLYOVER.SLAM_HEIGHT - DWM.FLYOVER.TERRAIN_CLEARANCE) * progress * progress : 0;
                this.setPosition(x, this.glideAltitude(x, z, climb), z);
            }
        }

        // Rotating is what horizontal flight looks like, so this is the only phase that spins.
        this.spinDuringTravel(i);
    }

    // The tail is all vertical movement: the model is already at rest facing its landing direction.
    private void tickTail(int j) {
        if (!this.tailStarted) {
            this.tailStarted = true;
            this.tailStartY = this.getY();
        }

        if (this.arrival == Arrival.SLAM) {
            // Plunge with ease-in gravity, finishing a few ticks before the exterior appears (clients see this
            // entity a few ticks late), then rest on the spot.
            int plungeTicks = Math.max(1, this.tailTicks - DWM.FLYOVER.SLAM_HOLD - DWM.FLYOVER.SLAM_LEAD);
            float progress = Math.min(1F, (float) j / (float) plungeTicks);

            this.setPosition(this.targetPos.x, MathHelper.lerp(progress * progress, this.tailStartY, this.targetPos.y), this.targetPos.z);
        }
        else {
            // Hold over the target and fade away, gone before the exterior fades in below.
            this.setAlpha(1F - (float) j / (float) this.tailTicks);
            this.setPosition(this.targetPos.x, this.tailStartY, this.targetPos.z);
        }
    }

    // Works out the spin for the whole travel phase: ramp up over spinRampTicks, cruise, then (over a target) slow
    // to a stop over settleTicks. Summed tick by tick that is rate * "effective" degrees, so the rate is chosen to
    // come out at exactly the landing facing plus whole turns - whichever number of turns keeps it closest to normal.
    //
    // freeStart: the starting angle doesn't matter (it appears in the sky), so it starts at whatever angle makes
    // the normal rate land on the facing exactly. Otherwise (a ground lift-off) it must start on the exterior's
    // facing, so the rate is adjusted instead.
    private void planSpin(boolean freeStart) {
        // A ramp of r ticks adds (r + 1) / 2 tick-equivalents of full rate, the slowdown of s ticks adds (s - 1) / 2,
        // and the ticks in between are at full rate.
        float effective = this.spinRampTicks > 0
            ? this.travelTicks - (this.spinRampTicks + this.settleTicks) / 2F
            : this.travelTicks - this.settleTicks / 2F - 0.5F;
        effective = Math.max(1F, effective);
        float natural = DWM.FLYOVER.SPIN_DEGREES_PER_TICK * effective;

        if (freeStart) {
            this.spinPlanTotal = natural;
            this.setSpinAngle(this.landingSpin - natural);
        }
        else {
            float remainder = MathHelper.wrapDegrees(this.landingSpin - this.getSpinAngle());
            if (remainder < 0) remainder += 360F;

            float total = remainder + 360F * Math.round((natural - remainder) / 360F);
            if (total < natural * 0.5F) total += 360F; // never (nearly) stop spinning mid-flight
            this.spinPlanTotal = total;
            this.spinPlanRate = total / effective;
        }

        this.spinPlanStart = this.getSpinAngle();
    }

    private void spinDuringTravel(int i) {
        // A flyover that ends over a target lands its last tick of travel exactly on the plan.
        if (this.settleTicks > 0 && i >= this.travelTicks) {
            this.setSpinAngle(this.spinPlanStart + this.spinPlanTotal);
            return;
        }

        float rate = this.spinPlanRate;
        if (this.spinRampTicks > 0 && i <= this.spinRampTicks) rate *= (float) i / (float) this.spinRampTicks;
        else if (this.settleTicks > 0 && i > this.travelTicks - this.settleTicks) rate *= (float) (this.travelTicks - i) / (float) this.settleTicks;

        this.setSpinAngle(this.getSpinAngle() + rate);
    }

    // Eases the altitude toward max(cruise altitude, terrain height + clearance) + extraClimb instead of snapping to
    // the sampled terrain height every tick, so it glides over hills. Unloaded terrain reads as the world's bottom,
    // which just falls back to cruiseY.
    private double glideAltitude(double x, double z, double extraClimb) {
        int terrainY = this.getWorld().getTopY(Heightmap.Type.WORLD_SURFACE, MathHelper.floor(x), MathHelper.floor(z));
        double targetAltitude = Math.max(this.cruiseY, terrainY + DWM.FLYOVER.TERRAIN_CLEARANCE) + extraClimb;
        return MathHelper.lerp(0.1F, this.getY(), targetAltitude);
    }

    // Never written into a chunk, so a stale one can't survive an unload or restart (e.g. if it froze in a chunk that stopped ticking).
    @Override
    public boolean shouldSave() {
        return false;
    }

    // Vanilla culls by bounding box size (~96 blocks here), which would pop it out of view long before it's gone.
    @Override
    public boolean shouldRender(double distance) {
        double range = DWM.FLYOVER.RENDER_DISTANCE;
        return distance < range * range;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }
}
