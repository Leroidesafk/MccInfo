package leroidesafk.mccinfo.client;

import leroidesafk.mccinfo.client.chat.EmojiOverlay;
import leroidesafk.mccinfo.client.chat.ItemClipboard;
import leroidesafk.mccinfo.client.chat.ModChat;
import leroidesafk.mccinfo.client.chat.ModChatOverlay;
import leroidesafk.mccinfo.client.chat.OverlayChatDrag;
import leroidesafk.mccinfo.client.events.CarnivalTicketOverlay;
import leroidesafk.mccinfo.client.events.CarnivalTracker;
import leroidesafk.mccinfo.client.events.EventTimerOverlay;
import leroidesafk.mccinfo.client.fishing.BestIsland;
import leroidesafk.mccinfo.client.fishing.FishCollection;
import leroidesafk.mccinfo.client.fishing.FishOrderTooltipHandler;
import leroidesafk.mccinfo.client.fishing.FishingMessagesOverlay;
import leroidesafk.mccinfo.client.gui.EventsScreen;
import leroidesafk.mccinfo.client.items.InventoryButtons;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.widget.RockPaperScissors;
import leroidesafk.mccinfo.client.patchnotes.PatchNoteToast;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import leroidesafk.mccinfo.client.spots.SpotPath;
import leroidesafk.mccinfo.client.travel.TravelCommand;
import leroidesafk.mccinfo.client.update.ModUpdater;
import leroidesafk.mccinfo.client.update.UpdateToast;
import leroidesafk.mccinfo.client.util.AsyncFiles;
import leroidesafk.mccinfo.client.voice.VoiceChat;
import net.fabricmc.api.ClientModInitializer;

public class MccinfoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Old files first, before any config is read
		LegacyMigration.run();
		// Features
		AsyncFiles.register();
		PrivateKeys.register();
		Permissions.register();
		FishOrderTooltipHandler.register();
		EventTimerOverlay.register();
		CarnivalTracker.register();
		MccServer.register();
		VoiceChat.register();
		ModChat.register();
		ItemClipboard.register();
		SpotPath.register();
		SpotSync.register();
		leroidesafk.mccinfo.client.relay.ModUsers.register();
		ModUpdater.register();
		UpdateToast.register();
		PatchNoteToast.register();
		RockPaperScissors.register();
		leroidesafk.mccinfo.client.widget.Widgets.register();
		TravelCommand.register();
		leroidesafk.mccinfo.client.moderator.ModeratorScreen.register();
		NeuItems.register();
		InventoryButtons.register();
		CarnivalTicketOverlay.register();
		FishingMessagesOverlay.register();
		ModChatOverlay.register();
		EmojiOverlay.register();
		FishCollection.register();
		BestIsland.register();
		EventsScreen.register();
		OverlayChatDrag.register();
		// Optional modules last
		for (ClientAddon addon : java.util.ServiceLoader.load(ClientAddon.class, ClientAddon.class.getClassLoader())) {
			addon.register();
		}
	}
}