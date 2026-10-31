package leroidesafk.mccinfo.client.update;

import leroidesafk.mccinfo.Mccinfo;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Deletes the mod jar of a banned player, then restarts the game (best way to keep VoiceChatSpammer far from us)
public final class ModUninstaller {
	private static boolean started;

	private ModUninstaller() {
	}

	public static void banned(boolean banned) {
		if (!banned || started) {
			return;
		}
		Path jar = ModUpdater.currentJar();
		if (jar == null) {
			return;
		}
		started = true;
		Minecraft client = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			try {
				if (ModUpdater.isWindows()) {
					startWindows(jar, ModUpdater.windowsCommandLine());
				} else {
					Files.deleteIfExists(jar);
					ModUpdater.restartUnix();
				}
			} catch (IOException e) {
				Mccinfo.LOGGER.warn("Could not uninstall the mod", e);
			}
		}, Util.nonCriticalIoPool()).whenComplete((ignored, failure) -> client.execute(client::stop));
	}

	// Windows locks the jar: a hidden PowerShell script deletes it once the game is closed
	private static void startWindows(Path jar, String commandLine) throws IOException {
		String current = ModUpdater.quote(jar);
		StringBuilder script = new StringBuilder("Wait-Process -Id " + ProcessHandle.current().pid() + " -ErrorAction SilentlyContinue\n")
				.append("for ($i = 0; $i -lt 40 -and (Test-Path -LiteralPath ").append(current).append("); $i++) {\n")
				.append("  Remove-Item -LiteralPath ").append(current).append(" -Force -ErrorAction SilentlyContinue\n")
				.append("  if (Test-Path -LiteralPath ").append(current).append(") { Start-Sleep -Milliseconds 500 }\n")
				.append("}\n");
		// Relaunch the game with the same command line
		if (commandLine != null) {
			String encodedCommand = Base64.getEncoder().encodeToString(commandLine.getBytes(StandardCharsets.UTF_8));
			script.append("if (-not (Test-Path -LiteralPath ").append(current).append(")) {\n")
					.append("$cmd = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('").append(encodedCommand).append("'))\n")
					.append("if ($cmd.StartsWith('\"')) { $end = $cmd.IndexOf('\"', 1); $exe = $cmd.Substring(1, $end - 1); $rest = $cmd.Substring($end + 1) }\n")
					.append("else { $end = $cmd.IndexOf(' '); if ($end -lt 0) { $exe = $cmd; $rest = '' } else { $exe = $cmd.Substring(0, $end); $rest = $cmd.Substring($end + 1) } }\n")
					.append("$rest = $rest.Trim()\n")
					.append("$info = New-Object System.Diagnostics.ProcessStartInfo($exe, $rest)\n")
					.append("$info.UseShellExecute = $false\n")
					.append("$info.WorkingDirectory = ").append(ModUpdater.quote(Path.of(System.getProperty("user.dir")).toAbsolutePath())).append("\n")
					.append("[System.Diagnostics.Process]::Start($info) | Out-Null\n")
					.append("}\n");
		}
		// The script deletes itself
		script.append("Remove-Item -LiteralPath $PSCommandPath -Force -ErrorAction SilentlyContinue\n");
		Path file = Files.createTempFile("mccinfo-cleanup", ".ps1");
		Files.writeString(file, "﻿" + script, StandardCharsets.UTF_8);
		new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
				"-File", file.toString()))
				.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
	}
}
