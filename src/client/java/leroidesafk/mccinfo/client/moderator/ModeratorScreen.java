package leroidesafk.mccinfo.client.moderator;

import leroidesafk.mccinfo.Mccinfo;
import leroidesafk.mccinfo.client.PrivateKeys;
import leroidesafk.mccinfo.client.chat.ModChat;
import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import leroidesafk.mccinfo.client.gui.MenuTooltip;
import leroidesafk.mccinfo.client.gui.SettingsPanel;
import leroidesafk.mccinfo.client.items.NeuItems;
import leroidesafk.mccinfo.client.relay.Permissions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Moderator Panel: online players on the left, sanctioned players on the right
public final class ModeratorScreen extends Screen implements MenuBackdrop.Overlay {
	private static final int MARGIN = 24;
	private static final int MAX_WIDTH = 640;
	private static final int MAX_HEIGHT = 420;
	private static final int BORDER = 3;
	private static final int PADDING = 12;
	private static final int COLUMN_GAP = 12;
	private static final int HEADER_HEIGHT = 18;
	private static final int SEARCH_HEIGHT = 14;
	private static final int SEARCH_GAP = 4;
	private static final int COLOR_SEARCH = 0xFF262626;
	private static final int HEADER_LABEL_GAP = 7;
	private static final int ROW_HEIGHT = 24;
	private static final int ROW_GAP = 4;
	private static final int CARD_PADDING = 6;
	private static final int HEAD_SIZE = 16;
	private static final int TEXT_GAP = 9;
	private static final int LINE_HEIGHT = 10;
	private static final int SECTION_GAP = 4;
	private static final int ICON_BUTTON_WIDTH = 20;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_GAP = 4;
	private static final int SCROLLBAR_WIDTH = 2;
	private static final int MIN_THUMB = 8;
	private static final float SCROLL_STEP = 20.0F;
	private static final float SCROLL_SMOOTH_MILLIS = 60.0F;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

	// Icons and online badges
	private static final Identifier ICONS = Mccinfo.id("textures/gui/ui/icons.png");
	private static final int ICONS_SIZE = 40;
	private static final int ICON = 8;
	private static final int ICON_CHAT_BAN = 13;
	private static final int ICON_VOICE_BAN = 10;
	private static final Identifier BADGES = Mccinfo.id("textures/gui/ui/players.png");
	private static final int BADGES_SIZE = 16;
	private static final int BADGE_CELL = 8;
	private static final int BADGE_SIZE = 8;
	private static final int BADGE_INSET = 1;
	private static final int BADGE_OVERHANG = 2;
	private static final int BADGE_OVERHANG_X = 3;
	private static final int BADGE_ONLINE = 0;
	private static final int BADGE_OFFLINE = 1;

	private static final int COLOR_PANEL_BORDER = 0xFF262626;
	private static final int COLOR_PANEL_BACKGROUND = 0xFF1A1A1A;
	private static final int COLOR_HEADER_TEXT = 0xFFBDBDBD;
	private static final int COLOR_HEADER_LINE = 0xFF3A3A3A;
	private static final int COLOR_ROW = 0xFF202020;
	private static final int COLOR_ROW_HOVER = 0xFF2A2A2A;
	private static final int COLOR_ROW_BORDER = 0xFF3A3A3A;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFBDBDBD;
	private static final int COLOR_META = 0xFF8C8C8C;
	// Name colors: admin gold, moderator purple like the Mod chat
	private static final int COLOR_ADMIN = 0xFFFFC31E;
	private static final int COLOR_MODERATOR = 0xFFBB1EFF;
	private static final int COLOR_BANNED_ICON = 0xFFE05050;
	private static final int COLOR_BAN = 0xFF6B2626;
	private static final int COLOR_BAN_HOVER = 0xFF8A3030;
	private static final int COLOR_UNBAN = 0xFF2E5A2E;
	private static final int COLOR_UNBAN_HOVER = 0xFF3C7A3C;
	private static final int COLOR_SCROLLBAR = 0xFF5A5A5A;

