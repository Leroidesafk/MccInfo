package leroidesafk.mccinfo.client.update;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

// Optional module that replaces the Download button
public interface UpdateAction {
	boolean offer(String version, JsonObject perms);

	boolean busy();

	void reset();

	Component status();

	int statusColor();

	Component button();

	boolean enabled();

	void click();
}
