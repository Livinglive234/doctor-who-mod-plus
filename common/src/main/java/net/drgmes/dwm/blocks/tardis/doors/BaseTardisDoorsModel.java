package net.drgmes.dwm.blocks.tardis.doors;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

public abstract class BaseTardisDoorsModel extends Model {
    private final ModelPart base;
    private final ModelPart door_left;
    private final ModelPart door_right;

    private final float doorAngle;
    private final float doorLeftClosedYaw;
    private final float doorRightClosedYaw;

    public BaseTardisDoorsModel(ModelPart root, float doorAngle) {
        super(RenderLayer::getEntityTranslucentCull);

        this.doorAngle = doorAngle;

        this.base = root.getChild("base");
        this.door_left = root.getChild("door_left");
        this.door_right = root.getChild("door_right");

        this.doorLeftClosedYaw = this.door_left.yaw;
        this.doorRightClosedYaw = this.door_right.yaw;
    }

    public BaseTardisDoorsModel(ModelPart root) {
        this(root, 1.75F);
    }

    @Override
    public void render(MatrixStack matrixStack, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        base.render(matrixStack, vertexConsumer, light, overlay, color);
    }

    public void renderDoors(MatrixStack matrixStack, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        door_left.render(matrixStack, vertexConsumer, light, overlay, color);
        door_right.render(matrixStack, vertexConsumer, light, overlay, color);
    }

    // Both ways, since the renderer now keeps one model across frames and tiles instead of baking a fresh one each time.
    public void setupAnim(BaseTardisDoorsBlockEntity tile) {
        boolean isOpen = tile.getCachedState().get(BaseTardisDoorsBlock.OPEN);
        this.door_left.yaw = isOpen ? -this.doorAngle : this.doorLeftClosedYaw;
        this.door_right.yaw = isOpen ? this.doorAngle : this.doorRightClosedYaw;
    }
}
