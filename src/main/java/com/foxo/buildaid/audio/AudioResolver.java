package com.foxo.buildaid.audio;

import com.foxo.buildaid.BuildAid;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolve URLs de audio, incluindo links diretos (MP3/radios) e links do YouTube.
 */
public final class AudioResolver {
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
			"(?:https?:\\/\\/)?(?:www\\.|m\\.|music\\.)?(?:youtube\\.com\\/(?:watch\\?v=|shorts\\/|embed\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
	);

	// Lista de instancias publicas e confiaveis de API Invidious/Piped para fallback
	private static final List<String> INVIDIOUS_INSTANCES = List.of(
			"https://invidious.nerdvpn.de",
			"https://inv.tux.pizza",
			"https://vid.puffyan.us",
			"https://invidious.protokolla.fi"
	);

	private AudioResolver() {
	}

	public static CompletableFuture<TrackInfo> resolve(String inputUrl, String addedBy, String relayServerUrl) {
		if (inputUrl == null || inputUrl.isBlank()) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("URL vazia"));
		}

		String trimmed = inputUrl.trim();
		String youtubeId = extractYouTubeId(trimmed);

		if (youtubeId != null) {
			return resolveYouTube(youtubeId, trimmed, addedBy, relayServerUrl);
		}

		// Link direto de audio (MP3, Radio, etc.)
		return resolveDirect(trimmed, addedBy);
	}

	public static String extractYouTubeId(String url) {
		if (url == null) return null;
		Matcher matcher = YOUTUBE_PATTERN.matcher(url);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	private static CompletableFuture<TrackInfo> resolveDirect(String url, String addedBy) {
		String title = extractFilenameOrDomain(url);
		String trackId = UUID.randomUUID().toString().substring(0, 8);
		TrackInfo track = new TrackInfo(
				trackId,
				title,
				"Stream Web",
				url,
				url,
				0, // Duracao desconhecida/ao vivo
				addedBy,
				""
		);
		return CompletableFuture.completedFuture(track);
	}

	private static CompletableFuture<TrackInfo> resolveYouTube(String videoId, String origUrl, String addedBy, String relayServerUrl) {
		String serverEndpoint = (relayServerUrl != null && !relayServerUrl.isBlank() && relayServerUrl.startsWith("http"))
				? relayServerUrl.replaceAll("/+$", "")
				: "https://buildaid-sync-server.onrender.com";

		String endpoint = serverEndpoint + "/api/resolve?v=" + videoId;
		return fetchJson(endpoint).thenApply(json -> {
			if (json != null && json.has("streamUrl") && !json.get("streamUrl").getAsString().isBlank()) {
				String streamUrl = json.get("streamUrl").getAsString();
				String title = json.has("title") ? json.get("title").getAsString() : "YouTube (" + videoId + ")";
				String author = json.has("author") ? json.get("author").getAsString() : "YouTube";
				long duration = json.has("durationSeconds") ? json.get("durationSeconds").getAsLong() : 0;
				String thumb = json.has("thumbnailUrl") ? json.get("thumbnailUrl").getAsString() : "";
				return new TrackInfo(videoId, title, author, origUrl, streamUrl, duration, addedBy, thumb);
			}
			throw new RuntimeException("Stream de audio indisponivel no servidor de relay");
		}).exceptionallyCompose(err -> resolveInvidiousFallback(videoId, origUrl, addedBy));
	}

	private static CompletableFuture<TrackInfo> resolveInvidiousFallback(String videoId, String origUrl, String addedBy) {
		return attemptInvidiousInstances(videoId, origUrl, addedBy, 0);
	}

	private static CompletableFuture<TrackInfo> attemptInvidiousInstances(String videoId, String origUrl, String addedBy, int index) {
		if (index >= INVIDIOUS_INSTANCES.size()) {
			return CompletableFuture.failedFuture(new RuntimeException("buildaid.msg.music_stream_unavailable"));
		}

		String baseUrl = INVIDIOUS_INSTANCES.get(index);
		String endpoint = baseUrl + "/api/v1/videos/" + videoId;

		return fetchJson(endpoint).thenApply(json -> {
			if (json == null) throw new RuntimeException("JSON nulo");

			String title = json.has("title") ? json.get("title").getAsString() : "YouTube (" + videoId + ")";
			String author = json.has("author") ? json.get("author").getAsString() : "YouTube";
			long duration = json.has("lengthSeconds") ? json.get("lengthSeconds").getAsLong() : 0;
			String thumb = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";

			String streamUrl = "";
			if (json.has("adaptiveFormats")) {
				JsonArray formats = json.getAsJsonArray("adaptiveFormats");
				for (JsonElement elem : formats) {
					JsonObject fmt = elem.getAsJsonObject();
					String type = fmt.has("type") ? fmt.get("type").getAsString() : "";
					if (type.contains("audio/mp4") || type.contains("audio/webm") || type.contains("audio/mpeg")) {
						if (fmt.has("url")) {
							streamUrl = fmt.get("url").getAsString();
							break;
						}
					}
				}
			}

			if (streamUrl.isBlank() && json.has("formatStreams")) {
				JsonArray formatStreams = json.getAsJsonArray("formatStreams");
				if (!formatStreams.isEmpty()) {
					JsonObject first = formatStreams.get(0).getAsJsonObject();
					if (first.has("url")) {
						streamUrl = first.get("url").getAsString();
					}
				}
			}

			if (streamUrl.isBlank()) {
				throw new RuntimeException("Sem streamUrl");
			}

			return new TrackInfo(videoId, title, author, origUrl, streamUrl, duration, addedBy, thumb);
		}).exceptionallyCompose(err -> {
			BuildAid.LOGGER.warn("[AudioResolver] Falha na instancia {}, tentando proxima...", baseUrl);
			return attemptInvidiousInstances(videoId, origUrl, addedBy, index + 1);
		});
	}

	private static CompletableFuture<JsonObject> fetchJson(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.header("User-Agent", "BuildAid-MinecraftMod/1.0")
					.header("Accept", "application/json")
					.timeout(Duration.ofSeconds(6))
					.GET()
					.build();

			return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
					.thenApply(response -> {
						if (response.statusCode() >= 200 && response.statusCode() < 300) {
							return JsonParser.parseString(response.body()).getAsJsonObject();
						}
						throw new RuntimeException("Status HTTP: " + response.statusCode());
					});
		} catch (Exception e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	private static String extractFilenameOrDomain(String url) {
		try {
			URI uri = URI.create(url);
			String path = uri.getPath();
			if (path != null && !path.isBlank() && !path.equals("/")) {
				String[] parts = path.split("/");
				String last = parts[parts.length - 1];
				if (!last.isBlank()) {
					return last;
				}
			}
			return uri.getHost() != null ? uri.getHost() : "Stream Web";
		} catch (Exception e) {
			return "Stream Web";
		}
	}
}
