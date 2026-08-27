package com.foxo.buildaid.screen.widget;

import com.foxo.buildaid.screen.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Botao chapado no visual do mod. Tres estilos: normal, primario (acento) e destrutivo.
 *
 * <p>O texto e truncado com "..." se nao couber -- assim botoes estreitos (ex.: as transformacoes
 * de blueprint divididas em 3 colunas) nunca vazam sobre o vizinho nem saem do painel. Opcionalmente
 * um tooltip mostra o texto completo quando o rotulo foi encurtado.
 */
public class ModButton extends AbstractWidget {
	public enum Style {
		NORMAL, PRIMARY, DANGER
	}

	private final Runnable onPress;
	private final Style style;
	private final Component tooltip;

	public ModButton(int x, int y, int width, int height, Component message, Style style, Runnable onPress) {
		this(x, y, width, height, message, style, onPress, null);
	}

	public ModButton(int x, int y, int width, int height, Component message, Style style,
			Runnable onPress, Component tooltip) {
		super(x, y, width, height, message);
		this.style = style;
		this.onPress = onPress;
		this.tooltip = tooltip;
	}

	public static ModButton of(int x, int y, int width, int height, String label, Runnable onPress) {
		return new ModButton(x, y, width, height, Component.literal(label), Style.NORMAL, onPress);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		boolean hovered = isHoveredOrFocused();
		int background;
		int textColor;

		if (!this.active) {
			background = Theme.SURFACE_DISABLED;
			textColor = Theme.TEXT_DISABLED;
		} else {
			background = switch (style) {
				case PRIMARY -> hovered ? Theme.accentHover() : Theme.accent();
				case DANGER -> hovered ? Theme.DANGER_HOVER : Theme.DANGER;
				case NORMAL -> hovered ? Theme.SURFACE_HOVER : Theme.SURFACE;
			};
			textColor = style == Style.PRIMARY ? Theme.TEXT_ON_ACCENT : Theme.TEXT;
		}

		Theme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), background);
		if (style == Style.NORMAL && this.active) {
			Theme.roundedOutline(graphics, getX(), getY(), getWidth(), getHeight(), Theme.RADIUS, Theme.BORDER);
		}

		Font font = Minecraft.getInstance().font;
		// Trunca o rotulo para caber no botao, com reticencias, em vez de deixar vazar.
		String label = getMessage().getString();
		int maxText = getWidth() - (Theme.BUTTON_LABEL_PADDING * 2); // was - 8
		if (font.width(label) > maxText) {
			label = font.plainSubstrByWidth(label, maxText - 6) + "...";
		}
		graphics.text(font, label,
				getX() + (getWidth() - font.width(label)) / 2,
				getY() + (getHeight() - font.lineHeight) / 2 + 1,
				textColor, false);

		// Tooltip aparece so quando o cursor esta em cima (e ha o que mostrar).
		if (hovered && tooltip != null) {
			graphics.setComponentTooltipForNextFrame(font, List.of(tooltip), mouseX, mouseY);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		onPress.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
