package leroidesafk.mccinfo.client;

import leroidesafk.mccinfo.client.relay.Permissions;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
		private InputConstants.Key key;
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

		public boolean consumeClick() {
			if (this.clicks > 0) {
				this.clicks--;
				return true;
			}
			return false;
		}

		public boolean matches(KeyEvent event) {
			return this.key.getType() == InputConstants.Type.KEYSYM && this.key.getValue() == event.key() && event.key() != InputConstants.UNKNOWN.getValue();
		}

		public boolean matchesMouse(MouseButtonEvent event) {
			return this.key.getType() == InputConstants.Type.MOUSE && this.key.getValue() == event.button();
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
			this.key = key;
			ModConfig config = ModConfig.get();
			config.privateKeys.put(this.id, key.getName());
			config.save();
		}

		private boolean isDown(Window window) {
			return switch (this.key.getType()) {
				case KEYSYM -> this.key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, this.key.getValue());
				case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), this.key.getValue()) == GLFW.GLFW_PRESS;
				default -> false;
			};
		}
	}

	// Keys of the mod
	public static final Key RECIPE_MODE = new Key("recipe_mode", "key.mccinfo.recipe_mode", false,
			() -> Permissions.has(Permissions.Perm.SEE_ITEMS), InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R), true);
	public static final Key COPY_ITEM = new Key("copy_item", "key.mccinfo.copy_item", false,
			() -> Permissions.has(Permissions.Perm.UTILS), InputConstants.UNKNOWN, true);
	public static final Key MODERATOR_PANEL = new Key("moderator_panel", "key.mccinfo.moderator_panel", false,
			() -> Permissions.isStaff() && Permissions.isRegistered(), InputConstants.UNKNOWN, true);
	public static final List<Key> ALL = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(RECIPE_MODE, COPY_ITEM, MODERATOR_PANEL));

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
			try {
				key.key = InputConstants.getKey(saved);
			} catch (IllegalArgumentException e) {
				key.key = key.defaultKey;
			}
		}
	}

	// A press counts only outside screens
	private static void tick(Minecraft client) {
		Window window = client.getWindow();
		for (Key key : ALL) {
			boolean down = key.isDown(window);
			if (down && !key.wasDown && client.gui.screen() == null && key.usable()) {
				key.clicks++;
			}
			key.wasDown = down;
		}
	}
}
