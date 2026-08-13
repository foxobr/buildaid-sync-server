package com.foxo.buildaid.hud;

import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.image.ImageLibrary;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Desenho compartilhado do painel de referencia.
 *
 * <p>Fica separado dos elementos de HUD de proposito: a tela de edicao (onde o jogador arrasta
 * e redimensiona) precisa desenhar exatamente o mesmo painel, entao as duas chamam daqui.
 *
 * <p>A opacidade sai de graca: a sobrecarga de {@code blit} com {@code int color} no fim aplica
 * um tint ARGB, e o byte de alpha desse tint e o nosso slider. Sem shader, sem mixin.
 */
public final class RefRenderer {
	private static final int BORDER_COLOR = 0xFFFFFFFF;

	private RefRenderer() {
	}

	/** Tint branco com a opacidade pedida -- multiplica a imagem sem alterar as cores. */
	public static int tint(float opacity) {
		int alpha = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
		return (alpha << 24) | 0x00FFFFFF;
	}

	private static int backgroundColor(float opacity) {
		int alpha = Math.clamp(Math.round(opacity * 170.0f), 0, 255);
		return (alpha << 24) | 0x0F1116;
	}

	private static int borderColor(float opacity) {
		int alpha = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
		return (alpha << 24) | (BORDER_COLOR & 0x00FFFFFF);
	}

	/**
	 * Desenha o painel completo (fundo, imagem recortada, borda).
	 *
	 * @param overrideOpacity se >= 0, ignora a opacidade da config (usado na tela de edicao,
	 *                        onde o painel aparece sempre solido para dar para mirar nas bordas)
	 */
	public static void drawPanel(GuiGraphicsExtractor graphics,
			BuildAidConfig.Panel panel,
			ImageLibrary.Loaded image,
			float overrideOpacity) {
		float opacity = overrideOpacity >= 0 ? overrideOpacity : panel.opacity;

		int x = panel.x;
		int y = panel.y;
		int width = panel.width;
		int height = panel.height;

		if (panel.showBackground) {
			graphics.fill(x, y, x + width, y + height, backgroundColor(opacity));
		}

		if (image != null) {
			// Recorta tudo que passar das bordas do painel (importa quando ha zoom/pan).
			graphics.enableScissor(x, y, x + width, y + height);
			drawFitted(graphics, x, y, width, height, image,
					panel.imageScale, panel.imageOffsetX, panel.imageOffsetY, opacity);
			graphics.disableScissor();
		}

		if (panel.showBorder) {
			drawBorder(graphics, x, y, width, height, borderColor(opacity));
		}
	}

	/** Desenha a imagem ocupando a tela toda, mantendo a proporcao. */
	public static void drawFullscreen(GuiGraphicsExtractor graphics,
			int screenWidth,
			int screenHeight,
			ImageLibrary.Loaded image,
			float opacity) {
		drawFitted(graphics, 0, 0, screenWidth, screenHeight, image, 1.0f, 0.0f, 0.0f, opacity);
	}

	/** Encaixa a imagem numa area arbitraria, recortando o que sobrar (usado na previa). */
	public static void drawFittedIn(GuiGraphicsExtractor graphics,
			int x, int y, int width, int height,
			ImageLibrary.Loaded image,
			float opacity) {
		graphics.enableScissor(x, y, x + width, y + height);
		drawFitted(graphics, x, y, width, height, image, 1.0f, 0.0f, 0.0f, opacity);
		graphics.disableScissor();
	}

	/**
	 * Encaixa a imagem na area mantendo a proporcao, aplicando zoom e deslocamento.
	 * Nunca deforma a imagem -- referencia esticada nao serve para nada.
	 */
	private static void drawFitted(GuiGraphicsExtractor graphics,
			int areaX, int areaY, int areaWidth, int areaHeight,
			ImageLibrary.Loaded image,
			float zoom, float offsetX, float offsetY,
			float opacity) {
		int sourceWidth = image.width();
		int sourceHeight = image.height();
		if (sourceWidth <= 0 || sourceHeight <= 0) {
			return;
		}

		float fit = Math.min((float) areaWidth / sourceWidth, (float) areaHeight / sourceHeight);
		float scale = fit * zoom;

		int drawWidth = Math.max(1, Math.round(sourceWidth * scale));
		int drawHeight = Math.max(1, Math.round(sourceHeight * scale));
		int drawX = Math.round(areaX + (areaWidth - drawWidth) / 2.0f + offsetX);
		int drawY = Math.round(areaY + (areaHeight - drawHeight) / 2.0f + offsetY);

		// blit(pipeline, id, x, y, uOffset, vOffset, destW, destH, srcW, srcH, texW, texH, tint)
		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				image.textureId(),
				drawX, drawY,
				0.0f, 0.0f,
				drawWidth, drawHeight,
				sourceWidth, sourceHeight,
				sourceWidth, sourceHeight,
				tint(opacity)
		);
	}

	public static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y + 1, x + 1, y + height - 1, color);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
	}
}
