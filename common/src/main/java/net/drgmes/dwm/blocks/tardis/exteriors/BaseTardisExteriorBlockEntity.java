package net.drgmes.dwm.blocks.tardis.exteriors;

import net.drgmes.dwm.DWM;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.systems.TardisSystemConsoleRoom;
import net.drgmes.dwm.common.tardis.systems.TardisSystemMaterialization;
import net.drgmes.dwm.enums.TardisExteriorState;
import net.drgmes.dwm.setup.ModSounds;
import net.drgmes.dwm.utils.helpers.DimensionHelper;
import net.drgmes.dwm.utils.helpers.TardisHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public abstract class BaseTardisExteriorBlockEntity extends BlockEntity {
    public String tardisId;

    private TardisExteriorState exteriorState = TardisExteriorState.MATERIALIZED;
    private boolean inited;
    private int tick = -1;
    private boolean cloaked = false;

    public BaseTardisExteriorBlockEntity(BlockEntityType<?> type, BlockPos blockPos, BlockState blockState) {
        super(type, blockPos, blockState);
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public void readNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(tag, registryLookup);

        if (tag.contains("tardisId")) this.tardisId = tag.getString("tardisId");
        if (tag.contains("exteriorState")) this.exteriorState = TardisExteriorState.valueOf(tag.getString("exteriorState"));
        if (tag.contains("tick")) this.tick = tag.getInt("tick");
        this.cloaked = tag.getBoolean("cloaked");
    }

    @Override
    public void writeNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (!this.inited) this.init();
        super.writeNbt(tag, registryLookup);

        if (this.tardisId != null) tag.putString("tardisId", this.tardisId);
        tag.putString("exteriorState", this.exteriorState.name());
        tag.putInt("tick", this.tick);
        tag.putBoolean("cloaked", this.cloaked);
    }

    public String getOrCreateTardisId() {
        if (this.tardisId == null || this.tardisId.isEmpty()) this.tardisId = UUID.randomUUID().toString();
        return this.tardisId;
    }

    public ServerWorld getTardisWorld() {
        if (this.tardisId == null || this.tardisId.isEmpty()) return null;
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return null;
        return DimensionHelper.getModWorld(this.getOrCreateTardisId(), serverWorld.getServer());
    }

    public ServerWorld getOrCreateTardisWorld() {
        return TardisHelper.getOrCreateTardisWorld(this);
    }

    public TardisExteriorState getExteriorState() {
        return this.exteriorState;
    }

    public float getMaterializedStateValue() {
        return switch (this.exteriorState) {
            case MATERIALIZED -> 1;
            case DEMATERIALIZED -> 0;
            case PROCESS_DEMAT -> (float) this.tick / DWM.TIMINGS.DEMAT_DURATION;
            case PROCESS_REMAT -> (float) (DWM.TIMINGS.REMAT_DURATION - this.tick) / DWM.TIMINGS.REMAT_DURATION;
            case PROCESS_PULSE -> (float) (DWM.TIMINGS.PULSE_LOOP - this.tick) / DWM.TIMINGS.PULSE_LOOP;
        };
    }

    public void init() {
        if (this.inited) return;
        this.inited = true;

        TardisStateManager.get(this.getTardisWorld()).ifPresent((tardis) -> {
            TardisSystemConsoleRoom consoleRoomSystem = tardis.getSystem(TardisSystemConsoleRoom.class);
            TardisSystemMaterialization materializationSystem = tardis.getSystem(TardisSystemMaterialization.class);

            if (!consoleRoomSystem.inProgress() && !materializationSystem.inProgress() && materializationSystem.isMaterialized()) {
                this.normalize();
            }
        });
    }

    public void reset() {
        this.tick = -1;

        this.exteriorState = switch (this.exteriorState) {
            case MATERIALIZED, DEMATERIALIZED -> this.exteriorState;
            case PROCESS_DEMAT -> TardisExteriorState.DEMATERIALIZED;
            case PROCESS_REMAT -> TardisExteriorState.MATERIALIZED;

            case PROCESS_PULSE -> {
                ModSounds.playTardisConsoleCrackSound(this.world, this.getPos());
                this.tick = DWM.TIMINGS.PULSE_LOOP;
                yield this.exteriorState;
            }
        };

        this.markDirty();
    }

    public void normalize() {
        this.tick = -1;
        this.exteriorState = TardisExteriorState.MATERIALIZED;
        this.markDirty();
    }

    public void demat() {
        this.tick = DWM.TIMINGS.DEMAT_DURATION;
        this.exteriorState = TardisExteriorState.PROCESS_DEMAT;
        if (!this.isSilent()) ModSounds.playTardisTakeoffSound(this.world, this.getPos());
        this.markDirty();
    }

    public void remat() {
        this.tick = DWM.TIMINGS.REMAT_DURATION;
        this.exteriorState = TardisExteriorState.PROCESS_REMAT;
        if (!this.isSilent()) ModSounds.playTardisLandingSound(this.world, this.getPos());
        this.markDirty();
    }

    // Cloak: invisible to look at, but the door, collision and access checks all work exactly as they always have -
    // Minecraft has no clean way to make a block solid for one player and walk-through for another without a much
    // bigger rework, so it stays a "hidden but still there" cloak rather than the show's fully intangible one.
    public boolean isCloaked() {
        return this.cloaked;
    }

    // Pushed by TardisStateManager.setCloakedEnabled, both directly here and to every client through the same
    // TardisExteriorUpdatePacket the demat/remat/pulse actions use, since plain markDirty() only reaches a chunk as
    // it (re)loads, not a client already watching it.
    public void setCloaked(boolean flag) {
        this.cloaked = flag;
        this.markDirty();
    }

    // Whether its TARDIS is set to silent travel. Only the server plays sounds, and only it can look that up.
    private boolean isSilent() {
        return TardisStateManager.get(this.getTardisWorld()).map(TardisStateManager::isSilentTravelEnabled).orElse(false);
    }

    // Flyover takeoff: no fade. The shell stays as it is until the block is removed a couple of ticks later, and
    // the flying copy appears in that very tick, so the handover is a single swap. Its sound is timed to the flyover
    // (TardisFlyoverSession), so none is played here.
    public void dematInstant() {
        this.tick = -1;
        this.markDirty();
    }

    // Flyover landing: no fade either - the shell is just there, with a puff of ground debris. The thud is played a
    // little earlier, by the flight system, so it lines up with the touchdown.
    public void rematInstant() {
        this.normalize();
        this.spawnLandingDust();
    }

    // Server-side only; clients get the state from the packet.
    private void spawnLandingDust() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;

        BlockState ground = serverWorld.getBlockState(this.getPos().down());
        if (ground.isAir() || !ground.getFluidState().isEmpty()) ground = Blocks.DIRT.getDefaultState();

        Vec3d origin = Vec3d.ofBottomCenter(this.getPos()).add(0, 0.1, 0);
        serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), origin.x, origin.y, origin.z, 60, 0.6, 0.1, 0.6, 0.15);
    }

    public void pulse() {
        this.tick = DWM.TIMINGS.PULSE_LOOP;
        this.exteriorState = TardisExteriorState.PROCESS_PULSE;
        ModSounds.playTardisConsoleCrackSound(this.world, this.getPos());
        this.markDirty();
    }

    public void tick() {
        if (this.tick < 0) return;
        if (this.tick > 0) this.tick -= 1;
        else this.reset();
    }
}
