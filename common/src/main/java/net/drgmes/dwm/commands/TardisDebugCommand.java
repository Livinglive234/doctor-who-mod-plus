package net.drgmes.dwm.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.drgmes.dwm.DWM;
import net.drgmes.dwm.commands.types.TardisDimensionArgumentType;
import net.drgmes.dwm.common.tardis.TardisStateManager;
import net.drgmes.dwm.common.tardis.consolerooms.TardisConsoleRoomEntry;
import net.drgmes.dwm.common.tardis.exteriors.TardisExteriorEntry;
import net.drgmes.dwm.utils.helpers.TardisHelper;
import net.minecraft.block.BlockState;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Locale;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class TardisDebugCommand {
    private static final DynamicCommandExceptionType INVALID_TARDIS_EXCEPTION = new DynamicCommandExceptionType((id) -> DWM.TEXTS.ARGUMENT_INVALID_TARDIS);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal(DWM.MODID).then(
                literal("tardis").then(
                    literal("debug").then(
                        argument("tardisId", new TardisDimensionArgumentType())
                            .requires((source) -> source.hasPermissionLevel(3))
                            .executes(TardisDebugCommand::executeDebug)
                    )
                ).then(
                    // Console control placement helper: aim at a spot on the console and run this - it prints
                    // the exact addControlEntry Vec3d for wherever the crosshair is pointing. Only reliable where
                    // the block's actual collision shape lines up with the decorative model's surface - for models
                    // whose collision box doesn't match their visuals (most console models), use "mark" instead.
                    literal("raytrace")
                        .requires((source) -> source.hasPermissionLevel(3))
                        .executes(TardisDebugCommand::executeRaytrace)
                ).then(
                    // Same math as raytrace, but reads the player's own eye position instead of a block raycast -
                    // for placing a control against a decorative model whose collision box doesn't match what's
                    // rendered. Go into spectator mode (collision-free), fly your eye right up against the spot
                    // in the model you want, then run this (typing in chat doesn't move you) to read it off.
                    literal("mark")
                        .requires((source) -> source.hasPermissionLevel(3))
                        .executes(TardisDebugCommand::executeMark)
                )
            )
        );
    }

    private static int executeDebug(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();

        ServerWorld tardisWorld = TardisDimensionArgumentType.getDimensionArgument(context, "tardisId");
        Optional<TardisStateManager> tardisHolder = TardisStateManager.get(tardisWorld);
        if (tardisHolder.isEmpty()) throw INVALID_TARDIS_EXCEPTION.create(null);

        MutableText text = Text.empty();
        TardisStateManager tardis = tardisHolder.get();
        TardisConsoleRoomEntry consoleRoom = tardis.getConsoleRoom();
        TardisExteriorEntry exteriorType = tardis.getExteriorType();

        text.append(Text.literal("< === " + tardis.getId() + " === >")).append("\n");
        text.append(Text.literal("\n"));
        text.append(Text.literal("Owner: ").append(Text.literal(tardis.getOwner() != null ? tardis.getOwner().toString() : "None").formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("Room: ").append(Text.literal(consoleRoom == null ? "None" : consoleRoom.name).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("Exterior: ").append(Text.literal(exteriorType == null ? "None" : exteriorType.name).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("\n"));
        text.append(Text.literal("Current Dim: ").append(Text.literal(tardis.getCurrentExteriorDimension().getValue().toString()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("Next Dim: ").append(Text.literal(tardis.getDestinationExteriorDimension().getValue().toString()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("Prev Dim: ").append(Text.literal(tardis.getPreviousExteriorDimension().getValue().toString()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("\n"));
        text.append(Text.literal("Current Pos: ").append(Text.literal(tardis.getCurrentExteriorPosition().toShortString()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("Next Pos: ").append(Text.literal(tardis.getDestinationExteriorPosition().toShortString()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("Prev Pos: ").append(Text.literal(tardis.getPreviousExteriorPosition().toShortString()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("\n"));
        text.append(Text.literal("Current Facing: ").append(Text.literal(tardis.getCurrentExteriorFacing().getName()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("Next Facing: ").append(Text.literal(tardis.getDestinationExteriorFacing().getName()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("Prev Facing: ").append(Text.literal(tardis.getPreviousExteriorFacing().getName()).formatted(Formatting.AQUA))).append("\n");
        text.append(Text.literal("\n"));
        text.append(Text.literal("Broken: ").append(Text.literal(String.valueOf(tardis.isBroken())).formatted(Formatting.BLUE))).append("\n");
        text.append(Text.literal("Door Locked: ").append(Text.literal(String.valueOf(tardis.isDoorsLocked())).formatted(Formatting.BLUE))).append("\n");
        text.append(Text.literal("Door Opened: ").append(Text.literal(String.valueOf(tardis.isDoorsOpened())).formatted(Formatting.BLUE))).append("\n");
        text.append(Text.literal("Light Enabled: ").append(Text.literal(String.valueOf(tardis.isLightEnabled())).formatted(Formatting.BLUE))).append("\n");
        text.append(Text.literal("Shields Enabled: ").append(Text.literal(String.valueOf(tardis.isShieldsEnabled())).formatted(Formatting.BLUE))).append("\n");
        text.append(Text.literal("Fuel Harvesting: ").append(Text.literal(String.valueOf(tardis.isFuelHarvesting())).formatted(Formatting.BLUE))).append("\n");
        text.append(Text.literal("Energy Harvesting: ").append(Text.literal(String.valueOf(tardis.isEnergyHarvesting())).formatted(Formatting.BLUE))).append("\n");
        text.append(Text.literal("\n"));
        text.append(Text.literal("Fuel Amount: ").append(Text.literal(tardis.getFuelAmount() + " / " + tardis.getFuelCapacity()).formatted(Formatting.GOLD))).append("\n");
        text.append(Text.literal("Energy Amount: ").append(Text.literal(tardis.getEnergyAmount() + " / " + tardis.getEnergyCapacity()).formatted(Formatting.GOLD))).append("\n");
        text.append(Text.literal("\n"));
        text.append(Text.literal("< ========================================== >"));

        if (player != null) player.sendMessage(text, false);
        else DWM.LOGGER.info(text.getString());

        return Command.SINGLE_SUCCESS;
    }

    // Mirrors TardisConsoleUnitControlEntry.createEntity's own formula in reverse: that one turns a hand-placed
    // Vec3d plus the console's facing into a world position; this turns a world position (wherever the crosshair
    // is) plus the console's facing back into the Vec3d that would produce it.
    private static int executeRaytrace(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        ServerWorld world = player.getServerWorld();
        if (!TardisHelper.isTardisDimension(world)) {
            player.sendMessage(Text.literal("Not inside a TARDIS.").formatted(Formatting.RED), false);
            return 0;
        }

        Optional<TardisStateManager> tardisHolder = TardisStateManager.get(world);
        if (tardisHolder.isEmpty()) return 0;

        BlockPos consolePos = tardisHolder.get().getMainConsolePosition();
        BlockState consoleState = world.getBlockState(consolePos);
        Direction facing = consoleState.get(Properties.HORIZONTAL_FACING);

        Vec3d eyePos = player.getEyePos();
        Vec3d reach = eyePos.add(player.getRotationVec(1.0F).multiply(8));
        BlockHitResult hitResult = world.raycast(new RaycastContext(eyePos, reach, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        Vec3d hitPos = hitResult.getType() == HitResult.Type.MISS ? reach : hitResult.getPos();

        float angle = 1.57F * ((facing.asRotation() - 90) / -90);
        Vec3d offset = hitPos.subtract(Vec3d.ofCenter(consolePos)).rotateY(-angle);

        player.sendMessage(Text.literal(String.format(Locale.ROOT, "Hit: %.4f, %.4f, %.4f", hitPos.x, hitPos.y, hitPos.z)).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal(String.format(Locale.ROOT, "new Vec3d(%.4fF, %.4fF, %.4fF)", offset.x, offset.y, offset.z)).formatted(Formatting.AQUA), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int executeMark(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        ServerWorld world = player.getServerWorld();
        if (!TardisHelper.isTardisDimension(world)) {
            player.sendMessage(Text.literal("Not inside a TARDIS.").formatted(Formatting.RED), false);
            return 0;
        }

        Optional<TardisStateManager> tardisHolder = TardisStateManager.get(world);
        if (tardisHolder.isEmpty()) return 0;

        BlockPos consolePos = tardisHolder.get().getMainConsolePosition();
        BlockState consoleState = world.getBlockState(consolePos);
        Direction facing = consoleState.get(Properties.HORIZONTAL_FACING);

        Vec3d eyePos = player.getEyePos();
        float angle = 1.57F * ((facing.asRotation() - 90) / -90);
        Vec3d offset = eyePos.subtract(Vec3d.ofCenter(consolePos)).rotateY(-angle);

        player.sendMessage(Text.literal(String.format(Locale.ROOT, "Eye: %.4f, %.4f, %.4f", eyePos.x, eyePos.y, eyePos.z)).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal(String.format(Locale.ROOT, "new Vec3d(%.4fF, %.4fF, %.4fF)", offset.x, offset.y, offset.z)).formatted(Formatting.AQUA), false);

        return Command.SINGLE_SUCCESS;
    }
}
