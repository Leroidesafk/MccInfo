package leroidesafk.mccinfo.client.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.PrivateKeys;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import com.mojang.blaze3d.platform.InputConstants;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.client.mixin.accessor.ContainerHoveredSlotAccessor;
import leroidesafk.mccinfo.client.mixin.accessor.LivingEntityEquipmentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Player preview wearing the hovered or locked cosmetics
public final class CosmeticPreview {
	// Player size and place
	private static final float SIZE_DIVIDER = 2.55F;
	private static final float SHIFT_Y_RATIO = 0.05F;
	private static final int SHIFT_X = 28;
	private static final int DOLL_HALF_WIDTH = 59;
	private static final int DOLL_MAX_HEIGHT = 200;
	private static final int DOLL_MIN_HEIGHT = 120;
	private static final int MIN_WIDTH = 58;
	private static final int SECTION_GAP = 4;
	// Worn cosmetic slots
	private static final int SLOT_PITCH = 20;
	private static final int MAX_SLOTS = 4;
	// Rotation with the mouse
	private static final float DEFAULT_YAW = 150.0F;
	private static final float DEFAULT_PITCH = -5.0F;
	private static final float YAW_SPEED = 2.0F;
	private static final float PITCH_SPEED = 1.0F;
	private static final float MAX_PITCH = 90.0F;
	private static final float FULL_TURN = 360.0F;
	// Back cosmetic on the player
	private static final float BACK_OFFSET = -2.1F;
	private static final float BACK_SCALE = 0.625F;
	// Color panel
	private static final Identifier PANEL = Mccinfo.id("textures/gui/ui/colors.png");
	private static final int PANEL_WIDTH = 146;
	private static final int PANEL_HEIGHT = 70;
	private static final int COLOR_COLUMNS = 6;
	private static final int COLOR_ROWS = 3;
	private static final int GROUP_SIZE = 3;
	private static final int GROUP_SLOTS = GROUP_SIZE * COLOR_ROWS;
	private static final int COLORS_PER_PAGE = COLOR_COLUMNS * COLOR_ROWS;
	private static final int COLOR_LEFT = 19;
	private static final int COLOR_TOP = 8;
	private static final int COLOR_CELL = 18;
	private static final int ARROW_SIZE = 16;
	private static final int PREVIOUS_LEFT = 2;
	private static final int NEXT_LEFT = 128;
	private static final int ARROW_TOP = 27;
	private static final int COLOR_HOVER = 0x40FFFFFF;
	private static final Identifier SELECTED_FRAME = Mccinfo.id("textures/gui/recipes/selected.png");
	private static final int SELECTED_FRAME_SIZE = 20;
	private static final Identifier COLOR_MODEL = Identifier.fromNamespaceAndPath("mcc", "island_interface/misc/color");
	private static final Identifier PREVIOUS_MODEL = Identifier.fromNamespaceAndPath("mcc", "island_interface/generic/scroll/left");
	private static final Identifier NEXT_MODEL = Identifier.fromNamespaceAndPath("mcc", "island_interface/generic/scroll/right");
	// Lock line of the tooltips
	private static final int CLICK_DARK = 0x505050;
	private static final int CLICK_TEXT = 0xECD584;
	private static final int CLICK_ACTION = 0xFEE761;
	private static final FontDescription ICON_FONT = new FontDescription.Resource(Mccinfo.id("icons"));
	private static final String MIDDLE_CLICK_ICON = "";
	private static final int ICON_COLOR = 0xFFFFFF;
	private static final String CHROMA_SET_PREFIX = "chroma_set/";
	private static final String ACTION_MARK = "> ";
	private static final Map<EntityRenderState, ItemStackRenderState> BACKS = Collections.synchronizedMap(new WeakHashMap<>());

	// Cosmetic slots of the player
	public enum Type {
		HAT(EquipmentSlot.HEAD, true),
		ACCESSORY(EquipmentSlot.OFFHAND, true),
		BACK(null, true),
		HAND(EquipmentSlot.MAINHAND, false);

		private final EquipmentSlot slot;
		private final boolean tintable;

		Type(EquipmentSlot slot, boolean tintable) {
			this.slot = slot;
			this.tintable = tintable;
		}
	}

