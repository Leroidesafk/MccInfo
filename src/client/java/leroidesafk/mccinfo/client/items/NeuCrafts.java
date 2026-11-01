package leroidesafk.mccinfo.client.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class NeuCrafts {
	// Tooltip colors
	private static final int HEADER_COLOR = 0x65FFFF;
	private static final int BULLET_COLOR = 0x505050;
	private static final int LABEL_COLOR = 0xA8B0B0;
	private static final int FREE_COLOR = 0x55FF56;
	private static final int VALUE_COLOR = 0xFFFFFF;
	private static final int CRAFT_LABEL_COLOR = 0xFEE761;
	// Unknown items and bundle badge
	private static final Identifier UNKNOWN_MODEL = Mccinfo.id("unknown");
	private static final Identifier MCC_ICON_FONT = Identifier.fromNamespaceAndPath("mcc", "icon");
	private static final String BUNDLE_BADGE = "";

	// Item placed in a fixed slot
	record Fixed(int slot, JsonElement item) {
	}

	// Craft type icon
	record Icon(String item, Identifier texture, Identifier model) {
	}

	// Chest layout of a craft type
	public record Layout(String type, String title, JsonElement titleComponent, int size, List<Integer> slots, List<Fixed> fixed, Fixed previous, Fixed next,
			Icon icon, List<String> npcs) {
	}

	// Price of an item
	record Cost(String item, int count) {
	}

	// Rewards shown as one item
	record Bundle(String id, String name, String icon, String color, List<String> description, List<Reward> items) {
	}

	// Item of a craft: result, reward or item for sale
	record Reward(String item, String name, int count, Double chance, Integer rolls, String amount, List<Cost> cost, List<Cost> alternative, Bundle bundle,
			String time) {
		boolean recipe() {
			return this.time != null && this.cost != null;
		}

		boolean sold() {
			return this.cost != null;
		}

		java.util.stream.Stream<Cost> prices() {
			return java.util.stream.Stream.concat(this.cost.stream(), this.alternative.stream());
		}

		boolean contains(String itemId) {
			return itemId.equals(this.item) || (this.bundle != null && this.bundle.items().stream().anyMatch(member -> itemId.equals(member.item())));
		}
	}

	// Craft: type, result and its items
	record Craft(String id, String type, String result, List<Reward> items, Layout layout) {
	}

	// Layouts by type, crafts, and items that have crafts
	private static final Map<String, Layout> LAYOUTS = new HashMap<>();
	private static final List<Craft> CRAFTS = new ArrayList<>();
	private static Set<String> withCrafts;

	private NeuCrafts() {
	}

	static void clear() {
		NeuNpcs.clear();
		LAYOUTS.clear();
		CRAFTS.clear();
		withCrafts = null;
	}

	// Layouts and crafts from the relay
	static void receive(JsonObject object) {
		withCrafts = null;
		if (object.has("layouts") && object.get("layouts").isJsonObject()) {
			LAYOUTS.clear();
			for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("layouts").entrySet()) {
				if (entry.getValue().isJsonObject()) {
					LAYOUTS.put(entry.getKey(), readLayout(entry.getKey(), entry.getValue().getAsJsonObject()));
				}
			}
		}
		if (object.has("crafts") && object.get("crafts").isJsonArray()) {
			CRAFTS.clear();
			for (JsonElement element : object.getAsJsonArray("crafts")) {
				if (element.isJsonObject()) {
					readCraft(element.getAsJsonObject());
				}
			}
		}
	}

	// Layout of a craft type or a vendor
	private static Layout readLayout(String type, JsonObject object) {
		List<Integer> slots = new ArrayList<>();
		if (object.has("slots") && object.get("slots").isJsonArray()) {
			object.getAsJsonArray("slots").forEach(slot -> slots.add(slot.getAsInt()));
		}
		List<Fixed> fixed = new ArrayList<>();
		if (object.has("fixed") && object.get("fixed").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("fixed")) {
				fixed.add(fixed(element.getAsJsonObject()));
			}
		}
		String title = object.has("title") ? object.get("title").getAsString() : type;
		int size = object.has("size") ? object.get("size").getAsInt() : 54;
		Fixed previous = object.has("previous") && object.get("previous").isJsonObject() ? fixed(object.getAsJsonObject("previous")) : null;
		Fixed next = object.has("next") && object.get("next").isJsonObject() ? fixed(object.getAsJsonObject("next")) : null;
		List<String> npcs = new ArrayList<>();
		if (object.has("npcs") && object.get("npcs").isJsonArray()) {
			object.getAsJsonArray("npcs").forEach(npc -> npcs.add(npc.getAsString()));
		} else if (object.has("npc") && object.get("npc").isJsonPrimitive()) {
			npcs.add(object.get("npc").getAsString());
		}
		return new Layout(type, title, object.get("titleComponent"), size, List.copyOf(slots), List.copyOf(fixed), previous, next, icon(object),
				List.copyOf(npcs));
	}

	private static Icon icon(JsonObject layout) {
		if (!layout.has("icon") || !layout.get("icon").isJsonObject()) {
			return null;
		}
		JsonObject icon = layout.getAsJsonObject("icon");
		if (icon.has("texture")) {
			String texture = icon.get("texture").getAsString();
			Identifier id = Identifier.tryParse(texture.contains(":") ? texture : Mccinfo.MOD_ID + ":textures/gui/recipes/" + texture + ".png");
			return id == null ? null : new Icon(null, id, null);
		}
		if (icon.has("model")) {
			Identifier model = Identifier.tryParse(icon.get("model").getAsString());
			return model == null ? null : new Icon(null, null, model);
		}
		return icon.has("item") ? new Icon(icon.get("item").getAsString(), null, null) : null;
	}

	// Icon as an item
	static ItemStack iconStack(Icon icon) {
		if (icon == null) {
			return null;
		}
		if (icon.item() != null) {
			return NeuItems.stack(icon.item());
		}
		if (icon.model() != null) {
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, icon.model());
			return stack;
		}
		return null;
	}

	private static Fixed fixed(JsonObject entry) {
		return new Fixed(entry.get("slot").getAsInt(), entry.get("item"));
	}

	// One craft and its rewards
	private static void readCraft(JsonObject object) {
		List<Reward> rewards = new ArrayList<>();
		if (object.has("items") && object.get("items").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("items")) {
				JsonObject entry = element.getAsJsonObject();
				List<Cost> cost = costs(entry, "cost");
				List<Cost> alternative = costs(entry, "alternative");
				rewards.add(new Reward(entry.has("item") ? entry.get("item").getAsString() : null,
						entry.has("name") ? entry.get("name").getAsString() : null, entry.has("count") ? entry.get("count").getAsInt() : 1,
						entry.has("chance") ? entry.get("chance").getAsDouble() : null, entry.has("rolls") ? entry.get("rolls").getAsInt() : null,
						entry.has("amount") ? entry.get("amount").getAsString() : null, cost, alternative == null ? List.of() : alternative,
						entry.has("bundle") && entry.get("bundle").isJsonObject() ? bundle(entry.getAsJsonObject("bundle")) : null,
						entry.has("time") && entry.get("time").isJsonPrimitive() ? entry.get("time").getAsString() : null));
			}
		}
		String result = object.has("result") && !object.get("result").isJsonNull() ? object.get("result").getAsString() : null;
		String type = object.get("type").getAsString();
		Layout own = object.has("layout") && object.get("layout").isJsonObject() ? readLayout(type, object.getAsJsonObject("layout")) : null;
		CRAFTS.add(new Craft(object.get("id").getAsString(), type, result, List.copyOf(rewards), own));
	}

	private static List<Cost> costs(JsonObject entry, String key) {
		if (!entry.has(key) || !entry.get(key).isJsonArray()) {
			return null;
		}
		List<Cost> costs = new ArrayList<>();
		for (JsonElement priceElement : entry.getAsJsonArray(key)) {
			JsonObject price = priceElement.getAsJsonObject();
			costs.add(new Cost(price.get("item").getAsString(), price.has("count") ? price.get("count").getAsInt() : 1));
		}
		return List.copyOf(costs);
	}

	private static Bundle bundle(JsonObject object) {
		List<Reward> members = new ArrayList<>();
		if (object.has("items") && object.get("items").isJsonArray()) {
			for (JsonElement element : object.getAsJsonArray("items")) {
				JsonObject member = element.getAsJsonObject();
				if (member.has("item")) {
					members.add(new Reward(member.get("item").getAsString(), null, member.has("count") ? member.get("count").getAsInt() : 1,
							null, null, null, null, List.of(), null, null));
				}
			}
		}
		List<String> description = new ArrayList<>();
		if (object.has("description") && object.get("description").isJsonArray()) {
			object.getAsJsonArray("description").forEach(line -> description.add(line.getAsString()));
		}
		String id = object.has("id") ? object.get("id").getAsString() : "?";
		return new Bundle(id, object.has("name") ? object.get("name").getAsString() : id, object.has("icon") ? object.get("icon").getAsString() : null,
				object.has("color") ? object.get("color").getAsString() : null, List.copyOf(description), List.copyOf(members));
	}

	// Bundle item listing its members
	private static ItemStack bundleStack(Bundle bundle) {
		ItemStack icon = bundle.icon() == null ? ItemStack.EMPTY : NeuItems.stack(bundle.icon());
		ItemStack stack = icon.isEmpty() ? unknown(bundle.name()) : icon.copy();
		TextColor iconColor = stack.getHoverName().getStyle().getColor();
		TextColor color = bundle.color() == null ? null : TextColor.parseColor(bundle.color()).result().orElse(null);
		TextColor nameColor = color != null ? color : iconColor != null ? iconColor : TextColor.fromRgb(VALUE_COLOR);
		stack.remove(DataComponents.CUSTOM_NAME);
		stack.set(DataComponents.ITEM_NAME, Component.literal(bundle.name()).withStyle(style -> style.withColor(nameColor).withItalic(false)));
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(BUNDLE_BADGE).withStyle(style -> style.withColor(VALUE_COLOR).withItalic(false).withFont(new FontDescription.Resource(MCC_ICON_FONT))));
		lines.add(Component.empty());
		for (String line : bundle.description()) {
			lines.add(Component.literal(line).withStyle(style -> style.withColor(LABEL_COLOR).withItalic(false)));
		}
		if (!lines.isEmpty()) {
			lines.add(Component.empty());
		}
		lines.add(Component.literal("Bundle includes:").withStyle(style -> style.withColor(LABEL_COLOR).withItalic(false)));
		for (Reward member : bundle.items()) {
			ItemStack item = NeuItems.stack(member.item());
			Component name = item.isEmpty() ? Component.literal(member.item()) : item.getHoverName();
			TextColor memberColor = name.getStyle().getColor();
			lines.add(Component.empty().withStyle(style -> style.withColor(VALUE_COLOR).withItalic(false))
					.append(Component.literal(" ▪").withStyle(style -> style.withColor(LABEL_COLOR)))
					.append(" " + member.count() + " ")
					.append(Component.literal("[" + name.getString() + "]").withStyle(style -> style.withColor(memberColor != null ? memberColor : TextColor.fromRgb(VALUE_COLOR)))));
		}
		stack.set(DataComponents.LORE, new ItemLore(lines));
		return stack;
	}

	// Items in at least one craft, computed once
	static boolean hasCrafts(String itemId) {
		if (withCrafts == null) {
			Set<String> ids = new HashSet<>();
			for (Craft craft : CRAFTS) {
				if (layout(craft) != null) {
					if (craft.result() != null) {
						ids.add(craft.result());
					}
					craft.items().stream().map(Reward::item).filter(Objects::nonNull).forEach(ids::add);
					craft.items().stream().filter(reward -> reward.bundle() != null).flatMap(reward -> reward.bundle().items().stream())
							.map(Reward::item).forEach(ids::add);
					craft.items().stream().filter(Reward::sold).flatMap(Reward::prices).map(Cost::item).forEach(ids::add);
				}
			}
			withCrafts = ids;
		}
		return withCrafts.contains(itemId);
	}

	public static Layout layoutForType(String type) {
		return LAYOUTS.get(type);
	}

	// Recipes (obtain) or uses of an item
	static void open(Screen parent, String itemId, boolean obtain) {
		if (parent instanceof AbstractContainerScreen<?> container && !container.getMenu().getCarried().isEmpty()) {
			return;
		}
		List<String> variants = NeuItems.variants(itemId);
		List<Craft> crafts = new ArrayList<>();
		for (String variant : variants) {
			CRAFTS.stream().filter(craft -> layout(craft) != null && !crafts.contains(craft))
					.filter(craft -> obtain ? obtainedFrom(craft, variant) : givenBy(craft, variant))
					.forEach(crafts::add);
		}
		if (!crafts.isEmpty()) {
			NeuItems.clickSound();
			Minecraft.getInstance().gui.setScreen(CraftScreen.create(List.copyOf(crafts), 0, parent, obtain ? variants : List.of()));
		}
	}

	private static boolean obtainedFrom(Craft craft, String itemId) {
		return craft.items().stream().anyMatch(reward -> reward.contains(itemId));
	}

	private static boolean givenBy(Craft craft, String itemId) {
		return itemId.equals(craft.result())
				|| craft.items().stream().filter(Reward::sold).flatMap(Reward::prices).anyMatch(cost -> itemId.equals(cost.item()));
	}

	static Layout layout(Craft craft) {
		return craft.layout() != null ? craft.layout() : LAYOUTS.get(craft.type());
	}

	// Menu title
	static Component title(Layout layout) {
		RegistryOps<JsonElement> ops = ops();
		if (ops != null && layout.titleComponent() != null) {
			Component title = ComponentSerialization.CODEC.parse(ops, layout.titleComponent()).result().orElse(null);
			if (title != null) {
				return title;
			}
		}
		return Component.literal(layout.title());
	}

	static ItemStack decode(JsonElement json) {
		RegistryOps<JsonElement> ops = ops();
		if (ops == null || json == null) {
			return ItemStack.EMPTY;
		}
		return ItemStack.CODEC.parse(ops, json).resultOrPartial(error -> Mccinfo.LOGGER.warn("Unreadable NEU craft item: {}", error))
				.orElse(ItemStack.EMPTY);
	}

	// Reward with its recipe, cost or loot info
	static ItemStack rewardStack(Reward reward) {
		ItemStack base = reward.bundle() != null ? bundleStack(reward.bundle()) : reward.item() == null ? ItemStack.EMPTY : NeuItems.stack(reward.item());
		if (base.isEmpty()) {
			base = unknown(reward.name() != null ? reward.name() : reward.item());
		}
		ItemStack stack = base.copyWithCount(Math.max(1, reward.count()));
		List<Component> lines = new ArrayList<>();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			lines.addAll(lore.lines());
		}
		List<Component> info = new ArrayList<>();
		if (reward.chance() != null) {
			info.add(infoLine("Chance: ", BigDecimal.valueOf(reward.chance()).stripTrailingZeros().toPlainString() + "%"));
		}
		if (reward.rolls() != null) {
			info.add(infoLine("Rolls: ", String.valueOf(reward.rolls())));
		}
		if (reward.amount() != null) {
			info.add(infoLine("Amount: ", reward.amount()));
		}
		if (reward.recipe()) {
			if (!lines.isEmpty()) {
				lines.add(Component.empty());
			}
			lines.addAll(recipeLines(reward));
		} else if (reward.sold()) {
			if (!lines.isEmpty()) {
				lines.add(Component.empty());
			}
			lines.addAll(reward.cost().isEmpty() ? List.of(freeLine()) : costLines("Cost:", reward.cost()));
			if (!reward.alternative().isEmpty()) {
				lines.add(Component.empty());
				lines.addAll(costLines("Alternative Cost:", reward.alternative()));
			}
		}
		if (!info.isEmpty()) {
			if (!lines.isEmpty()) {
				lines.add(Component.empty());
			}
			lines.add(Component.literal("Loot Preview Info:").withStyle(style -> style.withColor(HEADER_COLOR).withItalic(false)));
			lines.addAll(info);
		}
		stack.set(DataComponents.LORE, new ItemLore(lines));
		return stack;
	}

	// Recipe and cost lines
	private static Component timeLine(String time) {
		return Component.empty().withStyle(style -> style.withColor(VALUE_COLOR).withItalic(false))
				.append(Component.literal("Crafting Time:").withStyle(style -> style.withColor(CRAFT_LABEL_COLOR))).append(" " + time);
	}

	private static List<Component> recipeLines(Reward reward) {
		List<Component> lines = new ArrayList<>();
		lines.add(timeLine(reward.time()));
		lines.add(Component.literal("Crafting Materials:").withStyle(style -> style.withColor(CRAFT_LABEL_COLOR).withItalic(false)));
		for (Cost cost : reward.cost()) {
			lines.add(Component.empty().withStyle(style -> style.withColor(VALUE_COLOR).withItalic(false))
					.append(Component.literal("▪ ").withStyle(style -> style.withColor(LABEL_COLOR))).append(price(cost)));
		}
		return lines;
	}

	private static Component freeLine() {
		return Component.empty().withStyle(style -> style.withItalic(false))
				.append(Component.literal("Cost:").withStyle(style -> style.withColor(LABEL_COLOR).withItalic(false)))
				.append(" ").append(Component.literal("Free!").withStyle(style -> style.withColor(FREE_COLOR).withItalic(false)));
	}

	private static List<Component> costLines(String title, List<Cost> costs) {
		Component label = Component.literal(title).withStyle(style -> style.withColor(LABEL_COLOR).withItalic(false));
		if (costs.size() == 1) {
			return List.of(Component.empty().withStyle(style -> style.withColor(VALUE_COLOR).withItalic(false)).append(label).append(" ").append(price(costs.getFirst())));
		}
		List<Component> lines = new ArrayList<>();
		lines.add(label);
		for (Cost cost : costs) {
			lines.add(Component.empty().withStyle(style -> style.withColor(VALUE_COLOR).withItalic(false))
					.append(Component.literal(" ▪ ").withStyle(style -> style.withColor(BULLET_COLOR))).append(price(cost)));
		}
		return lines;
	}

	private static Component price(Cost cost) {
		MutableComponent amount = Component.empty().withStyle(style -> style.withColor(VALUE_COLOR)).append(String.format(Locale.US, "%,d ", cost.count()));
		String icon = NeuItems.costIcon(cost.item());
		if (icon != null) {
			return amount.append(Component.literal(icon).withStyle(style -> style.withColor(VALUE_COLOR).withFont(new FontDescription.Resource(MCC_ICON_FONT))));
		}
		ItemStack stack = NeuItems.stack(cost.item());
		Component name = stack.isEmpty() ? Component.literal(cost.item()) : stack.getHoverName();
		TextColor color = name.getStyle().getColor();
		return amount
				.append(Component.literal("[" + name.getString() + "]").withStyle(style -> style.withColor(color != null ? color : TextColor.fromRgb(VALUE_COLOR))));
	}

	// Placeholder for an item the mod does not have
	private static ItemStack unknown(String name) {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, UNKNOWN_MODEL);
		stack.set(DataComponents.ITEM_NAME, Component.literal(name == null ? "?" : name).withStyle(style -> style.withColor(VALUE_COLOR).withItalic(false)));
		return stack;
	}

	private static Component infoLine(String label, String value) {
		MutableComponent line = Component.empty().withStyle(style -> style.withItalic(false));
		line.append(Component.literal(" ▪ ").withStyle(style -> style.withColor(BULLET_COLOR)));
		line.append(" ");
		line.append(Component.literal(label).withStyle(style -> style.withColor(LABEL_COLOR)));
		line.append(Component.literal(value).withStyle(style -> style.withColor(VALUE_COLOR)));
		return line;
	}

	private static RegistryOps<JsonElement> ops() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		return connection == null ? null : RegistryOps.create(JsonOps.INSTANCE, connection.registryAccess());
	}
}
