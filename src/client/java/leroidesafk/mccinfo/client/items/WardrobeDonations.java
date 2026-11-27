package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.chat.MccApi;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;
import java.util.Map;

// Royal Donations icons in the Wardrobe
public final class WardrobeDonations {
	private static final String WARDROBE = "wardrobe";
	private static final int TITLE_SLOT = 8;
	private static final long REFRESH_MILLIS = 60_000L;

	private static Map<String, MccApi.Donation> donations = Map.of();
	private static long fetchedAt;
	private static boolean pending;

	private WardrobeDonations() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ContainerScreen) {
				ScreenEvents.afterTick(screen).register(current -> {
					if (enabled() && inWardrobe((ContainerScreen) current)) {
						refresh();
					}
				});
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			donations = Map.of();
			fetchedAt = 0L;
		});
	}

	static boolean enabled() {
		return ModConfig.get().wardrobeDonationIcon && Permissions.has(Permissions.Perm.UTILS) && MccApi.ready();
	}

	// Donations from the API, at most once a minute
	private static void refresh() {
		long now = System.currentTimeMillis();
		if (pending || fetchedAt != 0L && now - fetchedAt < REFRESH_MILLIS) {
			return;
		}
		pending = true;
		fetchedAt = now;
		MccApi.donations().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			pending = false;
			if (result != null) {
				donations = result;
			}
		}));
	}

	private static boolean inWardrobe(ContainerScreen screen) {
		Container container = screen.getMenu().getContainer();
		return container.getContainerSize() > TITLE_SLOT
				&& container.getItem(TITLE_SLOT).getHoverName().getString().strip().toLowerCase(Locale.ROOT).equals(WARDROBE);
	}

	// Donation of the cosmetic on a slot
	static MccApi.Donation donation(Slot slot) {
		if (!enabled() || donations.isEmpty() || !slot.hasItem()
				|| !(Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen) || slot.container != screen.getMenu().getContainer() || !inWardrobe(screen)) {
			return null;
		}
		return donations.get(slot.getItem().getHoverName().getString().strip().toLowerCase(Locale.ROOT));
	}
}
