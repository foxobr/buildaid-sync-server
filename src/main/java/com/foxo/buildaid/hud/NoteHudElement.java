package com.foxo.buildaid.hud;

import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.screen.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Blocos de anotacoes flutuantes: post-its de texto livre presos a um canto da tela.
 *
 * <p>Funcionam no mesmo espirito dos paineis de referencia -- varios ao mesmo tempo, cada um com
 * posicao, largura, opacidade e cor proprios -- so que o conteudo e texto digitado pelo jogador
 * em vez de imagem. A altura da caixa cresce conforme as linhas; quem manda na altura e o texto.
 *
 * <p>Desenhados depois dos paineis e antes do chat, para ficarem por cima das referencias sem
 * cobrir mensagens.
 */
public final class NoteHudElement implements HudElement {
	private static final int LINE_HEIGHT = 10;
	private static final int PAD = 6;
	/** A barrinha de acento ocupa 3 px a esquerda; o texto comeca depois dela. */
	private static final int ACCENT_WIDTH = 3;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();

		for (BuildAidConfig.Note note : BuildAidConfig.get().notes) {
			if (!note.visible || note.text == null || note.text.isBlank()) {
				continue;
			}

			List<FormattedCharSequence> lines = wrap(font, note.text, note.width - PAD * 2 - ACCENT_WIDTH);
			if (lines.isEmpty()) {
				continue;
			}

			int width = note.width;
			int height = lines.size() * LINE_HEIGHT + PAD * 2;

			// Nunca deixa a nota sair da tela, nem quando a janela encolhe.
			int x = Math.clamp(note.x, 0, Math.max(0, guiWidth - width));
			int y = Math.clamp(note.y, 0, Math.max(0, guiHeight - height));

			int accent = Theme.NOTE_ACCENTS[Math.floorMod(note.colorPreset, Theme.NOTE_ACCENTS.length)];

			int bgAlpha = Math.clamp(Math.round(note.opacity * 216), 0, 255);
			Theme.roundedRect(graphics, x, y, width, height, 3,
					(bgAlpha << 24) | 0x10141B);
			// Barrinha de acento do lado esquerdo, do jeito que os post-its sao marcados.
			Theme.roundedRect(graphics, x, y, ACCENT_WIDTH, height, 1, withAlpha(accent, note.opacity));

			for (int i = 0; i < lines.size(); i++) {
				graphics.text(font, lines.get(i), x + PAD + ACCENT_WIDTH, y + PAD + i * LINE_HEIGHT,
						withAlpha(0xFFE8ECF2, note.opacity), false);
			}
		}
	}

	/** Quebra primeiro nas quebras manuais (\n), depois no limite de largura. */
	public static List<FormattedCharSequence> wrap(Font font, String text, int maxWidth) {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (String paragraph : text.split("\n", -1)) {
			if (paragraph.isEmpty()) {
				// Linha vazia manual: vira espaco em branco na caixa.
				out.add(FormattedCharSequence.EMPTY);
				continue;
			}
			List<FormattedCharSequence> wrapped = font.split(Component.literal(paragraph), Math.max(20, maxWidth));
			out.addAll(wrapped.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : wrapped);
		}
		return out;
	}

	/** Altura que a caixa de uma nota ocupa com este texto e largura -- usada tambem pelo modo de ajuste. */
	public static int heightOf(Font font, String text, int width) {
		return wrap(font, text, width - PAD * 2 - ACCENT_WIDTH).size() * LINE_HEIGHT + PAD * 2;
	}

	private static int withAlpha(int argb, float opacity) {
		int a = Math.clamp(Math.round(((argb >>> 24) & 0xFF) * opacity), 0, 255);
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
