package com.foxo.buildaid.hud;

import com.foxo.buildaid.audio.AudioPlayer;
import com.foxo.buildaid.audio.TrackInfo;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.net.music.MusicPacket;
import com.foxo.buildaid.net.music.MusicSyncClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Mini Player HUD exibido discretamente no jogo com informacoes da musica atual, progresso e sala.
 */
public final class MusicHudElement implements HudElement {
	private static final int BG_COLOR = 0xB012141A;
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int ARTIST_COLOR = 0xFF9AA4B2;
	private static final int ACCENT_COLOR = 0xFF4AE3B5;
	private static final int BAR_BG_COLOR = 0x60FFFFFF;
	private static final int CARD_WIDTH = 180;
	private static final int CARD_HEIGHT = 38;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		BuildAidConfig.Music config = BuildAidConfig.get().music;
		if (!config.hudEnabled) {
			return;
		}

		MusicSyncClient syncClient = MusicSyncClient.get();
		MusicPacket.RoomState state = syncClient.getState();
		TrackInfo track = state.currentTrack();

		AudioPlayer player = AudioPlayer.get();
		boolean hasActiveTrack = !track.isEmpty() || player.isPlaying() || player.isPaused() || player.isBuffering();

		if (!hasActiveTrack) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		int x = config.hudX;
		int y = config.hudY;

		// Fundo translucidado com cantos arredondados
		graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, BG_COLOR);
		graphics.fill(x, y, x + CARD_WIDTH, y + 1, 0x40FFFFFF); // Linha de brilho superior

		// Icone de status e titulo
		String statusIcon = player.isBuffering() ? "⟳" : (player.isPlaying() ? "▶" : "⏸");
		String title = track.title().isBlank() ? "Música" : track.title();
		String titleSnippet = client.font.plainSubstrByWidth(statusIcon + " " + title, CARD_WIDTH - 12);
		graphics.text(client.font, Component.literal(titleSnippet), x + 6, y + 5, TITLE_COLOR, false);

		// Autor / Canal e Sala
		String author = track.author().isBlank() ? "Web" : track.author();
		String roomTag = syncClient.isInRoom() ? " [" + syncClient.getCurrentRoomId() + "]" : " [Solo]";
		String authorSnippet = client.font.plainSubstrByWidth(author + roomTag, CARD_WIDTH - 12);
		graphics.text(client.font, Component.literal(authorSnippet), x + 6, y + 16, ARTIST_COLOR, false);

		// Barra de progresso
		int barX = x + 6;
		int barY = y + 27;
		int barWidth = CARD_WIDTH - 60;
		int barHeight = 3;

		graphics.fill(barX, barY, barX + barWidth, barY + barHeight, BAR_BG_COLOR);

		long posSec = player.getPositionSeconds();
		long durSec = track.durationSeconds();

		if (durSec > 0) {
			float progress = Math.clamp((float) posSec / durSec, 0.0f, 1.0f);
			int filledWidth = (int) (barWidth * progress);
			graphics.fill(barX, barY, barX + filledWidth, barY + barHeight, ACCENT_COLOR);
		} else if (player.isPlaying()) {
			// Animacao pulsante para streams ao vivo
			int filledWidth = (int) ((System.currentTimeMillis() / 50) % barWidth);
			graphics.fill(barX, barY, barX + filledWidth, barY + barHeight, ACCENT_COLOR);
		}

		// Texto de tempo
		String timeText = formatTime(posSec) + "/" + (durSec > 0 ? formatTime(durSec) : "LIVE");
		graphics.text(client.font, Component.literal(timeText), x + CARD_WIDTH - 50, y + 25, ARTIST_COLOR, false);
	}

	private static String formatTime(long seconds) {
		long mins = seconds / 60;
		long secs = seconds % 60;
		return String.format("%02d:%02d", mins, secs);
	}
}
