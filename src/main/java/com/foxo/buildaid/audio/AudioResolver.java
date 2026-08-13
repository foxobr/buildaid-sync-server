package com.foxo.buildaid.audio;

import com.foxo.buildaid.BuildAid;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolve URLs de audio (YouTube, SoundCloud, Twitch, Web Radios e links diretos)
 * utilizando o LavaPlayer PlayerManager.
 */
public final class AudioResolver {

	private AudioResolver() {
	}

	public static CompletableFuture<TrackInfo> resolve(String inputUrl, String addedBy, String relayServerUrl) {
		if (inputUrl == null || inputUrl.isBlank()) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("URL vazia"));
		}

		String trimmed = inputUrl.trim();
		CompletableFuture<TrackInfo> future = new CompletableFuture<>();

		AudioPlayer.get().getPlayerManager().loadItem(trimmed, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				AudioTrackInfo info = track.getInfo();
				String title = (info.title != null && !info.title.isBlank()) ? info.title : "Faixa de Audio";
				String author = (info.author != null && !info.author.isBlank()) ? info.author : "Web";
				long durationSeconds = info.isStream ? 0 : Math.max(0, info.length / 1000);
				String artwork = info.artworkUrl != null ? info.artworkUrl : "";
				String id = info.identifier != null ? info.identifier : UUID.randomUUID().toString().substring(0, 8);

				TrackInfo trackInfo = new TrackInfo(
						id,
						title,
						author,
						trimmed,
						trimmed,
						durationSeconds,
						addedBy != null ? addedBy : "Player",
						artwork
				);
				future.complete(trackInfo);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				if (!playlist.getTracks().isEmpty()) {
					AudioTrack track = playlist.getSelectedTrack() != null ? playlist.getSelectedTrack() : playlist.getTracks().get(0);
					AudioTrackInfo info = track.getInfo();
					String title = (info.title != null && !info.title.isBlank()) ? info.title : playlist.getName();
					String author = (info.author != null && !info.author.isBlank()) ? info.author : "Playlist";
					long durationSeconds = info.isStream ? 0 : Math.max(0, info.length / 1000);
					String artwork = info.artworkUrl != null ? info.artworkUrl : "";
					String id = info.identifier != null ? info.identifier : UUID.randomUUID().toString().substring(0, 8);

					TrackInfo trackInfo = new TrackInfo(
							id,
							title,
							author,
							trimmed,
							trimmed,
							durationSeconds,
							addedBy != null ? addedBy : "Player",
							artwork
					);
					future.complete(trackInfo);
				} else {
					future.completeExceptionally(new RuntimeException("buildaid.msg.music_stream_unavailable"));
				}
			}

			@Override
			public void noMatches() {
				// Fallback para streams diretas caso nao case
				String title = extractFileName(trimmed);
				TrackInfo fallback = new TrackInfo(
						UUID.randomUUID().toString().substring(0, 8),
						title,
						"Stream Web",
						trimmed,
						trimmed,
						0,
						addedBy != null ? addedBy : "Player",
						""
				);
				future.complete(fallback);
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				BuildAid.LOGGER.error("[AudioResolver] Falha ao carregar faixa {}", trimmed, exception);
				future.completeExceptionally(exception);
			}
		});

		return future;
	}

	private static String extractFileName(String url) {
		try {
			int lastSlash = url.lastIndexOf('/');
			if (lastSlash >= 0 && lastSlash < url.length() - 1) {
				String name = url.substring(lastSlash + 1);
				int queryIdx = name.indexOf('?');
				if (queryIdx >= 0) {
					name = name.substring(0, queryIdx);
				}
				if (!name.isBlank()) {
					return name;
				}
			}
		} catch (Exception ignored) {
		}
		return "Stream de Audio";
	}
}
