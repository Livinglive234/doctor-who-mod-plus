package net.drgmes.dwm.blocks.tardis.consoleunits.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.event.events.client.ClientTickEvent;
import net.drgmes.dwm.DWM;
import net.drgmes.dwm.compat.immersiveportals.ExteriorViewState;
import net.drgmes.dwm.blocks.tardis.consoleunits.BaseTardisConsoleUnitBlockEntity;
import net.drgmes.dwm.network.server.TardisExteriorViewPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A live third-person view of the TARDIS's exterior: a camera a fixed distance away that orbits around it with A and D
 * at a fixed tilt. Immersive Portals renders the exterior's world (whatever dimension it is in) from that camera
 * into a framebuffer, which is drawn here as a flat picture - unlike a portal, so it doesn't shift as the viewer moves.
 * The server streams the surrounding chunks to this player while the screen is open (see ExteriorViewCompat).
 */
@Environment(EnvType.CLIENT)
public class TardisConsoleUnitMonitorExteriorViewScreen extends BaseTardisConsoleUnitMonitorScreen {
    private static final double CAMERA_DISTANCE = 8.0;
    private static final double MIN_CAMERA_DISTANCE = 1.5;
    private static final float CAMERA_PITCH = 30F; // looking down on it
    private static final float KEY_DEGREES_PER_SECOND = 60F;
    private static final int MAX_RENDER_DISTANCE = 8;
    private static final long FLYOVER_LOST_MS = 4000L;

    private final RegistryKey<World> dimension;
    private Vec3d target;
    private final int flyoverEntityId;
    private long lostSince = -1;
    private final UUID description = UUID.randomUUID();

    private Framebuffer framebuffer;
    private float yaw;
    private long lastFrameTime = Util.getMeasuringTimeMs();

    public TardisConsoleUnitMonitorExteriorViewScreen(BaseTardisConsoleUnitBlockEntity tardisConsoleUnitBlockEntity, RegistryKey<World> dimension, BlockPos exteriorPos, Direction facing, int flyoverEntityId) {
        super(DWM.TEXTS.MONITOR_EXTERIOR_VIEW_TITLE, tardisConsoleUnitBlockEntity);

        this.dimension = dimension;
        this.flyoverEntityId = flyoverEntityId;

        // The middle of the two-block exterior, which the camera always looks at. It starts out in front of the doors,
        // looking back at the TARDIS, so the yaw is the way the doors face reversed.
        this.target = Vec3d.ofCenter(exteriorPos).add(0, 0.5, 0);
        this.yaw = facing.getOpposite().asRotation();
    }

    @Override
    public boolean shouldCloseOnInventoryKey() {
        return true;
    }

