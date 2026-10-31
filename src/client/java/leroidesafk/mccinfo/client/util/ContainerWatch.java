package leroidesafk.mccinfo.client.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

// Tells if a container's items changed since the last check
public final class ContainerWatch {
	private Container container;
	private ItemStack[] stacks = new ItemStack[0];
	private int[] counts = new int[0];

	public boolean changed(Container container) {
		int size = container.getContainerSize();
		boolean changed = container != this.container || size != this.stacks.length;
		if (changed) {
			this.container = container;
			this.stacks = new ItemStack[size];
			this.counts = new int[size];
		}
		for (int slot = 0; slot < size; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack != this.stacks[slot] || stack.getCount() != this.counts[slot]) {
				this.stacks[slot] = stack;
				this.counts[slot] = stack.getCount();
				changed = true;
			}
		}
		return changed;
	}

	public void reset() {
		this.container = null;
		this.stacks = new ItemStack[0];
		this.counts = new int[0];
	}
}
