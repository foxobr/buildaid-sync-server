package com.foxo.buildaid.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Paleta e primitivas de desenho do menu.
 *
 * <p>Tudo o que define "a cara" do mod mora aqui: mudar uma constante muda a tela inteira.
 *
 * <p>Sobre os cantos arredondados: {@code fill} so desenha retangulo, entao a curva e montada
 * empilhando alguns retangulos (um octogono, na pratica). Com raio de 2-3 px na escala de GUI do
 * Minecraft o olho le como canto arredondado, e custa 7 quads em vez de um por linha.
 */
public final class Theme {
	/** Escurecimento do mundo atras do menu. */
	public static final int SCRIM = 0xC80B0D12;

	public static final int BACKGROUND = 0xF21A1D24;
	public static final int SURFACE = 0xFF232833;
	public static final int SURFACE_HOVER = 0xFF2E3542;
	public static final int SURFACE_SUNKEN = 0xFF14171D;
	public static final int SURFACE_DISABLED = 0xFF1E2129;

	public static final int ACCENT = 0xFF4A9EFF;
	public static final int ACCENT_HOVER = 0xFF6BB1FF;
	public static final int ACCENT_SOFT = 0x334A9EFF;

	public static final int DANGER = 0xFFE05260;
	public static final int DANGER_HOVER = 0xFFED6C79;

	public static final int TEXT = 0xFFE8ECF2;
	public static final int TEXT_DIM = 0xFF98A2B3;
	public static final int TEXT_DISABLED = 0xFF5A6273;
	public static final int TEXT_ON_ACCENT = 0xFF0B1220;

	public static final int BORDER = 0xFF333B49;

	public static final int RADIUS = 3;
	public static final int PAD = 8;
	public static final int ROW = 20;

	private Theme() {
	}

	/** Retangulo de cantos arredondados. Raio 0 cai para um fill simples. */
	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			int radius, int color) {
		if (width <= 0 || height <= 0) {
			return;
		}

		int r = Math.min(radius, Math.min(width, height) / 2);
		if (r <= 0) {
			graphics.fill(x, y, x + width, y + height, color);
			return;
		}

		// Faixa central (altura inteira) + duas laterais recuadas = octogono.
		graphics.fill(x + r, y, x + width - r, y + height, color);
		graphics.fill(x, y + r, x + r, y + height - r, color);
		graphics.fill(x + width - r, y + r, x + width, y + height - r, color);

		// Preenche o "degrau" dos cantos, deixando so 1 px cortado na diagonal.
		graphics.fill(x + 1, y + 1, x + r, y + r, color);
		graphics.fill(x + width - r, y + 1, x + width - 1, y + r, color);
		graphics.fill(x + 1, y + height - r, x + r, y + height - 1, color);
		graphics.fill(x + width - r, y + height - r, x + width - 1, y + height - 1, color);
	}

	public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		roundedRect(graphics, x, y, width, height, RADIUS, color);
	}

	/** Contorno de 1 px acompanhando o mesmo formato arredondado. */
	public static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
			int radius, int color) {
		int r = Math.min(radius, Math.min(width, height) / 2);
		graphics.fill(x + r, y, x + width - r, y + 1, color);
		graphics.fill(x + r, y + height - 1, x + width - r, y + height, color);
		graphics.fill(x, y + r, x + 1, y + height - r, color);
		graphics.fill(x + width - 1, y + r, x + width, y + height - r, color);

		// Um pixel em cada canto fecha a curva.
		graphics.fill(x + 1, y + 1, x + r, y + 2, color);
		graphics.fill(x + 1, y + 2, x + 2, y + r, color);
		graphics.fill(x + width - r, y + 1, x + width - 1, y + 2, color);
		graphics.fill(x + width - 2, y + 2, x + width - 1, y + r, color);
		graphics.fill(x + 1, y + height - 2, x + r, y + height - 1, color);
		graphics.fill(x + 1, y + height - r, x + 2, y + height - 2, color);
		graphics.fill(x + width - r, y + height - 2, x + width - 1, y + height - 1, color);
		graphics.fill(x + width - 2, y + height - r, x + width - 1, y + height - 2, color);
	}

	/** Linha divisoria horizontal fina. */
	public static void divider(GuiGraphicsExtractor graphics, int x, int y, int width) {
		graphics.fill(x, y, x + width, y + 1, BORDER);
	}

	/** Barrinha de acento vertical -- marca a aba ativa. */
	public static void accentBar(GuiGraphicsExtractor graphics, int x, int y, int height) {
		roundedRect(graphics, x, y, 3, height, 1, ACCENT);
	}

	/** Mistura alfa num ARGB ja pronto (0.0 a 1.0). */
	public static int withAlpha(int argb, float alpha) {
		int a = Math.clamp(Math.round(((argb >>> 24) & 0xFF) * alpha), 0, 255);
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
