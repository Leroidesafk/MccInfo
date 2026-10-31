package leroidesafk.mccinfo.client;

import leroidesafk.mccinfo.client.gui.EventsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ModMenuIntegration implements ModMenuApi {
	// Mod Menu config screen
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return EventsScreen::new;
	}
}
