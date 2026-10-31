package leroidesafk.mccinfo.client.gui;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.PrivateKeys;
import leroidesafk.mccinfo.client.chat.ModChat;
import leroidesafk.mccinfo.client.fishing.FishCollectionScreen;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import leroidesafk.mccinfo.client.voice.VoiceAdmin;
import leroidesafk.mccinfo.client.voice.VoiceChat;
import leroidesafk.mccinfo.client.voice.VoiceDeviceScreen;
import leroidesafk.mccinfo.client.voice.VoiceGroupScreen;
import leroidesafk.mccinfo.client.voice.VoiceSettings;
import leroidesafk.mccinfo.client.voice.VoiceVolumesScreen;
import leroidesafk.mccinfo.client.widget.Widgets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SettingsPages {
	static final int KEY_WIDTH = 70;
	private static final int DROPDOWN_EXTRA = 20;

	// Cards and sections added by optional modules
	private static final java.util.Map<String, List<java.util.function.Supplier<List<SettingsPanel.Card>>>> EXTRA_CARDS = new java.util.HashMap<>();
	private static final java.util.Map<String, List<java.util.function.Supplier<List<SettingsPanel.Section>>>> EXTRA_SECTIONS = new java.util.HashMap<>();

	private final Screen screen;
	// Key waiting for a new binding
	private PrivateKeys.Key listening;
	private boolean listeningPtt;

	SettingsPages(Screen screen) {
		this.screen = screen;
	}

	// Admin page at the bottom of the left column
	interface FooterPage {
		SettingsPanel.Page build(SettingsPages pages);

		List<EditBox> inputs();

		boolean submit(EditBox box);
	}

	private static FooterPage footerPage;

	private static final List<Supplier<SettingsPanel.Card>> GENERAL_CARDS = new ArrayList<>();

	static void addGeneralCard(Supplier<SettingsPanel.Card> card) {
		GENERAL_CARDS.add(card);
	}

	static void setFooterPage(FooterPage page) {
		footerPage = page;
	}

	SettingsPanel.Page footer() {
		return footerPage == null ? null : footerPage.build(this);
	}

	List<EditBox> footerInputs() {
		return footerPage == null ? List.of() : footerPage.inputs();
	}

	boolean submitFooter(EditBox box) {
		return footerPage != null && footerPage.submit(box);
	}

	Screen screen() {
		return this.screen;
	}

	static void offline() {
		if (Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("message.mccinfo.mccinfo.offline").withStyle(ChatFormatting.RED));
		}
	}

	// Pages of the two left boxes, by permission
	List<List<SettingsPanel.Page>> groups() {
		List<SettingsPanel.Page> utils = new ArrayList<>();
		if (Permissions.canOpenKeys()) {
			utils.add(keybinds());
		}
		if (Permissions.has(Permissions.Perm.SEE_MAP) || Permissions.has(Permissions.Perm.UTILS)) {
			utils.add(fishing());
		}
		if (Permissions.has(Permissions.Perm.UTILS)) {
			utils.add(utils());
		}
		if (Permissions.has(Permissions.Perm.VOICE_CHAT)) {
			utils.add(voiceChat());
		}
		if (Permissions.has(Permissions.Perm.UTILS)) {
			utils.add(widgets());
		}
		List<SettingsPanel.Page> events = Permissions.has(Permissions.Perm.EVENTS) ? List.of(halloween(), summer()) : List.of();
		return List.of(utils, events);
	}

	boolean listening() {
		return this.listening != null || this.listeningPtt;
	}

	void bind(InputConstants.Key key) {
		if (this.listening != null) {
			this.listening.set(key);
			this.listening = null;
		}
		if (this.listeningPtt) {
			VoiceSettings.setPttKey(key);
			this.listeningPtt = false;
		}
	}

	void stopListening() {
		this.listening = null;
		this.listeningPtt = false;
	}

	static Component text(String key) {
		return Component.translatable(key);
	}

	static SettingsPanel.Toggle toggle(java.util.function.BooleanSupplier value, java.util.function.Consumer<Boolean> onChange) {
		return new SettingsPanel.Toggle(value, onChange);
	}

	// Keybinds page
	private SettingsPanel.Page keybinds() {
		List<SettingsPanel.Card> layout = List.of(SettingsPanel.Card.of("keyboard_layout", text("screen.mccinfo.keyboard.title"),
				text("screen.mccinfo.settings.keyboard_layout.description"),
				new SettingsPanel.Action(() -> text("screen.mccinfo.settings.keyboard_layout.open"), KEY_WIDTH, () -> {
					Minecraft client = Minecraft.getInstance();
					client.gui.setScreen(new KeyboardLayoutScreen(client.gui.screen()));
				})));
		List<SettingsPanel.Card> general = new ArrayList<>();
		for (Supplier<SettingsPanel.Card> supplier : GENERAL_CARDS) {
			SettingsPanel.Card card = supplier.get();
			if (card != null) {
				general.add(card);
			}
		}
		List<SettingsPanel.Card> keys = new ArrayList<>();
		for (PrivateKeys.Key key : PrivateKeys.ALL) {
			if (key.usable() && !key.admin) {
				keys.add(keyCard(key));
			}
		}
		return new SettingsPanel.Page("keybinds", text("screen.mccinfo.events.whitelist"), SettingsPanel.ICON_KEYBINDS, List.of(
				new SettingsPanel.Section(text("screen.mccinfo.keyboard.title"), layout),
				new SettingsPanel.Section(text("screen.mccinfo.settings.section.general"), general),
				new SettingsPanel.Section(text("screen.mccinfo.events.whitelist"), keys)));
	}

	SettingsPanel.Card keyCard(PrivateKeys.Key key) {
		return SettingsPanel.Card.of("key_" + key.id, text(key.translationKey), Component.empty(),
				new SettingsPanel.Action(() -> keyLabel(key), KEY_WIDTH, () -> this.listening = key));
	}

	private Component keyLabel(PrivateKeys.Key key) {
		Component name = key.key().getDisplayName();
		return key == this.listening ? Component.literal("> ").append(name.copy().withStyle(ChatFormatting.YELLOW)).append(" <") : name;
	}

	// Utils page: navigation, transitions and chat
	private SettingsPanel.Page utils() {
		ModConfig config = ModConfig.get();
		boolean utilsPerm = Permissions.has(Permissions.Perm.UTILS);
		List<SettingsPanel.Card> transitions = new ArrayList<>();
		if (utilsPerm) transitions.add(SettingsPanel.Card.of("wipe_to_black", text("screen.mccinfo.settings.wipe_to_black"),
				text("screen.mccinfo.settings.wipe_to_black.description"),
				toggle(() -> config.wipeToBlack, value -> {
					config.wipeToBlack = value;
					config.save();
				})));
		if (utilsPerm && config.wipeToBlack) {
			transitions.add(SettingsPanel.Card.child("transition_style", text("screen.mccinfo.settings.transition_style"),
					text("screen.mccinfo.settings.transition_style.description"),
					dropdown(() -> TransitionWipe.styleName(TransitionWipe.style()), List.of(TransitionWipe.Style.values()), TransitionWipe::styleName, value -> {
						config.transitionStyle = value;
						config.save();
					})));
		}
		List<SettingsPanel.Card> chat = new ArrayList<>();
		if (utilsPerm) chat.add(SettingsPanel.Card.of("chat_ping", text("screen.mccinfo.settings.chat_ping"),
				text("screen.mccinfo.settings.chat_ping.description"),
				toggle(() -> config.chatPing, value -> {
					config.chatPing = value;
					config.save();
				})));
		if (utilsPerm && config.chatPing) {
			chat.add(SettingsPanel.Card.child("ping_sound", text("screen.mccinfo.settings.ping_sound"),
					text("screen.mccinfo.settings.ping_sound.description"),
					toggle(() -> config.pingSound, value -> {
						config.pingSound = value;
						config.save();
					})));
			chat.add(SettingsPanel.Card.child("ping_flash", text("screen.mccinfo.settings.ping_flash"),
					text("screen.mccinfo.settings.ping_flash.description"),
					toggle(() -> config.pingFlash, value -> {
						config.pingFlash = value;
						config.save();
					})));
		}
		if (utilsPerm) chat.add(SettingsPanel.Card.of("chat_copy", text("screen.mccinfo.settings.chat_copy"),
				text("screen.mccinfo.settings.chat_copy.description"),
				toggle(() -> config.chatCopy, value -> {
					config.chatCopy = value;
					config.save();
				})));
		if (utilsPerm) chat.add(SettingsPanel.Card.of("emojis", text("screen.mccinfo.settings.emojis"),
				text("screen.mccinfo.settings.emojis.description"),
				toggle(() -> config.emojis, value -> {
					config.emojis = value;
					config.save();
				})));
		if (utilsPerm) chat.add(SettingsPanel.Card.of("auto_emojis", text("screen.mccinfo.settings.auto_emojis"),
				text("screen.mccinfo.settings.auto_emojis.description"),
				toggle(() -> config.autoEmojis, value -> {
					config.autoEmojis = value;
					config.save();
				})));
		if (utilsPerm) chat.add(SettingsPanel.Card.of("mod_chat", text("screen.mccinfo.settings.mod_chat"),
				text("screen.mccinfo.settings.mod_chat.description"),
				toggle(() -> config.modChat, value -> {
					config.modChat = value;
					config.save();
					ModChat.settingsChanged();
				})));
		if (utilsPerm && config.modChat) {
			chat.add(SettingsPanel.Card.child("mod_chat_notify", text("screen.mccinfo.settings.mod_chat_notify"),
					text("screen.mccinfo.settings.mod_chat_notify.description"),
					toggle(() -> config.modChatNotify, value -> {
						config.modChatNotify = value;
						config.save();
					})));
			if (config.modChatNotify && Permissions.isStaff()) {
				chat.add(SettingsPanel.Card.grandchild("mod_chat_notify_channel", text("screen.mccinfo.settings.mod_chat_notify_channel"),
						text("screen.mccinfo.settings.mod_chat_notify_channel.description"),
						dropdown(() -> boxChannelName(config.modChatNotifyChannel), BOX_CHANNELS, SettingsPages::boxChannelName, value -> {
							config.modChatNotifyChannel = value;
							config.save();
						})));
			}
			chat.add(SettingsPanel.Card.child("mod_chat_box", text("screen.mccinfo.settings.mod_chat_box"),
					text("screen.mccinfo.settings.mod_chat_box.description"),
					toggle(() -> config.modChatBox, value -> {
						config.modChatBox = value;
						config.save();
						ModChat.settingsChanged();
					})));
			if (config.modChatBox && Permissions.isStaff()) {
				chat.add(SettingsPanel.Card.grandchild("mod_chat_box_channel", text("screen.mccinfo.settings.mod_chat_box_channel"),
						text("screen.mccinfo.settings.mod_chat_box_channel.description"),
						dropdown(() -> boxChannelName(config.modChatBoxChannel), BOX_CHANNELS, SettingsPages::boxChannelName, value -> {
							config.modChatBoxChannel = value;
							config.save();
							ModChat.settingsChanged();
						})));
			}
		}
		List<SettingsPanel.Card> navigation = extraCards("utils.navigation");
		return new SettingsPanel.Page("utils", text("screen.mccinfo.settings.utils"), SettingsPanel.ICON_UTILS, List.of(
				new SettingsPanel.Section(text("screen.mccinfo.settings.section.navigation"), navigation),
				new SettingsPanel.Section(text("screen.mccinfo.settings.section.transitions"), transitions),
				new SettingsPanel.Section(text("screen.mccinfo.settings.section.chat"), chat)));
	}

	// Widgets page
	private SettingsPanel.Page widgets() {
		ModConfig config = ModConfig.get();
		List<SettingsPanel.Card> cards = List.of(
				keyCard(Widgets.wheelKey()),
				SettingsPanel.Card.of("widget_duels", text("screen.mccinfo.settings.widget_duels"), text("screen.mccinfo.settings.widget_duels.description"),
						toggle(() -> config.widgetDuels, value -> {
							config.widgetDuels = value;
							config.save();
							Widgets.sendPrefs();
						})),
				SettingsPanel.Card.of("widget_waypoints", text("screen.mccinfo.settings.widget_waypoints"),
						text("screen.mccinfo.settings.widget_waypoints.description"),
						toggle(() -> config.widgetWaypoints, value -> {
							config.widgetWaypoints = value;
							config.save();
						})));
		return new SettingsPanel.Page("widgets", text("screen.mccinfo.settings.widgets"), SettingsPanel.ICON_WIDGETS, List.of(
				new SettingsPanel.Section(text("screen.mccinfo.settings.widgets"), cards)));
	}

	private static final Identifier SVC_MICROPHONE = Identifier.fromNamespaceAndPath("voicechat", "icons/microphone_button");
	private static final Identifier SVC_SPEAKER = Identifier.fromNamespaceAndPath("voicechat", "icons/speaker_button");
	private static final Identifier SVC_VOLUMES = Identifier.fromNamespaceAndPath("voicechat", "icons/adjust_volumes");
	private static final Identifier SVC_HIDE = Identifier.fromNamespaceAndPath("voicechat", "icons/hide_button");
	private static final String SVC_URL = "https://modrinth.com/plugin/simple-voice-chat/versions?g=";

	// Voice Chat page
	private SettingsPanel.Page voiceChat() {
		ModConfig config = ModConfig.get();
		List<SettingsPanel.Card> voice = new ArrayList<>();
		if (!VoiceChat.present()) {
			voice.add(SettingsPanel.Card.warning("voice_chat_missing", text("screen.mccinfo.settings.voice_chat_missing"),
					text("screen.mccinfo.settings.voice_chat_missing.description"),
					new SettingsPanel.Action(() -> text("screen.mccinfo.settings.voice_chat_missing.download"), 0,
							() -> Util.getPlatform().openUri(SVC_URL + SharedConstants.getCurrentVersion().id() + "&l=fabric"))));
		} else {
			if (VoiceAdmin.disabled() && Permissions.isAdmin()) {
				voice.add(SettingsPanel.Card.warning("voice_chat_off", text("screen.mccinfo.settings.voice_chat_off"),
						text("screen.mccinfo.settings.voice_chat_off.admin")));
			}
			voice.add(SettingsPanel.Card.of("voice_chat", text("screen.mccinfo.settings.voice_chat"),
					text("screen.mccinfo.settings.voice_chat.description"),
					toggle(() -> config.voiceChatEnabled, value -> {
						config.voiceChatEnabled = value;
						config.save();
					})));
			if (config.voiceChatEnabled) {
				List<SettingsPanel.Control> controls = new ArrayList<>(List.of(
						new SettingsPanel.IconButton(SVC_MICROPHONE, true, VoiceChat::muted, VoiceChat::canMute,
								() -> text(!VoiceChat.canMute() ? "screen.mccinfo.settings.voice_chat_mute.ptt"
										: VoiceChat.muted() ? "screen.mccinfo.settings.voice_chat_mute.off" : "screen.mccinfo.settings.voice_chat_mute.on"),
								VoiceChat::toggleMuted),
						new SettingsPanel.IconButton(SVC_SPEAKER, true, VoiceChat::deafened, VoiceChat::canDeafen,
								() -> text(VoiceChat.deafened() ? "screen.mccinfo.settings.voice_chat_deafen.off" : "screen.mccinfo.settings.voice_chat_deafen.on"),
								VoiceChat::toggleDeafened),
						new SettingsPanel.IconButton(SVC_VOLUMES, false, () -> false, () -> true,
								() -> text("screen.mccinfo.settings.voice_chat_volumes"), () -> {
									Minecraft client = Minecraft.getInstance();
									client.gui.setScreen(new VoiceVolumesScreen(client.gui.screen()));
								}),
						new SettingsPanel.IconButton(SVC_HIDE, true, VoiceChat::hudIconsHidden, () -> true,
								() -> text(VoiceChat.hudIconsHidden() ? "screen.mccinfo.settings.voice_chat_hud.off" : "screen.mccinfo.settings.voice_chat_hud.on"),
								VoiceChat::toggleHudIcons)));
				if (VoiceSettings.available()) {
					controls.add(new SettingsPanel.Break());
					controls.add(new SettingsPanel.IconButton(SVC_MICROPHONE, true, () -> {
						VoiceSettings.micTestFrame();
						return !VoiceSettings.micTesting();
					}, VoiceSettings::micTestAvailable,
							() -> text(!VoiceSettings.micTestAvailable() ? "screen.mccinfo.settings.voice_mic_test.unavailable"
									: VoiceSettings.micTesting() ? "screen.mccinfo.settings.voice_mic_test.stop" : "screen.mccinfo.settings.voice_mic_test.start"),
							VoiceSettings::toggleMicTest));
					controls.add(new SettingsPanel.Meter(VoiceSettings::micMeter, 3));
				}
				voice.add(SettingsPanel.Card.wide("voice_chat_controls", text("screen.mccinfo.settings.voice_chat_controls"),
						text("screen.mccinfo.settings.voice_chat_controls.description"), controls.toArray(SettingsPanel.Control[]::new)));
				voice.add(SettingsPanel.Card.of("voice_chat_group", text("screen.mccinfo.settings.voice_chat_group"),
						text("screen.mccinfo.settings.voice_chat_group.description"),
						new SettingsPanel.Action(() -> text("screen.mccinfo.settings.open"), KEY_WIDTH, () -> {
							Minecraft client = Minecraft.getInstance();
							client.gui.setScreen(new VoiceGroupScreen(client.gui.screen()));
						})));
				voice.add(SettingsPanel.Card.of("voice_chat_icons", text("screen.mccinfo.settings.voice_chat_icons"),
						text("screen.mccinfo.settings.voice_chat_icons.description"),
						toggle(() -> config.voiceChatIcons, value -> {
							config.voiceChatIcons = value;
							config.save();
						})));
			}
		}
		List<SettingsPanel.Section> sections = new ArrayList<>();
		sections.add(new SettingsPanel.Section(text("screen.mccinfo.settings.section.voice"), voice));
		if (VoiceChat.present() && config.voiceChatEnabled && VoiceSettings.available()) {
			sections.add(new SettingsPanel.Section(text("screen.mccinfo.settings.section.voice_settings"), voiceSettings()));
		}
		return new SettingsPanel.Page("voice_chat", text("screen.mccinfo.settings.voice_chat"), SettingsPanel.ICON_VOICE, sections);
	}

	private static final List<Integer> AUDIO_TYPES = List.of(VoiceSettings.AUDIO_NORMAL, VoiceSettings.AUDIO_REDUCED, VoiceSettings.AUDIO_OFF);
	private static final String[] AUDIO_TYPE_KEYS = {"normal", "reduced", "off"};

	// Simple Voice Chat settings
	private List<SettingsPanel.Card> voiceSettings() {
		List<SettingsPanel.Card> cards = new ArrayList<>();
		cards.add(SettingsPanel.Card.row("voice_devices",
				SettingsPanel.Card.of("voice_microphone", text("screen.mccinfo.settings.voice_microphone"), deviceDescription(VoiceSettings.microphone()),
						new SettingsPanel.Action(() -> text("screen.mccinfo.settings.select"), KEY_WIDTH, () -> openDevices(true))),
				SettingsPanel.Card.of("voice_speaker", text("screen.mccinfo.settings.voice_speaker"), deviceDescription(VoiceSettings.speaker()),
						new SettingsPanel.Action(() -> text("screen.mccinfo.settings.select"), KEY_WIDTH, () -> openDevices(false)))));
		cards.add(SettingsPanel.Card.of("voice_volume", text("screen.mccinfo.settings.voice_volume"), text("screen.mccinfo.settings.voice_volume.description"),
				new SettingsPanel.Slider(0, () -> Math.round(VoiceSettings.maxVolume() * 100.0), 1, () -> Math.round(VoiceSettings.volume() * 100.0),
						value -> VoiceSettings.setVolume(value / 100.0, false), VoiceSettings::saveVolume,
						value -> Component.literal(Math.round(value) + "%"), () -> true, null, null)));
		boolean canAgc = VoiceSettings.canAgc();
		boolean agc = canAgc && VoiceSettings.agc();
		if (canAgc) {
			cards.add(SettingsPanel.Card.of("voice_agc", text("screen.mccinfo.settings.voice_agc"),
					text(agc ? "screen.mccinfo.settings.voice_agc.description" : "screen.mccinfo.settings.voice_agc.warning"),
					toggle(VoiceSettings::agc, VoiceSettings::setAgc)));
		}
		if (!agc) {
			SettingsPanel.Slider gain = new SettingsPanel.Slider(VoiceSettings.MIN_GAIN, () -> VoiceSettings.MAX_GAIN, 1, VoiceSettings::gain,
					value -> VoiceSettings.setGain(value, false), VoiceSettings::saveGain,
					value -> {
						MutableComponent label = Component.translatable("screen.mccinfo.settings.voice_db", (value > 0 ? "+" : "") + Math.round(value));
						return value > VoiceSettings.GAIN_WARNING ? label.withStyle(ChatFormatting.RED) : label;
					}, () -> true, null,
					() -> VoiceSettings.gain() > VoiceSettings.GAIN_WARNING
							? Component.translatable("screen.mccinfo.settings.voice_gain.warning", Math.round(VoiceSettings.GAIN_WARNING)) : null);
			cards.add(canAgc
					? SettingsPanel.Card.child("voice_gain", text("screen.mccinfo.settings.voice_gain"), text("screen.mccinfo.settings.voice_gain.description"), gain)
					: SettingsPanel.Card.of("voice_gain", text("screen.mccinfo.settings.voice_gain"), text("screen.mccinfo.settings.voice_gain.description"), gain));
		}
		if (VoiceSettings.canDenoise()) {
			cards.add(SettingsPanel.Card.of("voice_denoiser", text("screen.mccinfo.settings.voice_denoiser"), text("screen.mccinfo.settings.voice_denoiser.description"),
					toggle(VoiceSettings::denoiser, VoiceSettings::setDenoiser)));
		}
		boolean voice = VoiceSettings.voiceActivation();
		cards.add(SettingsPanel.Card.of("voice_activation", text("screen.mccinfo.settings.voice_activation"),
				text("screen.mccinfo.settings.voice_activation.description"),
				dropdown(() -> activationName(VoiceSettings.voiceActivation()), List.of(false, true), SettingsPages::activationName, VoiceSettings::setVoiceActivation)));
		if (!voice) {
			cards.add(SettingsPanel.Card.child("voice_ptt", text("screen.mccinfo.settings.voice_ptt"), text("screen.mccinfo.settings.voice_ptt.description"),
					new SettingsPanel.Action(this::pttLabel, KEY_WIDTH, () -> {
						this.listening = null;
						this.listeningPtt = true;
					})));
		} else {
			if (VoiceSettings.canDenoise()) {
				cards.add(SettingsPanel.Card.child("voice_vad", text("screen.mccinfo.settings.voice_vad"), text("screen.mccinfo.settings.voice_vad.description"),
						toggle(VoiceSettings::vad, VoiceSettings::setVad)));
			}
			if (VoiceSettings.showsThreshold()) {
				cards.add(SettingsPanel.Card.child("voice_threshold", text("screen.mccinfo.settings.voice_threshold"),
						text("screen.mccinfo.settings.voice_threshold.description"),
						new SettingsPanel.Slider(VoiceSettings.MIN_THRESHOLD, () -> VoiceSettings.MAX_THRESHOLD, 1, VoiceSettings::threshold,
								value -> VoiceSettings.setThreshold(value, false), VoiceSettings::saveThreshold,
								value -> {
									MutableComponent label = Component.translatable("screen.mccinfo.settings.voice_db", Math.round(value));
									return value >= VoiceSettings.MAX_THRESHOLD ? label.withStyle(ChatFormatting.RED) : label;
								}, () -> true, VoiceSettings::micLevel,
								() -> VoiceSettings.threshold() >= VoiceSettings.MAX_THRESHOLD ? text("screen.mccinfo.settings.voice_threshold.warning") : null)));
			}
		}
		cards.add(SettingsPanel.Card.of("voice_audio_type", text("screen.mccinfo.settings.voice_audio_type"),
				text("screen.mccinfo.settings.voice_audio_type.description"),
				dropdown(() -> audioTypeName(VoiceSettings.audioType()), AUDIO_TYPES, SettingsPages::audioTypeName, VoiceSettings::setAudioType)));
		return cards;
	}

	private Component pttLabel() {
		Component name = VoiceSettings.pttKeyName();
		return this.listeningPtt ? Component.literal("> ").append(name.copy().withStyle(ChatFormatting.YELLOW)).append(" <") : name;
	}

	private static Component activationName(boolean voice) {
		return text(voice ? "screen.mccinfo.settings.voice_activation.voice" : "screen.mccinfo.settings.voice_activation.ptt");
	}

	private static Component audioTypeName(int type) {
		return text("screen.mccinfo.settings.voice_audio_type." + AUDIO_TYPE_KEYS[Math.clamp(type, 0, AUDIO_TYPE_KEYS.length - 1)]);
	}

	private static Component deviceDescription(String device) {
		Component name = device == null || device.isEmpty() ? text("screen.mccinfo.devices.default") : Component.literal(VoiceSettings.deviceName(device));
		return Component.translatable("screen.mccinfo.settings.voice_device.description", name);
	}

	private static void openDevices(boolean microphone) {
		Minecraft client = Minecraft.getInstance();
		client.gui.setScreen(new VoiceDeviceScreen(client.gui.screen(), microphone));
	}


	// Fishing page
	private SettingsPanel.Page fishing() {
		ModConfig config = ModConfig.get();
		boolean utilsPerm = Permissions.has(Permissions.Perm.UTILS);
		List<SettingsPanel.Card> display = new ArrayList<>();
		if (utilsPerm) {
			display.add(SettingsPanel.Card.of("fishing_messages", text("screen.mccinfo.events.fishing_messages"), text("screen.mccinfo.settings.fishing_messages.description"),
					toggle(() -> config.fishingMessages, value -> config.fishingMessages = value)));
			if (config.fishingMessages) {
				display.add(SettingsPanel.Card.child("fishing_messages_fishing_only", text("screen.mccinfo.settings.fishing_messages_fishing_only"),
						text("screen.mccinfo.settings.fishing_messages_fishing_only.description"),
						toggle(() -> config.fishingMessagesFishingOnly, value -> config.fishingMessagesFishingOnly = value)));
			}
			display.add(SettingsPanel.Card.of("compact_fishing_messages", text("screen.mccinfo.settings.compact_fishing_messages"),
					text("screen.mccinfo.settings.compact_fishing_messages.description"),
					toggle(() -> config.compactFishingMessages, value -> config.compactFishingMessages = value)));
		}
		display.addAll(extraCards("fishing.display"));

		List<SettingsPanel.Card> debug = !utilsPerm ? List.of() : List.of(SettingsPanel.Card.of("fish_collection", text("screen.mccinfo.events.fish_collection"),
				text("screen.mccinfo.settings.fish_collection.description"),
				new SettingsPanel.Action(() -> text("screen.mccinfo.settings.open"), KEY_WIDTH, () -> Minecraft.getInstance().gui.setScreen(new FishCollectionScreen(this.screen)))));

		List<SettingsPanel.Section> sections = new ArrayList<>();
		sections.add(new SettingsPanel.Section(text("screen.mccinfo.settings.section.display"), display));
		sections.addAll(extraSections("fishing"));
		sections.add(new SettingsPanel.Section(text("screen.mccinfo.settings.section.debug"), debug));
		return new SettingsPanel.Page("fishing", text("screen.mccinfo.events.fishing"), SettingsPanel.ICON_FISHING, sections);
	}

	// Halloween 2026 page
	private SettingsPanel.Page halloween() {
		ModConfig config = ModConfig.get();
		List<SettingsPanel.Card> overlays = new ArrayList<>();
		overlays.add(SettingsPanel.Card.of("event_timer", text("screen.mccinfo.settings.event_timer"), text("screen.mccinfo.settings.event_timer.description"),
				toggle(() -> config.halloweenTimer, value -> config.halloweenTimer = value)));
		overlays.add(SettingsPanel.Card.of("ticket_counter", text("screen.mccinfo.settings.ticket_counter"), text("screen.mccinfo.settings.ticket_counter.description"),
				toggle(() -> config.carnivalTickets, value -> config.carnivalTickets = value)));
		if (config.carnivalTickets) {
			overlays.add(SettingsPanel.Card.child("tickets_fishing_only", text("screen.mccinfo.settings.tickets_fishing_only"),
					text("screen.mccinfo.settings.tickets_fishing_only.description"),
					toggle(() -> config.ticketsFishingOnly, value -> config.ticketsFishingOnly = value)));
		}
		List<SettingsPanel.Card> fishing = new ArrayList<>(extraCards("halloween.fishing"));
		fishing.add(SettingsPanel.Card.of("ticket_icons", text("screen.mccinfo.settings.ticket_icons"), text("screen.mccinfo.settings.ticket_icons.description"),
				toggle(() -> config.ticketIcons, value -> config.ticketIcons = value)));
		return new SettingsPanel.Page("halloween_2026", text("screen.mccinfo.events.halloween_timer"), SettingsPanel.ICON_HALLOWEEN, List.of(
				new SettingsPanel.Section(text("screen.mccinfo.settings.section.overlays"), overlays),
				new SettingsPanel.Section(text("screen.mccinfo.events.fishing"), fishing)));
	}

	// Summer 2026 page
	private SettingsPanel.Page summer() {
		ModConfig config = ModConfig.get();
		return new SettingsPanel.Page("summer_2026", text("screen.mccinfo.events.summer_hearts"), SettingsPanel.ICON_SUMMER, List.of(
				new SettingsPanel.Section(text("screen.mccinfo.settings.section.fish_orders"), List.of(
						SettingsPanel.Card.of("fish_heart", text("screen.mccinfo.settings.fish_heart"), text("screen.mccinfo.settings.fish_heart.description"),
								toggle(() -> config.summerHearts, value -> config.summerHearts = value))))));
	}

	private static final List<String> BOX_CHANNELS = List.of("mccinfo", "mod", "both");

	private static Component boxChannelName(String channel) {
		return text(switch (channel) {
			case "mccinfo" -> "screen.mccinfo.settings.mod_chat_box_channel.mccinfo";
			case "mod" -> "screen.mccinfo.settings.mod_chat_box_channel.mod";
			default -> "screen.mccinfo.settings.mod_chat_box_channel.both";
		});
	}

	// Dropdown as wide as its longest choice
	static <T> SettingsPanel.Dropdown dropdown(java.util.function.Supplier<Component> label, List<T> values, Function<T, Component> name,
			java.util.function.Consumer<T> select) {
		int width = 0;
		for (T value : values) {
			width = Math.max(width, Minecraft.getInstance().font.width(name.apply(value)));
		}
		return dropdown(label, values, name, select, width + DROPDOWN_EXTRA);
	}

	static <T> SettingsPanel.Dropdown dropdown(java.util.function.Supplier<Component> label, List<T> values, Function<T, Component> name,
			java.util.function.Consumer<T> select, int width) {
		return new SettingsPanel.Dropdown(label, width, values.stream().map(name).toList(), index -> select.accept(values.get(index)));
	}

	static void addCards(String slot, java.util.function.Supplier<List<SettingsPanel.Card>> cards) {
		EXTRA_CARDS.computeIfAbsent(slot, key -> new ArrayList<>()).add(cards);
	}

	static void addSections(String page, java.util.function.Supplier<List<SettingsPanel.Section>> sections) {
		EXTRA_SECTIONS.computeIfAbsent(page, key -> new ArrayList<>()).add(sections);
	}

	private static List<SettingsPanel.Card> extraCards(String slot) {
		List<SettingsPanel.Card> cards = new ArrayList<>();
		for (java.util.function.Supplier<List<SettingsPanel.Card>> supplier : EXTRA_CARDS.getOrDefault(slot, List.of())) {
			cards.addAll(supplier.get());
		}
		return cards;
	}

	private static List<SettingsPanel.Section> extraSections(String page) {
		List<SettingsPanel.Section> sections = new ArrayList<>();
		for (java.util.function.Supplier<List<SettingsPanel.Section>> supplier : EXTRA_SECTIONS.getOrDefault(page, List.of())) {
			sections.addAll(supplier.get());
		}
		return sections;
	}
}
