package leroidesafk.mccinfo.client.mixin.accessor;

import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(ClientMannequin.class)
public interface ClientMannequinAccessor {
	// Skin being loaded
	@Accessor("skinLookup")
	CompletableFuture<Optional<PlayerSkin>> mccinfo$getSkinLookup();

	// Replace the skin being loaded
	@Accessor("skinLookup")
	void mccinfo$setSkinLookup(CompletableFuture<Optional<PlayerSkin>> lookup);

	// Apply a skin right away
	@Invoker("setSkin")
	void mccinfo$setSkin(PlayerSkin skin);
}
