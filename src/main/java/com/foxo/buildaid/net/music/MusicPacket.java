package com.foxo.buildaid.net.music;

import com.foxo.buildaid.audio.TrackInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Protocolo de mensagens JSON transmitidas via WebSocket entre clientes BuildAid e o servidor de sincronizacao.
 */
public final class MusicPacket {
	// Acoes do cliente -> servidor
	public static final String JOIN_ROOM = "JOIN_ROOM";
	public static final String LEAVE_ROOM = "LEAVE_ROOM";
	public static final String ADD_QUEUE = "ADD_QUEUE";
	public static final String REMOVE_QUEUE = "REMOVE_QUEUE";
	public static final String PLAY_INDEX = "PLAY_INDEX";
	public static final String TOGGLE_PLAY = "TOGGLE_PLAY";
	public static final String SKIP = "SKIP";
	public static final String SEEK = "SEEK";
	public static final String CHAT = "CHAT";

	// Respostas do servidor -> cliente
	public static final String SYNC_STATE = "SYNC_STATE";
	public static final String USER_JOINED = "USER_JOINED";
	public static final String USER_LEFT = "USER_LEFT";
	public static final String CHAT_BROADCAST = "CHAT_BROADCAST";
	public static final String ERROR = "ERROR";

	private MusicPacket() {
	}

	public static String createJoin(String roomId, String playerName) {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", JOIN_ROOM);
		obj.addProperty("roomId", roomId);
		obj.addProperty("playerName", playerName);
		return obj.toString();
	}

	public static String createAddQueue(TrackInfo track) {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", ADD_QUEUE);
		obj.add("track", track.toJson());
		return obj.toString();
	}

	public static String createRemoveQueue(int index) {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", REMOVE_QUEUE);
		obj.addProperty("index", index);
		return obj.toString();
	}

	public static String createPlayIndex(int index) {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", PLAY_INDEX);
		obj.addProperty("index", index);
		return obj.toString();
	}

	public static String createTogglePlay() {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", TOGGLE_PLAY);
		return obj.toString();
	}

	public static String createSkip() {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", SKIP);
		return obj.toString();
	}

	public static String createSeek(long seconds) {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", SEEK);
		obj.addProperty("seconds", seconds);
		return obj.toString();
	}

	public static String createChat(String message) {
		JsonObject obj = new JsonObject();
		obj.addProperty("type", CHAT);
		obj.addProperty("message", message);
		return obj.toString();
	}

	/**
	 * Estado completo de uma sala sincronizada.
	 */
	public record RoomState(
			String roomId,
			TrackInfo currentTrack,
			List<TrackInfo> queue,
			boolean isPlaying,
			long startedEpochMs,
			long pausedPositionMs,
			List<String> members
	) {
		public static final RoomState EMPTY = new RoomState("", TrackInfo.EMPTY, List.of(), false, 0, 0, List.of());

		public static RoomState fromJson(JsonObject obj) {
			if (obj == null) return EMPTY;

			String roomId = obj.has("roomId") ? obj.get("roomId").getAsString() : "";
			TrackInfo current = obj.has("currentTrack") && obj.get("currentTrack").isJsonObject()
					? TrackInfo.fromJson(obj.getAsJsonObject("currentTrack"))
					: TrackInfo.EMPTY;

			List<TrackInfo> queue = new ArrayList<>();
			if (obj.has("queue") && obj.get("queue").isJsonArray()) {
				for (JsonElement el : obj.getAsJsonArray("queue")) {
					if (el.isJsonObject()) {
						queue.add(TrackInfo.fromJson(el.getAsJsonObject()));
					}
				}
			}

			boolean isPlaying = obj.has("isPlaying") && obj.get("isPlaying").getAsBoolean();
			long started = obj.has("startedEpochMs") ? obj.get("startedEpochMs").getAsLong() : 0;
			long pausedPos = obj.has("pausedPositionMs") ? obj.get("pausedPositionMs").getAsLong() : 0;

			List<String> members = new ArrayList<>();
			if (obj.has("members") && obj.get("members").isJsonArray()) {
				for (JsonElement el : obj.getAsJsonArray("members")) {
					members.add(el.getAsString());
				}
			}

			return new RoomState(roomId, current, queue, isPlaying, started, pausedPos, members);
		}
	}
}
