package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
	// Boss bars shown on screen
	@Accessor("events")
	Map<UUID, LerpingBossEvent> mccinfo$getEvents();
}
