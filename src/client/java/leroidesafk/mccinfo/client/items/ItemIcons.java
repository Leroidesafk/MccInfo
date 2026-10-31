package leroidesafk.mccinfo.client.items;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.util.AsyncFiles;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ItemIcons {
	// Saved item look: id, model, model data, name
	record Icon(String item, String model, JsonElement customModelData, String name) {
	}

	// icons.json in the mod config folder
	private static final Path SAVE_PATH = ModConfig.file("icons.json");
	private static final Gson GSON = new Gson();

	// Saved icons and built stacks
	private static final Map<String, Icon> ICONS = new HashMap<>();
	private static final Map<String, ItemStack> STACKS = new HashMap<>();
	private static boolean loaded;

	private ItemIcons() {
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(SAVE_PATH)) {
			return;
		}
		try {
			Map<String, Icon> saved = GSON.fromJson(Files.readString(SAVE_PATH), new TypeToken<Map<String, Icon>>() { }.getType());
			if (saved != null) {
				saved.forEach((key, icon) -> {
					if (key != null && icon != null && icon.item() != null) {
						ICONS.put(key, icon);
					}
				});
			}
		} catch (IOException | JsonParseException e) {
			Mccinfo.LOGGER.warn("Unreadable item icons ({})", SAVE_PATH, e);
		}
	}

	public static void save() {
		AsyncFiles.write(SAVE_PATH, GSON.toJson(ICONS));
	}

	static String key(ItemStack stack) {
		return stack.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
	}

	// Save the look of a stack, true if it changed
	public static boolean remember(String key, ItemStack stack) {
		load();
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		Icon icon = new Icon(
				BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
				model == null ? null : model.toString(),
				customModelData == null ? null : CustomModelData.CODEC.encodeStart(JsonOps.INSTANCE, customModelData).result().orElse(null),
				stack.getHoverName().getString().trim());
		if (Objects.equals(ICONS.put(key, icon), icon)) {
			return false;
		}
		STACKS.remove(key);
		return true;
	}

	public static String name(String key) {
		load();
		Icon icon = ICONS.get(key);
		return icon == null ? null : icon.name();
	}

	public static ItemStack stack(String key) {
		load();
		Icon icon = ICONS.get(key);
		if (icon == null) {
			return ItemStack.EMPTY;
		}
		return STACKS.computeIfAbsent(key, k -> build(icon));
	}

	// Rebuild a stack from its saved look
	private static ItemStack build(Icon icon) {
		Identifier itemId = Identifier.tryParse(icon.item());
		if (itemId == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(itemId));
		Identifier model = icon.model() == null ? null : Identifier.tryParse(icon.model());
		if (model != null) {
			stack.set(DataComponents.ITEM_MODEL, model);
		}
		if (icon.customModelData() != null) {
			CustomModelData.CODEC.parse(JsonOps.INSTANCE, icon.customModelData()).result()
					.ifPresent(data -> stack.set(DataComponents.CUSTOM_MODEL_DATA, data));
		}
		return stack;
	}
}
