package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Tax box under the price anvil of the Island Exchange
public final class ExchangeTax {
	// 3% tax, Exchange menus before the anvil, box place
	private static final long KEEP_PERCENT = 97;
	private static final long CONTEXT_MILLIS = 120_000L;
	private static final int TITLE_SLOT = 8;
	private static final String[] CONTEXT_WORDS = {"listing", "island exchange"};
	private static final int BOX_X = 0;
	private static final int BOX_Y = 124;
	private static final int BOX_WIDTH = 176;
	private static final int BOX_HEIGHT = 16;
	private static final int MAX_LENGTH = 16;
	// Typed amount (k, m, b) and anvil price
	private static final Pattern NUMBER = Pattern.compile("^([\\d,]*\\.?\\d*)\\s*([kmb]?)$");
	private static final Pattern PRICE = Pattern.compile("^[\\d,]+$");

	// Last Exchange menu seen, box of the open anvil
	private static long lastContext;
	private static Screen activeScreen;
	private static EditBox activeBox;

	private ExchangeTax() {
	}

	// Anvil opened soon after an Exchange menu
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ExchangeTax::tick);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AnvilScreen anvil && allowed() && System.currentTimeMillis() - lastContext <= CONTEXT_MILLIS) {
				attach(client, anvil);
			}
		});
	}

	private static boolean allowed() {
		return ModConfig.get().exchangeTaxCalculator && Permissions.has(Permissions.Perm.UTILS) && OverlayStyle.onMccIsland();
	}

	private static void tick(Minecraft client) {
		if (client.gui.screen() instanceof ContainerScreen screen && exchangeContext(screen)) {
			lastContext = System.currentTimeMillis();
		}
	}

	private static boolean exchangeContext(ContainerScreen screen) {
		if (matches(screen.getTitle().getString())) {
			return true;
		}
		Container container = screen.getMenu().getContainer();
		return container.getContainerSize() > TITLE_SLOT && matches(container.getItem(TITLE_SLOT).getHoverName().getString());
	}

	private static boolean matches(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (String word : CONTEXT_WORDS) {
			if (lower.contains(word)) {
				return true;
			}
		}
		return false;
	}

	// Box under the anvil, synced both ways
	private static void attach(Minecraft client, AnvilScreen screen) {
		EditBox price = priceBox(screen);
		if (price == null) {
			return;
		}
		int left = (screen.width - 176) / 2;
		int top = (screen.height - 166) / 2;
		EditBox net = new EditBox(client.font, left + BOX_X, top + BOX_Y, BOX_WIDTH, BOX_HEIGHT, Component.translatable("screen.mccinfo.exchange_tax.hint"));
		net.setMaxLength(MAX_LENGTH);
		net.setHint(Component.translatable("screen.mccinfo.exchange_tax.hint").withStyle(ChatFormatting.DARK_GRAY));
		net.setTooltip(Tooltip.create(Component.translatable("screen.mccinfo.exchange_tax.tooltip")));
		String[] lastPrice = {null};
		String[] lastNet = {""};
		boolean[] updating = {false};
		// Amount typed: price written in the anvil
		net.setResponder(value -> {
			if (updating[0]) {
				return;
			}
			if (!NUMBER.matcher(value.strip().toLowerCase(Locale.ROOT)).matches()) {
				updating[0] = true;
				net.setValue(lastNet[0]);
				updating[0] = false;
				return;
			}
			lastNet[0] = value;
			Long wanted = parse(value);
			if (wanted == null) {
				return;
			}
			String listing = String.valueOf(priceFor(wanted));
			updating[0] = true;
			price.setValue(listing);
			lastPrice[0] = listing;
			updating[0] = false;
		});
		Screens.getWidgets(screen).add(net);
		activeScreen = screen;
		activeBox = net;
		ScreenEvents.remove(screen).register(current -> {
			if (activeScreen == current) {
				activeScreen = null;
				activeBox = null;
			}
		});
		// Price changed in the anvil: amount received
		Runnable sync = () -> {
			String value = price.getValue();
			if (value.equals(lastPrice[0]) || net.isFocused()) {
				return;
			}
			lastPrice[0] = value;
			Long listed = PRICE.matcher(value.strip()).matches() ? parse(value) : null;
			updating[0] = true;
			lastNet[0] = listed == null ? "" : String.valueOf(received(listed));
			net.setValue(lastNet[0]);
			updating[0] = false;
		};
		sync.run();
		ScreenEvents.afterTick(screen).register(current -> sync.run());
		ScreenMouseEvents.allowMouseClick(screen).register((current, event) -> {
			if (net.isFocused() && !net.isMouseOver(event.x(), event.y())) {
				unfocus(current, net);
			}
			return true;
		});
		// Keys go to the box, Enter or Tab back to the price
		ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> {
			if (!net.isFocused() || event.key() == InputConstants.KEY_ESCAPE) {
				return true;
			}
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_TAB) {
				unfocus(current, net);
				current.setFocused(price);
				return false;
			}
			net.keyPressed(event);
			return false;
		});
	}

	// Mouse over the box, no slot under it
	public static boolean covers(Object screen, double x, double y) {
		return screen == activeScreen && activeBox != null && activeBox.visible && activeBox.isMouseOver(x, y);
	}

	private static void unfocus(Screen screen, EditBox box) {
		box.setFocused(false);
		if (screen.getFocused() == box) {
			screen.setFocused(null);
		}
	}

	// Price field of the anvil
	private static EditBox priceBox(Screen screen) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof EditBox box) {
				return box;
			}
		}
		return null;
	}

	// Amount after tax, and the price that gives an amount
	static long received(long price) {
		return price * KEEP_PERCENT / 100;
	}

	static long priceFor(long wanted) {
		return (wanted * 100 + KEEP_PERCENT - 1) / KEEP_PERCENT;
	}

	private static Long parse(String value) {
		Matcher matcher = NUMBER.matcher(value.strip().toLowerCase(Locale.ROOT));
		if (!matcher.matches() || matcher.group(1).replace(",", "").isEmpty() || matcher.group(1).replace(",", "").equals(".")) {
			return null;
		}
		double multiplier = switch (matcher.group(2)) {
			case "k" -> 1e3;
			case "m" -> 1e6;
			case "b" -> 1e9;
			default -> 1;
		};
		try {
			double number = Double.parseDouble(matcher.group(1).replace(",", "")) * multiplier;
			return number >= 0 && number < 1e15 ? Math.round(number) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
