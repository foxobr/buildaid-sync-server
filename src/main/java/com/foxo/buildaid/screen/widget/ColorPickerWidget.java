package com.foxo.buildaid.screen.widget;

import com.foxo.buildaid.screen.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Seletor de cores no estilo programa de pintura: quadrado de saturacao/brilho + barra vertical
 * de matiz.
 *
 * <p>O quadrado e desenhado como uma grade de celulinas coloridas -- cada celula amostra um
 * ponto (S,V) para o matiz atual, que e exatamente o que um programa de pintura mostra. Arrastar
 * dentro do quadrado ou na barra atualiza a cor em tempo real; quem consome le {@link #pickedRgb()}
 * a cada frame ou escuta o callback.
 */
public class ColorPickerWidget extends AbstractWidget {
	private static final int HUE_WIDTH = 12;
	private static final int GAP = 4;
	/** Tamanho da celulina da grade SV. Menor = mais suave, mais fills por frame. */
	private static final int CELL = 5;

	private float hue = 210.0f / 360.0f;
	private float sat = 0.72f;
	private float val = 0.92f;

	private final Runnable onChange;

	public ColorPickerWidget(int x, int y, int width, int height, Runnable onChange) {
		super(x, y, width, height, Component.empty());
		this.onChange = onChange;
	}

	/** Cor atual em RGB (sem alfa). */
	public int pickedRgb() {
		return hsvToRgb(hue, sat, val);
	}

	public float hue() {
		return hue;
	}

	public float saturation() {
		return sat;
	}

	public float value() {
		return val;
	}

	public void setHsv(float h, float s, float v) {
		this.hue = Math.clamp(h, 0.0f, 1.0f);
		this.sat = Math.clamp(s, 0.0f, 1.0f);
		this.val = Math.clamp(v, 0.0f, 1.0f);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int svWidth = getWidth() - HUE_WIDTH - GAP;

		// --- quadrado S/V ---
		int cols = Math.max(1, svWidth / CELL);
		int rows = Math.max(1, getHeight() / CELL);
		for (int row = 0; row < rows; row++) {
			float v = 1.0f - (row + 0.5f) / rows;
			for (int col = 0; col < cols; col++) {
				float s = (col + 0.5f) / cols;
				graphics.fill(
						getX() + col * CELL,
						getY() + row * CELL,
						getX() + Math.min(svWidth, (col + 1) * CELL),
						getY() + Math.min(getHeight(), (row + 1) * CELL),
						0xFF000000 | hsvToRgb(hue, s, v));
			}
		}

		// --- barra de matiz ---
		int hueX = getX() + svWidth + GAP;
		int segments = Math.max(8, getHeight() / CELL);
		float segmentHeight = getHeight() / (float) segments;
		for (int i = 0; i < segments; i++) {
			graphics.fill(hueX,
					getY() + Math.round(i * segmentHeight),
					hueX + HUE_WIDTH,
					getY() + Math.round((i + 1) * segmentHeight),
					0xFF000000 | hsvToRgb(i / (float) segments, 1.0f, 1.0f));
		}
		Theme.roundedOutline(graphics, hueX, getY(), HUE_WIDTH, getHeight(), 2, Theme.BORDER);

		// --- cursores (presos dentro da area, para o anel nao vazar nas bordas) ---
		int ringSize = 9;
		int cursorX = getX() + Math.round(sat * (svWidth - ringSize)) + ringSize / 2;
		int cursorY = getY() + Math.round((1.0f - val) * (getHeight() - ringSize)) + ringSize / 2;
		// Anel duplo preto/branco: visivel sobre qualquer cor.
		Theme.roundedOutline(graphics, cursorX - ringSize / 2 - 1, cursorY - ringSize / 2 - 1,
				ringSize + 2, ringSize + 2, 2, 0xFF000000);
		Theme.roundedOutline(graphics, cursorX - ringSize / 2, cursorY - ringSize / 2,
				ringSize, ringSize, 2, 0xFFFFFFFF);

		int hueY = getY() + Math.clamp(Math.round(hue * getHeight()), 1, Math.max(1, getHeight() - 1));
		graphics.fill(hueX - 1, hueY - 1, hueX + HUE_WIDTH + 1, hueY + 1, 0xFFFFFFFF);
		graphics.fill(hueX - 1, hueY - 2, hueX + HUE_WIDTH + 1, hueY - 1, 0xFF000000);
		graphics.fill(hueX - 1, hueY + 1, hueX + HUE_WIDTH + 1, hueY + 2, 0xFF000000);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		pick(event.x(), event.y());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		pick(event.x(), event.y());
	}

	private void pick(double mouseX, double mouseY) {
		int svWidth = getWidth() - HUE_WIDTH - GAP;

		if (mouseX <= getX() + svWidth) {
			sat = Math.clamp((float) (mouseX - getX()) / svWidth, 0.0f, 1.0f);
			val = Math.clamp(1.0f - (float) (mouseY - getY()) / getHeight(), 0.0f, 1.0f);
		} else if (mouseX >= getX() + svWidth + GAP) {
			hue = Math.clamp((float) (mouseY - getY()) / getHeight(), 0.0f, 1.0f);
		} else {
			return;
		}

		if (onChange != null) {
			onChange.run();
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	/** Conversao HSV->RGB padrao (h/s/v em 0..1), devolve 0xRRGGBB. */
	public static int hsvToRgb(float h, float s, float v) {
		int hInt = (int) (h * 6.0f) % 6;
		float f = h * 6.0f - (int) (h * 6.0f);
		int p = Math.round(255 * v * (1.0f - s));
		int q = Math.round(255 * v * (1.0f - f * s));
		int t = Math.round(255 * v * (1.0f - (1.0f - f) * s));
		int b = Math.round(255 * v);
		return switch (Math.floorMod(hInt, 6)) {
			case 0 -> (b << 16) | (t << 8) | p;
			case 1 -> (q << 16) | (b << 8) | p;
			case 2 -> (p << 16) | (b << 8) | t;
			case 3 -> (p << 16) | (q << 8) | b;
			case 4 -> (t << 16) | (p << 8) | b;
			default -> (b << 16) | (p << 8) | q;
		};
	}

	/** Conversao RGB->HSV padrao; devolve {h, s, v} em 0..1. */
	public static float[] rgbToHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255.0f;
		float g = ((rgb >> 8) & 0xFF) / 255.0f;
		float b = (rgb & 0xFF) / 255.0f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;

		float h = 0.0f;
		if (delta > 0.0f) {
			if (max == r) {
				h = ((g - b) / delta) % 6.0f;
			} else if (max == g) {
				h = (b - r) / delta + 2.0f;
			} else {
				h = (r - g) / delta + 4.0f;
			}
			h /= 6.0f;
			if (h < 0.0f) {
				h += 1.0f;
			}
		}
		float s = max <= 0.0f ? 0.0f : delta / max;
		return new float[] { h, s, max };
	}
}
