package leroidesafk.mccinfo.client.mixin.voice;

import leroidesafk.mccinfo.client.voice.VoiceAdmin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.RenderEvents", remap = false)
public class VoiceHudIconMixin {
	// Whether Simple Voice Chat drew its icon this frame
	@Unique
	private boolean mccinfo$iconRendered;

	// Simple Voice Chat icon drawing
	@Shadow(remap = false)
	private void renderIcon(GuiGraphicsExtractor graphics, Identifier icon) {
		throw new AssertionError();
	}

	// Reset each frame
	@Inject(method = "onRenderHUD", at = @At("HEAD"), require = 0, remap = false)
	private void mccinfo$resetIcon(GuiGraphicsExtractor graphics, float delta, CallbackInfo info) {
		this.mccinfo$iconRendered = false;
	}

	// Announcement icon while announcing or hearing one
	@ModifyArg(method = "onRenderHUD", at = @At(value = "INVOKE",
			target = "Lde/maxhenkel/voicechat/voice/client/RenderEvents;renderIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;)V"),
			index = 1, require = 0, remap = false)
	private Identifier mccinfo$announceIcon(Identifier icon) {
		this.mccinfo$iconRendered = true;
		return VoiceAdmin.hudIcon(icon);
	}

	// Announcement icon for listeners when no icon was drawn
	@Inject(method = "onRenderHUD", at = @At(value = "INVOKE",
			target = "Lde/maxhenkel/voicechat/voice/client/ClientPlayerStateManager;getGroupID()Ljava/util/UUID;"),
			require = 0, remap = false)
	private void mccinfo$listenerIcon(GuiGraphicsExtractor graphics, float delta, CallbackInfo info) {
		if (!this.mccinfo$iconRendered && VoiceAdmin.hearingAnnouncement()) {
			renderIcon(graphics, VoiceAdmin.ANNOUNCE_ICON);
		}
	}
}