	private static final Map<UUID, ResolvableProfile> PROFILES = new HashMap<>();

	private record Hit(int x, int y, int width, int height, Runnable action) {
	}

	private static final class Column {
		private int left;
		private int width;
		private float scroll;
		private float scrollTarget;
		private int maxScroll;
	}

	private final @Nullable Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final Set<String> expanded = new HashSet<>();
	private final Column online = new Column();
	private final Column banned = new Column();
	private Component tooltip;
	private EditBox search;
	private int searchY;
	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int listTop;
	private int listBottom;
	private long lastFrame;

	public ModeratorScreen(@Nullable Screen parent) {
		super(Component.translatable("screen.mccinfo.moderator.title"));
		this.parent = parent;
	}

	// MODERATOR_PANEL key, staff only
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (PrivateKeys.MODERATOR_PANEL.consumeClick()) {
				if (client.gui.screen() == null && Permissions.isStaff()) {
					client.gui.setScreen(new ModeratorScreen(null));
				}
			}
		});
	}

	@Override
	public Screen backdropParent() {
		return this.parent;
	}

	@Override
	// Two columns of the same width under the search bar
	protected void init() {
		this.scale = SettingsPanel.uiScale();
		int virtualWidth = (int) (this.width / this.scale);
		int virtualHeight = (int) (this.height / this.scale);
		this.panelWidth = Math.min(MAX_WIDTH, virtualWidth - MARGIN * 2);
		this.panelHeight = Math.min(MAX_HEIGHT, virtualHeight - MARGIN * 2);
		this.left = (virtualWidth - this.panelWidth) / 2;
		this.top = (virtualHeight - this.panelHeight) / 2;
		this.searchY = this.top + BORDER + PADDING + HEADER_HEIGHT;
		this.listTop = this.searchY + SEARCH_HEIGHT + SEARCH_GAP + HEADER_HEIGHT + 2;
		this.listBottom = this.top + this.panelHeight - BORDER - PADDING;
		int inner = this.panelWidth - BORDER * 2 - PADDING * 2;
		int columnWidth = (inner - COLUMN_GAP) / 2;
		this.online.left = this.left + BORDER + PADDING;
		this.online.width = columnWidth;
		this.banned.left = this.online.left + columnWidth + COLUMN_GAP;
		this.banned.width = inner - columnWidth - COLUMN_GAP;
		// Keeps the search text when the screen is rebuilt
		String previous = this.search == null ? "" : this.search.getValue();
		boolean typing = this.search != null && this.getFocused() == this.search;
		this.search = new EditBox(this.font, this.online.left + 4, this.searchY + (SEARCH_HEIGHT - 8) / 2, inner - 8, 9,
				Component.translatable("screen.mccinfo.permissions.search"));
		this.search.setBordered(false);
		this.search.setTextColor(COLOR_TEXT);
		this.search.setMaxLength(16);
		this.search.setHint(Component.translatable("screen.mccinfo.permissions.search").withColor(COLOR_META));
		this.search.setValue(previous);
		this.search.setResponder(value -> {
			this.online.scrollTarget = 0;
			this.banned.scrollTarget = 0;
		});
		this.addWidget(this.search);
		this.setFocused(typing ? this.search : null);
	}

	private boolean matches(String name) {
		String query = this.search == null ? "" : this.search.getValue().strip().toLowerCase(java.util.Locale.ROOT);
		return query.isEmpty() || name.toLowerCase(java.util.Locale.ROOT).contains(query);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!MenuBackdrop.extract(this.parent, graphics, this.width, this.height, a)) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}

	// Opened cards show the buttons and the ban details
	private int cardHeight(ModChat.Sanction sanction) {
		if (!this.expanded.contains(sanction.uuid())) {
			return ROW_HEIGHT;
		}
		int height = ROW_HEIGHT + BUTTON_HEIGHT + CARD_PADDING;
		if (sanction.chat() != null) {
			height += SECTION_GAP + LINE_HEIGHT * 3 + sanction.chat().messages().size() * LINE_HEIGHT;
		}
		if (sanction.voice() != null) {
			height += SECTION_GAP + LINE_HEIGHT * 3;
		}
		return height + CARD_PADDING;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		long now = Util.getMillis();
		long elapsed = this.lastFrame == 0 ? 0 : now - this.lastFrame;
		this.lastFrame = now;
		updateScroll(this.online, elapsed);
		updateScroll(this.banned, elapsed);
		this.hits.clear();
		this.tooltip = null;
		int virtualX = (int) (mouseX / this.scale);
		int virtualY = (int) (mouseY / this.scale);
		// Lists from the relay, filtered by the search
		List<ModChat.OnlinePlayer> players = ModChat.onlinePlayers().stream().filter(player -> matches(player.name())).toList();
		List<ModChat.Sanction> sanctions = ModChat.sanctions().stream().filter(sanction -> matches(sanction.name())).toList();
		this.expanded.removeIf(uuid -> sanction(uuid) == null);
		int bannedHeight = 0;
		for (ModChat.Sanction sanction : sanctions) {
			bannedHeight += cardHeight(sanction) + ROW_GAP;
		}
		setMaxScroll(this.online, players.size() * (ROW_HEIGHT + ROW_GAP) - ROW_GAP);
		setMaxScroll(this.banned, bannedHeight - ROW_GAP);

		graphics.pose().pushMatrix();
		graphics.pose().scale(this.scale, this.scale);
		int right = this.left + this.panelWidth;
		int bottom = this.top + this.panelHeight;
		graphics.fill(this.left, this.top, right, bottom, COLOR_PANEL_BORDER);
		graphics.fill(this.left + BORDER, this.top + BORDER, right - BORDER, bottom - BORDER, COLOR_PANEL_BACKGROUND);
		int titleY = this.top + BORDER + PADDING;
		header(graphics, this.title, this.online.left, this.panelWidth - BORDER * 2 - PADDING * 2, titleY);
		int inner = this.panelWidth - BORDER * 2 - PADDING * 2;
		graphics.fill(this.online.left, this.searchY, this.online.left + inner, this.searchY + SEARCH_HEIGHT, COLOR_SEARCH);
		this.search.extractRenderState(graphics, virtualX, virtualY, a);
		int columnTitleY = this.searchY + SEARCH_HEIGHT + SEARCH_GAP;
		header(graphics, Component.translatable("screen.mccinfo.moderator.online", players.size()), this.online.left, this.online.width, columnTitleY);
		header(graphics, Component.translatable("screen.mccinfo.moderator.banned", sanctions.size()), this.banned.left, this.banned.width, columnTitleY);

		// Online column
		graphics.enableScissor(this.online.left - BADGE_OVERHANG_X, this.listTop, this.online.left + this.online.width + 1, this.listBottom);
		if (players.isEmpty()) {
			empty(graphics, this.online, "screen.mccinfo.moderator.online_empty");
		}
		boolean inOnline = inColumn(this.online, virtualX, virtualY);
		int y = this.listTop - Math.round(this.online.scroll);
		for (ModChat.OnlinePlayer player : players) {
			if (y + ROW_HEIGHT > this.listTop && y < this.listBottom) {
				onlineRow(graphics, player, this.online.left, y, this.online.width, inOnline ? virtualX : Integer.MIN_VALUE, inOnline ? virtualY : Integer.MIN_VALUE);
			}
			y += ROW_HEIGHT + ROW_GAP;
		}
		graphics.disableScissor();

		// Sanctioned column
		graphics.enableScissor(this.banned.left - BADGE_OVERHANG_X, this.listTop, this.banned.left + this.banned.width + 1, this.listBottom);
		if (sanctions.isEmpty()) {
			empty(graphics, this.banned, "screen.mccinfo.moderator.banned_empty");
		}
		boolean inBanned = inColumn(this.banned, virtualX, virtualY);
		y = this.listTop - Math.round(this.banned.scroll);
		for (ModChat.Sanction sanction : sanctions) {
			int height = cardHeight(sanction);
			if (y + height > this.listTop && y < this.listBottom) {
				bannedCard(graphics, sanction, this.banned.left, y, this.banned.width, height,
						inBanned ? virtualX : Integer.MIN_VALUE, inBanned ? virtualY : Integer.MIN_VALUE);
			}
			y += height + ROW_GAP;
		}
		graphics.disableScissor();
		scrollbar(graphics, this.online);
		scrollbar(graphics, this.banned);
		if (this.tooltip != null) {
			MenuTooltip.extract(graphics, this.tooltip, virtualX, virtualY, this.scale);
		}
		graphics.pose().popMatrix();
	}

	private void onlineRow(GuiGraphicsExtractor graphics, ModChat.OnlinePlayer player, int x, int y, int width, int mouseX, int mouseY) {
		graphics.fill(x, y, x + width, y + ROW_HEIGHT, COLOR_ROW_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + ROW_HEIGHT - 1, COLOR_ROW);
		head(graphics, player.uuid(), true, x + CARD_PADDING, y + (ROW_HEIGHT - HEAD_SIZE) / 2);
		int textRight = x + width - CARD_PADDING;
		// Chat and voice ban buttons, only for players not already banned from it
		if ("player".equals(player.role())) {
			ModChat.Sanction sanction = sanction(player.uuid());
			int buttonX = x + width - CARD_PADDING - ICON_BUTTON_WIDTH;
			int buttonY = y + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
			if (sanction == null || sanction.voice() == null) {
				iconButton(graphics, ICON_VOICE_BAN, "screen.mccinfo.moderator.ban_voice", buttonX, buttonY, mouseX, mouseY, "voice_ban", player.uuid());
				buttonX -= ICON_BUTTON_WIDTH + BUTTON_GAP;
			}
			if (sanction == null || sanction.chat() == null) {
				iconButton(graphics, ICON_CHAT_BAN, "screen.mccinfo.moderator.ban_chat", buttonX, buttonY, mouseX, mouseY, "chat_ban", player.uuid());
				buttonX -= ICON_BUTTON_WIDTH + BUTTON_GAP;
			}
			textRight = buttonX + ICON_BUTTON_WIDTH;
		}
		int textX = x + CARD_PADDING + HEAD_SIZE + TEXT_GAP;
		int color = "admin".equals(player.role()) ? COLOR_ADMIN : "moderator".equals(player.role()) ? COLOR_MODERATOR : COLOR_WHITE;
		graphics.text(this.font, clip(player.name(), textRight - textX), textX, y + (ROW_HEIGHT - this.font.lineHeight) / 2 + 1, color, true);
	}

	private void bannedCard(GuiGraphicsExtractor graphics, ModChat.Sanction sanction, int x, int y, int width, int height, int mouseX, int mouseY) {
		boolean open = this.expanded.contains(sanction.uuid());
		boolean rowHovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_HEIGHT;
		graphics.fill(x, y, x + width, y + height, COLOR_ROW_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOR_ROW);
		if (rowHovered) {
			graphics.fill(x + 1, y + 1, x + width - 1, y + ROW_HEIGHT - 1, COLOR_ROW_HOVER);
			NeuItems.pointer(graphics);
		}
		head(graphics, sanction.uuid(), sanction.online(), x + CARD_PADDING, y + (ROW_HEIGHT - HEAD_SIZE) / 2);
		int iconX = x + width - CARD_PADDING - ICON;
		// Red icons for each active ban
		int iconY = y + (ROW_HEIGHT - ICON) / 2;
		if (sanction.voice() != null) {
			icon(graphics, ICON_VOICE_BAN, iconX, iconY, COLOR_BANNED_ICON);
			iconX -= ICON + 4;
		}
		if (sanction.chat() != null) {
			icon(graphics, ICON_CHAT_BAN, iconX, iconY, COLOR_BANNED_ICON);
			iconX -= ICON + 4;
		}
		int textX = x + CARD_PADDING + HEAD_SIZE + TEXT_GAP;
		graphics.text(this.font, clip(sanction.name(), iconX + ICON - 4 - textX), textX, y + (ROW_HEIGHT - this.font.lineHeight) / 2 + 1, COLOR_WHITE, true);
		// Click on the row opens or closes the card
		addHit(x, y, width, ROW_HEIGHT, () -> {
			NeuItems.clickSound();
			if (!this.expanded.remove(sanction.uuid())) {
				this.expanded.add(sanction.uuid());
			}
		});
		if (!open) {
			return;
		}
		int inner = x + CARD_PADDING;
		int innerWidth = width - CARD_PADDING * 2;
		int lineY = y + ROW_HEIGHT;
		int half = (innerWidth - BUTTON_GAP) / 2;
		boolean chatBanned = sanction.chat() != null;
		boolean voiceBanned = sanction.voice() != null;
		wideButton(graphics, ICON_CHAT_BAN, chatBanned ? "screen.mccinfo.moderator.unban_chat" : "screen.mccinfo.moderator.ban_chat", !chatBanned,
				inner, lineY, half, mouseX, mouseY, chatBanned ? "chat_unban" : "chat_ban", sanction.uuid());
		wideButton(graphics, ICON_VOICE_BAN, voiceBanned ? "screen.mccinfo.moderator.unban_voice" : "screen.mccinfo.moderator.ban_voice", !voiceBanned,
				inner + half + BUTTON_GAP, lineY, innerWidth - half - BUTTON_GAP, mouseX, mouseY, voiceBanned ? "voice_unban" : "voice_ban", sanction.uuid());
		lineY += BUTTON_HEIGHT;
		// Who banned, when, and the messages kept with the ban
		ModChat.ChatBan chat = sanction.chat();
		if (chat != null) {
			lineY += SECTION_GAP;
			lineY = details(graphics, "screen.mccinfo.moderator.chat_ban_title", chat.by(), chat.date(), inner, lineY, innerWidth);
			for (ModChat.BanMessage message : chat.messages()) {
				String time = message.time() > 0 ? format(TIME, message.time()) + "  " : "";
				int timeWidth = this.font.width(time);
				graphics.text(this.font, time, inner, lineY, COLOR_META, false);
				graphics.text(this.font, clip(message.text(), innerWidth - timeWidth), inner + timeWidth, lineY, COLOR_TEXT, false);
				lineY += LINE_HEIGHT;
			}
		}
		ModChat.VoiceBan voice = sanction.voice();
		if (voice != null) {
			lineY += SECTION_GAP;
			details(graphics, "screen.mccinfo.moderator.voice_ban_title", voice.by(), voice.date(), inner, lineY, innerWidth);
		}
	}

	private int details(GuiGraphicsExtractor graphics, String title, String by, long date, int x, int y, int width) {
		graphics.text(this.font, Component.translatable(title), x, y, COLOR_WHITE, false);
		y += LINE_HEIGHT;
		graphics.text(this.font, clip(Component.translatable("screen.mccinfo.moderator.banned_by", by).getString(), width), x, y, COLOR_TEXT, false);
		y += LINE_HEIGHT;
		graphics.text(this.font, format(DATE, date), x, y, COLOR_META, false);
		return y + LINE_HEIGHT;
	}

	private void iconButton(GuiGraphicsExtractor graphics, int icon, String tooltip, int x, int y, int mouseX, int mouseY, String action, String uuid) {
		boolean hovered = mouseX >= x && mouseX < x + ICON_BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
		graphics.fill(x, y, x + ICON_BUTTON_WIDTH, y + BUTTON_HEIGHT, hovered ? COLOR_BAN_HOVER : COLOR_BAN);
		icon(graphics, icon, x + (ICON_BUTTON_WIDTH - ICON) / 2, y + (BUTTON_HEIGHT - ICON) / 2, COLOR_WHITE);
		if (hovered) {
			NeuItems.pointer(graphics);
			this.tooltip = Component.translatable(tooltip);
		}
		addHit(x, y, ICON_BUTTON_WIDTH, BUTTON_HEIGHT, () -> {
			NeuItems.clickSound();
			ModChat.moderate(action, uuid);
		});
	}

	private void wideButton(GuiGraphicsExtractor graphics, int icon, String label, boolean ban, int x, int y, int width, int mouseX, int mouseY, String action, String uuid) {
		boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
		graphics.fill(x, y, x + width, y + BUTTON_HEIGHT, ban ? hovered ? COLOR_BAN_HOVER : COLOR_BAN : hovered ? COLOR_UNBAN_HOVER : COLOR_UNBAN);
		Component text = Component.translatable(label);
		int contentWidth = ICON + 4 + this.font.width(text);
		int contentX = x + (width - contentWidth) / 2;
		icon(graphics, icon, contentX, y + (BUTTON_HEIGHT - ICON) / 2, COLOR_WHITE);
		graphics.text(this.font, text, contentX + ICON + 4, y + (BUTTON_HEIGHT - this.font.lineHeight) / 2 + 1, COLOR_WHITE, false);
		if (hovered) {
			NeuItems.pointer(graphics);
		}
		addHit(x, y, width, BUTTON_HEIGHT, () -> {
			NeuItems.clickSound();
			ModChat.moderate(action, uuid);
		});
	}

	private void addHit(int x, int y, int width, int height, Runnable action) {
		int top = Math.max(y, this.listTop);
		int visible = Math.min(y + height, this.listBottom) - top;
		if (visible > 0) {
			this.hits.addFirst(new Hit(x, top, width, visible, action));
		}
	}

	private static void icon(GuiGraphicsExtractor graphics, int index, int x, int y, int color) {
		int u = index % (ICONS_SIZE / ICON) * ICON;
		int v = index / (ICONS_SIZE / ICON) * ICON;
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, u, v, ICON, ICON, ICONS_SIZE, ICONS_SIZE, color);
	}

	// Player head with an online or offline badge
	private void head(GuiGraphicsExtractor graphics, String hex, boolean online, int x, int y) {
		UUID id = uuid(hex);
		if (id != null) {
			PlayerFaceExtractor.extractRenderState(graphics, PROFILES.computeIfAbsent(id, ResolvableProfile::createUnresolved), x, y, HEAD_SIZE);
		}
		int index = online ? BADGE_ONLINE : BADGE_OFFLINE;
		int badgeX = x + HEAD_SIZE + BADGE_OVERHANG_X + BADGE_INSET - BADGE_SIZE;
		int badgeY = y + HEAD_SIZE + BADGE_OVERHANG + BADGE_INSET - BADGE_SIZE;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BADGES, badgeX, badgeY, index % 2 * BADGE_CELL, index / 2 * BADGE_CELL,
				BADGE_SIZE, BADGE_SIZE, BADGE_CELL, BADGE_CELL, BADGES_SIZE, BADGES_SIZE);
	}

	private static ModChat.Sanction sanction(String uuid) {
		for (ModChat.Sanction sanction : ModChat.sanctions()) {
			if (sanction.uuid().equals(uuid)) {
				return sanction;
			}
		}
		return null;
	}

	private void empty(GuiGraphicsExtractor graphics, Column column, String key) {
		graphics.centeredText(this.font, Component.translatable(key), column.left + column.width / 2, (this.listTop + this.listBottom - this.font.lineHeight) / 2, COLOR_META);
	}

	private boolean inColumn(Column column, int x, int y) {
		return x >= column.left && x < column.left + column.width && y >= this.listTop && y < this.listBottom;
	}

	private void setMaxScroll(Column column, int contentHeight) {
		column.maxScroll = Math.max(0, contentHeight - (this.listBottom - this.listTop));
		column.scrollTarget = Mth.clamp(column.scrollTarget, 0.0F, column.maxScroll);
		column.scroll = Mth.clamp(column.scroll, 0.0F, column.maxScroll);
	}

	private void scrollbar(GuiGraphicsExtractor graphics, Column column) {
		if (column.maxScroll <= 0) {
			return;
		}
		int x = column.left + column.width + 2;
		int track = this.listBottom - this.listTop;
		int thumb = Math.max(MIN_THUMB, track * track / (track + column.maxScroll));
		int thumbY = this.listTop + Math.round((track - thumb) * column.scroll / column.maxScroll);
		graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumb, COLOR_SCROLLBAR);
	}

	private static void updateScroll(Column column, long elapsed) {
		if (Math.abs(column.scrollTarget - column.scroll) < 0.5F) {
			column.scroll = column.scrollTarget;
		} else {
			column.scroll += (column.scrollTarget - column.scroll) * (1.0F - (float) Math.exp(-elapsed / SCROLL_SMOOTH_MILLIS));
		}
	}

	private void header(GuiGraphicsExtractor graphics, Component label, int x, int width, int y) {
		int textWidth = this.font.width(label);
		int lineLength = Math.max(0, (width - textWidth - HEADER_LABEL_GAP * 2) / 2);
		int textY = y + (HEADER_HEIGHT - this.font.lineHeight) / 2;
		int lineY = textY + this.font.lineHeight / 2 - 1;
		if (lineLength > 0) {
			graphics.fill(x, lineY, x + lineLength, lineY + 1, COLOR_HEADER_LINE);
			graphics.fill(x + width - lineLength, lineY, x + width, lineY + 1, COLOR_HEADER_LINE);
		}
		graphics.text(this.font, label, x + lineLength + HEADER_LABEL_GAP, textY, COLOR_HEADER_TEXT, true);
	}

	private String clip(String text, int width) {
		if (this.font.width(text) <= width) {
			return text;
		}
		return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("…"))) + "…";
	}

	private static String format(DateTimeFormatter formatter, long millis) {
		return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
	}

	private static UUID uuid(String hex) {
		if (hex == null || hex.length() != 32) {
			return null;
		}
		try {
			return UUID.fromString(hex.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double x = event.x() / this.scale;
		double y = event.y() / this.scale;
		int inner = this.panelWidth - BORDER * 2 - PADDING * 2;
		if (x >= this.online.left && x < this.online.left + inner && y >= this.searchY && y < this.searchY + SEARCH_HEIGHT) {
			this.setFocused(this.search);
			return true;
		}
		this.setFocused(null);
		for (Hit hit : this.hits) {
			if (x >= hit.x() && x < hit.x() + hit.width() && y >= hit.y() && y < hit.y() + hit.height()) {
				hit.action().run();
				return true;
			}
		}
		// Click outside the panel closes it
		if (x < this.left || x >= this.left + this.panelWidth || y < this.top || y >= this.top + this.panelHeight) {
			this.onClose();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		double virtualX = x / this.scale;
		Column column = virtualX >= this.banned.left ? this.banned : this.online;
		if (column.maxScroll > 0 && scrollY != 0) {
			column.scrollTarget = Mth.clamp(column.scrollTarget - (float) scrollY * SCROLL_STEP, 0.0F, column.maxScroll);
		}
		return true;
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
