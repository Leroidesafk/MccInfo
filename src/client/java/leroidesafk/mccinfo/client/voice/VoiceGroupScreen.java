package leroidesafk.mccinfo.client.voice;

import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.MenuTooltip;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VoiceGroupScreen extends Screen implements MenuBackdrop.Overlay {
	// Panel, form and list layout
	private static final int MARGIN = 24;
	private static final int MAX_WIDTH = 320;
	private static final int MAX_HEIGHT = 380;
	private static final int FORM_WIDTH = 260;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int LABEL_HEIGHT = 12;
	private static final int INPUT_HEIGHT = 16;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 4;
	private static final int ICON_BUTTON_WIDTH = 24;
	private static final int ICON_BUTTON_HEIGHT = 24;
	private static final int SPRITE = 16;
	private static final int SHEET = 32;
	private static final int GAP = 8;
	private static final int GROUP_ROW_HEIGHT = 28;
	private static final int MEMBER_ROW_HEIGHT = 24;
	private static final int ROW_GAP = 4;
	private static final int ROW_PADDING = 6;
	private static final int ICON_SIZE = 16;
	private static final int HEAD_SIZE = 12;
	private static final int HEAD_GAP = 2;
	private static final int MAX_HEADS = 5;
	private static final int SCROLLBAR_WIDTH = BORDER;
	private static final int SCROLLBAR_GRAB = 2;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final int INACTIVE_SPRITE = 0x60FFFFFF;
	// Simple Voice Chat icons
	private static final Identifier GROUP_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/group");
	private static final Identifier LOCK_ICON = Identifier.fromNamespaceAndPath("voicechat", "icons/lock");
	private static final Identifier MICROPHONE = Identifier.fromNamespaceAndPath("voicechat", "icons/microphone_button");
	private static final Identifier SPEAKER = Identifier.fromNamespaceAndPath("voicechat", "icons/speaker_button");
	private static final Identifier GROUP_HUD = Identifier.fromNamespaceAndPath("voicechat", "icons/group_hud_button");
	private static final Identifier LEAVE = Identifier.fromNamespaceAndPath("voicechat", "icons/leave");
	// Group types
	private static final String[] TYPE_KEYS = {"normal", "open", "isolated"};

	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_ROW = 0xFF202020;
	private static final int COLOR_ROW_HOVER = 0xFF2A2A2A;
	private static final int COLOR_ROW_BORDER = 0xFF3A3A3A;
	private static final int COLOR_INPUT = 0xFF2C2C2C;
	private static final int COLOR_INPUT_BORDER = 0xFF161616;
	private static final int COLOR_BUTTON = 0xFF2A2A2A;
	private static final int COLOR_BUTTON_HOVER = 0xFF3A3A3A;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_DISABLED = 0xFF5A5A5A;
	private static final int COLOR_DESCRIPTION = 0xFF8C8C8C;
	private static final int COLOR_ADMIN_NOTE = 0xFFFFC94D;
	private static final int COLOR_ERROR = 0xFFD83A3A;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;
	private static final Map<UUID, ResolvableProfile> PROFILES = new HashMap<>();

	// List, create, password and current group views
	private enum View {
		LIST, CREATE, PASSWORD, GROUP
	}

	// Clickable area
	private record Hit(int x, int y, int width, int height, Runnable action) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
		}
	}

	// Text field and its place
	private record Field(EditBox box, int x, int y, int width) {
	}

	private final Screen parent;

	@Override
	public Screen backdropParent() {
		return this.parent;
	}
	private final List<Hit> hits = new ArrayList<>();
	private final List<Field> fields = new ArrayList<>();
	private View view = View.LIST;
	private EditBox name;
	private EditBox password;
	private EditBox joinPassword;
	private int type = VoiceChat.GROUP_NORMAL;
	private UUID joining;
	private String joiningName = "";
	private Component error;
	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int listTop;
	private int listBottom;
	private float scroll;
	private float scrollTarget;
	private long lastFrame;
	private int maxScroll;
	private boolean draggingScrollbar;
	private double dragOffset;
	private Component tooltip;

	public VoiceGroupScreen(Screen parent) {
		super(Component.translatable("screen.mccinfo.group.join_create.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.scale = SettingsPanel.uiScale();
		this.name = box(this.name, VoiceChat.GROUP_NAME_LENGTH, "screen.mccinfo.group.name");
		this.password = box(this.password, VoiceChat.GROUP_PASSWORD_LENGTH, "screen.mccinfo.group.password");
		this.joinPassword = box(this.joinPassword, VoiceChat.GROUP_PASSWORD_LENGTH, "screen.mccinfo.group.password");
	}

	// Text field kept when the screen is rebuilt
	private EditBox box(EditBox previous, int length, String hint) {
		EditBox box = new EditBox(this.font, 0, 0, 100, 9, Component.translatable(hint));
		box.setBordered(false);
		box.setTextColor(COLOR_WHITE);
		box.setMaxLength(length);
		box.setValue(previous == null ? "" : previous.getValue());
		box.setResponder(value -> this.error = null);
		this.addWidget(box);
		return box;
	}

	void wrongPassword() {
		if (this.view == View.PASSWORD) {
			this.error = Component.translatable("screen.mccinfo.group.wrong_password");
			this.joinPassword.setValue("");
		}
	}

	// Switch view
	private void open(View next) {
		if (this.view != next) {
			this.view = next;
			this.scroll = 0;
			this.scrollTarget = 0;
			this.error = null;
			this.setFocused(null);
		}
	}

	private void layout() {
		int virtualWidth = (int) (this.width / this.scale);
		int virtualHeight = (int) (this.height / this.scale);
		boolean form = this.view == View.CREATE || this.view == View.PASSWORD;
		this.panelWidth = Math.min(form ? FORM_WIDTH : MAX_WIDTH, virtualWidth - MARGIN * 2);
		int content = switch (this.view) {
			case CREATE -> HEADER_HEIGHT + (LABEL_HEIGHT + INPUT_HEIGHT) * 2 + LABEL_HEIGHT + BUTTON_HEIGHT * 2 + BUTTON_GAP;
			case PASSWORD -> HEADER_HEIGHT + LABEL_HEIGHT * 2 + INPUT_HEIGHT + LABEL_HEIGHT + BUTTON_HEIGHT;
			default -> Integer.MAX_VALUE;
		};
		this.panelHeight = Math.min(form ? content + (BORDER + PADDING) * 2 : MAX_HEIGHT, virtualHeight - MARGIN * 2);
		this.left = (virtualWidth - this.panelWidth) / 2;
		this.top = (virtualHeight - this.panelHeight) / 2;
		this.listTop = this.top + BORDER + PADDING + HEADER_HEIGHT + 2;
		int bottomRow = this.view == View.GROUP ? ICON_BUTTON_HEIGHT : BUTTON_HEIGHT;
		this.listBottom = bottomY(bottomRow) - GAP;
	}

	private int bottomY(int height) {
		return this.top + this.panelHeight - BORDER - PADDING - height;
	}

	private int contentLeft() {
		return this.left + BORDER + PADDING;
	}

	private int contentWidth() {
		return this.panelWidth - BORDER * 2 - PADDING * 2;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!MenuBackdrop.extract(this.parent, graphics, this.width, this.height, a)) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		VoiceChat.VoiceGroup current = VoiceChat.currentGroup();
		if (current != null) {
			open(View.GROUP);
		} else if (this.view == View.GROUP) {
			open(View.LIST);
		}
		layout();
		updateScroll();
		this.hits.clear();
		this.fields.clear();
		this.tooltip = null;
		int virtualX = (int) (mouseX / this.scale);
		int virtualY = (int) (mouseY / this.scale);

		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);
		int right = this.left + this.panelWidth;
		int bottom = this.top + this.panelHeight;
		graphics.fill(this.left, this.top, right, bottom, COLOR_PANEL_BORDER);
		graphics.fill(this.left + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER, COLOR_PANEL_BACKGROUND);
		switch (this.view) {
			case LIST -> extractList(graphics, virtualX, virtualY);
			case CREATE -> extractCreate(graphics, virtualX, virtualY, a);
			case PASSWORD -> extractPassword(graphics, virtualX, virtualY, a);
			case GROUP -> extractGroup(graphics, current, virtualX, virtualY);
		}
		if (this.maxScroll > 0) {
			int x = right - BORDER + (BORDER - SCROLLBAR_WIDTH) / 2;
			int thumbY = thumbY();
			graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight(), COLOR_SCROLLBAR);
		}
		if (this.tooltip != null) {
			MenuTooltip.extract(graphics, this.tooltip, virtualX, virtualY, this.scale);
		}
		graphics.pose().popMatrix();
	}

	// Groups list
	private void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = contentLeft();
		int width = contentWidth();
		extractHeader(graphics, this.title, x, width, this.top + BORDER + PADDING);
		boolean usable = VoiceChat.groupsUsable();
		List<VoiceChat.VoiceGroup> groups = usable ? VoiceChat.groups() : List.of();
		setMaxScroll(groups.size(), GROUP_ROW_HEIGHT);
		boolean inList = mouseY >= this.listTop && mouseY < this.listBottom;
		graphics.enableScissor(this.left + BORDER, this.listTop, this.left + this.panelWidth - BORDER, this.listBottom);
		if (groups.isEmpty()) {
			Component empty = Component.translatable(usable ? "screen.mccinfo.group.empty" : "screen.mccinfo.group.unavailable");
			graphics.centeredText(this.font, empty, x + width / 2, (this.listTop + this.listBottom - this.font.lineHeight) / 2, COLOR_DESCRIPTION);
		}
		int y = this.listTop - Math.round(this.scroll);
		for (VoiceChat.VoiceGroup group : groups) {
			if (y + GROUP_ROW_HEIGHT > this.listTop && y < this.listBottom) {
				extractGroupRow(graphics, group, x, y, width, inList ? mouseX : Integer.MIN_VALUE, inList ? mouseY : Integer.MIN_VALUE);
			}
			y += GROUP_ROW_HEIGHT + ROW_GAP;
		}
		graphics.disableScissor();
		extractError(graphics, x, width, this.listBottom - this.font.lineHeight - 2);
		extractButton(graphics, Component.translatable("screen.mccinfo.group.create_button"), x, bottomY(BUTTON_HEIGHT), width, BUTTON_HEIGHT, usable,
				mouseX, mouseY, () -> {
					this.type = VoiceChat.GROUP_NORMAL;
					this.name.setValue("");
					this.password.setValue("");
					open(View.CREATE);
				});
	}

	// Group row: name, lock and member heads
	private void extractGroupRow(GuiGraphicsExtractor graphics, VoiceChat.VoiceGroup group, int x, int y, int width, int mouseX, int mouseY) {
		boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + GROUP_ROW_HEIGHT;
		graphics.fill(x, y, x + width, y + GROUP_ROW_HEIGHT, COLOR_ROW_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + GROUP_ROW_HEIGHT - 1, hovered ? COLOR_ROW_HOVER : COLOR_ROW);
		int iconX = x + ROW_PADDING;
		int iconY = y + (GROUP_ROW_HEIGHT - ICON_SIZE) / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, group.password() ? LOCK_ICON : GROUP_ICON, iconX, iconY, ICON_SIZE, ICON_SIZE);
		int heads = Math.min(MAX_HEADS, group.members().size());
		int headsWidth = heads * (HEAD_SIZE + HEAD_GAP);
		Component more = group.members().size() > MAX_HEADS ? Component.literal("+" + (group.members().size() - MAX_HEADS)) : null;
		int moreWidth = more == null ? 0 : this.font.width(more) + HEAD_GAP;
		int headX = x + width - ROW_PADDING - moreWidth - headsWidth + HEAD_GAP;
		for (int i = 0; i < heads; i++) {
			VoiceChat.GroupMember member = group.members().get(i);
			PlayerFaceExtractor.extractRenderState(graphics, profile(member.id()), headX + i * (HEAD_SIZE + HEAD_GAP), y + (GROUP_ROW_HEIGHT - HEAD_SIZE) / 2, HEAD_SIZE);
		}
		if (more != null) {
			graphics.text(this.font, more, x + width - ROW_PADDING - this.font.width(more), y + (GROUP_ROW_HEIGHT - this.font.lineHeight) / 2 + 1, COLOR_DESCRIPTION, true);
		}
		int textX = iconX + ICON_SIZE + ROW_PADDING;
		int textRight = headX - ROW_PADDING;
		graphics.enableScissor(textX, y, Math.max(textX, textRight), y + GROUP_ROW_HEIGHT);
		graphics.text(this.font, Component.literal(group.name()), textX, y + 5, COLOR_WHITE, true);
		Component members = Component.translatable(group.members().size() == 1 ? "screen.mccinfo.group.member" : "screen.mccinfo.group.members", group.members().size());
		graphics.text(this.font, Component.translatable("screen.mccinfo.group.row_details", typeName(group.type()), members), textX, y + 15, COLOR_DESCRIPTION, true);
		graphics.disableScissor();
		if (hovered) {
			NeuItems.pointer(graphics);
			if (mouseX >= iconX && mouseX < iconX + ICON_SIZE && group.password()) {
				this.tooltip = Component.translatable("screen.mccinfo.group.locked");
			} else if (mouseX >= headX && !group.members().isEmpty()) {
				this.tooltip = Component.literal(String.join("\n", group.members().stream().map(VoiceChat.GroupMember::name).toList()));
			}
		}
		this.hits.add(new Hit(x, Math.max(y, this.listTop), width, Math.min(y + GROUP_ROW_HEIGHT, this.listBottom) - Math.max(y, this.listTop), () -> {
			NeuItems.clickSound();
			if (group.password()) {
				this.joining = group.id();
				this.joiningName = group.name();
				this.joinPassword.setValue("");
				open(View.PASSWORD);
			} else if (!VoiceChat.joinGroup(group.id(), null)) {
				this.error = Component.translatable("screen.mccinfo.group.offline");
			}
		}));
	}

	// Create form: name, password and type
	private void extractCreate(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = contentLeft();
		int width = contentWidth();
		int y = this.top + BORDER + PADDING;
		extractHeader(graphics, Component.translatable("screen.mccinfo.group.create.title"), x, width, y);
		y += HEADER_HEIGHT;
		y = extractField(graphics, Component.translatable("screen.mccinfo.group.name"), this.name, x, y, width, mouseX, mouseY, a);
		y = extractField(graphics, Component.translatable("screen.mccinfo.group.password_optional"), this.password, x, y, width, mouseX, mouseY, a);
		extractError(graphics, x, width, y + 2);
		y += LABEL_HEIGHT;
		Component typeLabel = Component.translatable("screen.mccinfo.group.type", typeName(this.type));
		if (extractButton(graphics, typeLabel, x, y, width, BUTTON_HEIGHT, true, mouseX, mouseY, () -> this.type = (this.type + 1) % TYPE_KEYS.length)) {
			this.tooltip = Component.translatable("screen.mccinfo.group.type." + TYPE_KEYS[this.type] + ".description");
		}
		y += BUTTON_HEIGHT + BUTTON_GAP;
		extractButton(graphics, Component.translatable("screen.mccinfo.group.create"), x, y, width, BUTTON_HEIGHT, canCreate(), mouseX, mouseY, this::create);
	}

	private boolean canCreate() {
		return VoiceChat.groupsUsable() && VoiceChat.validGroupName(this.name.getValue());
	}

	private void create() {
		if (!canCreate()) {
			return;
		}
		if (!VoiceChat.createGroup(this.name.getValue(), this.password.getValue(), this.type)) {
			this.error = Component.translatable("screen.mccinfo.group.offline");
		}
	}

	// Password to join a locked group
	private void extractPassword(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = contentLeft();
		int width = contentWidth();
		int y = this.top + BORDER + PADDING;
		extractHeader(graphics, Component.translatable("screen.mccinfo.group.password.title"), x, width, y);
		y += HEADER_HEIGHT;
		graphics.enableScissor(x, y, x + width, y + LABEL_HEIGHT);
		graphics.centeredText(this.font, Component.literal(this.joiningName), x + width / 2, y + 1, COLOR_WHITE);
		graphics.disableScissor();
		y += LABEL_HEIGHT;
		y = extractField(graphics, Component.translatable("screen.mccinfo.group.password"), this.joinPassword, x, y, width, mouseX, mouseY, a);
		if (this.error == null && Permissions.isAdmin()) {
			graphics.enableScissor(x, y, x + width, y + LABEL_HEIGHT);
			graphics.centeredText(this.font, Component.translatable("screen.mccinfo.group.admin_bypass"), x + width / 2, y + 2, COLOR_ADMIN_NOTE);
			graphics.disableScissor();
		}
		extractError(graphics, x, width, y + 2);
		y += LABEL_HEIGHT;
		extractButton(graphics, Component.translatable("screen.mccinfo.group.join"), x, y, width, BUTTON_HEIGHT, canJoin(), mouseX, mouseY, this::join);
	}

	private boolean canJoin() {
		return VoiceChat.groupsUsable() && this.joining != null && (!this.joinPassword.getValue().isEmpty() || Permissions.isAdmin());
	}

	private void join() {
		if (!canJoin()) {
			return;
		}
		if (!VoiceChat.joinGroup(this.joining, this.joinPassword.getValue())) {
			this.error = Component.translatable("screen.mccinfo.group.offline");
		}
	}

	// Current group: members and buttons
	private void extractGroup(GuiGraphicsExtractor graphics, VoiceChat.VoiceGroup group, int mouseX, int mouseY) {
		int x = contentLeft();
		int width = contentWidth();
		Component title = group.type() == VoiceChat.GROUP_NORMAL ? Component.literal(group.name())
				: Component.translatable("screen.mccinfo.group.title_type", group.name(), typeName(group.type()));
		extractHeader(graphics, title, x, width, this.top + BORDER + PADDING);
		setMaxScroll(group.members().size(), MEMBER_ROW_HEIGHT);
		graphics.enableScissor(this.left + BORDER, this.listTop, this.left + this.panelWidth - BORDER, this.listBottom);
		int y = this.listTop - Math.round(this.scroll);
		for (VoiceChat.GroupMember member : group.members()) {
			if (y + MEMBER_ROW_HEIGHT > this.listTop && y < this.listBottom) {
				graphics.fill(x, y, x + width, y + MEMBER_ROW_HEIGHT, COLOR_ROW_BORDER);
				graphics.fill(x + 1, y + 1, x + width - 1, y + MEMBER_ROW_HEIGHT - 1, COLOR_ROW);
				int iconY = y + (MEMBER_ROW_HEIGHT - ICON_SIZE) / 2;
				PlayerFaceExtractor.extractRenderState(graphics, profile(member.id()), x + ROW_PADDING, iconY, ICON_SIZE);
				int textX = x + ROW_PADDING * 2 + ICON_SIZE;
				int stateX = x + width - ROW_PADDING - ICON_SIZE;
				graphics.enableScissor(textX, Math.max(y, this.listTop), stateX - ROW_PADDING, Math.min(y + MEMBER_ROW_HEIGHT, this.listBottom));
				graphics.text(this.font, Component.literal(member.name()), textX, y + (MEMBER_ROW_HEIGHT - this.font.lineHeight) / 2 + 1, COLOR_WHITE, true);
				graphics.disableScissor();
				if (member.icon() != null) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, member.icon(), stateX, iconY, ICON_SIZE, ICON_SIZE);
				}
			}
			y += MEMBER_ROW_HEIGHT + ROW_GAP;
		}
		graphics.disableScissor();
		extractError(graphics, x, width, this.listBottom - this.font.lineHeight - 2);

		int buttonY = bottomY(ICON_BUTTON_HEIGHT);
		int buttonX = x;
		extractIconButton(graphics, MICROPHONE, true, VoiceChat.muted(), VoiceChat.canMute(), buttonX, buttonY, mouseX, mouseY,
				Component.translatable(!VoiceChat.canMute() ? "screen.mccinfo.settings.voice_chat_mute.ptt"
						: VoiceChat.muted() ? "screen.mccinfo.settings.voice_chat_mute.off" : "screen.mccinfo.settings.voice_chat_mute.on"),
				VoiceChat::toggleMuted);
		buttonX += ICON_BUTTON_WIDTH + BUTTON_GAP;
		extractIconButton(graphics, SPEAKER, true, VoiceChat.deafened(), VoiceChat.canDeafen(), buttonX, buttonY, mouseX, mouseY,
				Component.translatable(VoiceChat.deafened() ? "screen.mccinfo.settings.voice_chat_deafen.off" : "screen.mccinfo.settings.voice_chat_deafen.on"),
				VoiceChat::toggleDeafened);
		buttonX += ICON_BUTTON_WIDTH + BUTTON_GAP;
		boolean hud = VoiceChat.groupHudShown();
		extractIconButton(graphics, GROUP_HUD, true, hud, true, buttonX, buttonY, mouseX, mouseY,
				Component.translatable(hud ? "screen.mccinfo.group.hud.on" : "screen.mccinfo.group.hud.off"), VoiceChat::toggleGroupHud);
		extractIconButton(graphics, LEAVE, false, false, true, x + width - ICON_BUTTON_WIDTH, buttonY, mouseX, mouseY,
				Component.translatable("screen.mccinfo.group.leave"), () -> {
					if (!VoiceChat.leaveGroup()) {
						this.error = Component.translatable("screen.mccinfo.group.offline");
					}
				});
	}

	private void setMaxScroll(int rows, int rowHeight) {
		int height = rows * (rowHeight + ROW_GAP) - (rows == 0 ? 0 : ROW_GAP);
		this.maxScroll = Math.max(0, height - (this.listBottom - this.listTop));
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, this.maxScroll);
		this.scroll = Mth.clamp(this.scroll, 0.0F, this.maxScroll);
	}

	private int extractField(GuiGraphicsExtractor graphics, Component label, EditBox box, int x, int y, int width, int mouseX, int mouseY, float a) {
		graphics.text(this.font, label, x, y + 2, COLOR_TEXT, true);
		y += LABEL_HEIGHT;
		graphics.fill(x, y, x + width, y + INPUT_HEIGHT, COLOR_INPUT_BORDER);
		graphics.fill(x, y, x + width - 1, y + INPUT_HEIGHT - 1, COLOR_INPUT);
		box.setX(x + 5);
		box.setY(y + (INPUT_HEIGHT - 8) / 2);
		box.setWidth(width - 10);
		box.extractRenderState(graphics, mouseX, mouseY, a);
		this.fields.add(new Field(box, x, y, width));
		return y + INPUT_HEIGHT;
	}

	private void extractError(GuiGraphicsExtractor graphics, int x, int width, int y) {
		if (this.error != null) {
			graphics.centeredText(this.font, this.error, x + width / 2, y, COLOR_ERROR);
		}
	}

	private boolean extractButton(GuiGraphicsExtractor graphics, Component label, int x, int y, int width, int height, boolean active, int mouseX, int mouseY, Runnable action) {
		boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		graphics.fill(x, y, x + width, y + height, COLOR_ROW_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, hovered && active ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
		int color = !active ? COLOR_DISABLED : hovered ? COLOR_WHITE : COLOR_TEXT;
		graphics.text(this.font, label, x + (width - this.font.width(label)) / 2, y + (height - this.font.lineHeight) / 2 + 1, color, true);
		if (active) {
			this.hits.add(new Hit(x, y, width, height, () -> {
				NeuItems.clickSound();
				action.run();
			}));
			if (hovered) {
				NeuItems.pointer(graphics);
			}
		}
		return hovered;
	}

	private void extractIconButton(GuiGraphicsExtractor graphics, Identifier sprite, boolean sheet, boolean state, boolean active, int x, int y, int mouseX, int mouseY,
			Component tooltip, Runnable action) {
		boolean hovered = mouseX >= x && mouseX < x + ICON_BUTTON_WIDTH && mouseY >= y && mouseY < y + ICON_BUTTON_HEIGHT;
		graphics.fill(x, y, x + ICON_BUTTON_WIDTH, y + ICON_BUTTON_HEIGHT, COLOR_ROW_BORDER);
		graphics.fill(x + 1, y + 1, x + ICON_BUTTON_WIDTH - 1, y + ICON_BUTTON_HEIGHT - 1, hovered && active ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
		int color = active ? COLOR_WHITE : INACTIVE_SPRITE;
		int spriteX = x + (ICON_BUTTON_WIDTH - SPRITE) / 2;
		int spriteY = y + (ICON_BUTTON_HEIGHT - SPRITE) / 2;
		if (sheet) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, SHEET, SHEET, state ? SPRITE : 0, 0, spriteX, spriteY, SPRITE, SPRITE, color);
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, spriteX, spriteY, SPRITE, SPRITE, color);
		}
		if (hovered) {
			this.tooltip = tooltip;
		}
		if (active) {
			this.hits.add(new Hit(x, y, ICON_BUTTON_WIDTH, ICON_BUTTON_HEIGHT, () -> {
				NeuItems.clickSound();
				action.run();
			}));
			if (hovered) {
				NeuItems.pointer(graphics);
			}
		}
	}

	private void extractHeader(GuiGraphicsExtractor graphics, Component label, int x, int width, int y) {
		int textWidth = Math.min(this.font.width(label), width);
		int lineLength = Math.max(0, (width - textWidth - HEADER_LABEL_GAP * 2) / 2);
		int textY = y + (HEADER_HEIGHT - this.font.lineHeight) / 2;
		int lineY = textY + this.font.lineHeight / 2 - 1;
		if (lineLength > 0) {
			graphics.fill(x, lineY, x + lineLength, lineY + 1, COLOR_HEADER_LINE);
			graphics.fill(x + width - lineLength, lineY, x + width, lineY + 1, COLOR_HEADER_LINE);
		}
		int textX = lineLength > 0 ? x + lineLength + HEADER_LABEL_GAP : x;
		graphics.enableScissor(x, y, x + width, y + HEADER_HEIGHT);
		graphics.text(this.font, label, textX, textY, COLOR_HEADER_TEXT, true);
		graphics.disableScissor();
	}

	private static Component typeName(int type) {
		return Component.translatable("screen.mccinfo.group.type." + TYPE_KEYS[Mth.clamp(type, 0, TYPE_KEYS.length - 1)]);
	}

	private static ResolvableProfile profile(UUID id) {
		return PROFILES.computeIfAbsent(id, ResolvableProfile::createUnresolved);
	}

	private void updateScroll() {
		long now = Util.getMillis();
		long elapsed = this.lastFrame == 0 ? 0 : now - this.lastFrame;
		this.lastFrame = now;
		if (this.draggingScrollbar || Math.abs(this.scrollTarget - this.scroll) < 0.5F) {
			this.scroll = this.scrollTarget;
		} else {
			this.scroll += (this.scrollTarget - this.scroll) * (1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS));
		}
	}

	private int trackHeight() {
		return this.top + this.panelHeight - BORDER - this.listTop;
	}

	private int thumbHeight() {
		int trackHeight = trackHeight();
		return Math.max(MIN_THUMB, trackHeight * trackHeight / (trackHeight + this.maxScroll));
	}

	private int thumbY() {
		return this.listTop + Math.round((trackHeight() - thumbHeight()) * this.scroll / this.maxScroll);
	}

	private void dragScrollbar(double y) {
		int range = trackHeight() - thumbHeight();
		if (range > 0) {
			this.scrollTarget = Mth.clamp((float) ((y - this.dragOffset - this.listTop) * this.maxScroll / range), 0.0F, this.maxScroll);
			this.scroll = this.scrollTarget;
		}
	}

	// Clicks on the buttons and rows
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		for (Field field : this.fields) {
			if (x >= field.x() && x < field.x() + field.width() && y >= field.y() && y < field.y() + INPUT_HEIGHT) {
				this.setFocused(field.box());
				field.box().onClick(new MouseButtonEvent(x, y, event.buttonInfo()), doubleClick);
				return true;
			}
		}
		this.setFocused(null);
		int right = this.left + this.panelWidth;
		if (this.maxScroll > 0 && x >= right - BORDER - SCROLLBAR_GRAB && x < right && y >= this.listTop && y < this.top + this.panelHeight - BORDER) {
			int thumbY = thumbY();
			int thumbHeight = thumbHeight();
			this.dragOffset = y >= thumbY && y < thumbY + thumbHeight ? y - thumbY : thumbHeight / 2.0;
			this.draggingScrollbar = true;
			dragScrollbar(y);
			return true;
		}
		for (Hit hit : List.copyOf(this.hits)) {
			if (hit.height() > 0 && hit.contains(x, y)) {
				hit.action().run();
				return true;
			}
		}
		if (x < this.left || x >= right || y < this.top || y >= this.top + this.panelHeight) {
			this.onClose();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.draggingScrollbar) {
			dragScrollbar(event.y() / this.scale);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.draggingScrollbar) {
			this.draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.maxScroll > 0 && scrollY != 0) {
			this.scrollTarget = Mth.clamp(this.scrollTarget - (float) scrollY * SCROLL_STEP, 0.0F, this.maxScroll);
		}
		return true;
	}

	// Forms: Escape goes back, Enter sends, Tab switches field
	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean form = this.view == View.CREATE || this.view == View.PASSWORD;
		if (form && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			open(View.LIST);
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			if (this.view == View.CREATE) {
				create();
				return true;
			}
			if (this.view == View.PASSWORD) {
				join();
				return true;
			}
		}
		if (form && event.key() == GLFW.GLFW_KEY_TAB) {
			EditBox next = this.view == View.CREATE ? (this.getFocused() == this.name ? this.password : this.name) : this.joinPassword;
			this.setFocused(next);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
