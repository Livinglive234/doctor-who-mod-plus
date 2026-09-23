package net.drgmes.dwm.setup;

import net.drgmes.dwm.network.client.TardisTakeoffSoundPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.function.Supplier;

public class ModSounds {
    private static final double EARSHOT = 16; // blocks, at volume 1

    public static final Supplier<SoundEvent> SONIC_SCREWDRIVER_MAIN = Registration.registerSoundEvent("sonic_screwdriver_main");
    public static final Supplier<SoundEvent> SONIC_SUNGLASSES_MAIN = Registration.registerSoundEvent("sonic_sunglasses_main");

    public static final Supplier<SoundEvent> TARDIS_DOORS_KNOCK = Registration.registerSoundEvent("tardis_doors_knock");
    public static final Supplier<SoundEvent> TARDIS_DOORS_UNLOCK = Registration.registerSoundEvent("tardis_doors_unlock");
    public static final Supplier<SoundEvent> TARDIS_DOORS_LOCK = Registration.registerSoundEvent("tardis_doors_lock");
    public static final Supplier<SoundEvent> TARDIS_DOORS_CLOSE = Registration.registerSoundEvent("tardis_doors_close");
    public static final Supplier<SoundEvent> TARDIS_DOORS_CLOSE_WOODEN = Registration.registerSoundEvent("tardis_doors_close_wooden");

    public static final Supplier<SoundEvent> TARDIS_BROKEN_FLARING = Registration.registerSoundEvent("tardis_broken_flaring");
    public static final Supplier<SoundEvent> TARDIS_ERROR = Registration.registerSoundEvent("tardis_error");
    public static final Supplier<SoundEvent> TARDIS_BELL = Registration.registerSoundEvent("tardis_bell");
    public static final Supplier<SoundEvent> TARDIS_CLOISTER_BELL = Registration.registerSoundEvent("tardis_cloister_bell");
    public static final Supplier<SoundEvent> TARDIS_FLIGHT = Registration.registerSoundEvent("tardis_flight");
    public static final Supplier<SoundEvent> TARDIS_LAND = Registration.registerSoundEvent("tardis_land");
    public static final Supplier<SoundEvent> TARDIS_LAND_GROUND = Registration.registerSoundEvent("tardis_land_ground");
    public static final Supplier<SoundEvent> TARDIS_TAKEOFF = Registration.registerSoundEvent("tardis_takeoff");

    public static final Supplier<SoundEvent> TARDIS_CONTROL_1 = Registration.registerSoundEvent("tardis_control_1");
    public static final Supplier<SoundEvent> TARDIS_CONTROL_2 = Registration.registerSoundEvent("tardis_control_2");
    public static final Supplier<SoundEvent> TARDIS_CONTROL_3 = Registration.registerSoundEvent("tardis_control_3");
    public static final Supplier<SoundEvent> TARDIS_CONTROL_4 = Registration.registerSoundEvent("tardis_control_4");
    public static final Supplier<SoundEvent> TARDIS_CONTROL_HANDBRAKE_ON = Registration.registerSoundEvent("tardis_control_handbrake_on");
    public static final Supplier<SoundEvent> TARDIS_CONTROL_HANDBRAKE_OFF = Registration.registerSoundEvent("tardis_control_handbrake_off");
    public static final Supplier<SoundEvent> TARDIS_CONTROL_RANDOMIZER = Registration.registerSoundEvent("tardis_control_randomizer");

    public static final Supplier<SoundEvent> TARDIS_PHONE_RING = Registration.registerSoundEvent("tardis_phone_ring");
    public static final Supplier<SoundEvent> TARDIS_PHONE_RINGBACK = Registration.registerSoundEvent("tardis_phone_ringback");
    public static final Supplier<SoundEvent> TARDIS_PHONE_HANGUP = Registration.registerSoundEvent("tardis_phone_hangup");

    public static void init() {
    }

