package leroidesafk.mccinfo.client.emote;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.function.Supplier;

public final class EmoteSound extends AbstractTickableSoundInstance {
	// Sounds read from the emote cache
	public static final String NAMESPACE = "mccinfo_emote";
	private static final int ATTENUATION = 32;

	private final Supplier<Vec3> position;
	private final Supplier<Boolean> alive;

	// Sound following the player
	EmoteSound(String emote, String sound, Supplier<Vec3> position, Supplier<Boolean> alive, boolean looping) {
		super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(NAMESPACE, emote + "/" + sound)), SoundSource.PLAYERS,
				SoundInstance.createUnseededRandom());
		this.position = position;
		this.alive = alive;
		this.looping = looping;
		Vec3 start = position.get();
		if (start != null) {
			this.x = start.x;
			this.y = start.y;
			this.z = start.z;
		}
	}

	// Streamed file, no sound event needed
	@Override
	public WeighedSoundEvents resolve(SoundManager soundManager) {
		this.sound = new Sound(this.identifier, random -> 1.0F, random -> 1.0F, 1, Sound.Type.FILE, true, false, ATTENUATION);
		return new WeighedSoundEvents(this.identifier, null);
	}

	// Stops with the emote or the setting
	@Override
	public void tick() {
		Vec3 now = this.position.get();
		if (!this.alive.get() || now == null) {
			stop();
			return;
		}
		this.x = now.x;
		this.y = now.y;
		this.z = now.z;
	}

	// Cache file of a sound location
	public static Path file(Identifier location) {
		if (!NAMESPACE.equals(location.getNamespace())) {
			return null;
		}
		String path = location.getPath();
		if (path.startsWith("sounds/")) {
			path = path.substring(7);
		}
		if (path.endsWith(".ogg")) {
			path = path.substring(0, path.length() - 4);
		}
		int slash = path.indexOf('/');
		return slash <= 0 ? null : EmoteLibrary.soundFile(path.substring(0, slash), path.substring(slash + 1));
	}
}
