package leroidesafk.mccinfo.client.voice;

import com.mojang.blaze3d.platform.InputConstants;
import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class VoiceSettings {
	// Limits of the sliders
	public static final double MIN_GAIN = -40.0;
	public static final double MAX_GAIN = 24.0;
	public static final double GAIN_WARNING = 10.0;
	public static final double MIN_THRESHOLD = -127.0;
	public static final double MAX_THRESHOLD = 0.0;
	// Audio types
	public static final int AUDIO_NORMAL = 0;
	public static final int AUDIO_REDUCED = 1;
	public static final int AUDIO_OFF = 2;

	// Simple Voice Chat config reached by reflection
	private static boolean loaded;
	private static boolean failed;
	private static Object config;
	private static Method entryGet;
	private static Method entrySet;
	private static Method entrySave;
	private static Method doubleMax;
	private static Object volume;
	private static Object gain;
	private static Object agc;
	private static Object denoiser;
	private static Object vad;
	private static Object threshold;
	private static Object activation;
	private static Object audioType;
	private static Object microphone;
	private static Object speaker;
	private static Object voiceActivation;
	private static List<Object> audioTypes;
	private static Method canUseAgc;
	private static Method canUseDenoiser;
	private static Method getClient;
	private static Method getSoundManager;
	private static Method maxGain;
	private static Method reloadAudio;
	private static Method deviceNames;
	private static Method speakers;
	private static Method cleanName;
	// Microphone test classes
	private static Class<?> micTestClass;
	private static Class<?> micListenerClass;
	private static Method micActive;
	private static Method micRendered;
	private static Method micStop;
	private static Method micPress;

	private static Object micTest;
	private static volatile double micLevel = MIN_THRESHOLD;

	private VoiceSettings() {
	}

	private static boolean load() {
		if (loaded || failed) {
			return loaded;
		}
		try {
			Class<?> entry = Class.forName("de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry");
			entryGet = entry.getMethod("get");
			entrySet = entry.getMethod("set", Object.class);
			entrySave = entry.getMethod("save");
			doubleMax = Class.forName("de.maxhenkel.voicechat.configbuilder.entry.DoubleConfigEntry").getMethod("getMax");
			config = Class.forName("de.maxhenkel.voicechat.VoicechatClient").getField("CLIENT_CONFIG").get(null);
			if (config == null) {
				return false;
			}
			volume = field("voiceChatVolume");
			gain = field("microphoneGain");
			agc = field("agc");
			denoiser = field("denoiser");
			vad = field("vad");
			threshold = field("voiceActivationThreshold");
			activation = field("microphoneActivationType");
			audioType = field("audioType");
			microphone = field("microphone");
			speaker = field("speaker");
			voiceActivation = Class.forName("de.maxhenkel.voicechat.voice.client.MicrophoneActivationType").getField("VOICE").get(null);
			Class<?> audio = Class.forName("de.maxhenkel.voicechat.voice.client.speaker.AudioType");
			audioTypes = List.of(audio.getField("NORMAL").get(null), audio.getField("REDUCED").get(null), audio.getField("OFF").get(null));
			canUseAgc = Class.forName("de.maxhenkel.voicechat.natives.SpeexManager").getMethod("canUseAgc");
			canUseDenoiser = Class.forName("de.maxhenkel.voicechat.natives.RNNoiseManager").getMethod("canUseDenoiser");
			getClient = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager").getMethod("getClient");
			Class<?> client = Class.forName("de.maxhenkel.voicechat.voice.client.ClientVoicechat");
			getSoundManager = client.getMethod("getSoundManager");
			reloadAudio = client.getMethod("reloadAudio");
			Class<?> soundManager = Class.forName("de.maxhenkel.voicechat.voice.client.SoundManager");
			maxGain = soundManager.getMethod("getMaxGain");
			speakers = soundManager.getMethod("getAllSpeakers");
			cleanName = soundManager.getMethod("cleanDeviceName", String.class);
			deviceNames = Class.forName("de.maxhenkel.voicechat.voice.client.microphone.MicrophoneManager").getMethod("deviceNames");
			micTestClass = Class.forName("de.maxhenkel.voicechat.gui.widgets.MicTestButton");
			micListenerClass = Class.forName("de.maxhenkel.voicechat.gui.widgets.MicTestButton$MicListener");
			micActive = micTestClass.getMethod("isMicActive");
			micRendered = micTestClass.getMethod("updateLastRender");
			micStop = micTestClass.getMethod("stop");
			for (Method method : micTestClass.getMethods()) {
				if (method.getName().equals("onPress") && method.getParameterCount() == 1) {
					micPress = method;
				}
			}
			if (micPress == null) {
				throw new NoSuchMethodException("onPress");
			}
			loaded = true;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			failed = true;
			Mccinfo.LOGGER.warn("Voice chat: could not read the Simple Voice Chat settings ({})", error.toString());
		}
		return loaded;
	}

	private static Object field(String name) throws ReflectiveOperationException {
		return config.getClass().getField(name).get(config);
	}

	// Settings shown only when everything was found
	public static boolean available() {
		return VoiceChat.present() && load();
	}

	private interface Action {
		void run() throws ReflectiveOperationException;
	}

	private interface Value<T> {
		T get() throws ReflectiveOperationException;
	}

	// Safe calls, a warning in the log on error
	private static void run(Action action) {
		if (!available()) {
			return;
		}
		try {
			action.run();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warn(error);
		}
	}

	private static <T> T value(Value<T> value, T fallback) {
		if (!available()) {
			return fallback;
		}
		try {
			T result = value.get();
			return result == null ? fallback : result;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			warn(error);
			return fallback;
		}
	}

	private static boolean warned;

	private static void warn(Throwable error) {
		if (!warned) {
			warned = true;
			Mccinfo.LOGGER.warn("Voice chat: could not use a Simple Voice Chat setting ({})", error.toString());
		}
	}

	private static Object get(Object entry) throws ReflectiveOperationException {
		return entryGet.invoke(entry);
	}

	private static void set(Object entry, Object value, boolean save) throws ReflectiveOperationException {
		Object result = entrySet.invoke(entry, value);
		if (save) {
			entrySave.invoke(result);
		}
	}

	private static void save(Object entry) throws ReflectiveOperationException {
		entrySave.invoke(entry);
	}

	private static Object client() throws ReflectiveOperationException {
		return getClient.invoke(null);
	}

	private static void reload() throws ReflectiveOperationException {
		Object client = client();
		if (client != null) {
			reloadAudio.invoke(client);
		}
	}

	// Speaker volume
	public static double volume() {
		return value(() -> (Double) get(volume), 1.0);
	}

	public static double maxVolume() {
		return value(() -> {
			double max = (Double) doubleMax.invoke(volume);
			Object client = client();
			Object manager = client == null ? null : getSoundManager.invoke(client);
			return manager == null ? max : Math.min(max, (Float) maxGain.invoke(manager));
		}, 2.0);
	}

	public static void setVolume(double value, boolean save) {
		run(() -> set(volume, value, save));
	}

	public static void saveVolume() {
		run(() -> save(volume));
	}

	// Microphone gain
	public static double gain() {
		return value(() -> (Double) get(gain), 0.0);
	}

	public static void setGain(double value, boolean save) {
		run(() -> set(gain, value, save));
	}

	public static void saveGain() {
		run(() -> save(gain));
	}

	// Automatic gain
	public static boolean canAgc() {
		return value(() -> (Boolean) canUseAgc.invoke(null), false);
	}

	public static boolean agc() {
		return value(() -> (Boolean) get(agc), false);
	}

	public static void setAgc(boolean value) {
		run(() -> set(agc, value, true));
	}

	// Noise suppression
	public static boolean canDenoise() {
		return value(() -> (Boolean) canUseDenoiser.invoke(null), false);
	}

	public static boolean denoiser() {
		return value(() -> (Boolean) get(denoiser), false);
	}

	public static void setDenoiser(boolean value) {
		run(() -> set(denoiser, value, true));
	}

	// Push to talk or voice activation
	public static boolean voiceActivation() {
		return value(() -> voiceActivation.equals(get(activation)), false);
	}

	public static void setVoiceActivation(boolean voice) {
		run(() -> {
			Object type = voice ? voiceActivation : voiceActivation.getClass().getField("PTT").get(null);
			set(activation, type, true);
		});
	}

	public static boolean vad() {
		return value(() -> (Boolean) get(vad), false);
	}

	public static void setVad(boolean value) {
		run(() -> set(vad, value, true));
	}

	public static boolean showsThreshold() {
		return voiceActivation() && !(canDenoise() && vad());
	}

	public static double threshold() {
		return value(() -> (Double) get(threshold), -50.0);
	}

	public static void setThreshold(double value, boolean save) {
		run(() -> set(threshold, value, save));
	}

	public static void saveThreshold() {
		run(() -> save(threshold));
	}

	// Push to talk key
	public static KeyMapping pttKey() {
		return value(() -> (KeyMapping) Class.forName("de.maxhenkel.voicechat.voice.client.KeyEvents").getField("KEY_PTT").get(null), null);
	}

	public static Component pttKeyName() {
		KeyMapping key = pttKey();
		return key == null ? Component.literal("?") : key.getTranslatedKeyMessage();
	}

	public static void setPttKey(InputConstants.Key key) {
		KeyMapping mapping = pttKey();
		if (mapping == null) {
			return;
		}
		mapping.setKey(key);
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	// Audio type
	public static int audioType() {
		return value(() -> Math.max(0, audioTypes.indexOf(get(audioType))), AUDIO_NORMAL);
	}

	public static void setAudioType(int type) {
		run(() -> {
			set(audioType, audioTypes.get(Math.clamp(type, 0, audioTypes.size() - 1)), true);
			stopMicTest();
			reload();
		});
	}

	// Devices
	public static List<String> microphones() {
		return value(() -> new ArrayList<>(castList(deviceNames.invoke(null))), List.of());
	}

	public static List<String> speakers() {
		return value(() -> new ArrayList<>(castList(speakers.invoke(null))), List.of());
	}

	private static List<String> castList(Object list) {
		List<String> names = new ArrayList<>();
		if (list instanceof List<?> values) {
			for (Object value : values) {
				names.add(String.valueOf(value));
			}
		}
		return names;
	}

	public static String microphone() {
		return value(() -> String.valueOf(get(microphone)), "");
	}

	public static String speaker() {
		return value(() -> String.valueOf(get(speaker)), "");
	}

	public static void setMicrophone(String device) {
		run(() -> {
			set(microphone, device, true);
			stopMicTest();
			reload();
		});
	}

	public static void setSpeaker(String device) {
		run(() -> {
			set(speaker, device, true);
			stopMicTest();
			reload();
		});
	}

	public static String deviceName(String device) {
		return value(() -> (String) cleanName.invoke(null, device), device);
	}

	// Microphone test button
	public static boolean micTestAvailable() {
		return value(() -> {
			Object client = client();
			return client != null && getSoundManager.invoke(client) != null;
		}, false);
	}

	public static boolean micTesting() {
		Object test = micTest;
		return test != null && value(() -> (Boolean) micActive.invoke(test), false);
	}

	public static void toggleMicTest() {
		if (micTesting()) {
			stopMicTest();
			return;
		}
		run(() -> {
			stopMicTest();
			if (!micTestAvailable()) {
				return;
			}
			Object listener = Proxy.newProxyInstance(micListenerClass.getClassLoader(), new Class<?>[]{micListenerClass}, (proxy, method, args) -> {
				switch (method.getName()) {
					case "onMicValue" -> micLevel = (Double) args[0];
					case "onStop" -> micLevel = MIN_THRESHOLD;
					case "hashCode" -> {
						return System.identityHashCode(proxy);
					}
					case "equals" -> {
						return proxy == args[0];
					}
					case "toString" -> {
						return "MccInfoMicListener";
					}
					default -> {
					}
				}
				return null;
			});
			Object test = micTestClass.getConstructor(int.class, int.class, boolean.class, micListenerClass).newInstance(0, 0, false, listener);
			micTest = test;
			micPress.invoke(test, new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
		});
	}

	public static void micTestFrame() {
		Object test = micTest;
		if (test != null) {
			run(() -> micRendered.invoke(test));
		}
	}

	public static void stopMicTest() {
		Object test = micTest;
		micTest = null;
		micLevel = MIN_THRESHOLD;
		if (test != null) {
			run(() -> micStop.invoke(test));
		}
	}

	// Level meter falling smoothly
	private static final double METER_FLOOR = -60.0;
	private static final double METER_FALL_MILLIS = 180.0;
	private static double meter;
	private static long meterAt;

	public static double micMeter() {
		long now = System.currentTimeMillis();
		long elapsed = meterAt == 0 ? 0 : Math.min(1000, now - meterAt);
		meterAt = now;
		double target = micTesting() ? Math.clamp((micLevel - METER_FLOOR) / -METER_FLOOR, 0.0, 1.0) : 0.0;
		meter = target >= meter ? target : meter + (target - meter) * (1.0 - Math.exp(-elapsed / METER_FALL_MILLIS));
		return meter < 0.005 ? 0.0 : meter;
	}

	public static double micLevel() {
		return micTesting() ? (micLevel - MIN_THRESHOLD) / (MAX_THRESHOLD - MIN_THRESHOLD) : 0.0;
	}
}
