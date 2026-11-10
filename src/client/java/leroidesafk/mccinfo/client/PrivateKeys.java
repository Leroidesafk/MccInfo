package leroidesafk.mccinfo.client;

import leroidesafk.mccinfo.client.relay.Permissions;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BooleanSupplier;

// Mod keys set in the MccInfo menu, not in the game Options
public final class PrivateKeys {
	public static final class Key {
		public final String id;
		public final String translationKey;
		public final boolean admin;
		private final BooleanSupplier allowed;
		private final InputConstants.Key defaultKey;
		public boolean available;
		public String page;
		public String section;
		private InputConstants.Key key;
		private int modifiers;
		private boolean wasDown;
		private int clicks;

		private Key(String id, String translationKey, boolean admin, BooleanSupplier allowed, InputConstants.Key defaultKey, boolean available) {
			this.id = id;
			this.translationKey = translationKey;
			this.admin = admin;
			this.allowed = allowed;
			this.defaultKey = defaultKey;
			this.available = available;
			this.key = defaultKey;
		}

		// Page and section the key links to
		public Key in(String page, String section) {
			this.page = page;
			this.section = section;
			return this;
		}

		public boolean consumeClick() {
			if (this.clicks > 0) {
				this.clicks--;
				return true;
			}
			return false;
		}

		public boolean matches(KeyEvent event) {
			return this.key.getType() == InputConstants.Type.KEYSYM && this.key.getValue() == event.key() && event.key() != InputConstants.UNKNOWN.getValue()
					&& accepts(modifiersOf(event.modifiers(), this.key));
		}

		public boolean matchesMouse(MouseButtonEvent event) {
			return this.key.getType() == InputConstants.Type.MOUSE && this.key.getValue() == event.button() && accepts(modifiersOf(event.modifiers(), this.key));
		}

		private boolean accepts(int held) {
			if ((held & this.modifiers) != this.modifiers) {
				return false;
			}
			for (Key other : ALL) {
				if (other != this && other.usable() && other.key.equals(this.key) && Integer.bitCount(other.modifiers) > Integer.bitCount(this.modifiers)
						&& (held & other.modifiers) == other.modifiers) {
					return false;
				}
			}
			return true;
		}

		public int modifiers() {
			return this.modifiers;
		}

		public Component displayName() {
			return comboName(this.key, this.modifiers);
		}

		public String comboId() {
			return PrivateKeys.comboId(this.key, this.modifiers);
		}

		public boolean usable() {
			return this.available && this.allowed.getAsBoolean();
		}

		// Key held right now, even in a screen
		public boolean held() {
			return usable() && isDown(Minecraft.getInstance().getWindow());
		}

		public InputConstants.Key key() {
			return this.key;
		}

		public void set(InputConstants.Key key) {
			set(key, 0);
		}

		public void set(InputConstants.Key key, int modifiers) {
			this.key = key;
			this.modifiers = key.equals(InputConstants.UNKNOWN) ? 0 : modifiersOf(modifiers, key);
			ModConfig config = ModConfig.get();
			config.privateKeys.put(this.id, comboId());
			config.save();
		}

		private boolean isDown(Window window) {
			int held = heldModifiers(window);
			boolean modifiersOk = this.modifiers == 0 || (held & this.modifiers) == this.modifiers;
			return modifiersOk && baseDown(window);
		}

		private boolean pressedExactly(Window window) {
			return baseDown(window) && accepts(modifiersOf(heldModifiers(window), this.key));
		}

