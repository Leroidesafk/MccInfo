package leroidesafk.mccinfo.client;

import leroidesafk.mccinfo.client.gui.TransitionWipe;
import leroidesafk.mccinfo.client.items.BetterItemAmounts;
import leroidesafk.mccinfo.client.util.AsyncFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
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

	// File of the config folder
	public static Path file(String name) {
		return DIRECTORY.resolve(name);
	}

	private static ModConfig instance;

	// Command button of the inventory
	public record InventoryButton(int slot, String command, String background, String iconType, String icon) {
	}

	// Forge or Assembler craft and its end time
	public record CraftTimer(String station, int slot, String name, com.google.gson.JsonElement icon, long earliest, long end, boolean chatPending) {
	}

	// Chat room and its color
	public record ChatRoom(String name, String color, boolean muted) {
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

	// Other features
	public boolean fishingMessages;
	public boolean fishingMessagesFishingOnly;
	public boolean compactFishingMessages;
	// Icons on items and slots
	public boolean bestIslandIcon = true;
	public boolean islandWindIcons = true;
	public boolean fishCheckIcon = true;
	public boolean perkUpgradeIcon = true;
	public boolean perkLevels = true;
	public BetterItemAmounts.PerkLevelStyle perkLevelStyle = BetterItemAmounts.PerkLevelStyle.LEVEL;
	public boolean notOwnedIcon = true;
	public boolean missingMaterialsIcon = true;
	public boolean maxedCosmeticIcon = true;
	public boolean wardrobeDonationIcon;
	public boolean crateChances = true;
	public boolean lockedPerkIcon = true;
	public boolean completedCollectionIcon = true;
	public boolean betterItemAmounts = true;
	public boolean playerNumbers = false;
	public boolean assemblerOnTop = true;
	// Forge and Assembler Notification
	public boolean craftNotify = true;
	public boolean craftNotifyToast = true;
	public boolean craftNotifyChat = true;
	public boolean craftNotifySound = true;
	public List<CraftTimer> craftTimers = new ArrayList<>();
	// Island Exchange and Settings Info
	public boolean exchangeLowestPrice = true;
	public boolean exchangeTaxCalculator = true;
	public boolean settingsInfo = true;
	// Badge popularity notifications
	public boolean badgeNotify;
	public String badgeNotifyMode = "suggested";
	public Set<String> badgeNotifyGames = new HashSet<>();
	public String badgeNotifyPopularity = "high";
	public boolean badgeNotifySound = true;
	// Item list, recipes and Cosmetic Preview
	public boolean neuItemsHidden;
	public boolean mccJei = true;
	public boolean cosmeticPreview = true;
	// Badges of the MccInfo users
	public boolean seeModBadges = true;
	// Noxcrew API key
	public String apiToken = "";
	// Item list columns and favorites
	public int neuColumns;
	public List<String> neuFavorites = new ArrayList<>();
	// Fishing messages box
	public int messagesX = UNSET;
	public int messagesY = UNSET;
	public int messagesWidth = UNSET;
	public int messagesHeight = UNSET;
	public int messagesTextPixels = UNSET;
	public boolean messagesHidden;

	// Carnival tickets box and private keys
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
	public List<String> augmentUses = new ArrayList<>();
	public List<Map<String, Integer>> augmentBonuses = new ArrayList<>();
	public String bait;
	public Integer baitUses;
	public Map<String, Integer> baitBonuses = new HashMap<>();
	public String line;
	public Integer lineUses;
	public Integer lineMaxUses;
	public Map<String, Integer> lineBonuses = new HashMap<>();
	public Set<String> lockedIslands = new HashSet<>();
	public Set<String> collapsedSections = new HashSet<>();
	public List<InventoryButton> inventoryButtons = new ArrayList<>();
	// Last patch note shown and black transition
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
	// Server chats and chat rooms
	public boolean serverChats = true;
	public boolean focusReply = true;
	public boolean roomChats = true;
	public List<ChatRoom> chatRooms = new ArrayList<>();
	public Map<String, String> chatRoomIcons = new HashMap<>();
	// MccInfo chat and its box
	public boolean modChat = true;
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
	public List<List<String>> widgetWheels;
	public Set<String> widgetSeen;
	public boolean emoteSounds = true;
	// Cloak and Tab list
	@SerializedName(value = "hideFirstPersonCloak", alternate = "hideFirstPersonBackpack")
	public boolean hideFirstPersonCloak = true;
	@SerializedName(value = "showInventoryCloak", alternate = "showInventoryBackpack")
	public boolean showInventoryCloak;
	public boolean hideTabDots = true;
	// Parkour Warrior
	public boolean parkourLimitTint = true;
	public boolean parkourSplits = true;
	public boolean parkourSplitTimer = true;
	public boolean parkourSplitChat = true;
	public boolean parkourSplitImprovements = true;
	public boolean parkourImprovementsInFishingMessages = true;
	public int parkourImprovementAt = -3;
	public boolean parkourSplitAverage;
	// Quest Lock: locked games and tasks
	public boolean questLock = true;
	public Set<String> questLockGames = new HashSet<>();
	public Set<String> questLockTasks = new HashSet<>();

	// Voice chat notice already shown
	public boolean voiceDisclaimerSeen;

	private ModConfig() {
	}

	// Loaded on first use
	public static ModConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	// Default values when missing or unreadable
	private static ModConfig load() {
		if (Files.exists(PATH)) {
			try {
				ModConfig loaded = GSON.fromJson(Files.readString(PATH), ModConfig.class);
				if (loaded != null) {
					// Fields missing from older configs
					if (loaded.augmentBonuses == null) {
						loaded.augmentBonuses = new ArrayList<>();
					}
					if (loaded.augmentUses == null) {
						loaded.augmentUses = new ArrayList<>();
					}
					if (loaded.baitBonuses == null) {
						loaded.baitBonuses = new HashMap<>();
					}
					if (loaded.lineBonuses == null) {
						loaded.lineBonuses = new HashMap<>();
					}
					if (loaded.badgeNotifyGames == null) {
						loaded.badgeNotifyGames = new HashSet<>();
					}
					if (loaded.questLockGames == null) {
						loaded.questLockGames = new HashSet<>();
					}
					if (loaded.questLockTasks == null) {
						loaded.questLockTasks = new HashSet<>();
					}
					if (loaded.badgeNotifyMode == null) {
						loaded.badgeNotifyMode = "suggested";
					}
					if (loaded.badgeNotifyPopularity == null) {
						loaded.badgeNotifyPopularity = "high";
					}
					if (loaded.lockedIslands == null) {
						loaded.lockedIslands = new HashSet<>();
					}
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
					if (loaded.chatRooms == null) {
						loaded.chatRooms = new ArrayList<>();
					}
					loaded.chatRooms.removeIf(room -> room == null || room.name() == null || room.name().isBlank());
					if (loaded.craftTimers == null) {
						loaded.craftTimers = new ArrayList<>();
					}
					loaded.craftTimers.removeIf(timer -> timer == null || timer.station() == null || timer.name() == null);
					if (loaded.chatRoomIcons == null) {
						loaded.chatRoomIcons = new HashMap<>();
					}
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

	// Written in the background
	public void save() {
		AsyncFiles.write(PATH, GSON.toJson(this));
	}
}
