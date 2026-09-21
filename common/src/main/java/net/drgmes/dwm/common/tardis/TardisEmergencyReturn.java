package net.drgmes.dwm.common.tardis;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.systems.TardisSystemFlight;
import net.drgmes.dwm.common.tardis.systems.TardisSystemMaterialization;
import net.drgmes.dwm.common.tardis.systems.TardisSystemMaterialization.LandingSpot;
import net.drgmes.dwm.enums.TardisVerticalScanning;
import net.drgmes.dwm.setup.ModSounds;
import net.drgmes.dwm.utils.helpers.DimensionHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The emergency return. When its owner is down to half a heart the TARDIS tolls the cloister bell, inside and out, twice; when they
 * die it tolls three times and then takes itself to a spot near their bed, or near the world spawn if they have none.
 * <p>
 * Nothing here is saved: a sequence a restart cuts short isn't worth a save format.
 */
public class TardisEmergencyReturn {
    private static final float HALF_HEART = 1.0F;
    private static final int LANDING_ATTEMPTS = 16;
    private static final int MIN_DISTANCE_FROM_BED = 2;

    private enum OwnerState {
        HEALTHY,
        WOUNDED,
        DEAD,
    }

    private final TardisStateManager tardis;

    private OwnerState ownerState = OwnerState.HEALTHY;
    private int chimesLeft = 0;
    private int chimeTimer = 0;

    // Where the owner would respawn, noted at the moment they die: by the time the bell is done they may have respawned
    // (and a bed that was there may have been used up or, if it was in the way, not been used at all).
    private boolean travelPending = false;
    private RegistryKey<World> homeDimension;
    private BlockPos homePosition;

    public TardisEmergencyReturn(TardisStateManager tardis) {
        this.tardis = tardis;
    }

    public void tick() {
        if (!this.tardis.isEmergencyReturnEnabled()) {
            this.reset();
            return;
        }

        this.watchOwner();
        this.ring();

        if (this.travelPending && this.chimesLeft == 0 && this.chimeTimer == 0) this.travel();
    }

    private void reset() {
        this.ownerState = OwnerState.HEALTHY;
        this.chimesLeft = 0;
        this.chimeTimer = 0;
        this.travelPending = false;
        this.homeDimension = null;
        this.homePosition = null;
    }

    private void watchOwner() {
        UUID ownerId = this.tardis.getOwner();
        if (ownerId == null) return;

        ServerPlayerEntity owner = this.tardis.getWorld().getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null) return;

