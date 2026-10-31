package leroidesafk.mccinfo.client.util;

import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Saves files off the main thread
public final class AsyncFiles {
	// Latest text per file, older writes are skipped
	private static final Map<Path, String> PENDING = new ConcurrentHashMap<>();
	private static final Object LOCK = new Object();

	private AsyncFiles() {
	}

	public static void register() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> flush());
	}

	public static void write(Path path, String text) {
		if (PENDING.put(path, text) == null) {
			Util.ioPool().execute(() -> writePending(path));
		}
	}

	public static void flush() {
		for (Path path : PENDING.keySet()) {
			writePending(path);
		}
	}

	private static void writePending(Path path) {
		synchronized (LOCK) {
			String text = PENDING.remove(path);
			if (text == null) {
				return;
			}
			// Temporary file then move, so a crash never leaves a half-written file
			try {
				Files.createDirectories(path.getParent());
				Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
				Files.writeString(temporary, text, StandardCharsets.UTF_8);
				try {
					Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				Mccinfo.LOGGER.warn("Could not save {}", path, e);
			}
		}
	}
}
