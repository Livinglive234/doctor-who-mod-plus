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
 * The owner watch. Whether or not the Emergency Return lever is on, when the owner is down to a heart and a half the
 * TARDIS tolls the cloister bell, inside and out, up to ten times, stopping as soon as they heal past that.
 * <p>
 * When the owner dies, with the lever on it tolls three times and then takes itself to a spot near their bed, or near
 * the world spawn if they have none. With it off, it only closes its doors if they're open.
 * <p>
 * Either way the owner-death check also drops Cloak, if it's up, so a keyless owner isn't locked out of their own TARDIS.
 * <p>
 * Nothing here is saved: a sequence a restart cuts short isn't worth a save format.
 */
public class TardisEmergencyReturn {
    private static final float WOUNDED_HEALTH = 3.0F; // a heart and a half
    private static final int WOUNDED_CHIMES = 10;
    private static final int LANDING_ATTEMPTS = 16;
    private static final int MIN_DISTANCE_FROM_BED = 2;
    private static final int HOME_RADIUS = DWM.TIMINGS.EMERGENCY_HOME_RADIUS;

    private enum OwnerState {
        HEALTHY,
        WOUNDED,
        DEAD,
    }

    private final TardisStateManager tardis;

    private OwnerState ownerState = OwnerState.HEALTHY;
    private int chimesLeft = 0;
    private int chimeTimer = 0;

    private record Home(RegistryKey<World> dimension, BlockPos position) {
    }

    // Where the owner would respawn, noted at the moment they die: by the time the bell is done they may have respawned
    // (and a bed that was there may have been used up or, if it was in the way, not been used at all). Set while the
    // trip there is still to be made.
    private Home home = null;

    public TardisEmergencyReturn(TardisStateManager tardis) {
        this.tardis = tardis;
    }

    public void tick() {
        // All of this but the trip home runs regardless of the Emergency Return lever.
        this.watchOwner();

        // Switched off mid-sequence: there's no trip to make, and no death toll to finish.
        if (!this.tardis.isEmergencyReturnEnabled()) {
            this.home = null;
            if (this.ownerState == OwnerState.DEAD) this.chimesLeft = 0;
        }

        this.ring();

        if (this.home != null && this.chimesLeft == 0 && this.chimeTimer == 0) this.travel();
    }

    private void watchOwner() {
        UUID ownerId = this.tardis.getOwner();
        if (ownerId == null) return;

        ServerPlayerEntity owner = this.tardis.getWorld().getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null) return;

        if (owner.isDead()) {
            if (this.ownerState == OwnerState.DEAD) return;

            this.ownerState = OwnerState.DEAD;

            if (this.tardis.isEmergencyReturnEnabled()) {
                this.chimesLeft = 3;
                this.noteHome(owner);
            }
            else {
                this.chimesLeft = 0;
                if (this.tardis.isDoorsOpened()) this.tardis.setDoorsOpenState(false);
            }

            // Otherwise a dead owner with no key on them couldn't get back into their own TARDIS.
            if (this.tardis.isCloakedEnabled()) this.tardis.setCloakedEnabled(false, null);
        }
        else if (owner.getHealth() <= WOUNDED_HEALTH) {
            if (this.ownerState != OwnerState.HEALTHY) return;

            this.ownerState = OwnerState.WOUNDED;
            this.chimesLeft = WOUNDED_CHIMES;
        }
        else {
            // Healed: the wounded toll stops, but a death toll already under way (they've respawned) plays out.
            if (this.ownerState == OwnerState.WOUNDED) this.chimesLeft = 0;
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
            this.home = new Home(bedWorld.getRegistryKey(), bed);
        }
        else {
            // The spawn point is stored at whatever height it was set, so it is taken to the ground it is on
            ServerWorld overworld = server.getOverworld();
            BlockPos spawn = overworld.getSpawnPos();

            this.home = new Home(overworld.getRegistryKey(), spawn.withY(overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ())));
        }
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
        // Redirecting a flight that is under way isn't safe (the flyover was planned for its first destination), so it waits.
        // When that flight lands it turns the lever off, which ends the wait.
        if (this.tardis.isTravelInProgress()) return;

        Home home = this.home;
        this.home = null;

        TardisSystemMaterialization materializationSystem = this.tardis.getSystem(TardisSystemMaterialization.class);
        if (this.isHome(materializationSystem, home)) return;

        // Both systems are there, or the lever would be off
        boolean canFly = this.tardis.getFuelAmount() > 0 || this.tardis.getEnergyAmount() > 0;
        ServerWorld homeWorld = DimensionHelper.getWorld(home.dimension(), this.tardis.getWorld().getServer());
        LandingSpot spot = canFly && homeWorld != null ? this.findLandingSpot(materializationSystem, homeWorld, home.position()) : null;

        if (spot == null) {
            ModSounds.playTardisFailSound(this.tardis.getWorld(), this.tardis.getMainConsolePosition());
            return;
        }

        this.tardis.setDestinationDimension(home.dimension());
        this.tardis.setDestinationPosition(spot.pos());
        this.tardis.setDestinationFacing(spot.facing());
        this.tardis.markEmergencyReturnFlight();
        this.tardis.getSystem(TardisSystemFlight.class).init(true, this.tardis.getOwner());
        this.tardis.markConsoleTilesUpdated();
    }

    private boolean isHome(TardisSystemMaterialization materializationSystem, Home home) {
        return materializationSystem.isMaterialized()
            && this.tardis.getCurrentExteriorDimension().equals(home.dimension())
            && this.tardis.getCurrentExteriorPosition().getSquaredDistance(home.position()) <= HOME_RADIUS * HOME_RADIUS;
    }

    /**
     * Tries spots around the bed, and takes the one nearest its floor: the search follows the ground up or down, so a
     * spot beside a bed in a house can just as well be a cellar or a cave below it.
     */
    private LandingSpot findLandingSpot(TardisSystemMaterialization materializationSystem, ServerWorld homeWorld, BlockPos home) {
        Random random = homeWorld.getRandom();
        Direction facing = this.tardis.getDestinationExteriorFacing();

        LandingSpot best = null;
        int bestOffset = Integer.MAX_VALUE; // how far it is above or below the home
        for (int i = 0; i < LANDING_ATTEMPTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = MIN_DISTANCE_FROM_BED + random.nextDouble() * (HOME_RADIUS - MIN_DISTANCE_FROM_BED);
            BlockPos candidate = home.add((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));

            LandingSpot spot = materializationSystem.findLandingSpot(homeWorld, candidate, facing, TardisVerticalScanning.BOTTOM);
            if (spot == null || spot.pos().getSquaredDistance(home) > HOME_RADIUS * HOME_RADIUS) continue;

            int offset = Math.abs(spot.pos().getY() - home.getY());
            if (offset < bestOffset) {
                best = spot;
                bestOffset = offset;
            }

            if (bestOffset <= 1) break;
        }

        return best;
    }
}
