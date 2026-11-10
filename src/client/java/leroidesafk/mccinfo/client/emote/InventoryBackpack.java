package leroidesafk.mccinfo.client.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import leroidesafk.mccinfo.client.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Backpack on the player in the inventory preview
public final class InventoryBackpack {
	private static final int FULL_BRIGHT = 15728880;
	private static final Map<EntityRenderState, List<Attached>> ATTACHED = Collections.synchronizedMap(new WeakHashMap<>());

	// Armor stand, its place and the emote motion
	private record Attached(EntityRenderState state, Vector3f offset, Matrix4f motion) {
	}

	private InventoryBackpack() {
	}

	// Armor stands riding the player, turned with its body
	public static void attach(EntityRenderState playerState, LivingEntity entity) {
		ATTACHED.remove(playerState);
		if (!ModConfig.get().showInventoryBackpack || !(entity instanceof Player) || !(playerState instanceof LivingEntityRenderState living)) {
			return;
		}
		float delta = living.bodyRot - entity.yBodyRot;
		Matrix4f body = Emotes.bodyMotion((Player) entity);
		Matrix4f motion = body == null ? null : Emotes.passengerMotion(body, 0.0F, 0.0F, 0.0F, living.bodyRot);
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		List<Attached> attached = new ArrayList<>();
		for (Entity passenger : entity.getIndirectPassengers()) {
			if (!(passenger instanceof ArmorStand)) {
				continue;
			}
			EntityRenderState state = dispatcher.extractEntity(passenger, 1.0F);
			Emotes.forgetPassenger(state);
			// Full light, no shadow, name or outline
			state.shadowPieces.clear();
			state.outlineColor = 0;
			state.nameTag = null;
			state.leashStates = null;
			state.displayFireAnimation = false;
			state.lightCoords = FULL_BRIGHT;
			if (state instanceof LivingEntityRenderState stand) {
				stand.bodyRot += delta;
			}
			Vector3f offset = new Vector3f((float) (passenger.getX() - entity.getX()), (float) (passenger.getY() - entity.getY()),
					(float) (passenger.getZ() - entity.getZ())).rotateY((float) Math.toRadians(-delta));
			attached.add(new Attached(state, offset, motion));
		}
		if (!attached.isEmpty()) {
			ATTACHED.put(playerState, List.copyOf(attached));
		}
	}

	// Drawn with the player, following its emote
	public static void submit(EntityRenderState playerState, CameraRenderState camera, PoseStack poseStack, SubmitNodeCollector collector) {
		List<Attached> attached = ATTACHED.remove(playerState);
		if (attached == null) {
			return;
		}
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		for (Attached passenger : attached) {
			poseStack.pushPose();
			if (passenger.motion() != null) {
				poseStack.mulPose(passenger.motion());
			}
			dispatcher.submit(passenger.state(), camera, passenger.offset().x, passenger.offset().y, passenger.offset().z, poseStack, collector);
			poseStack.popPose();
		}
	}
}