        if (owner.isDead()) {
            if (this.ownerState == OwnerState.DEAD) return;

            this.ownerState = OwnerState.DEAD;
            this.chimesLeft = 3;
            this.noteHome(owner);
        }
        else if (owner.getHealth() <= HALF_HEART) {
            if (this.ownerState != OwnerState.HEALTHY) return;

            this.ownerState = OwnerState.WOUNDED;
            this.chimesLeft = 2;
        }
        else {
            this.ownerState = OwnerState.HEALTHY;
        }
    }

    // The bed, or the world spawn if there is none. A bed that has been broken since it was slept in is none, though
    // the player still remembers it.
    private void noteHome(ServerPlayerEntity owner) {
        MinecraftServer server = this.tardis.getWorld().getServer();

        BlockPos bed = owner.getSpawnPointPosition();
        ServerWorld bedWorld = bed != null ? DimensionHelper.getWorld(owner.getSpawnPointDimension(), server) : null;
        BlockState bedState = bedWorld != null ? bedWorld.getBlockState(bed) : null;

        if (bedState != null && (bedState.isIn(BlockTags.BEDS) || bedState.isOf(Blocks.RESPAWN_ANCHOR))) {
            this.homeDimension = bedWorld.getRegistryKey();
            this.homePosition = bed;
        }
        else {
            // The spawn point is stored at whatever height it was set, so it is taken to the ground it is on
            ServerWorld overworld = server.getOverworld();
            BlockPos spawn = overworld.getSpawnPos();

            this.homeDimension = overworld.getRegistryKey();
            this.homePosition = spawn.withY(overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()));
        }

        this.travelPending = true;
    }

    // A toll's sound is as long as the interval to the next, so the last one is waited out too, before the TARDIS sets off.
    private void ring() {
        if (this.chimeTimer > 0) this.chimeTimer -= 1;
        if (this.chimeTimer > 0 || this.chimesLeft == 0) return;

        ModSounds.playTardisCloisterBellSound(this.tardis.getWorld(), this.tardis.getMainConsolePosition());
        this.ringExterior();
        this.chimesLeft -= 1;
        this.chimeTimer = DWM.TIMINGS.EMERGENCY_CHIME_INTERVAL;
    }

    // Heard outside too, when the TARDIS is there to be heard.
    private void ringExterior() {
        if (!this.tardis.getSystem(TardisSystemMaterialization.class).isMaterialized()) return;

        ServerWorld exteriorWorld = this.tardis.getExteriorWorld();
        if (exteriorWorld != null) ModSounds.playTardisCloisterBellSound(exteriorWorld, this.tardis.getCurrentExteriorPosition());
    }

    private void travel() {
        TardisSystemMaterialization materializationSystem = this.tardis.getSystem(TardisSystemMaterialization.class);
        TardisSystemFlight flightSystem = this.tardis.getSystem(TardisSystemFlight.class);

        // Redirecting a flight that is under way isn't safe (the flyover was planned for its first destination), so it waits.
        // When that flight lands it turns the lever off, which ends the wait.
        if (materializationSystem.inProgress() || flightSystem.inProgress()) return;
        this.travelPending = false;

        if (this.isHome(materializationSystem)) return;

        boolean canFly = materializationSystem.isEnabled() && flightSystem.isEnabled() && (this.tardis.getFuelAmount() > 0 || this.tardis.getEnergyAmount() > 0);
        ServerWorld homeWorld = DimensionHelper.getWorld(this.homeDimension, this.tardis.getWorld().getServer());
        LandingSpot spot = canFly && homeWorld != null ? this.findLandingSpot(materializationSystem, homeWorld) : null;

        if (spot == null) {
            ModSounds.playTardisFailSound(this.tardis.getWorld(), this.tardis.getMainConsolePosition());
            return;
        }

        this.tardis.setDestinationDimension(this.homeDimension);
        this.tardis.setDestinationPosition(spot.pos());
        this.tardis.setDestinationFacing(spot.facing());
        flightSystem.init(true, this.tardis.getOwner());
        this.tardis.markConsoleTilesUpdated();
    }

    private boolean isHome(TardisSystemMaterialization materializationSystem) {
        int radius = DWM.TIMINGS.EMERGENCY_HOME_RADIUS;

        return materializationSystem.isMaterialized()
            && this.tardis.getCurrentExteriorDimension().equals(this.homeDimension)
            && this.tardis.getCurrentExteriorPosition().getSquaredDistance(this.homePosition) <= radius * radius;
    }

    /**
     * Tries spots around the bed, and takes the one nearest its floor: the search follows the ground up or down, so a
     * spot beside a bed in a house can just as well be a cellar or a cave below it.
     */
    private LandingSpot findLandingSpot(TardisSystemMaterialization materializationSystem, ServerWorld homeWorld) {
        Random random = homeWorld.getRandom();
        Direction facing = this.tardis.getDestinationExteriorFacing();
        int radius = DWM.TIMINGS.EMERGENCY_HOME_RADIUS;

        LandingSpot best = null;
        for (int i = 0; i < LANDING_ATTEMPTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = MIN_DISTANCE_FROM_BED + random.nextDouble() * (radius - MIN_DISTANCE_FROM_BED);
            BlockPos candidate = this.homePosition.add((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));

            LandingSpot spot = materializationSystem.findLandingSpot(homeWorld, candidate, facing, TardisVerticalScanning.BOTTOM);
            if (spot == null || spot.pos().getSquaredDistance(this.homePosition) > radius * radius) continue;

            if (best == null || Math.abs(spot.pos().getY() - this.homePosition.getY()) < Math.abs(best.pos().getY() - this.homePosition.getY())) best = spot;
            if (Math.abs(best.pos().getY() - this.homePosition.getY()) <= 1) break;
        }

        return best;
    }
}
