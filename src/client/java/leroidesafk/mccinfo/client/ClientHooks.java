package leroidesafk.mccinfo.client;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

// Hooks for optional modules
public final class ClientHooks {
	public static final int NO_GLOW = -1;
	private static final List<ToIntFunction<Entity>> GLOWS = new CopyOnWriteArrayList<>();
	private static final List<BiPredicate<ChatScreen, MouseButtonEvent>> CHAT_CLICKS = new CopyOnWriteArrayList<>();

	private ClientHooks() {
	}

	// Entity glow colors
	public static void addGlow(ToIntFunction<Entity> glow) {
		GLOWS.add(glow);
	}

	public static int glowColor(Entity entity) {
		for (ToIntFunction<Entity> glow : GLOWS) {
			int color = glow.applyAsInt(entity);
			if (color != NO_GLOW) {
				return color;
			}
		}
		return NO_GLOW;
	}

	// Chat clicks
	public static void addChatClick(BiPredicate<ChatScreen, MouseButtonEvent> click) {
		CHAT_CLICKS.add(click);
	}

	public static boolean chatClicked(ChatScreen screen, MouseButtonEvent event) {
		for (BiPredicate<ChatScreen, MouseButtonEvent> click : CHAT_CLICKS) {
			if (click.test(screen, event)) {
				return true;
			}
		}
		return false;
	}
}
