package net.drgmes.dwm.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import net.drgmes.dwm.compat.immersiveportals.ExteriorViewCameraMemory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

import static dev.architectury.event.events.client.ClientCommandRegistrationEvent.literal;

// Client-side: the camera it reports lives in the player's own exterior view screen.
public class ExteriorViewCameraCommand {
    public static void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        dispatcher.register(literal("dwmcam").executes((context) -> {
            ClientCommandSourceStack source = context.getSource();

            if (!ExteriorViewCameraMemory.used) {
                source.arch$sendFailure(Text.literal("Open the monitor's Exterior View first, move the camera, and close it."));
                return 0;
            }

            source.arch$sendSuccess(() -> Text.literal(String.format(Locale.ROOT, "Exterior view camera when last closed: orbit %.1f deg from the doors, tilt %.1f deg down, %.1f blocks away.", ExteriorViewCameraMemory.orbitFromDoors, ExteriorViewCameraMemory.pitch, ExteriorViewCameraMemory.distance)).formatted(Formatting.AQUA), false);
            source.arch$sendSuccess(() -> Text.literal(String.format(Locale.ROOT, "CAMERA_PITCH = %.1fF", ExteriorViewCameraMemory.pitch)).formatted(Formatting.GRAY), false);
            return 1;
        }));
    }
}
