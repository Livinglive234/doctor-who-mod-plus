package net.drgmes.dwm.blocks.tardis.exteriors;

import net.drgmes.dwm.compat.immersiveportals.ImmersivePortalsShellCompat;
import net.drgmes.dwm.compat.iris.Iris;
import net.drgmes.dwm.enums.TardisExteriorState;
import net.drgmes.dwm.setup.ModCompats;
import net.drgmes.dwm.utils.helpers.CommonHelper;
import net.drgmes.dwm.utils.helpers.WorldHelper;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;

import java.util.function.Function;

public abstract class BaseTardisExteriorBlockRenderer<C extends BaseTardisExteriorBlockEntity> implements BlockEntityRenderer<C> {
    // The flicker swings the alpha by 0.4 either side of the materialized value (see getAlpha), so below 0.4 + the
    // visibility cutoff it dips out of sight and back every cycle. Shader packs draw each peak solid, so under one a
    // demat/remat isn't drawn below this value at all.
    private static final float IRIS_MIN_FADE_VALUE = 0.42F;

    protected final BlockEntityRendererFactory.Context ctx;
    protected final EntityModelLayer modelLayer;
    protected final Function<ModelPart, BaseTardisExteriorModel> modelFactory;

    protected final float modelScale;
    protected final float modelYScale;
    protected final float modelYOffset;

    public BaseTardisExteriorBlockRenderer(BlockEntityRendererFactory.Context context, EntityModelLayer modelLayer, Function<ModelPart, BaseTardisExteriorModel> modelFactory, float modelScale, float modelYScale, float modelYOffset) {
        this.ctx = context;
        this.modelLayer = modelLayer;
        this.modelFactory = modelFactory;
        this.modelScale = modelScale;
        this.modelYScale = modelYScale;
        this.modelYOffset = modelYOffset;
    }

    // @Override
    public Box getRenderBoundingBox(C blockEntity) {
        return WorldHelper.getRenderBoundingBox(blockEntity);
    }

    @Override
    public int getRenderDistance() {
        return 256;
    }

    @Override
    public void render(C tile, float delta, MatrixStack matrixStack, VertexConsumerProvider buffer, int light, int overlay) {
        DoubleBlockHalf half = tile.getCachedState().get(BaseTardisExteriorBlock.HALF);
        if (half != DoubleBlockHalf.LOWER) return;
        if (ImmersivePortalsShellCompat.shouldHideExteriorShell(tile.tardisId)) return;
        if (tile.isCloaked()) return;

        boolean hasImmersivePortals = ModCompats.immersivePortals();
        boolean hasEnabledIrisShaders = ModCompats.iris() && Iris.isShaderPackInUse();

        boolean isLit = tile.getCachedState().get(BaseTardisExteriorBlock.LIT);
        boolean isOpen = tile.getCachedState().get(BaseTardisExteriorBlock.OPEN);
        float rotateDegrees = tile.getCachedState().get(BaseTardisExteriorBlock.FACING).asRotation();
        TardisExteriorState exteriorState = tile.getExteriorState();
        float alpha = this.getAlpha(tile, exteriorState);

        BaseTardisExteriorModel model = this.modelFactory.apply(this.ctx.getLayerModelPart(this.modelLayer));
        RenderLayer renderLayer = model.getLayer(this.modelLayer.getId());
        model.setupAnim(tile);

        matrixStack.push();
        matrixStack.translate(0.5, 0.5, 0.5);
        matrixStack.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(180));
        matrixStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(180));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotateDegrees));
        matrixStack.translate(0, this.modelScale * this.modelYOffset, 0);
        matrixStack.scale(this.modelScale, this.modelScale + this.modelYScale, this.modelScale);

        VertexConsumer vertexConsumer = buffer.getBuffer(renderLayer);
        int color = CommonHelper.getColorWithAlpha(0x00FFFFFF, alpha);

        // Some Iris shader packs draw screen-space effects off any submitted geometry, whatever its blended alpha, so an
        // alpha-0 shell would still show as a solid shape. Don't submit it at all (a no-op for vanilla rendering).
        // For the same reason every flicker peak of the faint end of a demat/remat shows as a solid pop, so under shaders
        // that end is cut off.
        boolean isFading = exteriorState == TardisExteriorState.PROCESS_DEMAT || exteriorState == TardisExteriorState.PROCESS_REMAT;
        boolean isVisible = hasEnabledIrisShaders
            ? alpha > 0.02F && (!isFading || tile.getMaterializedStateValue() >= IRIS_MIN_FADE_VALUE)
            : alpha > 0F;

        if (isVisible) {
            model.render(matrixStack, vertexConsumer, light, overlay, color);
            if (!hasImmersivePortals || !isOpen) model.renderDoors(matrixStack, vertexConsumer, light, overlay, color);

            // The lamp rides the same flickering alpha as the shell during a demat/remat, instead of snapping with the LIT state.
            boolean lampGlows = isLit || exteriorState == TardisExteriorState.PROCESS_DEMAT || exteriorState == TardisExteriorState.PROCESS_REMAT;
            model.renderLamp(matrixStack, lampGlows && !hasEnabledIrisShaders ? buffer.getBuffer(RenderLayer.getEntityAlpha(this.modelLayer.getId())) : vertexConsumer, light, overlay, color);
            if (isOpen && !hasImmersivePortals) model.renderBoti(matrixStack, !hasEnabledIrisShaders ? buffer.getBuffer(RenderLayer.getEndPortal()) : vertexConsumer, light, overlay, color);
        }

        matrixStack.pop();
    }

    private float getAlpha(C tile, TardisExteriorState exteriorState) {
        float value = tile.getMaterializedStateValue();

        float alpha = switch (exteriorState) {
            case PROCESS_PULSE -> (float) Math.cos(value * 5) * 0.4F + 0.75F;
            default -> value < 0.1F ? 0 : (float) Math.cos(value * 30) * 0.4F + value;
        };

        return CommonHelper.clamp(alpha, 0, 1);
    }
}