		private boolean baseDown(Window window) {
			return switch (this.key.getType()) {
				case KEYSYM -> this.key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, this.key.getValue());
				case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), this.key.getValue()) == GLFW.GLFW_PRESS;
				default -> false;
			};
		}
	}

	// Keys of the mod
	public static final Key RECIPE_MODE =new Key("recipe_mode", "key.mccinfo.recipe_mode", false,
			() -> Permissions.has(Permissions.Perm.SEE_ITEMS), InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R), true).in("utils", null);
	public static final Key COPY_ITEM = new Key("copy_item", "key.mccinfo.copy_item", false,
			() -> Permissions.has(Permissions.Perm.UTILS), InputConstants.UNKNOWN, true).in("utils", null);
	public static final Key BADGE_TRACKER = new Key("badge_tracker", "key.mccinfo.badge_tracker", false,
			() -> Permissions.has(Permissions.Perm.UTILS), InputConstants.UNKNOWN, true).in("utils", "screen.mccinfo.settings.section.badges");
	public static final Key FISH_COLLECTION = new Key("fish_collection", "key.mccinfo.fish_collection", false,
			() -> Permissions.has(Permissions.Perm.UTILS), InputConstants.UNKNOWN, true).in("fishing", "screen.mccinfo.settings.section.collection");
	public static final Key MODERATOR_PANEL = new Key("moderator_panel", "key.mccinfo.moderator_panel", false,
			() -> Permissions.isStaff() && Permissions.isRegistered(), InputConstants.UNKNOWN, true);
	public static final List<Key> ALL = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(RECIPE_MODE, COPY_ITEM, FISH_COLLECTION, BADGE_TRACKER, MODERATOR_PANEL));

	private PrivateKeys() {
	}

	public static void register() {
		for (Key key : ALL) {
			load(key);
		}
		ClientTickEvents.END_CLIENT_TICK.register(PrivateKeys::tick);
	}

	// Key added by an optional module
	public static Key add(String id, String translationKey, boolean admin, BooleanSupplier allowed, InputConstants.Key defaultKey) {
		Key key = new Key(id, translationKey, admin, allowed, defaultKey, true);
		load(key);
		ALL.add(key);
		return key;
	}

	private static void load(Key key) {
		String saved = ModConfig.get().privateKeys.get(key.id);
		if (saved != null) {
			int modifiers = 0;
			String name = saved;
			boolean found = true;
			while (found) {
				found = false;
				for (int index = 0; index < MODIFIER_IDS.length; index++) {
					if (name.startsWith(MODIFIER_IDS[index] + "+")) {
						modifiers |= MODIFIER_BITS[index];
						name = name.substring(MODIFIER_IDS[index].length() + 1);
						found = true;
					}
				}
			}
			try {
				key.key = InputConstants.getKey(name);
				key.modifiers = modifiersOf(modifiers, key.key);
			} catch (IllegalArgumentException e) {
				key.key = key.defaultKey;
				key.modifiers = 0;
			}
		}
	}

	public static final int MODIFIER_MASK = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT;
	private static final String[] MODIFIER_IDS = {"ctrl", "shift", "alt"};
	private static final String[] MODIFIER_NAMES = {"Ctrl", "Shift", "Alt"};
	private static final int[] MODIFIER_BITS = {GLFW.GLFW_MOD_CONTROL, GLFW.GLFW_MOD_SHIFT, GLFW.GLFW_MOD_ALT};

	public static boolean isModifierKey(int keyCode) {
		return modifierBit(keyCode) != 0;
	}

	private static int modifierBit(int keyCode) {
		return switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> GLFW.GLFW_MOD_CONTROL;
			case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> GLFW.GLFW_MOD_SHIFT;
			case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> GLFW.GLFW_MOD_ALT;
			default -> 0;
		};
	}

	public static int modifiersOf(int modifiers, InputConstants.Key key) {
		int own = key.getType() == InputConstants.Type.KEYSYM ? modifierBit(key.getValue()) : 0;
		return modifiers & MODIFIER_MASK & ~own;
	}

	public static int heldModifiers(Window window) {
		int held = 0;
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
			held |= GLFW.GLFW_MOD_CONTROL;
		}
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
			held |= GLFW.GLFW_MOD_SHIFT;
		}
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT)) {
			held |= GLFW.GLFW_MOD_ALT;
		}
		return held;
	}

	public static Component modifiersName(int modifiers) {
		MutableComponent name = Component.empty();
		for (int index = 0; index < MODIFIER_BITS.length; index++) {
			if ((modifiers & MODIFIER_BITS[index]) != 0) {
				name.append(MODIFIER_NAMES[index] + " + ");
			}
		}
		return name;
	}

	public static Component comboName(InputConstants.Key key, int modifiers) {
		if (key.equals(InputConstants.UNKNOWN)) {
			return key.getDisplayName();
		}
		return modifiersName(modifiers).copy().append(key.getDisplayName());
	}

	public static String comboId(InputConstants.Key key, int modifiers) {
		StringBuilder id = new StringBuilder();
		for (int index = 0; index < MODIFIER_BITS.length; index++) {
			if ((modifiers & MODIFIER_BITS[index]) != 0) {
				id.append(MODIFIER_IDS[index]).append('+');
			}
		}
		return id.append(key.getName()).toString();
	}

	// A press counts only outside screens
	private static void tick(Minecraft client) {
		Window window = client.getWindow();
		for (Key key : ALL) {
			boolean down = key.pressedExactly(window) || key.wasDown && key.isDown(window);
			if (down && !key.wasDown && client.gui.screen() == null && key.usable()) {
				key.clicks++;
			}
			key.wasDown = down;
		}
	}
}
