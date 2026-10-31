package leroidesafk.mccinfo.client.chat;

import leroidesafk.mccinfo.client.ModConfig;
import leroidesafk.mccinfo.client.gui.EventsScreen;
import leroidesafk.mccinfo.client.gui.MovableOverlay;
import leroidesafk.mccinfo.client.gui.OverlayStyle;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

public final class OverlayChatDrag {
	// Box dragged from the chat
	private static MovableOverlay dragged;
	private static double dragOffsetX;
	private static double dragOffsetY;

	private OverlayChatDrag() {
	}

	public static void register() {
		HudElementRegistry.addLast(Mccinfo.id("overlay_chat_drag"), OverlayChatDrag::followMouse);
	}

	public static boolean mouseClicked(MouseButtonEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		// Grab a box under the mouse
		for (MovableOverlay overlay : EventsScreen.OVERLAYS) {
			int x = overlay.x(screenWidth);
			int y = overlay.y(screenHeight);
			if (overlay.visible() && event.x() >= x && event.x() < x + overlay.width() && event.y() >= y && event.y() < y + overlay.height()) {
				dragged = overlay;
				dragOffsetX = event.x() - x;
				dragOffsetY = event.y() - y;
				return true;
			}
		}
		return false;
	}

	public static boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (scrollY == 0) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		// Wheel zooms a zoomable box
		for (MovableOverlay overlay : EventsScreen.OVERLAYS) {
			int x = overlay.x(screenWidth);
			int y = overlay.y(screenHeight);
			if (overlay.visible() && overlay.zoomable() && mouseX >= x && mouseX < x + overlay.width() && mouseY >= y && mouseY < y + overlay.height()) {
				overlay.changeZoom((int) Math.signum(scrollY));
				ModConfig.get().save();
				return true;
			}
		}
		return false;
	}

	private static void followMouse(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (dragged == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		boolean leftPressed = GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		// Drop on release or when the chat closes
		if (!leftPressed || !(client.gui.screen() instanceof ChatScreen)) {
			dragged = null;
			ModConfig.get().save();
			return;
		}
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		// Box follows the mouse
		double mouseX = client.mouseHandler.xpos() * screenWidth / client.getWindow().getScreenWidth();
		double mouseY = client.mouseHandler.ypos() * screenHeight / client.getWindow().getScreenHeight();
		dragged.setPosition(
				OverlayStyle.clamp((int) Math.round(mouseX - dragOffsetX), screenWidth - dragged.width()),
				OverlayStyle.clamp((int) Math.round(mouseY - dragOffsetY), screenHeight - dragged.height()));
	}
}
