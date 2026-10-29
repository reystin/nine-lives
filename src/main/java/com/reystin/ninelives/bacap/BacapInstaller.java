package com.reystin.ninelives.bacap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reystin.ninelives.NineLives;
import com.reystin.ninelives.advancement.AdvancementCounter;
import com.reystin.ninelives.core.Mode;
import com.reystin.ninelives.core.ModeManager;
import com.reystin.ninelives.core.Net;
import com.reystin.ninelives.core.ServerMessages;
import com.reystin.ninelives.net.PayloadTypes;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Авто-установка датапака BlazeandCave's Advancements Pack:
 * 1) детект отсутствия пака в datapacks мира (по содержимому zip);
 * 2) автоматический запуск при выборе режима BACAP (без диалога подтверждения);
 * 3) скачивание через API Modrinth с подбором версии под текущую игру;
 * 4) sha1-проверка (хэш берётся из ответа API, не зашивается в код);
 * 5) проверка zip, файл в datapacks, включение и /reload без выхода из мира.
 *
 * Разрешены только домены из конфига — и для API, и для самого файла, и для
 * каждого редиректа. Сам датапак в jar мода не включается.
 */
public final class BacapInstaller {
	private BacapInstaller() {
	}

	/** Уже запущена ли установка (защита от двойного запуска). */
	private static final AtomicBoolean INSTALLING = new AtomicBoolean(false);
	/** Была ли авто-установка в этом запуске сервера (ручной повтор — командой). */
	private static final AtomicBoolean AUTO_ATTEMPTED = new AtomicBoolean(false);

	private static final int MAX_REDIRECTS = 5;
	private static final long MAX_SIZE = 64L * 1024 * 1024;
	private static final String USER_AGENT = "ninelives-mod/1.0 (Minecraft Fabric mod)";

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			// Редиректы обрабатываем вручную: каждый хост проверяется по конфигу.
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	private record ResolvedVersion(String version, long size, String url, String sha1) {
	}

	private record Lookup(ResolvedVersion version, String errorKey, String detail) {
	}

	// =====================================================================
	// Детект
	// =====================================================================

