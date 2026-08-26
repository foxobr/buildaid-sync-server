package com.foxo.buildaid.screen.widget;

import com.foxo.buildaid.screen.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Botao chapado no visual do mod. Tres estilos: normal, primario (acento) e destrutivo.
 */
public class ModButton extends AbstractWidget {
	public enum Style {
		NORMAL, PRIMARY, DANGER
	}

	private final Runnable onPress;
	private final Style style;

	public ModButton(int x, int y, int width, int height, Component message, Style style, Runnable onPress) {
		super(x, y, width, height, message);
		this.style = style;
		this.onPress = onPress;
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
		String label = getMessage().getString();
		int labelWidth = font.width(label);
		graphics.text(font, label,
				getX() + (getWidth() - labelWidth) / 2,
				getY() + (getHeight() - font.lineHeight) / 2 + 1,
				textColor, false);
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
