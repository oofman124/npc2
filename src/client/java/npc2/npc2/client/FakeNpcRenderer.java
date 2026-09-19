package npc2.npc2.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.resources.Identifier;
import npc2.npc2.FakeNpcEntity;

/**
 * Humanoid renderer for PathfinderMob NPCs using the player model.
 */
public class FakeNpcRenderer extends HumanoidMobRenderer<FakeNpcEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE =
		Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

	public FakeNpcRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(
			this,
			ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
			context.getEquipmentRenderer()
		));
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(FakeNpcEntity mob, HumanoidArm arm) {
		InteractionHand hand = mob.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		if (mob.isUsingItem() && mob.getUsedItemHand() == hand && mob.getItemBlockingWith() != null) {
			return HumanoidModel.ArmPose.BLOCK;
		}

		return super.getArmPose(mob, arm);
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
