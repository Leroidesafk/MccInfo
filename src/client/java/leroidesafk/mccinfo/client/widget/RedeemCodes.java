package leroidesafk.mccinfo.client.widget;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import leroidesafk.mccinfo.client.relay.SpotSync;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Redeem codes checked by the relay (nothing to see here :P)
public final class RedeemCodes {
	public static final int MAX_LENGTH = 32;

	public record Result(boolean ok, String reason, List<String> rewards) {
	}

	private static Consumer<Result> listener;

	private RedeemCodes() {
	}

	public static void register() {
		SpotSync.addHandler("redeem_result", object -> Minecraft.getInstance().execute(() -> receive(object)));
	}

	// Letters, digits, - and _ only
	public static boolean allowed(char character) {
		return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' || character >= '0' && character <= '9'
				|| character == '-' || character == '_';
	}

	public static String clean(String text) {
		StringBuilder builder = new StringBuilder();
		for (char character : text.strip().toCharArray()) {
			if (allowed(character) && builder.length() < MAX_LENGTH) {
				builder.append(character);
			}
		}
		return builder.toString();
	}

	// Code sent to the relay, one answer awaited
	public static boolean send(String code, Consumer<Result> onResult) {
		if (!SpotSync.connected()) {
			return false;
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "redeem_code");
		message.addProperty("code", code);
		listener = onResult;
		return SpotSync.sendMessage(message);
	}

	// Result: ok or reason, and the rewards
	private static void receive(JsonObject object) {
		Consumer<Result> current = listener;
		listener = null;
		if (current == null) {
			return;
		}
		boolean ok = object.has("ok") && object.get("ok").isJsonPrimitive() && object.get("ok").getAsBoolean();
		String reason = object.has("reason") && object.get("reason").isJsonPrimitive() ? object.get("reason").getAsString() : "error";
		List<String> rewards = new ArrayList<>();
		if (object.has("rewards") && object.get("rewards").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("rewards")) {
				if (element.isJsonPrimitive()) {
					rewards.add(element.getAsString());
				}
			}
		}
		current.accept(new Result(ok, reason, List.copyOf(rewards)));
	}
}