    public static void playSound(World world, BlockPos blockPos, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, blockPos, sound, SoundCategory.BLOCKS, volume, pitch);
    }

    // Anchored to an exact point rather than a block, e.g. a control entity's own position - so it fades and pans
    // the way any other positional sound does as a player moves away from or around it.
    public static void playSound(World world, Vec3d pos, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.BLOCKS, volume, pitch);
    }

    public static void playTardisConsoleCrackSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_BROKEN_FLARING.get(), 0.65F, 1.0F);
    }

    public static void playTardisRepairSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0F, 1.0F);
    }

    public static void playTardisDoorsKnockSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_DOORS_KNOCK.get(), 1.0F, 1.0F);
    }

    public static void playTardisDoorsUnlockSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_DOORS_UNLOCK.get(), 1.0F, 1.0F);
    }

    public static void playTardisDoorsLockSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_DOORS_LOCK.get(), 1.0F, 1.0F);
    }

    public static void playTardisDoorsOpenSound(World world, BlockPos blockPos, boolean isWooden) {
        playSound(world, blockPos, isWooden ? SoundEvents.BLOCK_WOODEN_DOOR_OPEN : SoundEvents.BLOCK_IRON_DOOR_OPEN, 1.0F, 1.0F);
    }

    public static void playTardisDoorsCloseSound(World world, BlockPos blockPos, boolean isWooden) {
        playSound(world, blockPos, isWooden ? TARDIS_DOORS_CLOSE_WOODEN.get() : TARDIS_DOORS_CLOSE.get(), 1.0F, 1.0F);
    }

    public static void playTardisShieldsOnSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0F, 1.0F);
    }

    public static void playTardisShieldsOffSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 1.0F);
    }

    public static void playTardisHandbrakeOnSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_CONTROL_HANDBRAKE_ON.get(), 1.0F, 1.0F);
    }

    public static void playTardisHandbrakeOffSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_CONTROL_HANDBRAKE_OFF.get(), 1.0F, 1.0F);
    }

    public static void playTardisComponentAddedSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0F, 1.0F);
    }

    public static void playTardisComponentRemovedSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 1.0F);
    }

    public static void playTardisTakeoffSound(World world, BlockPos blockPos) {
        playTardisTakeoffSound(world, blockPos, 1.0F);
    }

    public static void playTardisTakeoffSound(World world, BlockPos blockPos, float volume) {
        playSound(world, blockPos, TARDIS_TAKEOFF.get(), volume, 1.0F);
    }

    /**
     * The takeoff sound, faded out after {@code fadeDelay} ticks over {@code fadeTicks}, so it can be made to end with
     * a flyover. Played by each client in earshot, since a sound the server plays can't be faded.
     */
    public static void playTardisTakeoffSound(ServerWorld world, BlockPos blockPos, float volume, int fadeDelay, int fadeTicks) {
        Vec3d center = Vec3d.ofCenter(blockPos);
        new TardisTakeoffSoundPacket(blockPos, volume, fadeDelay, fadeTicks)
            .sendTo(world.getPlayers((player) -> player.squaredDistanceTo(center) < EARSHOT * EARSHOT));
    }

    public static void playTardisLandingSound(World world, BlockPos blockPos) {
        playTardisLandingSound(world, blockPos, 1.0F);
    }

    public static void playTardisLandingSound(World world, BlockPos blockPos, float volume) {
        playSound(world, blockPos, TARDIS_LAND.get(), volume, 1.0F);
    }

    // The heavy thud of a flyover slam landing. Mono on purpose: Minecraft only positions and fades mono sounds in 3D.
    public static void playTardisGroundLandingSound(World world, BlockPos blockPos) {
        playTardisGroundLandingSound(world, blockPos, 1.0F);
    }

    public static void playTardisGroundLandingSound(World world, BlockPos blockPos, float volume) {
        playSound(world, blockPos, TARDIS_LAND_GROUND.get(), volume, 1.0F);
    }

    public static void playTardisFlightSound(World world, BlockPos blockPos) {
        playTardisFlightSound(world, blockPos, 1.0F);
    }

    public static void playTardisFlightSound(World world, BlockPos blockPos, float volume) {
        playSound(world, blockPos, TARDIS_FLIGHT.get(), volume, 1.0F);
    }

    public static void playTardisFailSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_ERROR.get(), 1.0F, 1.0F);
    }

    public static void playTardisBellSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_BELL.get(), 1.0F, 1.0F);
    }

    // One toll of the cloister bell, mono so that it is heard from where it is. It lasts DWM.TIMINGS.EMERGENCY_CHIME_INTERVAL.
    public static void playTardisCloisterBellSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_CLOISTER_BELL.get(), 1.0F, 1.0F);
    }

    public static void playSonicSunglassesMainSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SONIC_SUNGLASSES_MAIN.get(), 0.25F, 1.0F);
    }

    public static void playSonicScrewdriverMainSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SONIC_SCREWDRIVER_MAIN.get(), 0.25F, 1.0F);
    }

    public static void playSonicScrewdriverPutSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, 1.0F, 1.0F);
    }

    public static void playSonicScrewdriverPickupSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, 1.0F, 1.0F);
    }

    public static void playTardisArsStructureCreatedSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0F, 1.0F);
    }

    public static void playTardisArsStructureDestroyedSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 1.0F);
    }

    public static void playTardisTeleporterSentSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 1.0F);
    }

    public static void playTardisTeleporterReceivedSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0F, 1.0F);
    }

    // Natural pitch - these are real recordings, not a vanilla sound reused for its tone, so pitching them up
    // like a control click would just speed the whole recording up and make it sound wrong.
    public static void playTardisPhoneRingSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_PHONE_RING.get(), 0.6F, 1.0F);
    }

    public static void playTardisPhoneRingSound(World world, Vec3d pos) {
        playSound(world, pos, TARDIS_PHONE_RING.get(), 0.6F, 1.0F);
    }

    public static void playTardisPhoneRingbackSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_PHONE_RINGBACK.get(), 0.6F, 1.0F);
    }

    // Pitched down slightly, but baked into the recording itself (see hangup.ogg) rather than via the pitch
    // param here - that param changes playback speed, not just tone, which is what made the ring sound earlier.
    public static void playTardisPhoneEndSound(World world, BlockPos blockPos) {
        playSound(world, blockPos, TARDIS_PHONE_HANGUP.get(), 0.4F, 1.0F);
    }
}
