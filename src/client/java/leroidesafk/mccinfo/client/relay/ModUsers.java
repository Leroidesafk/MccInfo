package leroidesafk.mccinfo.client.relay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;

public final class ModUsers {
	// Tab icon per role
	private static final Identifier USER = Mccinfo.id("textures/tab/user.png");
	private static final Identifier MOD = Mccinfo.id("textures/tab/mod.png");
	private static final Identifier SUPPORTER = Mccinfo.id("textures/tab/supporter.png");
	private static final int ICON_TEXTURE = 12;
	private static final int ROW_HEIGHT = 8;

	// Lowercase name of each mod user and their icon
	private static Map<String, Identifier> users = Map.of();

	private ModUsers() {
	}

	public static void register() {
		SpotSync.addHandler("mod_users", ModUsers::receive);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> users = Map.of());
	}

	private static void receive(JsonObject object) {
		Map<String, Identifier> received = new HashMap<>();
		if (object.has("users") && object.get("users").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("users")) {
				if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
					JsonObject user = element.getAsJsonObject();
					String badge = user.has("badge") ? user.get("badge").getAsString() : "user";
					received.put(user.get("name").getAsString().toLowerCase(Locale.ROOT),
							"mod".equals(badge) ? MOD : "supporter".equals(badge) ? SUPPORTER : USER);
				}
			}
		}
		users = Map.copyOf(received);
	}

	public static Identifier badge(PlayerInfo info) {
		Map<String, Identifier> current = users;
		if (current.isEmpty()) {
			return null;
		}
		Identifier own = current.get(info.getProfile().name().toLowerCase(Locale.ROOT));
		if (own != null) {
			return own;
		}
		// MCC Island tab: the name is a piece of the decorated line
		Component display = info.getTabListDisplayName();
		return display == null ? null : display.visit((style, text) -> Optional.ofNullable(current.get(text.strip().toLowerCase(Locale.ROOT))), Style.EMPTY)
				.orElse(null);
	}

	public static void extractIcon(GuiGraphicsExtractor graphics, Identifier icon, int right, int y) {
		// Whole screen pixels per texture pixel, on top of the row text
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		int texel = Math.max(1, ROW_HEIGHT * guiScale / ICON_TEXTURE);
		int sizePixels = ICON_TEXTURE * texel;
		int leftPixels = right * guiScale - sizePixels;
		int topPixels = y * guiScale + (ROW_HEIGHT * guiScale - sizePixels) / 2;
		graphics.nextStratum();
		graphics.pose().pushMatrix();
		graphics.pose().translate(leftPixels / (float) guiScale, topPixels / (float) guiScale);
		graphics.pose().scale(texel / (float) guiScale, texel / (float) guiScale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, 0, 0, 0.0F, 0.0F, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE);
		graphics.pose().popMatrix();
	}
}
