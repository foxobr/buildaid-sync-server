package com.foxo.buildaid.screen;

import com.foxo.buildaid.config.BuildAidConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Paleta e primitivas de desenho do menu com suporte a Temas de Cores Globais.
 */
public final class Theme {
	public static final int SCRIM = 0xC80B0D12;

	public static final int BACKGROUND = 0xF21A1D24;
	public static final int SURFACE = 0xFF232833;
	public static final int SURFACE_HOVER = 0xFF2E3542;
	public static final int SURFACE_SUNKEN = 0xFF14171D;
	public static final int SURFACE_DISABLED = 0xFF1E2129;

	// Compatibilidade
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

	/** Cores de acento das anotacoes (post-its), na ordem dos presets da config. */
	public static final int[] NOTE_ACCENTS = {
			0xFF4A9EFF, // Ciano
			0xFF50E3C2, // Esmeralda
			0xFFF1C40F, // Ouro
			0xFFE05260, // Rubi
			0xFFA855F7, // Ametista
			0xFFE8ECF2  // Branco
	};

	private Theme() {
	}

	public static int accent() {
		return switch (BuildAidConfig.get().uiTheme) {
			case 1 -> 0xFF50E3C2; // Esmeralda
			case 2 -> 0xFFFFB300; // Ouro
			case 3 -> 0xFFE05260; // Rubi
			case 4 -> 0xFFA855F7; // Ametista
			case 5 -> 0xFFF43F5E; // Neon
			case 6 -> 0xFFE2E8F0; // Monocromatico
			default -> 0xFF4A9EFF; // Ciano
		};
	}

	public static int accentHover() {
		return switch (BuildAidConfig.get().uiTheme) {
			case 1 -> 0xFF76F2D6;
			case 2 -> 0xFFFFC43D;
			case 3 -> 0xFFED6C79;
			case 4 -> 0xFFC084FC;
			case 5 -> 0xFFFB7185;
			case 6 -> 0xFFF8FAFC;
			default -> 0xFF6BB1FF;
		};
	}

	public static int accentSoft() {
		return withAlpha(accent(), 0.25f);
	}

	public static Component themeName(int theme) {
		return switch (theme) {
			case 1 -> Component.translatable("buildaid.menu.theme_emerald");
			case 2 -> Component.translatable("buildaid.menu.theme_gold");
			case 3 -> Component.translatable("buildaid.menu.theme_ruby");
			case 4 -> Component.translatable("buildaid.menu.theme_amethyst");
			case 5 -> Component.translatable("buildaid.menu.theme_neon");
			case 6 -> Component.translatable("buildaid.menu.theme_monochrome");
			default -> Component.translatable("buildaid.menu.theme_cyan");
		};
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

		graphics.fill(x + r, y, x + width - r, y + height, color);
		graphics.fill(x, y + r, x + r, y + height - r, color);
		graphics.fill(x + width - r, y + r, x + width, y + height - r, color);

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
		roundedRect(graphics, x, y, 3, height, 1, accent());
	}

	/** Mistura alfa num ARGB ja pronto (0.0 a 1.0). */
	public static int withAlpha(int argb, float alpha) {
		int a = Math.clamp(Math.round(((argb >>> 24) & 0xFF) * alpha), 0, 255);
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
