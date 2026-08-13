package com.foxo.buildaid.audio;

import com.foxo.buildaid.BuildAid;
import com.foxo.buildaid.net.music.MusicSyncClient;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Motor de reproducao de audio baseado em LavaPlayer com suporte nativo a YouTube,
 * SoundCloud, Twitch, Web Streams, MP3, AAC, FLAC e Opus.
 */
public final class AudioPlayer {
	private static final AudioPlayer INSTANCE = new AudioPlayer();

	private final AudioPlayerManager playerManager;
	private final com.sedmelluq.discord.lavaplayer.player.AudioPlayer lavaPlayer;

	private SourceDataLine soundLine;
	private FloatControl gainControl;
	private volatile float volume = 0.5f;
	private volatile String currentUrl = "";

	private Thread audioOutputThread;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean buffering = new AtomicBoolean(false);

	private AudioPlayer() {
		this.playerManager = new DefaultAudioPlayerManager();
		this.playerManager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
		this.playerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);

		// Registrar gerenciador do YouTube moderno e fontes remotas
		try {
			YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager();
			this.playerManager.registerSourceManager(yt);
			BuildAid.LOGGER.info("[AudioPlayer] YoutubeAudioSourceManager registrado com sucesso!");
		} catch (Throwable t) {
			BuildAid.LOGGER.error("[AudioPlayer] Erro ao registrar YoutubeAudioSourceManager", t);
		}

		AudioSourceManagers.registerRemoteSources(this.playerManager, com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
		AudioSourceManagers.registerLocalSource(this.playerManager);

		this.lavaPlayer = this.playerManager.createPlayer();
		this.lavaPlayer.setVolume((int) (this.volume * 100));

		this.lavaPlayer.addListener(new AudioEventAdapter() {
			@Override
			public void onTrackEnd(com.sedmelluq.discord.lavaplayer.player.AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
				buffering.set(false);
				if (endReason.mayStartNext) {
					MusicSyncClient.get().skip();
				}
			}

			@Override
			public void onTrackException(com.sedmelluq.discord.lavaplayer.player.AudioPlayer player, AudioTrack track, FriendlyException exception) {
				buffering.set(false);
				BuildAid.LOGGER.error("[AudioPlayer] Falha na reproducao da faixa: {}", exception.getMessage());
			}

			@Override
			public void onTrackStuck(com.sedmelluq.discord.lavaplayer.player.AudioPlayer player, AudioTrack track, long thresholdMs) {
				buffering.set(true);
			}
		});

		startOutputWorker();
	}

	public static AudioPlayer get() {
		return INSTANCE;
	}

	public AudioPlayerManager getPlayerManager() {
		return playerManager;
	}

	public synchronized void play(String identifier, long offsetSeconds) {
		if (identifier == null || identifier.isBlank()) {
			stop();
			return;
		}

		currentUrl = identifier;
		buffering.set(true);

		playerManager.loadItem(identifier, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				buffering.set(false);
				if (offsetSeconds > 0) {
					track.setPosition(offsetSeconds * 1000);
				}
				lavaPlayer.playTrack(track);
				lavaPlayer.setPaused(false);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				buffering.set(false);
				if (!playlist.getTracks().isEmpty()) {
					AudioTrack track = playlist.getSelectedTrack() != null ? playlist.getSelectedTrack() : playlist.getTracks().get(0);
					if (offsetSeconds > 0) {
						track.setPosition(offsetSeconds * 1000);
					}
					lavaPlayer.playTrack(track);
					lavaPlayer.setPaused(false);
				}
			}

			@Override
			public void noMatches() {
				buffering.set(false);
				BuildAid.LOGGER.warn("[AudioPlayer] Nenhuma midia compativel encontrada para: {}", identifier);
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				buffering.set(false);
				BuildAid.LOGGER.error("[AudioPlayer] Erro ao carregar faixa: {}", exception.getMessage());
			}
		});
	}

	public void pause() {
		lavaPlayer.setPaused(true);
	}

	public void resume() {
		lavaPlayer.setPaused(false);
	}

	public void togglePause() {
		lavaPlayer.setPaused(!lavaPlayer.isPaused());
	}

	public synchronized void stop() {
		buffering.set(false);
		currentUrl = "";
		lavaPlayer.stopTrack();
	}

	public void setVolume(float newVolume) {
		this.volume = Math.clamp(newVolume, 0.0f, 1.0f);
		if (lavaPlayer != null) {
			lavaPlayer.setVolume((int) (this.volume * 100));
		}
		applyHardwareVolume();
	}

	public float getVolume() {
		return volume;
	}

	public boolean isPlaying() {
		return lavaPlayer.getPlayingTrack() != null && !lavaPlayer.isPaused();
	}

	public boolean isPaused() {
		return lavaPlayer.getPlayingTrack() != null && lavaPlayer.isPaused();
	}

	public boolean isBuffering() {
		return buffering.get();
	}

	public long getPositionSeconds() {
		AudioTrack track = lavaPlayer.getPlayingTrack();
		return track != null ? track.getPosition() / 1000 : 0;
	}

	public String getCurrentUrl() {
		return currentUrl;
	}

	private void startOutputWorker() {
		running.set(true);
		audioOutputThread = new Thread(this::audioPumpLoop, "BuildAid-LavaPlayer-Pump");
		audioOutputThread.setDaemon(true);
		audioOutputThread.start();
	}

	private void audioPumpLoop() {
		AudioDataFormat format = playerManager.getConfiguration().getOutputFormat();

		try {
			ensureSoundLine(format.sampleRate, format.channelCount);
		} catch (Exception e) {
			BuildAid.LOGGER.error("[AudioPlayer] Nao foi possivel inicializar a linha de som", e);
		}

		while (running.get()) {
			try {
				AudioFrame frame = lavaPlayer.provide(20, TimeUnit.MILLISECONDS);
				if (frame != null) {
					byte[] data = frame.getData();
					if (soundLine != null && soundLine.isOpen()) {
						soundLine.write(data, 0, data.length);
					}
				} else {
					Thread.sleep(5);
				}
			} catch (InterruptedException ignored) {
				break;
			} catch (Exception e) {
				BuildAid.LOGGER.error("[AudioPlayer] Erro no loop de saida de audio", e);
			}
		}

		closeSoundLine();
	}

	private synchronized void ensureSoundLine(int sampleRate, int channels) throws LineUnavailableException {
		if (soundLine != null && soundLine.isOpen()) {
			return;
		}

		AudioFormat audioFormat = new AudioFormat(
				sampleRate,
				16,
				channels,
				true,
				false
		);

		soundLine = AudioSystem.getSourceDataLine(audioFormat);
		soundLine.open(audioFormat, 32 * 1024);
		if (soundLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			gainControl = (FloatControl) soundLine.getControl(FloatControl.Type.MASTER_GAIN);
		}
		applyHardwareVolume();
		soundLine.start();
	}

	private void applyHardwareVolume() {
		if (gainControl != null) {
			try {
				if (volume <= 0.001f) {
					gainControl.setValue(gainControl.getMinimum());
				} else {
					float min = gainControl.getMinimum();
					float max = Math.min(gainControl.getMaximum(), 6.0f);
					float dB = (float) (Math.log10(volume) * 20.0);
					gainControl.setValue(Math.clamp(dB, min, max));
				}
			} catch (Exception ignored) {
			}
		}
	}

	private synchronized void closeSoundLine() {
		if (soundLine != null) {
			try {
				soundLine.stop();
				soundLine.close();
			} catch (Exception ignored) {
			}
			soundLine = null;
			gainControl = null;
		}
	}
}
