package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
	// Chat text field
	@Accessor("input")
	EditBox mccinfo$getInput();

	// How the chat is shown while this screen is open
	@Accessor("displayMode")
	ChatComponent.DisplayMode mccinfo$getDisplayMode();
}
