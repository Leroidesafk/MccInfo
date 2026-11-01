package leroidesafk.mccinfo.client.mixin.emote;

import leroidesafk.mccinfo.client.emote.EmoteSound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
	// Emote sounds streamed from the cache
	@Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
	private void mccinfo$emoteStream(Identifier location, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> info) {
		if (!EmoteSound.NAMESPACE.equals(location.getNamespace())) {
			return;
		}
		Path file = EmoteSound.file(location);
		info.setReturnValue(CompletableFuture.supplyAsync(() -> {
			if (file == null) {
				throw new CompletionException(new IOException("Unknown emote sound " + location));
			}
			try {
				return looping ? new LoopingAudioStream(JOrbisAudioStream::new, Files.newInputStream(file)) : new JOrbisAudioStream(Files.newInputStream(file));
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}, Util.nonCriticalIoPool()));
	}
}