	/** Есть ли BACAP в мире: ищем namespace датапака внутри zip'ов datapacks. */
	public static boolean isPackPresent(MinecraftServer server) {
		Path datapacks = server.getWorldPath(LevelResource.DATAPACK_DIR);
		if (!Files.isDirectory(datapacks)) return false;
		try (var files = Files.list(datapacks)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				if (Files.isDirectory(file)) {
					for (String ns : NineLives.config().bacapNamespaces) {
						if (Files.isDirectory(file.resolve("data").resolve(ns))) {
							return true;
						}
					}
					continue;
				}
				if (!name.toLowerCase().endsWith(".zip")) continue;
				try (ZipFile zip = new ZipFile(file.toFile())) {
					if (zipHasNamespace(zip)) return true;
				} catch (IOException ignored) {
					// Битый zip не считается установленным паком.
				}
			}
		} catch (IOException e) {
			NineLives.LOGGER.error("[Nine Lives] failed to scan datapacks", e);
		}
		return false;
	}

	private static boolean zipHasNamespace(ZipFile zip) {
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			String entry = entries.nextElement().getName();
			for (String ns : NineLives.config().bacapNamespaces) {
				if (entry.startsWith("data/" + ns + "/advancement")
						|| entry.startsWith("data/" + ns + "/advancements")) {
					return true;
				}
			}
		}
		return false;
	}

	// =====================================================================
	// Авто-установка
	// =====================================================================

	/** Сброс флагов между запусками сервера (в т.ч. интегрированного). */
	public static void resetSession() {
		AUTO_ATTEMPTED.set(false);
	}

	/** Вызывается при активации режима BACAP: сразу установить пак, если его нет. */
	public static void onModeActivated(MinecraftServer server) {
		if (isPackPresent(server)) return;
		AUTO_ATTEMPTED.set(true);
		notifyAutoStart(server);
		startInstall(server, null);
	}

	/**
	 * При входе хоста/оператора: если режим bc и пака нет — автоматически
	 * доустановить его. Повторная попытка в одном запуске сервера — только
	 * вручную командой /ninelives datapack install.
	 */
	public static void ensureInstalled(MinecraftServer server, ServerPlayer player, Set<UUID> notified) {
		if (!ServerMessages.isPrompter(player)) return;
		if (ModeManager.effectiveMode(server) != Mode.BACAP) return;
		if (isPackPresent(server)) return;

		if (INSTALLING.get()) {
			notifyAutoStart(player);
			return;
		}
		if (!AUTO_ATTEMPTED.compareAndSet(false, true)) {
			if (notified.add(player.getUUID())) {
				ServerMessages.chat(player, "ninelives.cmd.datapack_missing");
			}
			return;
		}
		notifyAutoStart(server);
		startInstall(server, null);
	}

	private static void notifyAutoStart(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerMessages.isPrompter(player)) {
				notifyAutoStart(player);
			}
		}
	}

	private static void notifyAutoStart(ServerPlayer player) {
		if (Net.isModded(player)) {
			Net.sendIfModded(player, new PayloadTypes.DownloadProgressPayload(
					PayloadTypes.DownloadProgressPayload.STAGE_PREPARING, 0, "", ""));
		} else {
			ServerMessages.chat(player, "ninelives.bacap.auto_started");
		}
	}

	// =====================================================================
	// Установка
	// =====================================================================

	/** Скачивание и установка в фоне (авто-запуск или ручная команда). */
	public static void startInstall(MinecraftServer server, ServerPlayer source) {
		if (!INSTALLING.compareAndSet(false, true)) {
			if (source != null) ServerMessages.chat(source, "ninelives.bacap.already_installing");
			return;
		}
		AUTO_ATTEMPTED.set(true);
		CompletableFuture.runAsync(() -> {
			try {
				Lookup lookup = resolveVersion();
				if (lookup.errorKey != null) {
					fail(server, lookup.errorKey, lookup.detail);
					return;
				}
				downloadAndInstall(server, lookup.version);
			} finally {
				INSTALLING.set(false);
			}
		});
	}

	// =====================================================================
	// Modrinth API
	// =====================================================================

	/** Запрос версии, подходящей текущей игре. Возвращает версию либо ключ ошибки. */
	private static Lookup resolveVersion() {
		try {
			String gameVersion = SharedConstants.getCurrentVersion().name();
			String query = "[\"" + gameVersion + "\"]";
			String loaders = "[\"datapack\"]";
			String url = "https://api.modrinth.com/v2/project/" + NineLives.config().bacapProject
					+ "/version?game_versions=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
					+ "&loaders=" + URLEncoder.encode(loaders, StandardCharsets.UTF_8);
			URI uri = URI.create(url);
			if (!isAllowed(uri)) {
				return new Lookup(null, "ninelives.bacap.fail_domains", String.valueOf(uri.getHost()));
			}
			HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(uri)
					.header("User-Agent", USER_AGENT)
					.timeout(Duration.ofSeconds(20))
					.GET().build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return new Lookup(null, "ninelives.bacap.fail_no_version", "HTTP " + response.statusCode());
			}
			JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
			if (versions.isEmpty()) {
				return new Lookup(null, "ninelives.bacap.fail_no_version", "no version for " + gameVersion);
			}

			// Modrinth уже отфильтровал, но перепроверяем и выбираем самую свежую.
			JsonObject newest = null;
			String newestDate = "";
			for (JsonElement element : versions) {
				JsonObject version = element.getAsJsonObject();
				if (!jsonArrayContains(version.getAsJsonArray("game_versions"), gameVersion)) continue;
				if (!jsonArrayContains(version.getAsJsonArray("loaders"), "datapack")) continue;
				String date = version.has("date_published") ? version.get("date_published").getAsString() : "";
				if (newest == null || date.compareTo(newestDate) > 0) {
					newest = version;
					newestDate = date;
				}
			}
			if (newest == null) {
				return new Lookup(null, "ninelives.bacap.fail_no_version", "no matching version");
			}

			JsonObject primaryFile = null;
			JsonArray files = newest.getAsJsonArray("files");
			for (JsonElement fileEl : files) {
				JsonObject file = fileEl.getAsJsonObject();
				if (file.has("primary") && file.get("primary").getAsBoolean()) {
					primaryFile = file;
					break;
				}
			}
			if (primaryFile == null && !files.isEmpty()) {
				primaryFile = files.get(0).getAsJsonObject();
			}
			if (primaryFile == null || !primaryFile.has("url")) {
				return new Lookup(null, "ninelives.bacap.fail_no_version", "no file in version");
			}

			URI fileUri = URI.create(primaryFile.get("url").getAsString());
			if (!isAllowed(fileUri)) {
				return new Lookup(null, "ninelives.bacap.fail_domains", String.valueOf(fileUri.getHost()));
			}
			// Хэш из ответа API обязателен (не зашиваем и не считаем сами как эталон).
			if (!primaryFile.has("hashes") || !primaryFile.getAsJsonObject("hashes").has("sha1")) {
				return new Lookup(null, "ninelives.bacap.fail_no_hash", "sha1 missing from API response");
			}
			String sha1 = primaryFile.getAsJsonObject("hashes").get("sha1").getAsString();
			if (sha1.isBlank()) {
				return new Lookup(null, "ninelives.bacap.fail_no_hash", "sha1 empty in API response");
			}
			String versionNumber = newest.has("version_number")
					? newest.get("version_number").getAsString() : "?";
			long size = primaryFile.has("size") ? primaryFile.get("size").getAsLong() : -1;
			if (size > MAX_SIZE) {
				return new Lookup(null, "ninelives.bacap.fail_too_large", humanSize(size));
			}
			return new Lookup(new ResolvedVersion(versionNumber, size, fileUri.toString(), sha1), null, null);
		} catch (Exception e) {
			NineLives.LOGGER.error("[Nine Lives] BACAP version lookup failed", e);
			return new Lookup(null, "ninelives.bacap.fail_download", e.getClass().getSimpleName());
		}
	}

	private static boolean jsonArrayContains(JsonArray array, String value) {
		if (array == null) return false;
		for (JsonElement element : array) {
			if (value.equals(element.getAsString())) return true;
		}
		return false;
	}

	// =====================================================================
	// Скачивание и установка
	// =====================================================================

	private static void downloadAndInstall(MinecraftServer server, ResolvedVersion resolved) {
		Path temp = null;
		try {
			report(server, PayloadTypes.DownloadProgressPayload.STAGE_DOWNLOADING, 0, "");
			temp = Files.createTempFile("ninelives-bacap", ".zip");
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			downloadTo(temp, resolved, digest, server);
			String sha1 = HexFormat.of().formatHex(digest.digest());
			if (!sha1.equalsIgnoreCase(resolved.sha1)) {
				Files.deleteIfExists(temp);
				fail(server, "ninelives.bacap.fail_hash", "sha1 mismatch: " + sha1);
				return;
			}
			report(server, PayloadTypes.DownloadProgressPayload.STAGE_VERIFYING, 100, "");

			if (!isValidPackZip(temp)) {
				Files.deleteIfExists(temp);
				fail(server, "ninelives.bacap.fail_invalid_zip", "pack.mcmeta or data/<namespace> missing");
				return;
			}

			Path datapacks = server.getWorldPath(LevelResource.DATAPACK_DIR);
			Files.createDirectories(datapacks);
			String fileName = "BlazeandCaves-Advancements-Pack-"
					+ resolved.version.replaceAll("[^A-Za-z0-9._-]", "_") + ".zip";
			Path target = datapacks.resolve(fileName);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			temp = null;
			report(server, PayloadTypes.DownloadProgressPayload.STAGE_ENABLING, 100, "");

			final String installedFile = fileName;
			final String installedVersion = resolved.version;
			server.execute(() -> {
				try {
					// Явно включаем пак: новый zip в world/datapacks сам не активируется.
					String packId = "file/" + installedFile;
					server.getCommands().performPrefixedCommand(
							server.createCommandSourceStack(), "datapack enable \"" + packId + "\"");
					server.getCommands().performPrefixedCommand(
							server.createCommandSourceStack(), "reload");
				} catch (Exception e) {
					NineLives.LOGGER.error("[Nine Lives] failed to enable/reload BACAP", e);
				}
				AdvancementCounter.recalculateAll(server); // дублирует reload-хук, но не вредит
				ModeManager.refreshClients(server);
				notifyPrompters(server, player -> {
					ServerMessages.chat(player, "ninelives.bacap.installed", installedVersion);
					Net.sendIfModded(player, new PayloadTypes.DownloadProgressPayload(
							PayloadTypes.DownloadProgressPayload.STAGE_DONE, 100, "", ""));
				});
				NineLives.LOGGER.info("[Nine Lives] BACAP {} installed as {}", installedVersion, installedFile);
			});
		} catch (Exception e) {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
				}
			}
			NineLives.LOGGER.error("[Nine Lives] BACAP download failed", e);
			fail(server, "ninelives.bacap.fail_download", e.getClass().getSimpleName());
		}
	}

	/** Скачивание с ручной обработкой редиректов и лимитом размера. */
	private static void downloadTo(Path temp, ResolvedVersion resolved, MessageDigest digest,
			MinecraftServer server) throws Exception {
		URI uri = URI.create(resolved.url);
		for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
			if (!isAllowed(uri)) {
				throw new IOException("blocked download host: " + uri.getHost());
			}
			HttpResponse<InputStream> response = HTTP.send(HttpRequest.newBuilder(uri)
					.header("User-Agent", USER_AGENT)
					.timeout(Duration.ofMinutes(5))
					.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			int code = response.statusCode();
			if (code >= 300 && code < 400) {
				String location = response.headers().firstValue("Location").orElse(null);
				response.body().close();
				if (location == null) throw new IOException("redirect without Location");
				uri = uri.resolve(location);
				continue;
			}
			if (code != 200) {
				response.body().close();
				throw new IOException("HTTP " + code);
			}
			long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
			if (contentLength > MAX_SIZE) {
				response.body().close();
				throw new IOException("file too large: " + contentLength);
			}
			long expected = resolved.size > 0 ? resolved.size : contentLength > 0 ? contentLength : 1L;
			long total = 0;
			int lastPercent = -1;
			try (InputStream in = response.body(); var out = Files.newOutputStream(temp)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) > 0) {
					total += read;
					if (total > MAX_SIZE) throw new IOException("size cap exceeded");
					out.write(buffer, 0, read);
					digest.update(buffer, 0, read);
					int percent = (int) Math.min(100, total * 100 / expected);
					if (percent >= lastPercent + 10 && percent < 100) {
						lastPercent = percent;
						report(server, PayloadTypes.DownloadProgressPayload.STAGE_DOWNLOADING, percent, "");
					}
				}
			}
			return;
		}
		throw new IOException("too many redirects");
	}

	private static boolean isValidPackZip(Path file) {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			if (zip.getEntry("pack.mcmeta") == null) return false;
			return zipHasNamespace(zip);
		} catch (IOException e) {
			return false;
		}
	}

	// =====================================================================
	// Ошибки и прогресс
	// =====================================================================

	/** Сообщение об ошибке + ссылка на ручную установку + откат на режим 1. */
	private static void fail(MinecraftServer server, String errorKey, String detail) {
		NineLives.LOGGER.error("[Nine Lives] BACAP install failed: {} ({})", errorKey, detail);
		server.execute(() -> {
			notifyPrompters(server, player -> {
				ServerMessages.chat(player, errorKey, errorArgs(errorKey, detail));
				Net.sendIfModded(player, new PayloadTypes.DownloadProgressPayload(
						PayloadTypes.DownloadProgressPayload.STAGE_ERROR, 0, errorKey, detail));
			});
			if (NineLives.config().fallbackToVanillaOnFailure
					&& ModeManager.effectiveMode(server) == Mode.BACAP) {
				ModeManager.setMode(server, Mode.VANILLA); // включает пересчёт
				notifyPrompters(server, player -> ServerMessages.chat(player, "ninelives.bacap.fallback"));
			}
		});
	}

	/** Набор аргументов зависит от числа %s в конкретном ключе. */
	private static Object[] errorArgs(String errorKey, String detail) {
		return switch (errorKey) {
			case "ninelives.bacap.fail_no_version" ->
					new Object[] { SharedConstants.getCurrentVersion().name(), manualLink() };
			case "ninelives.bacap.fail_download" -> new Object[] { detail, manualLink() };
			default -> new Object[] { manualLink() };
		};
	}

	/** Прогресс: экран (полная версия) и action bar (серверная). */
	private static void report(MinecraftServer server, int stage, int percent, String errorKey) {
		server.execute(() -> notifyPrompters(server, player -> {
			Net.sendIfModded(player, new PayloadTypes.DownloadProgressPayload(stage, percent, errorKey, ""));
			if (stage == PayloadTypes.DownloadProgressPayload.STAGE_DOWNLOADING
					&& (percent == 25 || percent == 50 || percent == 75)) {
				ServerMessages.actionbar(player, "ninelives.bacap.downloading", percent);
			} else if (stage == PayloadTypes.DownloadProgressPayload.STAGE_VERIFYING
					|| stage == PayloadTypes.DownloadProgressPayload.STAGE_ENABLING) {
				ServerMessages.actionbar(player, "ninelives.bacap.install_progress");
			}
		}));
	}

	private static void notifyPrompters(MinecraftServer server,
			java.util.function.Consumer<ServerPlayer> action) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerMessages.isPrompter(player)) {
				action.accept(player);
			}
		}
	}

	/** Кликабельная ссылка на страницу ручной установки. */
	private static Component manualLink() {
		return Component.literal("[" + com.reystin.ninelives.config.L10n.fallback("ninelives.bacap.manual_link") + "]")
				.withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)
						.withUnderlined(true)
						.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(
								java.net.URI.create(NineLives.config().bacapManualUrl))));
	}

	/** Хост из белого списка конфига; только https. */
	private static boolean isAllowed(URI uri) {
		if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
		String host = uri.getHost();
		return host != null && NineLives.config().allowedDomains.contains(host);
	}

	private static String humanSize(long bytes) {
		if (bytes < 0) return "?";
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
		return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
	}

	/** Список zip-файлов datapacks (для отладки). */
	public static List<String> datapackFiles(MinecraftServer server) {
		List<String> names = new ArrayList<>();
		Path datapacks = server.getWorldPath(LevelResource.DATAPACK_DIR);
		if (!Files.isDirectory(datapacks)) return names;
		try (var files = Files.list(datapacks)) {
			files.forEach(p -> names.add(p.getFileName().toString()));
		} catch (IOException ignored) {
		}
		return names;
	}
}
