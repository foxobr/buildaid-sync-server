package com.foxo.buildaid.screen.widget;

import com.foxo.buildaid.screen.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Slider de valor inteiro com trilho preenchido.
 *
 * <p>Herda de {@link AbstractSliderButton} so pela mecanica de arrastar (que ja lida com o
 * {@code MouseButtonEvent} novo do 26.2); o desenho e todo proprio.
 *
 * <p>O rotulo vem de uma funcao que recebe o valor e devolve o texto ja montado, para a frase
 * inteira ("Opacidade: 85%") poder morar no arquivo de traducao.
 */
public class ModSlider extends AbstractSliderButton {
	private final IntFunction<Component> messageBuilder;
	private final int min;
	private final int max;
	private final IntConsumer onChange;

	public ModSlider(int x, int y, int width, int height, IntFunction<Component> messageBuilder,
			int min, int max, int initial, IntConsumer onChange) {
		super(x, y, width, height, Component.empty(), normalize(initial, min, max));
		this.messageBuilder = messageBuilder;
		this.min = min;
		this.max = max;
		this.onChange = onChange;
		updateMessage();
	}

	private static double normalize(int value, int min, int max) {
		if (max == min) {
			return 0.0;
		}
		return Math.clamp((value - min) / (double) (max - min), 0.0, 1.0);
	}

	public int intValue() {
		return min + (int) Math.round(this.value * (max - min));
	}

	@Override
	protected void updateMessage() {
		// A superclasse pode chamar isto durante o proprio construtor, antes dos campos.
		if (messageBuilder == null) {
			return;
		}
		setMessage(messageBuilder.apply(intValue()));
	}

	@Override
	protected void applyValue() {
		if (onChange != null) {
			onChange.accept(intValue());
		}
	}

	// AbstractSliderButton alarga este metodo para public -- nao da para estreitar de volta.
	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;

		int trackHeight = 4;
		int trackY = getY() + getHeight() - trackHeight - 1;

		graphics.text(font, getMessage(), getX(), getY(),
				this.active ? Theme.TEXT : Theme.TEXT_DISABLED, false);

		Theme.roundedRect(graphics, getX(), trackY, getWidth(), trackHeight, 2, Theme.SURFACE_SUNKEN);

		int filled = (int) Math.round(this.value * getWidth());
		if (filled > 0) {
			Theme.roundedRect(graphics, getX(), trackY, filled, trackHeight, 2,
					this.active ? Theme.ACCENT : Theme.SURFACE_DISABLED);
		}

		int knobX = Math.clamp(getX() + filled - 3, getX(), getX() + getWidth() - 6);
		Theme.roundedRect(graphics, knobX, trackY - 3, 6, trackHeight + 6, 3,
				isHoveredOrFocused() ? 0xFFFFFFFF : Theme.TEXT);
	}
}
