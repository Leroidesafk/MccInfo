package leroidesafk.mccinfo.client.relay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;

public final class ModUsers {
	// Tab row and hidden badge color
	private static final int ROW_HEIGHT = 8;
	private static int hiddenColor = 0xFFFF4040;

	public record Badge(String badge, boolean hidden) {
	}

	public record Icon(Identifier texture, int width, int height) {
	}

	// Lowercase name of each mod user, icon of each badge
	private static Map<String, Badge> users = Map.of();
	private static Map<String, Icon> icons = Map.of();

	private ModUsers() {
	}

	// Hidden badges drawn only once a color is given
	public static void setHiddenBadgeColor(int argb) {
		hiddenColor = argb;
	}

	public static void register() {
		SpotSync.addHandler("mod_users", ModUsers::receive);
		SpotSync.addHandler("tab_icons", object -> Minecraft.getInstance().execute(() -> receiveIcons(object)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> users = Map.of());
	}

	private static void receive(JsonObject object) {
		Map<String, Badge> received = new HashMap<>();
		if (object.has("users") && object.get("users").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("users")) {
				if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
					JsonObject user = element.getAsJsonObject();
					String badge = user.has("badge") ? user.get("badge").getAsString() : "user";
					boolean hidden = user.has("hidden") && user.get("hidden").getAsBoolean();
					received.put(user.get("name").getAsString().toLowerCase(Locale.ROOT), new Badge(badge, hidden));
				}
			}
		}
		users = Map.copyOf(received);
	}

	// Icons sent by the relay
	private static void receiveIcons(JsonObject object) {
		Map<String, Icon> loaded = new HashMap<>();
		JsonObject list = object.has("icons") && object.get("icons").isJsonObject() ? object.getAsJsonObject("icons") : new JsonObject();
		for (Map.Entry<String, JsonElement> entry : list.entrySet()) {
			String name = entry.getKey();
			if (!name.matches("[a-z0-9_]{1,32}") || !entry.getValue().isJsonPrimitive()) {
				continue;
			}
			try {
				NativeImage image = NativeImage.read(Base64.getDecoder().decode(entry.getValue().getAsString()));
				Identifier texture = Mccinfo.id("tab_icon/" + name);
				Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "Tab icon " + name, image));
				loaded.put(name, new Icon(texture, image.getWidth(), image.getHeight()));
			} catch (IOException | RuntimeException e) {
				Mccinfo.LOGGER.warn("Unreadable tab icon {}", name, e);
			}
		}
		for (Map.Entry<String, Icon> old : icons.entrySet()) {
			if (!loaded.containsKey(old.getKey())) {
				Minecraft.getInstance().getTextureManager().release(old.getValue().texture());
			}
		}
		icons = Map.copyOf(loaded);
	}

	// Badge of a tab player
	public static boolean seesBadges() {
		return ModConfig.get().seeModBadges;
	}

	public static Badge badge(PlayerInfo info) {
		Map<String, Badge> current = users;
		if (current.isEmpty() || !seesBadges()) {
			return null;
		}
		Badge own = current.get(info.getProfile().name().toLowerCase(Locale.ROOT));
		if (own != null) {
			return shown(own) ? own : null;
		}
		// MCC Island tab: the name is a piece of the decorated line
		Component display = info.getTabListDisplayName();
		Badge found = display == null ? null : display.visit((style, text) -> Optional.ofNullable(current.get(text.strip().toLowerCase(Locale.ROOT))), Style.EMPTY)
				.orElse(null);
		return found != null && shown(found) ? found : null;
	}

	// Badge icon of a player, user by default
	public static Icon playerIcon(String name) {
		Badge badge = name == null ? null : users.get(name.toLowerCase(Locale.ROOT));
		Icon icon = badge == null ? null : icons.get(badge.badge());
		return icon != null ? icon : icons.get("user");
	}

	private static boolean shown(Badge badge) {
		return icons.containsKey(badge.badge()) && (!badge.hidden() || hiddenColor != 0);
	}

	public static int extractUserIcon(GuiGraphicsExtractor graphics, int left, int y) {
		Icon icon = icons.get("user");
		if (icon == null) {
			return 0;
		}
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		org.joml.Matrix3x2f pose = new org.joml.Matrix3x2f(graphics.pose());
		float localScale = (float) Math.hypot(pose.m00(), pose.m01());
		float pixelsPerUnit = guiScale * localScale;
		int texel = 2 * Math.max(1, Math.round(ROW_HEIGHT * pixelsPerUnit / icon.height()));
		int widthPixels = icon.width() * texel;
		int heightPixels = icon.height() * texel;
		org.joml.Vector2f corner = pose.transformPosition(new org.joml.Vector2f(left, y));
		int leftPixels = Math.round(corner.x * guiScale);
		int topPixels = Math.round(corner.y * guiScale + (ROW_HEIGHT * pixelsPerUnit - heightPixels) / 2.0F);
		graphics.pose().pushMatrix();
		graphics.pose().identity();
		graphics.pose().translate(leftPixels / (float) guiScale, topPixels / (float) guiScale);
		graphics.pose().scale(texel / (float) guiScale, texel / (float) guiScale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), 0, 0, 0.0F, 0.0F, icon.width(), icon.height(), icon.width(), icon.height(), 0xFFFFFFFF);
		graphics.pose().popMatrix();
		return Math.round(widthPixels / pixelsPerUnit);
	}

	public static void extractIcon(GuiGraphicsExtractor graphics, Badge badge, int right, int y) {
		Icon icon = icons.get(badge.badge());
		if (icon == null) {
			return;
		}
		// Whole screen pixels per texture pixel, on top of the row text
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		int texel = Math.max(1, ROW_HEIGHT * guiScale / icon.height());
		int widthPixels = icon.width() * texel;
		int heightPixels = icon.height() * texel;
		int leftPixels = right * guiScale - widthPixels;
		int topPixels = y * guiScale + (ROW_HEIGHT * guiScale - heightPixels) / 2;
		graphics.nextStratum();
		graphics.pose().pushMatrix();
		graphics.pose().translate(leftPixels / (float) guiScale, topPixels / (float) guiScale);
		graphics.pose().scale(texel / (float) guiScale, texel / (float) guiScale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), 0, 0, 0.0F, 0.0F, icon.width(), icon.height(), icon.width(), icon.height(),
				badge.hidden() ? hiddenColor : 0xFFFFFFFF);
		graphics.pose().popMatrix();
	}
}
