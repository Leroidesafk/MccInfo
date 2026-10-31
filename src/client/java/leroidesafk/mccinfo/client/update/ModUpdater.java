package leroidesafk.mccinfo.client.update;

import leroidesafk.mccinfo.client.MccIsland;
import leroidesafk.mccinfo.client.relay.Permissions;
import leroidesafk.mccinfo.client.relay.SpotSync;
import com.mojang.brigadier.Command;
import leroidesafk.mccinfo.Mccinfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// Downloads and installs the version required by the relay
public final class ModUpdater {
	private static final Duration TIMEOUT = Duration.ofMinutes(2);
	private static final long MAX_JAR_BYTES = 64L << 20;
	private static final Pattern PATH = Pattern.compile("/update/[0-9A-Za-z.+-]{1,32}\\.jar");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9+/]{86}==");
	private static final String DOWNLOAD_SUFFIX = ".download";
	private static final Pattern FILE = Pattern.compile("mccinfo-[0-9A-Za-z.+-]{1,40}\\.jar");
	// Public key of the update signature (working paired with my personal one)
	private static final String SIGNING_KEY = "MCowBQYDK2VwAyEAd6O7UI3AdeMrLlw1d5kL467d9A5/KjBi8zL/HFxGz5M=";

	// Jar announced by the relay
	public record UpdateInfo(String path, long size, String sha256, String signature, String file) {
		boolean valid() {
			return this.path != null && PATH.matcher(this.path).matches() && this.size > 0 && this.size <= MAX_JAR_BYTES
					&& this.sha256 != null && SHA256.matcher(this.sha256).matches()
					&& this.signature != null && SIGNATURE.matcher(this.signature).matches();
		}
	}

	enum State {
		IDLE,
		DOWNLOADING,
		INSTALLED,
		FAILED
	}

	private record Installed(Path current, Path downloaded, Path target, String commandLine) {
	}

	private static String version;
	private static UpdateInfo update;
	private static State state = State.IDLE;
	private static String error;
	private static boolean restartFailed;
	private static boolean debugScreen;
	private static boolean older;
	private static boolean allowed = true;
	private static URI downloadUrl;

	private ModUpdater() {
	}

	// /mccupdate
	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(literal("mccupdate").executes(context -> {
			if (!MccIsland.isConnected()) {
				context.getSource().sendError(Component.translatable("message.mccinfo.not_on_island"));
				return 0;
			}
			if (version == null) {
				context.getSource().sendFeedback(Component.translatable("screen.mccinfo.update.up_to_date", Permissions.MOD_VERSION));
				return Command.SINGLE_SUCCESS;
			}
			UpdateToast.show();
			return Command.SINGLE_SUCCESS;
		})));
	}

	// Debug commands
	public static int debugUpdate(FabricClientCommandSource source, boolean autoUpdate) {
		if (!MccIsland.isConnected()) {
			source.sendError(Component.translatable("message.mccinfo.not_on_island"));
			return 0;
		}
		if (state == State.DOWNLOADING || state == State.INSTALLED) {
			source.sendError(Component.literal("An update is already downloading or installed."));
			return 0;
		}
		if (!SpotSync.sendAdmin(autoUpdate ? "debug_update" : "debug_update_denied", null, null, null)) {
			source.sendError(Component.translatable("message.mccinfo.mccinfo.offline"));
			return 0;
		}
		state = State.IDLE;
		error = null;
		debugScreen = true;
		return Command.SINGLE_SUCCESS;
	}

	public static int debugCancel(FabricClientCommandSource source) {
		if (state == State.DOWNLOADING || state == State.INSTALLED) {
			source.sendError(Component.translatable("message.mccinfo.debug_unupdate.too_late"));
			return 0;
		}
		debugScreen = false;
		state = State.IDLE;
		error = null;
		version = null;
		update = null;
		older = false;
		allowed = true;
		downloadUrl = null;
		SpotSync.reconnect();
		source.sendFeedback(Component.translatable("message.mccinfo.debug_unupdate.done"));
		return Command.SINGLE_SUCCESS;
	}

	// Called with every perms message
	public static void offer(boolean outdated, String requiredVersion, UpdateInfo info, boolean autoUpdate, String link) {
		if (state == State.DOWNLOADING || state == State.INSTALLED) {
			return;
		}
		if (!outdated) {
			version = null;
			update = null;
			older = false;
			allowed = true;
			downloadUrl = null;
			state = State.IDLE;
			return;
		}
		// Toast only for a new version or a change of permission
		boolean newVersion = !requiredVersion.equals(version) || allowed != autoUpdate;
		version = requiredVersion;
		allowed = autoUpdate;
		downloadUrl = autoUpdate ? null : downloadLink(link);
		boolean valid = autoUpdate && info != null && info.valid();
		older = valid && isOlder(requiredVersion);
		update = valid && !older ? info : null;
		if (debugScreen || newVersion) {
			debugScreen = false;
			UpdateToast.show();
		}
	}

	// Never install an older version
	private static boolean isOlder(String target) {
		Version current = FabricLoader.getInstance().getModContainer(Mccinfo.MOD_ID).map(container -> container.getMetadata().getVersion()).orElse(null);
		if (current == null) {
			return true;
		}
		try {
			return Version.parse(target).compareTo(current) < 0;
		} catch (VersionParsingException e) {
			return true;
		}
	}

	// Ed25519 signature of the version and SHA-256
	private static boolean signatureValid(String targetVersion, String sha256, String signature) {
		try {
			PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(SIGNING_KEY)));
			Signature verifier = Signature.getInstance("Ed25519");
			verifier.initVerify(key);
			verifier.update(("mccfishheart-update\n" + targetVersion + "\n" + sha256).getBytes(StandardCharsets.UTF_8));
			return verifier.verify(Base64.getDecoder().decode(signature));
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			Mccinfo.LOGGER.warn("Could not check the update signature", e);
			return false;
		}
	}


	// Read by the toast
	static String requiredVersion() {
		return version;
	}


	static boolean older() {
		return older;
	}

	static boolean allowed() {
		return allowed;
	}

	static boolean available() {
		return update != null;
	}

	static URI downloadUrl() {
		return downloadUrl;
	}

	// Download Link set by an admin, https only
	private static URI downloadLink(String link) {
		if (link == null || link.isBlank() || link.length() > 300) {
			return null;
		}
		try {
			URI uri = new URI(link.strip());
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri : null;
		} catch (java.net.URISyntaxException e) {
			return null;
		}
	}

	static State state() {
		return state;
	}

	static String error() {
		return error;
	}

	static boolean restartFailed() {
		return restartFailed;
	}

	// Download in the background, then restart
	static void install() {
		if (update == null || state == State.DOWNLOADING || state == State.INSTALLED) {
			return;
		}
		URI uri = SpotSync.httpUri(update.path());
		if (uri == null) {
			fail("invalid relay address");
			return;
		}
		Path current = currentJar();
		if (current == null) {
			fail("mod jar not found (development environment?)");
			return;
		}
		state = State.DOWNLOADING;
		error = null;
		UpdateInfo info = update;
		String targetVersion = version;
		Minecraft client = Minecraft.getInstance();
		CompletableFuture.supplyAsync(() -> {
			try {
				return download(uri, info, current, targetVersion);
			} catch (IOException e) {
				throw new java.util.concurrent.CompletionException(e);
			}
		}, net.minecraft.util.Util.nonCriticalIoPool()).whenComplete((installed, failure) -> client.execute(() -> {
			if (failure == null) {
				state = State.INSTALLED;
				restart(client, installed);
				return;
			}
			Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
			Mccinfo.LOGGER.warn("Could not update the mod", cause);
			fail(cause.getMessage() == null ? cause.toString() : cause.getMessage());
		}));
	}

	private static void fail(String message) {
		state = State.FAILED;
		error = message;
	}

	// Jar of the running mod, null in a development environment
	static Path currentJar() {
		ModOrigin origin = FabricLoader.getInstance().getModContainer(Mccinfo.MOD_ID).map(container -> container.getOrigin()).orElse(null);
		if (origin == null || origin.getKind() != ModOrigin.Kind.PATH || origin.getPaths().size() != 1) {
			return null;
		}
		Path jar = origin.getPaths().getFirst().toAbsolutePath();
		return Files.isRegularFile(jar) && jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar") ? jar : null;
	}

	private static Installed download(URI uri, UpdateInfo info, Path current, String targetVersion) throws IOException {
		String targetName = info.file() != null && FILE.matcher(info.file()).matches() ? info.file() : "mccinfo-" + targetVersion + ".jar";
		Path mods = current.getParent();
		Path downloaded = mods.resolve(targetName + DOWNLOAD_SUFFIX);
		Path target = mods.resolve(targetName);
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		try (HttpClient http = SpotSync.relayHttpClient().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()) {
			HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				response.body().close();
				throw new IOException("the relay answered " + response.statusCode());
			}
			try (InputStream in = response.body()) {
				copyChecked(in, downloaded, info, digest);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			Files.deleteIfExists(downloaded);
			throw new IOException("download interrupted", e);
		} catch (IOException e) {
			Files.deleteIfExists(downloaded);
			throw e;
		}
		// Hash and signature checks
		String sha256 = HexFormat.of().formatHex(digest.digest());
		if (!sha256.equals(info.sha256())) {
			Files.deleteIfExists(downloaded);
			throw new IOException("SHA-256 differs from the one announced by the relay");
		}
		if (!signatureValid(targetVersion, sha256, info.signature())) {
			Files.deleteIfExists(downloaded);
			throw new IOException("invalid signature: jar refused");
		}

		// Replace the jar now, or after closing if Windows locks it
		String commandLine = isWindows() ? windowsCommandLine() : null;
		boolean sameFile = current.equals(target);
		try {
			if (!sameFile) {
				Files.delete(current);
			}
			Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
			Mccinfo.LOGGER.info("Update installed: {} replaces {}", target, current);
			return new Installed(current, null, target, commandLine);
		} catch (IOException locked) {
			if (!isWindows()) {
				if (Files.exists(current)) {
					Files.deleteIfExists(downloaded);
				}
				throw locked;
			}
			Mccinfo.LOGGER.info("Update ready: {} will replace {} when the game closes", target, current);
			return new Installed(current, downloaded, target, commandLine);
		}
	}

	// Stops if the file is bigger or smaller than announced
	private static void copyChecked(InputStream in, Path downloaded, UpdateInfo info, MessageDigest digest) throws IOException {
		long written = 0;
		try (OutputStream out = Files.newOutputStream(downloaded)) {
			byte[] buffer = new byte[64 * 1024];
			for (int read; (read = in.read(buffer)) != -1; ) {
				written += read;
				if (written > info.size()) {
					throw new IOException("file bigger than announced");
				}
				digest.update(buffer, 0, read);
				out.write(buffer, 0, read);
			}
		}
		if (written != info.size()) {
			throw new IOException("incomplete file (" + written + "/" + info.size() + " bytes)");
		}
	}

	static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	// Command line of the game, to relaunch it the same way
	static String windowsCommandLine() {
		String script = "[Console]::OutputEncoding = [Text.Encoding]::UTF8\n"
				+ "(Get-CimInstance Win32_Process -Filter \"ProcessId=" + ProcessHandle.current().pid() + "\").CommandLine";
		try {
			Process process = new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encode(script)))
					.redirectError(ProcessBuilder.Redirect.DISCARD).start();
			process.getOutputStream().close();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0 || output.isEmpty()) {
				return null;
			}
			return output;
		} catch (IOException e) {
			Mccinfo.LOGGER.warn("Game command line not found", e);
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	// Restart failed = the toast asks for a manual restart
	private static void restart(Minecraft client, Installed installed) {
		try {
			if (isWindows()) {
				restartWindows(installed);
			} else if (!restartUnix()) {
				restartFailed = true;
				return;
			}
		} catch (IOException e) {
			Mccinfo.LOGGER.warn("Could not restart the game", e);
			restartFailed = true;
			return;
		}
		if (isWindows() && installed.commandLine() == null) {
			restartFailed = true;
			return;
		}
		client.stop();
	}

	// Hidden PowerShell script: wait for the game to close, swap the jar, relaunch
	private static void restartWindows(Installed installed) throws IOException {
		StringBuilder script = new StringBuilder("Wait-Process -Id " + ProcessHandle.current().pid() + " -ErrorAction SilentlyContinue\n");
		if (installed.downloaded() != null) {
			String current = quote(installed.current());
			script.append("for ($i = 0; $i -lt 40 -and (Test-Path -LiteralPath ").append(current).append("); $i++) {\n")
					.append("  Remove-Item -LiteralPath ").append(current).append(" -Force -ErrorAction SilentlyContinue\n")
					.append("  if (Test-Path -LiteralPath ").append(current).append(") { Start-Sleep -Milliseconds 500 }\n")
					.append("}\n")
					.append("if (Test-Path -LiteralPath ").append(current).append(") { Remove-Item -LiteralPath $PSCommandPath -Force -ErrorAction SilentlyContinue; exit }\n")
					.append("Move-Item -LiteralPath ").append(quote(installed.downloaded())).append(" -Destination ")
					.append(quote(installed.target())).append(" -Force\n");
		}
		if (installed.commandLine() != null) {
			String encodedCommand = Base64.getEncoder().encodeToString(installed.commandLine().getBytes(StandardCharsets.UTF_8));
			script.append("$cmd = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('").append(encodedCommand).append("'))\n")
					.append("if ($cmd.StartsWith('\"')) { $end = $cmd.IndexOf('\"', 1); $exe = $cmd.Substring(1, $end - 1); $rest = $cmd.Substring($end + 1) }\n")
					.append("else { $end = $cmd.IndexOf(' '); if ($end -lt 0) { $exe = $cmd; $rest = '' } else { $exe = $cmd.Substring(0, $end); $rest = $cmd.Substring($end + 1) } }\n")
					.append("$rest = $rest.Trim()\n")
					.append("$dir = ").append(quote(Path.of(System.getProperty("user.dir")).toAbsolutePath())).append("\n")
					.append("$info = New-Object System.Diagnostics.ProcessStartInfo($exe, $rest)\n")
					.append("$info.UseShellExecute = $false\n")
					.append("$info.WorkingDirectory = $dir\n")
					.append("[System.Diagnostics.Process]::Start($info) | Out-Null\n");
		}
		script.append("Remove-Item -LiteralPath $PSCommandPath -Force -ErrorAction SilentlyContinue\n");
		Path file = Files.createTempFile("mccinfo-update", ".ps1");
		Files.writeString(file, "\uFEFF" + script, StandardCharsets.UTF_8);
		new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
				"-File", file.toString()))
				.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
	}

	// Shell waits for the game to close, then relaunches it
	static boolean restartUnix() throws IOException {
		ProcessHandle.Info info = ProcessHandle.current().info();
		if (info.command().isEmpty() || info.arguments().isEmpty()) {
			return false;
		}
		List<String> command = new ArrayList<>(List.of("sh", "-c",
				"while kill -0 " + ProcessHandle.current().pid() + " 2>/dev/null; do sleep 0.5; done; exec \"$@\"", "sh", info.command().get()));
		command.addAll(List.of(info.arguments().get()));
		new ProcessBuilder(command).directory(Path.of(System.getProperty("user.dir")).toFile())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		return true;
	}

	private static String encode(String script) {
		return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
	}

	static String quote(Path path) {
		return "'" + path.toString().replace("'", "''") + "'";
	}
}
