package com.foxo.buildaid.audio;

import com.foxo.buildaid.BuildAid;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Motor de reproducao de audio client-side com decodificacao de streaming MP3 e controle de volume.
 *
 * <p>Executa a decodificacao e envio de dados para a placa de som em uma thread dedicada em segundo plano,
 * sem interferir na renderizacao ou no loop principal do Minecraft.
 */
public final class AudioPlayer {
	private static final AudioPlayer INSTANCE = new AudioPlayer();

	private Thread playThread;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean paused = new AtomicBoolean(false);
	private final AtomicBoolean buffering = new AtomicBoolean(false);

	private volatile String currentUrl = "";
	private volatile float volume = 0.5f; // 0.0 a 1.0 (50% padrao)
	private final AtomicLong currentPositionMs = new AtomicLong(0);
	private volatile long startOffsetMs = 0;

	private SourceDataLine line;
	private FloatControl gainControl;

	private AudioPlayer() {
	}

	public static AudioPlayer get() {
		return INSTANCE;
	}

	public synchronized void play(String streamUrl, long offsetSeconds) {
		stop();
		if (streamUrl == null || streamUrl.isBlank()) {
			return;
		}

		currentUrl = streamUrl;
		startOffsetMs = Math.max(0, offsetSeconds * 1000);
		currentPositionMs.set(startOffsetMs);
		paused.set(false);
		running.set(true);
		buffering.set(true);

		playThread = new Thread(this::streamWorker, "BuildAid-AudioPlayer");
		playThread.setDaemon(true);
		playThread.start();
	}

	public void pause() {
		paused.set(true);
		if (line != null && line.isOpen()) {
			line.stop();
		}
	}

	public void resume() {
		paused.set(false);
		if (line != null && line.isOpen()) {
			line.start();
		}
	}

	public void togglePause() {
		if (paused.get()) {
			resume();
		} else {
			pause();
		}
	}

	public synchronized void stop() {
		running.set(false);
		paused.set(false);
		buffering.set(false);
		if (playThread != null) {
			playThread.interrupt();
			playThread = null;
		}
		closeLine();
		currentPositionMs.set(0);
		currentUrl = "";
	}

	public void setVolume(float newVolume) {
		this.volume = Math.clamp(newVolume, 0.0f, 1.0f);
		applyVolume();
	}

	public float getVolume() {
		return volume;
	}

	public boolean isPlaying() {
		return running.get() && !paused.get() && !buffering.get();
	}

	public boolean isPaused() {
		return running.get() && paused.get();
	}

	public boolean isBuffering() {
		return running.get() && buffering.get();
	}

	public long getPositionSeconds() {
		return currentPositionMs.get() / 1000;
	}

	private void applyVolume() {
		if (gainControl != null) {
			try {
				if (volume <= 0.001f) {
					gainControl.setValue(gainControl.getMinimum());
				} else {
					// Curva logaritmica perceptiva para controle de decibeis
					float min = gainControl.getMinimum();
					float max = Math.min(gainControl.getMaximum(), 6.0f);
					float dB = (float) (Math.log10(volume) * 20.0);
					gainControl.setValue(Math.clamp(dB, min, max));
				}
			} catch (Exception ignored) {
			}
		}
	}

	private void streamWorker() {
		InputStream inputStream = null;
		Bitstream bitstream = null;

		try {
			BuildAid.LOGGER.info("[AudioPlayer] Conectando a stream: {}", currentUrl);
			URLConnection connection = URI.create(currentUrl).toURL().openConnection();
			connection.setRequestProperty("User-Agent", "Mozilla/5.0 BuildAid-MinecraftMod/1.0");
			connection.setConnectTimeout(15000);
			connection.setReadTimeout(30000);

			inputStream = new BufferedInputStream(connection.getInputStream(), 64 * 1024);
			bitstream = new Bitstream(inputStream);
			Decoder decoder = new Decoder();

			buffering.set(false);

			byte[] byteBuffer = new byte[8192];
			long skippedMs = 0;

			while (running.get()) {
				if (paused.get()) {
					Thread.sleep(50);
					continue;
				}

				Header header = bitstream.readFrame();
				if (header == null) {
					// Fim da stream
					break;
				}

				float frameMs = header.ms_per_frame();

				// Pular frames ate o offset desejado
				if (skippedMs < startOffsetMs) {
					skippedMs += (long) frameMs;
					bitstream.closeFrame();
					continue;
				}

				SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
				short[] pcm = output.getBuffer();
				int pcmLen = output.getBufferLength();
				int sampleRate = output.getSampleFrequency();
				int channels = output.getChannelCount();

				ensureLine(sampleRate, channels);

				int byteIdx = 0;
				for (int i = 0; i < pcmLen; i++) {
					short sample = pcm[i];
					byteBuffer[byteIdx++] = (byte) (sample & 0xFF);
					byteBuffer[byteIdx++] = (byte) ((sample >> 8) & 0xFF);
				}

				if (line != null && line.isOpen()) {
					line.write(byteBuffer, 0, byteIdx);
				}

				currentPositionMs.addAndGet((long) frameMs);
				bitstream.closeFrame();
			}
		} catch (InterruptedException ignored) {
			// Parada solicitada
		} catch (Exception e) {
			if (running.get()) {
				BuildAid.LOGGER.error("[AudioPlayer] Erro na reproducao da stream de audio", e);
			}
		} finally {
			buffering.set(false);
			running.set(false);
			try {
				if (bitstream != null) {
					bitstream.close();
				}
				if (inputStream != null) {
					inputStream.close();
				}
			} catch (Exception ignored) {
			}
			closeLine();
		}
	}

	private void ensureLine(int sampleRate, int channels) throws LineUnavailableException {
		if (line != null && line.isOpen()) {
			return;
		}

		AudioFormat format = new AudioFormat(
				sampleRate,
				16, // 16-bit
				channels,
				true, // signed
				false // little-endian
		);

		line = AudioSystem.getSourceDataLine(format);
		line.open(format, 64 * 1024);
		if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
		}
		applyVolume();
		line.start();
	}

	private void closeLine() {
		if (line != null) {
			try {
				line.stop();
				line.flush();
				line.close();
			} catch (Exception ignored) {
			}
			line = null;
			gainControl = null;
		}
	}
}
