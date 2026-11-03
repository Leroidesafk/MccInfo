package leroidesafk.mccinfo.client.emote;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class EmoteSound extends AbstractTickableSoundInstance {
	public static final String NAMESPACE = "mccinfo_emote";
	private static final int ATTENUATION = 16;
	private static final int SKIP_CHUNK = 1 << 16;
	private static final String STEREO = "stereo";

	private final Identifier file;
	private final DoubleSupplier loudness;
	private final BooleanSupplier alive;
	private final Supplier<Vec3> position;

	// Emote music or sound
	EmoteSound(String emote, String sound, double offset, DoubleSupplier loudness, BooleanSupplier alive, Supplier<Vec3> position, boolean looping) {
		super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(NAMESPACE, emote + "/" + sound)), SoundSource.PLAYERS,
				SoundInstance.createUnseededRandom());
		this.file = Identifier.fromNamespaceAndPath(NAMESPACE, emote + "/" + sound + "/" + Math.max(0L, Math.round(offset * 1000.0))
				+ (position == null ? "/" + STEREO : ""));
		this.loudness = loudness;
		this.alive = alive;
		this.position = position;
		this.looping = looping;
		this.relative = position == null;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.volume = (float) loudness.getAsDouble();
		place();
	}

	// Heard without direction
	boolean centered() {
		return this.position == null;
	}

	// Sound on the player
	private void place() {
		if (this.position == null) {
			return;
		}
		Vec3 at = this.position.get();
		this.x = at.x;
		this.y = at.y;
		this.z = at.z;
	}

	@Override
	public WeighedSoundEvents resolve(SoundManager soundManager) {
		this.sound = new Sound(this.file, random -> 1.0F, random -> 1.0F, 1, Sound.Type.FILE, true, false, ATTENUATION);
		return new WeighedSoundEvents(this.identifier, null);
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	// Volume and position
	@Override
	public void tick() {
		if (!this.alive.getAsBoolean()) {
			stop();
			return;
		}
		this.volume = (float) this.loudness.getAsDouble();
		place();
	}

	// Stream from the emote files
	public static AudioStream stream(Identifier location, boolean looping) throws IOException {
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
		String[] parts = path.split("/");
		if (parts.length < 2 || parts.length > 4 || parts.length == 4 && !STEREO.equals(parts[3])) {
			return null;
		}
		byte[] data = EmoteLibrary.soundData(parts[0], parts[1]);
		if (data == null) {
			return null;
		}
		double offset = 0.0;
		if (parts.length >= 3) {
			try {
				offset = Long.parseLong(parts[2]) / 1000.0;
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return new Stream(data, looping, offset, parts.length != 4);
	}

	// Start offset, loop and mono
	private static final class Stream implements AudioStream {
		private final byte[] data;
		private final boolean looping;
		private final boolean mono;
		private AudioStream current;
		private ByteBuffer pending;

		private Stream(byte[] data, boolean looping, double offset, boolean mono) throws IOException {
			this.mono = mono;
			this.data = data;
			this.looping = looping;
			this.current = open();
			AudioFormat format = this.current.getFormat();
			long skip = (long) (offset * format.getSampleRate()) * format.getFrameSize();
			long pass = 0;
			while (skip > 0) {
				ByteBuffer chunk = this.current.read(SKIP_CHUNK);
				if (!chunk.hasRemaining()) {
					if (!looping || pass == 0) {
						break;
					}
					skip %= pass;
					pass = 0;
					this.current.close();
					this.current = open();
					continue;
				}
				if (chunk.remaining() > skip) {
					chunk.position(chunk.position() + (int) skip);
					this.pending = chunk;
					break;
				}
				skip -= chunk.remaining();
				pass += chunk.remaining();
			}
		}

		private AudioStream open() throws IOException {
			return new JOrbisAudioStream(new ByteArrayInputStream(this.data));
		}

		@Override
		public AudioFormat getFormat() {
			AudioFormat format = this.current.getFormat();
			if (!this.mono || !stereo(format)) {
				return format;
			}
			return new AudioFormat(format.getEncoding(), format.getSampleRate(), 16, 1, 2, format.getFrameRate(), format.isBigEndian());
		}

		private static boolean stereo(AudioFormat format) {
			return format.getChannels() == 2 && format.getSampleSizeInBits() == 16;
		}

		// Stereo to mono
		private ByteBuffer mono(ByteBuffer input) {
			AudioFormat format = this.current.getFormat();
			if (!this.mono || !stereo(format) || !input.hasRemaining()) {
				return input;
			}
			ByteBuffer source = input.slice().order(format.isBigEndian() ? java.nio.ByteOrder.BIG_ENDIAN : java.nio.ByteOrder.LITTLE_ENDIAN);
			int frames = source.remaining() / 4;
			ByteBuffer output = org.lwjgl.BufferUtils.createByteBuffer(frames * 2).order(source.order());
			for (int i = 0; i < frames; i++) {
				int left = source.getShort(i * 4);
				int right = source.getShort(i * 4 + 2);
				output.putShort((short) ((left + right) / 2));
			}
			output.flip();
			return output;
		}

		@Override
		public ByteBuffer read(int expectedSize) throws IOException {
			if (this.pending != null) {
				ByteBuffer rest = this.pending;
				this.pending = null;
				if (rest.hasRemaining()) {
					return mono(rest);
				}
			}
			ByteBuffer result = this.current.read(expectedSize);
			if (!result.hasRemaining() && this.looping) {
				this.current.close();
				this.current = open();
				result = this.current.read(expectedSize);
			}
			return mono(result);
		}

		@Override
		public void close() throws IOException {
			this.current.close();
		}
	}
}
