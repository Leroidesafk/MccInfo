package leroidesafk.mccinfo.client;

import leroidesafk.mccinfo.client.gui.TransitionWipe;
import leroidesafk.mccinfo.client.util.AsyncFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModConfig {
	// Default position or size
	public static final int UNSET = -1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve(Mccinfo.MOD_ID);
	private static final Path PATH = file("config.json");

	public static Path file(String name) {
		return DIRECTORY.resolve(name);
	}

	private static ModConfig instance;

	public record InventoryButton(int slot, String command, String background, String iconType, String icon) {
	}

	// Event features
	public boolean summerHearts;
	public boolean halloweenTimer;
	public Boolean carnivalTickets;
	public Boolean ticketsFishingOnly;
	public Boolean ticketIcons;

	// Halloween timer box
	public int timerX = UNSET;
	public int timerY = UNSET;
	public boolean timerHidden;

	// Other features and their boxes
	public boolean fishingMessages;
	public boolean fishingMessagesFishingOnly;
	public boolean compactFishingMessages;
	public boolean neuItemsHidden;
	public int neuColumns;
	public int messagesX = UNSET;
	public int messagesY = UNSET;
	public int messagesWidth = UNSET;
	public int messagesHeight = UNSET;
	public int messagesTextPixels = UNSET;
	public boolean messagesHidden;

	public int ticketsX = UNSET;
	public int ticketsY = UNSET;
	public boolean ticketsHidden;
	public Map<String, String> privateKeys = new HashMap<>();

	// Saved game data
	public Map<String, Integer> tickets = new HashMap<>();
	public Map<String, Integer> perks = new HashMap<>();
	public Map<String, String> overclocks = new HashMap<>();
	public Map<String, Integer> equipmentBonuses = new HashMap<>();
	public List<String> augments = new ArrayList<>();
	public String bait;
	public String line;
	public Set<String> collapsedSections = new HashSet<>();
	public List<InventoryButton> inventoryButtons = new ArrayList<>();
	public String lastPatchNote;
	public boolean wipeToBlack;
	// Chat and voice settings
	public boolean chatPing = true;
	public boolean chatCopy = true;
	public boolean pingSound = true;
	public boolean pingFlash = true;
	public boolean voiceChatEnabled = true;
	public boolean voiceAnnounceMute = true;
	public boolean voiceChatIcons = true;
	// MccInfo chat and its box
	public boolean modChat = true;
	public boolean modChatNotify;
	public String modChatNotifyChannel = "both";
	public boolean modChatBox;
	public String modChatBoxChannel = "both";
	public int modChatBoxX = UNSET;
	public int modChatBoxY = UNSET;
	public int modChatBoxWidth = UNSET;
	public int modChatBoxHeight = UNSET;
	public int modChatBoxTextPixels = UNSET;
	public boolean modChatBoxHidden;
	// Emojis and their box
	public boolean emojis = true;
	public boolean autoEmojis = true;
	public int emojiX = UNSET;
	public int emojiY = UNSET;
	public int emojiWidth = UNSET;
	public int emojiHeight = UNSET;
	public int emojiScale = UNSET;
	public boolean emojiHidden;
	// Keyboard and transitions
	public String keyboardLayout;
	public String keyboardSize;
	public TransitionWipe.Style transitionStyle = TransitionWipe.Style.DIAMOND;

	// Widgets and emotes
	public boolean widgetDuels = true;
	public boolean widgetWaypoints = true;
	public boolean emoteSounds = true;

	// Voice chat notice already shown
	public boolean voiceDisclaimerSeen;

	private ModConfig() {
	}

	public static ModConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static ModConfig load() {
		if (Files.exists(PATH)) {
			try {
				ModConfig loaded = GSON.fromJson(Files.readString(PATH), ModConfig.class);
				if (loaded != null) {
					// Fields missing from older configs
					if (loaded.tickets == null) {
						loaded.tickets = new HashMap<>();
					}
					if (loaded.perks == null) {
						loaded.perks = new HashMap<>();
					}
					if (loaded.overclocks == null) {
						loaded.overclocks = new HashMap<>();
					}
					if (loaded.equipmentBonuses == null) {
						loaded.equipmentBonuses = new HashMap<>();
					}
					if (loaded.augments == null) {
						loaded.augments = new ArrayList<>();
					}
					if (loaded.inventoryButtons == null) {
						loaded.inventoryButtons = new ArrayList<>();
					}
					loaded.inventoryButtons.removeIf(button -> button == null || button.command() == null);
					if (loaded.privateKeys == null) {
						loaded.privateKeys = new HashMap<>();
					}
					if (loaded.collapsedSections == null) {
						loaded.collapsedSections = new HashSet<>();
					}
					loaded.fillEventDefaults();
					return loaded;
				}
			} catch (IOException | JsonParseException e) {
				Mccinfo.LOGGER.warn("Unreadable config ({}), using default values", PATH, e);
			}
		}
		ModConfig config = new ModConfig();
		config.fillEventDefaults();
		return config;
	}

	// Event settings follow the old Halloween timer setting
	private void fillEventDefaults() {
		if (this.carnivalTickets == null) {
			this.carnivalTickets = this.halloweenTimer;
		}
		if (this.ticketsFishingOnly == null) {
			this.ticketsFishingOnly = true;
		}
		if (this.ticketIcons == null) {
			this.ticketIcons = this.halloweenTimer;
		}
	}

	public void save() {
		AsyncFiles.write(PATH, GSON.toJson(this));
	}
}