    // A live view of the world shouldn't freeze it while open in singleplayer.
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        ExteriorViewState.open = true;
        if (this.framebuffer == null) this.framebuffer = new SimpleFramebuffer(2, 2, true, true);
    }

    @Override
    public void removed() {
        super.removed();
        ExteriorViewState.open = false;
        if (handingOver) handingOver = false;
        else new TardisExteriorViewPacket(false).sendToServer();

        // The last frame requested is rendered by Immersive Portals at the end of the game frame, after this screen
        // closes, and cannot be cancelled - so the framebuffer can only be freed once that has happened.
        if (this.framebuffer != null) {
            RETIRED.add(new Retired(this.framebuffer, Util.getMeasuringTimeMs()));
            this.framebuffer = null;
            if (!retirementHooked) {
                retirementHooked = true;
                ClientTickEvent.CLIENT_POST.register(client -> freeRetired());
            }
        }
    }

    // Set when a new exterior view is about to replace this one (the flight landed): the server has already started
    // the new view's stream, which this one's "closed" message must not cancel.
    private static boolean handingOver;

    public static void handOver() {
        handingOver = true;
    }

    private static final List<Retired> RETIRED = new ArrayList<>();
    private static boolean retirementHooked;

    private record Retired(Framebuffer framebuffer, long at) {
    }

    private static void freeRetired() {
        long now = Util.getMeasuringTimeMs();
        RETIRED.removeIf(r -> {
            if (now - r.at() < 1000) return false;
            r.framebuffer().delete();
            return true;
        });
    }

    @Override
    public void renderAdditional(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderAdditional(context, mouseX, mouseY, delta);
        if (this.client == null || this.framebuffer == null) return;

        if (!this.followFlyover(this.client.getRenderTickCounter().getTickDelta(false))) return;
        this.pollCameraKeys();

        Vector2i leftTop = this.getLeftTopRenderPos(0, 0);
        Vector2i rightBottom = this.getRightBottomRenderPos(0, 0);
        this.requestFrame();
        this.drawFrame(context, leftTop, rightBottom);

        context.drawCenteredTextWithShadow(this.textRenderer, DWM.TEXTS.MONITOR_EXTERIOR_VIEW_HINT, (leftTop.x + rightBottom.x) / 2, rightBottom.y - 11, 0xE0E0E0);
    }

    // With the TARDIS in flight the view follows its flyover, wherever that has got to. Returns false if the screen has
    // just closed because the flyover is gone (the flight is over); the entity can take a moment to arrive at first.
    private boolean followFlyover(float delta) {
        if (this.flyoverEntityId < 0) return true;

        ClientWorld world = ClientWorldLoader.getWorld(this.dimension);
        Entity flyover = world == null ? null : world.getEntityById(this.flyoverEntityId);

        if (flyover != null && !flyover.isRemoved()) {
            this.target = flyover.getLerpedPos(delta).add(0, 1.0, 0); // the middle of the model, which stands on its position
            this.lostSince = -1;
            return true;
        }

        long now = Util.getMeasuringTimeMs();

        if (this.lostSince < 0) this.lostSince = now;
        if (now - this.lostSince < FLYOVER_LOST_MS) return true;

        this.close();
        return false;
    }

    // A orbits the camera round to the left of the TARDIS, D to the right: the camera moves that way and turns to
    // keep it centered, which is the yaw going up for left and down for right.
    private void pollCameraKeys() {
        long now = Util.getMeasuringTimeMs();
        float step = KEY_DEGREES_PER_SECOND * Math.min(now - this.lastFrameTime, 100L) / 1000F;
        this.lastFrameTime = now;

        long handle = this.client.getWindow().getHandle();
        if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_A)) this.yaw = MathHelper.wrapDegrees(this.yaw + step);
        if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_D)) this.yaw = MathHelper.wrapDegrees(this.yaw - step);
    }

    // Straight back from the target along the view direction, but pulled in if terrain (a wall, a tree) is in the way,
    // so the camera is never inside a block looking at nothing.
    private Vec3d getCameraPos(ClientWorld world) {
        double yawRad = Math.toRadians(this.yaw);
        double pitchRad = Math.toRadians(CAMERA_PITCH);
        Vec3d viewDirection = new Vec3d(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        Vec3d backward = viewDirection.negate();

        // Starts just outside the TARDIS's own block, so its own shape isn't what stops the ray.
        Vec3d from = this.target.add(backward.multiply(0.9));
        Vec3d to = this.target.add(backward.multiply(CAMERA_DISTANCE));

        BlockHitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        if (hit.getType() == HitResult.Type.MISS) return to;

        double distance = Math.max(MIN_CAMERA_DISTANCE, this.target.distanceTo(hit.getPos()) - 0.3);
        return this.target.add(backward.multiply(distance));
    }

    // Queues the exterior's world to be drawn into the framebuffer after this frame's GUI; what's drawn on screen is
    // the previous frame's, one frame behind.
    private void requestFrame() {
        ClientWorld world = ClientWorldLoader.getWorld(this.dimension);

        // The view matrix of a camera at this yaw and pitch, the way the game itself builds it.
        Matrix4f cameraTransformation = new Matrix4f()
            .rotateX((float) Math.toRadians(CAMERA_PITCH))
            .rotateY((float) Math.toRadians(this.yaw + 180F));

        WorldRenderInfo info = new WorldRenderInfo.Builder()
            .setWorld(world)
            .setCameraPos(this.getCameraPos(world))
            .setCameraTransformation(cameraTransformation)
            .setOverwriteCameraTransformation(true)
            .setDescription(this.description)
            .setRenderDistance(Math.min(this.client.options.getClampedViewDistance(), MAX_RENDER_DISTANCE))
            .setDoRenderHand(false)
            .setEnableViewBobbing(false)
            // The sky dome washes the whole picture out to a flat colour once the view has been open a moment (seen with
            // Iris installed, shaders off); the framebuffer is cleared to the sky colour anyway, so it looks the same without.
            .setDoRenderSky(false)
            .setHasFog(true)
            .build();

        GuiPortalRendering.submitNextFrameRendering(info, this.framebuffer);
    }

    // The framebuffer has the window's proportions, so it fills the whole area by cropping the picture's edges to the
    // area's proportions (centred, so the TARDIS stays in the middle) instead of leaving bars around it.
    private void drawFrame(DrawContext context, Vector2i leftTop, Vector2i rightBottom) {
        Window window = this.client.getWindow();
        float aspect = (float) window.getFramebufferWidth() / Math.max(1, window.getFramebufferHeight());

        float x1 = leftTop.x;
        float y1 = leftTop.y;
        float x2 = rightBottom.x;
        float y2 = rightBottom.y;

        float areaAspect = (x2 - x1) / Math.max(1F, y2 - y1);
        float uMin = 0F, uMax = 1F, vMin = 0F, vMax = 1F;
        if (areaAspect > aspect) {
            vMin = (1F - aspect / areaAspect) / 2F;
            vMax = 1F - vMin;
        }
        else {
            uMin = (1F - areaAspect / aspect) / 2F;
            uMax = 1F - uMin;
        }

        context.draw();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, this.framebuffer.getColorAttachment());

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        builder.vertex(matrix, x1, y2, 0F).texture(uMin, vMin);
        builder.vertex(matrix, x2, y2, 0F).texture(uMax, vMin);
        builder.vertex(matrix, x2, y1, 0F).texture(uMax, vMax);
        builder.vertex(matrix, x1, y1, 0F).texture(uMin, vMax);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }
}
