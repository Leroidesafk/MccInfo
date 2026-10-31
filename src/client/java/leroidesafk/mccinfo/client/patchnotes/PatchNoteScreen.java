package leroidesafk.mccinfo.client.patchnotes;

import leroidesafk.mccinfo.client.gui.MenuBackdrop;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

// Patch notes window, over the menu
public final class PatchNoteScreen extends Screen implements MenuBackdrop.Overlay {
	private final Screen parent;

	@Override
	public Screen backdropParent() {
		return this.parent;
	}
	private final PatchNotePanel panel;

	public PatchNoteScreen(Screen parent, PatchNotePanel panel) {
		super(Component.translatable("screen.mccinfo.patchnotes.title"));
		this.parent = parent;
		this.panel = panel;
	}

	@Override
	protected void init() {
		this.panel.layout(this.font, this.width, this.height);
	}

	@Override
	// Shows the menu behind, or the normal background if opened elsewhere
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (!MenuBackdrop.extract(this.parent, graphics, this.width, this.height, a)) {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		this.panel.layout(this.font, this.width, this.height);
		this.panel.extract(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.panel.scroll(scrollY);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && (this.panel.startDrag(event.x(), event.y()) || this.panel.click(event.x(), event.y()))) {
			return true;
		}
		// Click outside the panel closes it
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !this.panel.contains(event.x(), event.y())) {
			this.onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return this.panel.drag(event.y()) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return this.panel.stopDrag() || super.mouseReleased(event);
	}

	@Override
	// Back to the menu
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
