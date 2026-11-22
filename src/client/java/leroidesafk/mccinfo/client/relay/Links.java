package leroidesafk.mccinfo.client.relay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

// Links of the menu sent by the relay, https only
public final class Links {
	public static final String TIP = "tip";
	public static final String DISCORD = "discord";
	private static final int MAX_URL = 512;

	private static Map<String, String> links = Map.of();

	private Links() {
	}

	public static String get(String name) {
		return links.get(name);
	}

	public static void receive(JsonObject object) {
		if (!object.has("links") || !object.get("links").isJsonObject()) {
			return;
		}
		Map<String, String> received = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("links").entrySet()) {
			JsonElement value = entry.getValue();
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
				String url = value.getAsString().strip();
				if (url.startsWith("https://") && url.length() <= MAX_URL) {
					received.put(entry.getKey(), url);
				}
			}
		}
		links = Map.copyOf(received);
	}
}
