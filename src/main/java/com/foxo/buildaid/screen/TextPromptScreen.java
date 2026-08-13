package com.foxo.buildaid.screen;

import com.foxo.buildaid.screen.widget.ModButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Caixa de dialogo simples para digitar um texto.
 *
 * <p>Existe porque o menu principal nao tem espaco sobrando para mais um campo de texto, e
 * renomear pedindo para o jogador reaproveitar a caixa de URL seria confuso. Volta sozinha para a
 * tela anterior ao confirmar ou cancelar.
 */
public class TextPromptScreen extends Screen {
	private static final int WIDTH = 260;
	private static final int HEIGHT = 92;

	private final Screen parent;
	private final Component prompt;
	private final String initialValue;
	private final Consumer<String> onConfirm;

	private EditBox input;
	private int panelX;
	private int panelY;

	public TextPromptScreen(Screen parent, Component title, Component prompt,
			String initialValue, Consumer<String> onConfirm) {
		super(title);
		this.parent = parent;
		this.prompt = prompt;
		this.initialValue = initialValue;
		this.onConfirm = onConfirm;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		panelX = (this.width - WIDTH) / 2;
		panelY = (this.height - HEIGHT) / 2;

		input = new EditBox(this.font, panelX + Theme.PAD, panelY + 34,
				WIDTH - Theme.PAD * 2, 20, this.prompt);
		input.setMaxLength(120);
		input.setValue(initialValue == null ? "" : initialValue);
		addRenderableWidget(input);
		setInitialFocus(input);

		int half = (WIDTH - Theme.PAD * 3) / 2;
		addRenderableWidget(new ModButton(panelX + Theme.PAD, panelY + HEIGHT - 28, half, 20,
				Component.translatable("buildaid.menu.confirm"), ModButton.Style.PRIMARY, this::confirm));
		addRenderableWidget(new ModButton(panelX + Theme.PAD * 2 + half, panelY + HEIGHT - 28, half, 20,
				Component.translatable("buildaid.menu.cancel"), ModButton.Style.NORMAL, this::onClose));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, Theme.SCRIM);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Theme.roundedRect(graphics, panelX, panelY, WIDTH, HEIGHT, 4, Theme.BACKGROUND);
		Theme.roundedOutline(graphics, panelX, panelY, WIDTH, HEIGHT, 4, Theme.BORDER);

		graphics.text(this.font, this.title, panelX + Theme.PAD, panelY + 10, Theme.TEXT, false);
		graphics.text(this.font, this.prompt, panelX + Theme.PAD, panelY + 22, Theme.TEXT_DIM, false);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			confirm();
			return true;
		}
		return super.keyPressed(event);
	}

	private void confirm() {
		String value = input.getValue().trim();
		if (!value.isEmpty()) {
			onConfirm.accept(value);
		}
		onClose();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(parent);
	}
}
