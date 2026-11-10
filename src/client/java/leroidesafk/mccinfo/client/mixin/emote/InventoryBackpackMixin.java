package leroidesafk.mccinfo.client.mixin.emote;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import leroidesafk.mccinfo.client.emote.InventoryBackpack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Backpack on the player in the inventory
@Mixin(InventoryScreen.class)
public class InventoryBackpackMixin {
	@WrapOperation(method = "extractEntityInInventoryFollowsMouse", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;entity(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3fc;Lorg/joml/Quaternionfc;Lorg/joml/Quaternionfc;IIII)V"))
	private static void mccinfo$attachBackpack(GuiGraphicsExtractor graphics, EntityRenderState state, float scale, Vector3fc translation, Quaternionfc rotation,
			Quaternionfc overrideCameraAngle, int x0, int y0, int x1, int y1, Operation<Void> original, @Local(argsOnly = true) LivingEntity entity) {
		InventoryBackpack.attach(state, entity);
		original.call(graphics, state, scale, translation, rotation, overrideCameraAngle, x0, y0, x1, y1);
	}
}
