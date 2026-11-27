package leroidesafk.mccinfo.client.items;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// Drag of an item from the item list onto a target
public final class NeuDrag {
	// Screen that accepts dropped items
	public interface Target {
		boolean accepts(Screen screen);

		String targetAt(Screen screen, double mouseX, double mouseY);

		void drop(Screen screen, String target, String id);
	}

	private static final double MOVE_THRESHOLD = 3.0;
	private static final int HALF_ITEM = 8;
	private static final List<Target> TARGETS = new ArrayList<>();

	private static Screen screen;
	private static Target target;
	private static String id;
	private static ItemStack stack = ItemStack.EMPTY;
	private static double startX;
	private static double startY;
	private static boolean moved;

	private NeuDrag() {
	}

	public static void addTarget(Target added) {
		TARGETS.add(added);
	}

	static boolean begin(Screen current, String itemId, ItemStack itemStack, double mouseX, double mouseY) {
		for (Target candidate : TARGETS) {
			if (candidate.accepts(current)) {
				screen = current;
				target = candidate;
				id = itemId;
				stack = itemStack;
				startX = mouseX;
				startY = mouseY;
				moved = false;
				return true;
			}
		}
		return false;
	}

	static boolean drag(Screen current, double mouseX, double mouseY) {
		if (id == null) {
			return false;
		}
		if (current != screen) {
			clear();
			return false;
		}
		if (!moved && Math.hypot(mouseX - startX, mouseY - startY) >= MOVE_THRESHOLD) {
			moved = true;
		}
		return true;
	}

	// Click without moving opens the crafts, else drop on the target
	static boolean release(Screen current, double mouseX, double mouseY) {
		if (id == null) {
			return false;
		}
		String itemId = id;
		Target dropTarget = target;
		boolean wasMoved = moved;
		boolean same = current == screen;
		clear();
		if (!same) {
			return false;
		}
		if (!wasMoved) {
			NeuCrafts.open(current, itemId, true);
			return true;
		}
		String over = dropTarget.targetAt(current, mouseX, mouseY);
		if (over != null) {
			dropTarget.drop(current, over, itemId);
		}
		return true;
	}

	public static boolean dragging(Screen current) {
		return id != null && moved && current == screen;
	}

	static void extract(Screen current, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (dragging(current) && !stack.isEmpty()) {
			graphics.fakeItem(stack, mouseX - HALF_ITEM, mouseY - HALF_ITEM);
		}
	}

	private static void clear() {
		screen = null;
		target = null;
		id = null;
		stack = ItemStack.EMPTY;
		moved = false;
	}
}