	// Places of the player, slots and color panel
	public record Layout(int dollLeft, int dollTop, int dollRight, int dollBottom, int slotsTop, int panelLeft, int panelTop, boolean tools) {
		private int centerX() {
			return (this.dollLeft + this.dollRight) / 2;
		}

		private boolean inDoll(double x, double y) {
			return x >= this.dollLeft && x < this.dollRight && y >= this.dollTop && y < this.dollBottom;
		}

		private int slotsLeft() {
			return centerX() - (MAX_SLOTS * SLOT_PITCH - NeuItems.GAP) / 2;
		}

		boolean blocks(int left, int top, int right, int bottom) {
			if (overlaps(left, top, right, bottom, this.dollLeft, this.dollTop, this.dollRight, this.dollBottom)) {
				return true;
			}
			if (!this.tools) {
				return false;
			}
			int slotsLeft = slotsLeft();
			return overlaps(left, top, right, bottom, slotsLeft, this.slotsTop, slotsLeft + MAX_SLOTS * SLOT_PITCH - NeuItems.GAP,
					this.slotsTop + NeuItems.SQUARE_SIZE)
					|| overlaps(left, top, right, bottom, this.panelLeft, this.panelTop, this.panelLeft + PANEL_WIDTH, this.panelTop + PANEL_HEIGHT);
		}

		private static boolean overlaps(int left, int top, int right, int bottom, int otherLeft, int otherTop, int otherRight, int otherBottom) {
			return left < otherRight && otherLeft < right && top < otherBottom && otherTop < bottom;
		}
	}

	private record Locked(String id, ItemStack stack) {
	}

	private record Chroma(String key, List<Integer> colors) {
	}

