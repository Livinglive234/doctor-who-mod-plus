package net.drgmes.dwm.entities.tardis.exteriors;

import net.drgmes.dwm.blocks.tardis.exteriors.BaseTardisExteriorModel;
import net.drgmes.dwm.blocks.tardis.exteriors.tardisexteriorcapsule.models.TardisExteriorCapsuleModel;
import net.drgmes.dwm.blocks.tardis.exteriors.tardisexteriorphonebox.models.TardisExteriorPhoneBoxModel;
import net.drgmes.dwm.blocks.tardis.exteriors.tardisexteriorpolicebox.models.TardisExteriorPoliceBoxModel;
import net.drgmes.dwm.common.tardis.exteriors.TardisExteriors;
import net.drgmes.dwm.compat.iris.Iris;
import net.drgmes.dwm.setup.ModCompats;
import net.drgmes.dwm.utils.helpers.CommonHelper;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders the exterior shell models of the landed TARDIS, posed exactly as BaseTardisExteriorBlockRenderer poses
 * them, so the handover between the exterior and the flyover is seamless. The entity's origin is the bottom-centre
 * of its block, not the block's corner. Doors are closed, and it is lit up so it shows against a night sky.
 */
public class TardisFlyoverEntityRenderer extends EntityRenderer<TardisFlyoverEntity> {
    // What a landed TARDIS stands in: its lit top block gives off the maximum block light.
    private static final int MIN_BLOCK_LIGHT = 15;

    // The scale numbers must match the ones the block renderers pass to BaseTardisExteriorBlockRenderer.
    private record Visual(EntityModelLayer layer, BaseTardisExteriorModel model, float scale, float yScale, float yOffset) {
    }

    private final Visual policeBox;
    private final Visual phoneBox;
    private final Visual capsule;

    public TardisFlyoverEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0F;

        this.policeBox = new Visual(TardisExteriorPoliceBoxModel.LAYER_LOCATION, new TardisExteriorPoliceBoxModel(context.getPart(TardisExteriorPoliceBoxModel.LAYER_LOCATION)), 0.465F, 0.025F, -0.5F);
        this.phoneBox = new Visual(TardisExteriorPhoneBoxModel.LAYER_LOCATION, new TardisExteriorPhoneBoxModel(context.getPart(TardisExteriorPhoneBoxModel.LAYER_LOCATION)), 0.465F, 0.025F, -0.5F);
        this.capsule = new Visual(TardisExteriorCapsuleModel.LAYER_LOCATION, new TardisExteriorCapsuleModel(context.getPart(TardisExteriorCapsuleModel.LAYER_LOCATION)), 0.465F, 0.01F, -0.45F);
    }

    // Never darker than a landed TARDIS, however dark it is outside. Shader packs light entities by block light
    // too, so this holds with them as well, unlike the lamp's glow layer, which is skipped under shaders.
    @Override
    protected int getBlockLight(TardisFlyoverEntity entity, BlockPos pos) {
        return Math.max(super.getBlockLight(entity, pos), MIN_BLOCK_LIGHT);
    }

    @Override
    public Identifier getTexture(TardisFlyoverEntity entity) {
        return null;
    }

    @Override
    public void render(TardisFlyoverEntity entity, float yaw, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        Visual visual = this.getVisual(entity.getExteriorTypeName());
        BaseTardisExteriorModel model = visual.model();
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(model.getLayer(visual.layer().getId()));

        matrixStack.push();
        // The block renderer starts from the block's centre; this origin is already bottom-centre, so only the half-block height is left.
        matrixStack.translate(0, 0.5, 0);
        matrixStack.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(180));
        matrixStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(180));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getSpinAngle(tickDelta)));
        matrixStack.translate(0, visual.scale() * visual.yOffset(), 0);
        matrixStack.scale(visual.scale(), visual.scale() + visual.yScale(), visual.scale());

        // Shader packs ignore alpha, so under one a fade is the shell simply being there or not, switching halfway.
        boolean hasEnabledIrisShaders = ModCompats.iris() && Iris.isShaderPackInUse();
        float alpha = entity.getAlpha();
        if (hasEnabledIrisShaders ? alpha >= 0.5F : alpha > 0F) {
            int color = CommonHelper.getColorWithAlpha(0x00FFFFFF, alpha);
            model.render(matrixStack, vertexConsumer, light, OverlayTexture.DEFAULT_UV, color);
            model.renderDoors(matrixStack, vertexConsumer, light, OverlayTexture.DEFAULT_UV, color);

            // The lamp always glows at full brightness. Under a shader pack the glow layer is skipped, as on the landed exterior.
            VertexConsumer lampConsumer = hasEnabledIrisShaders ? vertexConsumer : vertexConsumers.getBuffer(RenderLayer.getEntityAlpha(visual.layer().getId()));
            model.renderLamp(matrixStack, lampConsumer, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, color);
        }

        matrixStack.pop();

        super.render(entity, yaw, tickDelta, matrixStack, vertexConsumers, light);
    }

    private Visual getVisual(String exteriorTypeName) {
        if (TardisExteriors.POLICE_BOX.name.equals(exteriorTypeName)) return this.policeBox;
        if (TardisExteriors.PHONE_BOX.name.equals(exteriorTypeName)) return this.phoneBox;
        return this.capsule;
    }
}
