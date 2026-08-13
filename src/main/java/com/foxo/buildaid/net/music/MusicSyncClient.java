package com.foxo.buildaid.net.music;

import com.foxo.buildaid.BuildAid;
import com.foxo.buildaid.Feedback;
import com.foxo.buildaid.audio.AudioPlayer;
import com.foxo.buildaid.audio.AudioResolver;
import com.foxo.buildaid.audio.TrackInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Cliente WebSocket de sincronizacao de salas de musica e controle local.
 */
public final class MusicSyncClient implements WebSocket.Listener {
	private static final MusicSyncClient INSTANCE = new MusicSyncClient();

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(6))
			.build();

	private WebSocket webSocket;
	private String currentServerUrl = "wss://buildaid-sync-server.onrender.com";
	private String currentRoomId = "";
	private String playerName = "Player";

	// Estado local (usado tanto em modo solo quanto espelhado da sala conectada)
	private MusicPacket.RoomState state = MusicPacket.RoomState.EMPTY;
	private final List<Consumer<MusicPacket.RoomState>> listeners = new CopyOnWriteArrayList<>();
	private final StringBuilder messageBuffer = new StringBuilder();

	// Fila local para quando o usuario estiver em modo Solo (sem servidor)
	private final List<TrackInfo> localQueue = new ArrayList<>();
	private int localQueueIndex = -1;

	private MusicSyncClient() {
	}

	public static MusicSyncClient get() {
		return INSTANCE;
	}

	public void addListener(Consumer<MusicPacket.RoomState> listener) {
		listeners.add(listener);
		listener.accept(state);
	}

	public void removeListener(Consumer<MusicPacket.RoomState> listener) {
		listeners.remove(listener);
	}

	private void notifyListeners() {
		for (Consumer<MusicPacket.RoomState> listener : listeners) {
			try {
				listener.accept(state);
			} catch (Exception ignored) {
			}
		}
	}

	public boolean isConnected() {
		return webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
	}

	public boolean isInRoom() {
		return isConnected() && !currentRoomId.isBlank();
	}

	public String getCurrentRoomId() {
		return currentRoomId;
	}

	public MusicPacket.RoomState getState() {
		return state;
	}

	public static String detectServerRoomId(Minecraft client) {
		if (client == null) return "geral";
		if (client.getCurrentServer() != null) {
			String ip = client.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT).trim();
			ip = ip.replace(":25565", "");
			return "srv_" + ip.replaceAll("[^a-zA-Z0-9.-]", "_");
		}
		if (client.hasSingleplayerServer()) {
			return "singleplayer";
		}
		return "geral";
	}

	public static String detectServerDisplayName(Minecraft client) {
		if (client == null) return "Geral";
		if (client.getCurrentServer() != null) {
			String name = client.getCurrentServer().name;
			if (name != null && !name.isBlank()) return name;
			return client.getCurrentServer().ip;
		}
		if (client.hasSingleplayerServer()) {
			return "Mundo Local";
		}
		return "Menu Principal";
	}

	public void autoJoinCurrentServer(Minecraft client) {
		com.foxo.buildaid.config.BuildAidConfig config = com.foxo.buildaid.config.BuildAidConfig.get();
		if (!config.music.autoServerRoom) {
			return;
		}
		String roomId = detectServerRoomId(client);
		String user = client.player != null ? client.player.getName().getString() : playerName;
		connectAndJoin(config.music.serverUrl, roomId, user, true);
	}

	public void onServerLeave() {
		com.foxo.buildaid.config.BuildAidConfig config = com.foxo.buildaid.config.BuildAidConfig.get();
		if (config.music.autoServerRoom && isInRoom()) {
			leaveRoom();
		}
	}

	// ---------------------------------------------------------------- Conexao & Salas

	public synchronized void connectAndJoin(String serverUrl, String roomId, String username) {
		connectAndJoin(serverUrl, roomId, username, false);
	}

	public synchronized void connectAndJoin(String serverUrl, String roomId, String username, boolean silent) {
		if (serverUrl == null || serverUrl.isBlank()) {
			serverUrl = "ws://localhost:3000";
		}
		this.currentServerUrl = serverUrl.trim();
		this.currentRoomId = roomId != null ? roomId.trim() : "";
		this.playerName = username != null && !username.isBlank() ? username : "Player";

		if (webSocket != null) {
			try {
				webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnecting");
			} catch (Exception ignored) {
			}
			webSocket = null;
		}

		try {
			URI uri = URI.create(currentServerUrl);
			httpClient.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(4))
					.buildAsync(uri, this)
					.thenAccept(ws -> {
						this.webSocket = ws;
						BuildAid.LOGGER.info("[MusicSyncClient] Conectado ao servidor: {}", currentServerUrl);
						if (!currentRoomId.isBlank()) {
							send(MusicPacket.createJoin(currentRoomId, playerName));
						}
					})
					.exceptionally(err -> {
						BuildAid.LOGGER.debug("[MusicSyncClient] Servidor {} indisponivel: {}", currentServerUrl, err.getMessage());
						if (!silent) {
							Feedback.error("buildaid.msg.music_connect_failed", err.getMessage());
						}
						return null;
					});
		} catch (Exception e) {
			if (!silent) {
				Feedback.error("buildaid.msg.music_invalid_server");
			}
		}
	}

	public synchronized void leaveRoom() {
		if (isConnected() && !currentRoomId.isBlank()) {
			send(MusicPacket.createJoin("", playerName));
		}
		currentRoomId = "";
		state = MusicPacket.RoomState.EMPTY;
		AudioPlayer.get().stop();
		notifyListeners();
	}

	public synchronized void disconnect() {
		leaveRoom();
		if (webSocket != null) {
			try {
				webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "User disconnected");
			} catch (Exception ignored) {
			}
			webSocket = null;
		}
	}

	// ---------------------------------------------------------------- Acoes de Reproducao

	public void addTrack(String url) {
		Minecraft client = Minecraft.getInstance();
		String user = client.player != null ? client.player.getName().getString() : playerName;

		AudioResolver.resolve(url, user, httpUrlFromWs(currentServerUrl))
				.thenAccept(track -> {
					if (track.isEmpty() || track.streamUrl().isBlank()) {
						Feedback.error("buildaid.msg.music_resolve_failed");
						return;
					}

					if (isInRoom()) {
						send(MusicPacket.createAddQueue(track));
						Feedback.info("buildaid.msg.music_added", track.title());
					} else {
						// Modo Solo local
						localQueue.add(track);
						if (state.currentTrack().isEmpty()) {
							playLocalIndex(localQueue.size() - 1);
						} else {
							updateLocalState(state.currentTrack(), isPlayingSolo());
							Feedback.info("buildaid.msg.music_added", track.title());
						}
					}
				})
				.exceptionally(err -> {
					Feedback.error("buildaid.msg.music_resolve_failed");
					return null;
				});
	}

	public void playIndex(int index) {
		if (isInRoom()) {
			send(MusicPacket.createPlayIndex(index));
		} else {
			playLocalIndex(index);
		}
	}

	public void togglePlay() {
		if (isInRoom()) {
			send(MusicPacket.createTogglePlay());
		} else {
			if (state.currentTrack().isEmpty() && !localQueue.isEmpty()) {
				playLocalIndex(0);
				return;
			}
			AudioPlayer.get().togglePause();
			updateLocalState(state.currentTrack(), AudioPlayer.get().isPlaying());
		}
	}

	public void skip() {
		if (isInRoom()) {
			send(MusicPacket.createSkip());
		} else {
			if (localQueueIndex + 1 < localQueue.size()) {
				playLocalIndex(localQueueIndex + 1);
			} else {
				AudioPlayer.get().stop();
				localQueueIndex = -1;
				updateLocalState(TrackInfo.EMPTY, false);
			}
		}
	}

	public void removeQueue(int index) {
		if (isInRoom()) {
			send(MusicPacket.createRemoveQueue(index));
		} else {
			if (index >= 0 && index < localQueue.size()) {
				localQueue.remove(index);
				if (index == localQueueIndex) {
					skip();
				} else {
					if (index < localQueueIndex) localQueueIndex--;
					updateLocalState(state.currentTrack(), AudioPlayer.get().isPlaying());
				}
			}
		}
	}

	// ---------------------------------------------------------------- WebSocket Listener

	@Override
	public void onOpen(WebSocket ws) {
		this.webSocket = ws;
		ws.request(1);
	}

	@Override
	public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
		messageBuffer.append(data);
		if (last) {
			String message = messageBuffer.toString();
			messageBuffer.setLength(0);
			handleIncomingMessage(message);
		}
		ws.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
		BuildAid.LOGGER.info("[MusicSyncClient] Conexao encerrada: {} ({})", reason, statusCode);
		this.webSocket = null;
		return null;
	}

	@Override
	public void onError(WebSocket ws, Throwable error) {
		BuildAid.LOGGER.error("[MusicSyncClient] Erro no WebSocket", error);
	}

	private void handleIncomingMessage(String jsonStr) {
		try {
			JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
			String type = json.has("type") ? json.get("type").getAsString() : "";

			switch (type) {
				case MusicPacket.SYNC_STATE -> {
					if (json.has("state")) {
						MusicPacket.RoomState newState = MusicPacket.RoomState.fromJson(json.getAsJsonObject("state"));
						applyServerState(newState);
					}
				}
				case MusicPacket.CHAT_BROADCAST -> {
					String sender = json.has("sender") ? json.get("sender").getAsString() : "Sala";
					String msg = json.has("message") ? json.get("message").getAsString() : "";
					Feedback.info("buildaid.msg.music_chat", sender, msg);
				}
				case MusicPacket.ERROR -> {
					String errMsg = json.has("message") ? json.get("message").getAsString() : "Erro desconhecido";
					Feedback.error("buildaid.msg.music_server_error", errMsg);
				}
			}
		} catch (Exception e) {
			BuildAid.LOGGER.error("[MusicSyncClient] Falha ao processar mensagem do servidor: {}", jsonStr, e);
		}
	}

	private void applyServerState(MusicPacket.RoomState newState) {
		boolean trackChanged = !state.currentTrack().id().equals(newState.currentTrack().id())
				|| !state.currentTrack().streamUrl().equals(newState.currentTrack().streamUrl());

		this.state = newState;
		notifyListeners();

		if (newState.currentTrack().isEmpty()) {
			AudioPlayer.get().stop();
			return;
		}

		if (newState.isPlaying()) {
			if (trackChanged || !AudioPlayer.get().isPlaying()) {
				long now = System.currentTimeMillis();
				long offset = Math.max(0, (now - newState.startedEpochMs()) / 1000);
				AudioPlayer.get().play(newState.currentTrack().streamUrl(), offset);
			} else if (AudioPlayer.get().isPaused()) {
				AudioPlayer.get().resume();
			}
		} else {
			if (AudioPlayer.get().isPlaying()) {
				AudioPlayer.get().pause();
			}
		}
	}

	// ---------------------------------------------------------------- Modo Solo Local

	private void playLocalIndex(int index) {
		if (index < 0 || index >= localQueue.size()) return;
		localQueueIndex = index;
		TrackInfo track = localQueue.get(index);
		updateLocalState(track, true);
		AudioPlayer.get().play(track.streamUrl(), 0);
	}

	private boolean isPlayingSolo() {
		return AudioPlayer.get().isPlaying();
	}

	private void updateLocalState(TrackInfo current, boolean isPlaying) {
		this.state = new MusicPacket.RoomState(
				currentRoomId.isBlank() ? "Solo" : currentRoomId,
				current,
				new ArrayList<>(localQueue),
				isPlaying,
				System.currentTimeMillis(),
				0,
				List.of(playerName)
		);
		notifyListeners();
	}

	private void send(String payload) {
		if (isConnected()) {
			webSocket.sendText(payload, true);
		}
	}

	private static String httpUrlFromWs(String wsUrl) {
		if (wsUrl == null) return "http://localhost:3000";
		if (wsUrl.startsWith("ws://")) {
			return "http://" + wsUrl.substring(5);
		} else if (wsUrl.startsWith("wss://")) {
			return "https://" + wsUrl.substring(6);
		}
		return wsUrl;
	}
}