	private record Worn(Type type, ItemStack stack, int x, int y) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + NeuItems.SQUARE_SIZE && mouseY >= this.y && mouseY < this.y + NeuItems.SQUARE_SIZE;
		}
	}

	// Locked cosmetics, tints, chromas and rotation
	private static final Map<Type, Locked> LOCKED = new EnumMap<>(Type.class);
	private static final Map<Type, Integer> TINTS = new EnumMap<>(Type.class);
	private static final Map<Integer, ItemStack> COLOR_ITEMS = new HashMap<>();
	private static List<Integer> colors = List.of();
	private static List<Worn> worn = List.of();
	private static Type shownColorsFor;
	private static boolean shownChromas;
	private static String handChroma;
	private static int chromaPage;
	private static JsonObject chromaSource;
	private static List<Chroma> chromas = List.of();
	private static Type selected;
	private static int colorPage;
	private static ItemStack hovered = ItemStack.EMPTY;
	private static ItemStack previousIcon;
	private static ItemStack nextIcon;
	private static float yaw = DEFAULT_YAW;
	private static float pitch = DEFAULT_PITCH;
	private static boolean dragging;
	private static boolean reserved;

	private CosmeticPreview() {
	}

	static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (PrivateKeys.LOCK_COSMETIC.usable() && typeOf(stack) != null && NeuItems.previewShown(Minecraft.getInstance().gui.screen())) {
				String last = lines.isEmpty() ? "" : lines.getLast().getString();
				if (!last.isBlank() && !last.contains(ACTION_MARK)) {
					lines.add(Component.empty());
				}
				lines.add(lockLine(isLocked(idFor(stack))));
			}
		});
	}

	// Frame on the slots of locked cosmetics
	public static void extractLockedFrame(GuiGraphicsExtractor graphics, Slot slot) {
		if (LOCKED.isEmpty() || !slot.hasItem() || typeOf(slot.getItem()) == null || !NeuItems.previewShown(Minecraft.getInstance().gui.screen())
				|| !isLocked(idFor(slot.getItem()))) {
			return;
		}
		int offset = (SELECTED_FRAME_SIZE - NeuItems.SQUARE_SIZE) / 2 + 1;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SELECTED_FRAME, slot.x - offset, slot.y - offset, 0.0F, 0.0F, SELECTED_FRAME_SIZE, SELECTED_FRAME_SIZE,
				SELECTED_FRAME_SIZE, SELECTED_FRAME_SIZE);
	}

	static String idFor(ItemStack stack) {
		String name = stack.getHoverName().getString();
		String id = NeuItems.idByName(name);
		return id != null ? id : "name:" + name;
	}

	private static Component lockLine(boolean locked) {
		PrivateKeys.Key key = PrivateKeys.LOCK_COSMETIC;
		boolean middle = key.key().getType() == InputConstants.Type.MOUSE && key.key().getValue() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && key.modifiers() == 0;
		MutableComponent line = Component.empty();
		if (middle) {
			line.append(Component.literal(MIDDLE_CLICK_ICON).withStyle(style -> style.withFont(ICON_FONT).withColor(ICON_COLOR)));
		}
		Component keyName = middle ? Component.literal("Middle-Click") : key.displayName();
		return line.append(Component.literal(" > ").withColor(CLICK_DARK))
				.append(Component.empty().append(keyName).append(" to ").withColor(CLICK_TEXT))
				.append(Component.literal(locked ? "Unlock from Cosmetic Preview" : "Lock on Cosmetic Preview").withColor(CLICK_ACTION));
	}

	static void setColors(List<Integer> list) {
		colors = List.copyOf(list);
		colorPage = Math.clamp(colorPage, 0, colorPages() - 1);
	}

	private static int colorPages() {
		return pages(colors.size());
	}

	private static int pages(int count) {
		return Math.max(1, (count + COLORS_PER_PAGE - 1) / COLORS_PER_PAGE);
	}

	// Chroma Sets from the NEU items
	private static List<Chroma> chromas() {
		JsonObject defaults = NeuItems.cosmeticDefaults();
		if (defaults == chromaSource) {
			return chromas;
		}
		chromaSource = defaults;
		List<Chroma> list = new ArrayList<>();
		if (defaults != null && defaults.has("chromas") && defaults.get("chromas").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : defaults.getAsJsonObject("chromas").entrySet()) {
				if (!entry.getValue().isJsonObject() || !entry.getValue().getAsJsonObject().has("colors")
						|| !entry.getValue().getAsJsonObject().get("colors").isJsonArray()) {
					continue;
				}
				List<Integer> values = new ArrayList<>();
				for (JsonElement color : entry.getValue().getAsJsonObject().getAsJsonArray("colors")) {
					if (color.isJsonPrimitive() && color.getAsJsonPrimitive().isNumber()) {
						values.add(color.getAsInt());
					}
				}
				if (!values.isEmpty()) {
					list.add(new Chroma(entry.getKey(), List.copyOf(values)));
				}
			}
		}
		chromas = List.copyOf(list);
		chromaPage = Math.clamp(chromaPage, 0, pages(chromas.size()) - 1);
		return chromas;
	}

	private static boolean chromable(Type type, ItemStack stack) {
		if (type != Type.HAND) {
			return false;
		}
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return data != null && !data.colors().isEmpty();
	}

	private static boolean paintable(Type type, ItemStack stack) {
		return type.tintable || chromable(type, stack);
	}

	private static ItemStack chromaIcon(Chroma chroma) {
		ItemStack icon = NeuItems.stack(CHROMA_SET_PREFIX + chroma.key());
		return icon.isEmpty() ? colorItem(chroma.colors().getFirst()) : icon;
	}

	// Layout in the favorites area
	static Layout layout(int left, int top, int right, int bottom) {
		if (right - left < MIN_WIDTH) {
			return null;
		}
		int centerX = right - DOLL_HALF_WIDTH + SHIFT_X;
		int available = bottom - top;
		int tools = SECTION_GAP + NeuItems.SQUARE_SIZE + SECTION_GAP + PANEL_HEIGHT;
		int dollHeight = Math.min(DOLL_MAX_HEIGHT, available - tools);
		boolean withTools = dollHeight >= DOLL_MIN_HEIGHT && centerX - PANEL_WIDTH / 2 >= 0;
		if (!withTools) {
			dollHeight = Math.min(DOLL_MAX_HEIGHT, available);
			if (dollHeight < DOLL_MIN_HEIGHT) {
				return null;
			}
		}
		int total = dollHeight + (withTools ? tools : 0);
		int dollTop = top + (available - total) / 2;
		int slotsTop = dollTop + dollHeight + SECTION_GAP;
		int panelTop = slotsTop + NeuItems.SQUARE_SIZE + SECTION_GAP;
		return new Layout(centerX - DOLL_HALF_WIDTH, dollTop, centerX + DOLL_HALF_WIDTH, dollTop + dollHeight, slotsTop, centerX - PANEL_WIDTH / 2, panelTop,
				withTools);
	}

	static boolean reserved() {
		return reserved;
	}

	static void updateReserved(Screen screen) {
		reserved = !LOCKED.isEmpty() || typeOf(hovered) != null || typeOf(hoveredSlot(screen)) != null;
	}

	private static ItemStack hoveredSlot(Screen screen) {
		if (screen instanceof AbstractContainerScreen<?>) {
			Slot slot = ((ContainerHoveredSlotAccessor) screen).mccinfo$getHoveredSlot();
			if (slot != null) {
				return slot.getItem();
			}
		}
		return ItemStack.EMPTY;
	}

	// Clicks on the slots, colors and arrows
	static boolean click(Layout layout, MouseButtonEvent event) {
		double x = event.x();
		double y = event.y();
		if (layout.tools() && PrivateKeys.LOCK_COSMETIC.usable() && PrivateKeys.LOCK_COSMETIC.matchesMouse(event)) {
			for (Worn slot : worn) {
				if (slot.contains(x, y)) {
					if (LOCKED.remove(slot.type()) != null) {
						NeuItems.clickSound();
					}
					return true;
				}
			}
		}
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (layout.inDoll(x, y)) {
			dragging = true;
			return true;
		}
		if (!layout.tools()) {
			return false;
		}
		for (Worn slot : worn) {
			if (slot.contains(x, y)) {
				selected = slot.type();
				NeuItems.clickSound();
				return true;
			}
		}
		if (shownColorsFor == null || x < layout.panelLeft() || x >= layout.panelLeft() + PANEL_WIDTH || y < layout.panelTop()
				|| y >= layout.panelTop() + PANEL_HEIGHT) {
			return false;
		}
		int localX = (int) Math.floor(x - layout.panelLeft());
		int localY = (int) Math.floor(y - layout.panelTop());
		int page = shownChromas ? chromaPage : colorPage;
		int size = shownChromas ? chromas().size() : colors.size();
		if (onArrow(localX, localY, PREVIOUS_LEFT) && page > 0) {
			setPage(page - 1);
			NeuItems.clickSound();
		} else if (onArrow(localX, localY, NEXT_LEFT) && page < pages(size) - 1) {
			setPage(page + 1);
			NeuItems.clickSound();
		} else {
			int index = entryAt(localX, localY, page, size);
			if (index >= 0 && shownChromas) {
				String key = chromas().get(index).key();
				handChroma = key.equals(handChroma) ? null : key;
				NeuItems.clickSound();
			} else if (index >= 0) {
				Integer color = colors.get(index);
				if (color.equals(TINTS.get(shownColorsFor))) {
					TINTS.remove(shownColorsFor);
				} else {
					TINTS.put(shownColorsFor, color);
				}
				NeuItems.clickSound();
			}
		}
		return true;
	}

	private static void setPage(int page) {
		if (shownChromas) {
			chromaPage = page;
		} else {
			colorPage = page;
		}
	}

	private static boolean onArrow(int localX, int localY, int arrowLeft) {
		return localX >= arrowLeft && localX < arrowLeft + ARROW_SIZE && localY >= ARROW_TOP && localY < ARROW_TOP + ARROW_SIZE;
	}

	private static int entryAt(int localX, int localY, int page, int size) {
		int column = Math.floorDiv(localX - COLOR_LEFT, COLOR_CELL);
		int row = Math.floorDiv(localY - COLOR_TOP, COLOR_CELL);
		if (column < 0 || column >= COLOR_COLUMNS || row < 0 || row >= COLOR_ROWS) {
			return -1;
		}
		int index = page * COLORS_PER_PAGE + column / GROUP_SIZE * GROUP_SLOTS + row * GROUP_SIZE + column % GROUP_SIZE;
		return index < size ? index : -1;
	}

	// Turns the player
	static boolean drag(double dragX, double dragY) {
		if (!dragging) {
			return false;
		}
		yaw = (yaw - (float) dragX * YAW_SPEED) % FULL_TURN;
		pitch = Math.clamp(pitch - (float) dragY * PITCH_SPEED, -MAX_PITCH, MAX_PITCH);
		return true;
	}

	static boolean stopDrag() {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	public static boolean enabled() {
		return ModConfig.get().cosmeticPreview;
	}

	// Cosmetic slot of an item
	public static Type typeOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model == null) {
			return null;
		}
		String path = model.getPath();
		if (path.startsWith("island_lobby/fishing/rods") || path.startsWith("island_cosmetics/weapon_skins/")) {
			return Type.HAND;
		}
		if (!path.startsWith("island_cosmetics/")) {
			return null;
		}
		if (path.contains("/hat/") || path.contains("/hair/")) {
			return Type.HAT;
		}
		if (path.contains("/accessory/")) {
			return Type.ACCESSORY;
		}
		if (path.contains("/back/")) {
			return Type.BACK;
		}
		return null;
	}

	static void hover(ItemStack stack) {
		if (typeOf(stack) != null) {
			hovered = stack;
		}
	}

	// Middle Click lock
	static boolean toggleLock(String id, ItemStack stack) {
		Type type = typeOf(stack);
		if (type == null || id == null) {
			return false;
		}
		Locked current = LOCKED.get(type);
		if (current != null && current.id().equals(id)) {
			LOCKED.remove(type);
		} else {
			LOCKED.put(type, new Locked(id, stack));
			selected = type;
		}
		NeuItems.clickSound();
		return true;
	}

	static boolean isLocked(String id) {
		if (id == null) {
			return false;
		}
		for (Locked locked : LOCKED.values()) {
			if (locked.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	// Player, slots and color panel
	static void extract(GuiGraphicsExtractor graphics, Screen screen, Layout layout, int mouseX, int mouseY) {
		ItemStack hover = hovered;
		hovered = ItemStack.EMPTY;
		if (hover.isEmpty() && typeOf(hoveredSlot(screen)) != null) {
			hover = hoveredSlot(screen);
		}
		worn = List.of();
		shownColorsFor = null;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Map<Type, ItemStack> shown = new EnumMap<>(Type.class);
		LOCKED.forEach((type, locked) -> shown.put(type, locked.stack()));
		Type hoverType = typeOf(hover);
		if (hoverType != null) {
			shown.put(hoverType, hover);
		}
		if (shown.isEmpty()) {
			return;
		}
		Map<Type, ItemStack> wearing = wearing(player, shown);
		extractDoll(graphics, player, layout, wearing);
		if (layout.tools() && reserved) {
			extractTools(graphics, layout, wearing, hoverType, mouseX, mouseY);
		}
	}

	// Real equipment with the hovered and locked cosmetics on top
	private static Map<Type, ItemStack> wearing(LocalPlayer player, Map<Type, ItemStack> shown) {
		Map<Type, ItemStack> wearing = new EnumMap<>(Type.class);
		for (Type type : Type.values()) {
			ItemStack stack = shown.get(type);
			if (stack == null) {
				stack = type == Type.BACK ? realBack(player) : player.getItemBySlot(type.slot);
				if (typeOf(stack) != type) {
					stack = ItemStack.EMPTY;
				}
			}
			if (stack.isEmpty()) {
				continue;
			}
			Integer tint = TINTS.get(type);
			if (tint != null && type.tintable) {
				stack = stack.copy();
				stack.set(DataComponents.DYED_COLOR, new DyedItemColor(tint));
			}
			Chroma chroma = handChroma == null ? null : chromas().stream().filter(entry -> entry.key().equals(handChroma)).findFirst().orElse(null);
			if (chroma != null && chromable(type, stack)) {
				CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
				stack = stack.copy();
				stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.floats(), data.flags(), data.strings(), chroma.colors()));
			}
			wearing.put(type, stack);
		}
		return wearing;
	}

	private static void extractDoll(GuiGraphicsExtractor graphics, LocalPlayer player, Layout layout, Map<Type, ItemStack> wearing) {
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf xRotation = new Quaternionf().rotateX((float) Math.toRadians(pitch));
		rotation.mul(xRotation);
		EntityRenderState state = extractState(player, wearing);
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = yaw;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
			living.walkAnimationSpeed = 0.0F;
		}
		ItemStack back = wearing.getOrDefault(Type.BACK, ItemStack.EMPTY);
		if (!back.isEmpty()) {
			ItemStackRenderState backState = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(backState, back, ItemDisplayContext.HEAD, player);
			BACKS.put(state, backState);
		}
		int height = layout.dollBottom() - layout.dollTop();
		float size = height / SIZE_DIVIDER;
		int halfWidth = layout.dollRight() - layout.dollLeft();
		int x = layout.centerX();
		int y = Math.round((layout.dollTop() + layout.dollBottom()) / 2.0F + height * SHIFT_Y_RATIO);
		Vector3f translation = rotation.transform(new Vector3f(0.0F, state.boundingBoxHeight / 2.0F, 0.0F)).negate();
		graphics.entity(state, size, translation, rotation, xRotation, x - halfWidth, y - height, x + halfWidth, y + height);
	}

	private static void extractTools(GuiGraphicsExtractor graphics, Layout layout, Map<Type, ItemStack> wearing, Type hoverType, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		Type current = selected != null && wearing.containsKey(selected) ? selected : hoverType != null && wearing.containsKey(hoverType) ? hoverType : null;
		if (current == null) {
			for (Map.Entry<Type, ItemStack> entry : wearing.entrySet()) {
				if (paintable(entry.getKey(), entry.getValue())) {
					current = entry.getKey();
					break;
				}
			}
		}
		List<Worn> slots = new ArrayList<>();
		int left = layout.centerX() - (wearing.size() * SLOT_PITCH - NeuItems.GAP) / 2;
		for (Map.Entry<Type, ItemStack> entry : wearing.entrySet()) {
			slots.add(new Worn(entry.getKey(), entry.getValue(), left + slots.size() * SLOT_PITCH, layout.slotsTop()));
		}
		worn = List.copyOf(slots);
		graphics.nextStratum();
		Worn hoveredSlot = null;
		for (Worn slot : slots) {
			boolean over = slot.contains(mouseX, mouseY);
			if (over) {
				hoveredSlot = slot;
			}
			boolean light = over || slot.type() == current || slot.type() == hoverType;
			OverlayStyle.fillRounded(graphics, slot.x(), slot.y(), NeuItems.SQUARE_SIZE, NeuItems.SQUARE_SIZE, light ? NeuItems.HOVER_COLOR : NeuItems.BOX_COLOR);
			graphics.item(slot.stack(), slot.x() + 1, slot.y() + 1);
		}
		if (hoveredSlot != null) {
			graphics.setTooltipForNextFrame(font, hoveredSlot.stack(), mouseX, mouseY);
			NeuItems.pointer(graphics);
		}
		if (current == null || !paintable(current, wearing.get(current))) {
			return;
		}
		shownColorsFor = current;
		shownChromas = !current.tintable;
		extractColors(graphics, font, layout, current, mouseX, mouseY);
	}

	// Tints or Chroma Sets
	private static void extractColors(GuiGraphicsExtractor graphics, Font font, Layout layout, Type type, int mouseX, int mouseY) {
		int left = layout.panelLeft();
		int top = layout.panelTop();
		graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
		int localX = mouseX - left;
		int localY = mouseY - top;
		int page = shownChromas ? chromaPage : colorPage;
		int size = shownChromas ? chromas().size() : colors.size();
		int hoveredColor = entryAt(localX, localY, page, size);
		Integer tint = TINTS.get(type);
		int first = page * COLORS_PER_PAGE;
		for (int slot = 0; slot < COLORS_PER_PAGE && first + slot < size; slot++) {
			int x = left + COLOR_LEFT + (slot / GROUP_SLOTS * GROUP_SIZE + slot % GROUP_SIZE) * COLOR_CELL;
			int y = top + COLOR_TOP + slot % GROUP_SLOTS / GROUP_SIZE * COLOR_CELL;
			ItemStack icon;
			boolean chosen;
			if (shownChromas) {
				Chroma chroma = chromas().get(first + slot);
				icon = chromaIcon(chroma);
				chosen = chroma.key().equals(handChroma);
			} else {
				int color = colors.get(first + slot);
				icon = colorItem(color);
				chosen = tint != null && tint == color;
			}
			if (chosen) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, SELECTED_FRAME, x - 1, y - 1, 0.0F, 0.0F, SELECTED_FRAME_SIZE, SELECTED_FRAME_SIZE,
						SELECTED_FRAME_SIZE, SELECTED_FRAME_SIZE);
			}
			graphics.item(icon, x + 1, y + 1);
			if (first + slot == hoveredColor) {
				graphics.fill(x + 1, y + 1, x + COLOR_CELL - 1, y + COLOR_CELL - 1, COLOR_HOVER);
				if (shownChromas) {
					graphics.setTooltipForNextFrame(font, icon, mouseX, mouseY);
				}
			}
		}
		boolean previous = page > 0;
		boolean next = page < pages(size) - 1;
		if (previous) {
			graphics.item(previousIcon(), left + PREVIOUS_LEFT, top + ARROW_TOP);
		}
		if (next) {
			graphics.item(nextIcon(), left + NEXT_LEFT, top + ARROW_TOP);
		}
		if (hoveredColor >= 0) {
			NeuItems.pointer(graphics);
		} else if (previous && onArrow(localX, localY, PREVIOUS_LEFT)) {
			graphics.setTooltipForNextFrame(font, Component.literal("Previous Page"), mouseX, mouseY);
			NeuItems.pointer(graphics);
		} else if (next && onArrow(localX, localY, NEXT_LEFT)) {
			graphics.setTooltipForNextFrame(font, Component.literal("Next Page"), mouseX, mouseY);
			NeuItems.pointer(graphics);
		}
	}

	private static ItemStack colorItem(int color) {
		return COLOR_ITEMS.computeIfAbsent(color, value -> {
			ItemStack stack = new ItemStack(Items.LEATHER_HORSE_ARMOR);
			stack.set(DataComponents.ITEM_MODEL, COLOR_MODEL);
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(value));
			return stack;
		});
	}

	private static ItemStack previousIcon() {
		if (previousIcon == null) {
			previousIcon = new ItemStack(Items.ECHO_SHARD);
			previousIcon.set(DataComponents.ITEM_MODEL, PREVIOUS_MODEL);
		}
		return previousIcon;
	}

	private static ItemStack nextIcon() {
		if (nextIcon == null) {
			nextIcon = new ItemStack(Items.ECHO_SHARD);
			nextIcon.set(DataComponents.ITEM_MODEL, NEXT_MODEL);
		}
		return nextIcon;
	}

	// Render state of the dressed player
	private static EntityRenderState extractState(LocalPlayer player, Map<Type, ItemStack> wearing) {
		EntityEquipment equipment = ((LivingEntityEquipmentAccessor) player).mccinfo$getEquipment();
		Map<EquipmentSlot, ItemStack> previous = new EnumMap<>(EquipmentSlot.class);
		wearing.forEach((type, stack) -> {
			if (type.slot != null) {
				previous.put(type.slot, equipment.set(type.slot, stack));
			}
		});
		try {
			EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
			EntityRenderer<? super LocalPlayer, ?> renderer = dispatcher.getRenderer(player);
			EntityRenderState state = renderer.createRenderState(player, 1.0F);
			state.shadowPieces.clear();
			state.outlineColor = 0;
			return state;
		} finally {
			previous.forEach(equipment::set);
		}
	}

	private static ItemStack realBack(LocalPlayer player) {
		for (Entity passenger : player.getIndirectPassengers()) {
			if (passenger instanceof ArmorStand stand && !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				return stand.getItemBySlot(EquipmentSlot.HEAD);
			}
		}
		return ItemStack.EMPTY;
	}

	// Back cosmetic layer of the preview
	public static final class BackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
		public BackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			ItemStackRenderState back = BACKS.get(state);
			if (back == null) {
				return;
			}
			PlayerModel model = getParentModel();
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			model.body.translateAndRotate(poseStack);
			poseStack.translate(0.0F, BACK_OFFSET, 0.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
			poseStack.scale(BACK_SCALE, -BACK_SCALE, -BACK_SCALE);
			back.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
	}
}
