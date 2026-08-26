package com.foxo.buildaid.screen.widget;

import com.foxo.buildaid.screen.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Interruptor tipo pilula: rotulo a esquerda, chave a direita.
 *
 * <p>Le o valor de um {@link BooleanSupplier} a cada frame em vez de guardar copia -- assim a
 * chave nunca fica dessincronizada quando algo de fora (tecla de atalho, outra aba) muda a config.
 */
public class ModToggle extends AbstractWidget {
	private static final int TRACK_WIDTH = 26;
	private static final int TRACK_HEIGHT = 12;

	private final BooleanSupplier getter;
	private final Consumer<Boolean> setter;

	public ModToggle(int x, int y, int width, int height, Component label,
			BooleanSupplier getter, Consumer<Boolean> setter) {
		super(x, y, width, height, label);
		this.getter = getter;
		this.setter = setter;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		boolean on = getter.getAsBoolean();
		boolean hovered = isHoveredOrFocused();

		if (hovered) {
			Theme.roundedRect(graphics, getX() - 4, getY() - 2, getWidth() + 8, getHeight() + 4, Theme.SURFACE_HOVER);
		}

		Font font = Minecraft.getInstance().font;
		graphics.text(font, getMessage().getString(), getX(),
				getY() + (getHeight() - font.lineHeight) / 2 + 1,
				this.active ? Theme.TEXT : Theme.TEXT_DISABLED, false);

		int trackX = getX() + getWidth() - TRACK_WIDTH;
		int trackY = getY() + (getHeight() - TRACK_HEIGHT) / 2;

		int trackColor = !this.active ? Theme.SURFACE_DISABLED : on ? Theme.accent() : Theme.SURFACE_SUNKEN;
		Theme.roundedRect(graphics, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT / 2, trackColor);
		if (!on) {
			Theme.roundedOutline(graphics, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT / 2, Theme.BORDER);
		}

		int knobSize = TRACK_HEIGHT - 4;
		int knobX = on ? trackX + TRACK_WIDTH - knobSize - 2 : trackX + 2;
		Theme.roundedRect(graphics, knobX, trackY + 2, knobSize, knobSize, knobSize / 2,
				this.active ? 0xFFFFFFFF : Theme.TEXT_DISABLED);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		setter.accept(!getter.getAsBoolean());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
