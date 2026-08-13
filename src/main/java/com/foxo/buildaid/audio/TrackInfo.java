package com.foxo.buildaid.audio;

import com.google.gson.JsonObject;

/**
 * Representa uma faixa de audio (musica, stream, video do YouTube).
 */
public record TrackInfo(
		String id,
		String title,
		String author,
		String originalUrl,
		String streamUrl,
		long durationSeconds,
		String addedBy,
		String thumbnailUrl
) {
	public static final TrackInfo EMPTY = new TrackInfo("", "", "", "", "", 0, "", "");

	public boolean isEmpty() {
		return title == null || title.isBlank();
	}

	public String formattedDuration() {
		if (durationSeconds <= 0) {
			return "--:--";
		}
		long minutes = durationSeconds / 60;
		long seconds = durationSeconds % 60;
		if (minutes >= 60) {
			long hours = minutes / 60;
			minutes = minutes % 60;
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format("%d:%02d", minutes, seconds);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("id", id != null ? id : "");
		json.addProperty("title", title != null ? title : "");
		json.addProperty("author", author != null ? author : "");
		json.addProperty("originalUrl", originalUrl != null ? originalUrl : "");
		json.addProperty("streamUrl", streamUrl != null ? streamUrl : "");
		json.addProperty("durationSeconds", durationSeconds);
		json.addProperty("addedBy", addedBy != null ? addedBy : "");
		json.addProperty("thumbnailUrl", thumbnailUrl != null ? thumbnailUrl : "");
		return json;
	}

	public static TrackInfo fromJson(JsonObject json) {
		if (json == null) {
			return EMPTY;
		}
		String id = json.has("id") ? json.get("id").getAsString() : "";
		String title = json.has("title") ? json.get("title").getAsString() : "";
		String author = json.has("author") ? json.get("author").getAsString() : "";
		String orig = json.has("originalUrl") ? json.get("originalUrl").getAsString() : "";
		String stream = json.has("streamUrl") ? json.get("streamUrl").getAsString() : "";
		long dur = json.has("durationSeconds") ? json.get("durationSeconds").getAsLong() : 0;
		String addedBy = json.has("addedBy") ? json.get("addedBy").getAsString() : "";
		String thumb = json.has("thumbnailUrl") ? json.get("thumbnailUrl").getAsString() : "";
		return new TrackInfo(id, title, author, orig, stream, dur, addedBy, thumb);
	}
}
