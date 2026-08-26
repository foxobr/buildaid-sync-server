package com.foxo.buildaid.screen;

import com.foxo.buildaid.Feedback;
import com.foxo.buildaid.screen.widget.ModButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Editor do texto de uma anotacao.
 *
 * <p>Cada linha e um {@link EditBox} proprio -- reaproveitar o widget do jogo sai muito mais
 * barato e acessivel do que escrever um editor de texto com cursor do zero, e cobre o caso de
 * uso (listas de materiais, lembretes de passo a passo). Enter cria a linha seguinte; o "x"
 * ao lado remove; linhas em branco no fim sao descartadas ao salvar.
 */
public class NoteEditScreen extends Screen {
	private static final int PANEL_WIDTH = 280;
	private static final int HEADER_HEIGHT = 40;
	private static final int FOOTER_HEIGHT = 32;
	private static final int ROW_HEIGHT = 22;
	private static final int MAX_CHARS_PER_LINE = 90;

	private final Screen parent;
	private final Consumer<String> onSave;
	private final String initialText;
	private final List<String> lines;
	/** Caixas na ordem das linhas visiveis; reconstruido a cada init. */
	private final List<EditBox> lineBoxes = new ArrayList<>();
	/** Linha que deve receber o foco depois de reconstruir os widgets. */
	private int focusRequest = -1;

	public NoteEditScreen(Screen parent, String initialText, Consumer<String> onSave) {
		super(Component.translatable("buildaid.noteedit.title"));
		this.parent = parent;
		this.onSave = onSave;
		this.initialText = initialText == null ? "" : initialText;
		this.lines = new ArrayList<>();
		if (!this.initialText.isEmpty()) {
			for (String line : this.initialText.split("\n", -1)) {
				lines.add(line.length() > MAX_CHARS_PER_LINE ? line.substring(0, MAX_CHARS_PER_LINE) : line);
			}
		}
		if (lines.isEmpty()) {
			lines.add("");
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelHeight() {
		return Math.min(340, this.height - 24);
	}

	/** Quantas linhas cabem na caixa com o tamanho atual da janela. */
	private int capacity() {
		return Math.max(3, (panelHeight() - HEADER_HEIGHT - FOOTER_HEIGHT) / ROW_HEIGHT);
	}

	@Override
	protected void init() {
		int px = (this.width - PANEL_WIDTH) / 2;
		int py = (this.height - panelHeight()) / 2;

		int rowsTop = py + HEADER_HEIGHT;
		int visible = Math.min(lines.size(), capacity());
		lineBoxes.clear();

		for (int i = 0; i < visible; i++) {
			int rowY = rowsTop + i * ROW_HEIGHT;
			int captured = i;
			EditBox box = new EditBox(this.font, px + Theme.PAD, rowY,
					PANEL_WIDTH - Theme.PAD * 2 - 16, 16, Component.empty());
			box.setMaxLength(MAX_CHARS_PER_LINE);
			String value = lines.get(i);
			box.setValue(value == null ? "" : value);
			box.setResponder(value2 -> {
				if (captured < lines.size()) {
					lines.set(captured, value2);
				}
			});
			addRenderableWidget(box);
			lineBoxes.add(box);

			if (visible > 1) {
				addRenderableWidget(new ModButton(px + PANEL_WIDTH - Theme.PAD - 12, rowY - 1, 12, 16,
						Component.literal("x"), ModButton.Style.DANGER, () -> removeLine(captured)));
			}
		}

		int footerY = py + panelHeight() - FOOTER_HEIGHT + 6;
		boolean canAdd = lines.size() < capacity();

		ModButton add = new ModButton(px + Theme.PAD, footerY, 74, 20,
				Component.translatable("buildaid.noteedit.add_line"), ModButton.Style.NORMAL, this::addLine);
		add.active = canAdd;
		addRenderableWidget(add);

		addRenderableWidget(new ModButton(px + PANEL_WIDTH - Theme.PAD - 140, footerY, 64, 20,
				Component.translatable("buildaid.menu.cancel"), ModButton.Style.NORMAL, this::cancel));
		addRenderableWidget(new ModButton(px + PANEL_WIDTH - Theme.PAD - 70, footerY, 70, 20,
				Component.translatable("buildaid.menu.confirm"), ModButton.Style.PRIMARY, this::confirm));

		// Foco pedido por quem mexeu na lista (Enter adiciona e ja foca a linha nova).
		if (focusRequest >= 0 && focusRequest < lineBoxes.size()) {
			setFocused(lineBoxes.get(focusRequest));
		} else if (!lineBoxes.isEmpty()) {
			setFocused(lineBoxes.getFirst());
		}
	}

	private void addLine() {
		if (lines.size() >= capacity()) {
			return;
		}
		lines.add("");
		focusRequest = Math.max(0, lines.size() - 1);
		rebuildWidgets();
	}

	private void removeLine(int index) {
		if (lines.size() <= 1 || index < 0 || index >= lines.size()) {
			return;
		}
		lines.remove(index);
		focusRequest = Math.min(index, lines.size() - 1);
		rebuildWidgets();
	}

	private void confirm() {
		// Linhas vazias no fim nao servem para nada dentro da caixa desenhada.
		List<String> cleaned = new ArrayList<>(lines);
		while (cleaned.size() > 1 && (cleaned.getLast() == null || cleaned.getLast().isBlank())) {
			cleaned.removeLast();
		}
		String joined = String.join("\n", cleaned);
		onSave.accept(joined);
		if (!joined.equals(initialText)) {
			Feedback.info("buildaid.msg.note_saved");
		}
		this.minecraft.setScreenAndShow(parent);
	}

	private void cancel() {
		this.minecraft.setScreenAndShow(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, Theme.SCRIM);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int px = (this.width - PANEL_WIDTH) / 2;
		int py = (this.height - panelHeight()) / 2;

		Theme.roundedRect(graphics, px, py, PANEL_WIDTH, panelHeight(), 4, Theme.BACKGROUND);
		Theme.roundedOutline(graphics, px, py, PANEL_WIDTH, panelHeight(), 4, Theme.BORDER);

		graphics.text(this.font, this.title, px + Theme.PAD, py + 10, Theme.TEXT, false);
		graphics.text(this.font, Component.translatable("buildaid.noteedit.hint"),
				px + Theme.PAD, py + 24, Theme.TEXT_DIM, false);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			if (getFocused() instanceof EditBox target) {
				int index = lineBoxes.indexOf(target);
				if (index >= 0) {
					if (index == lines.size() - 1 && lines.size() < capacity()) {
						addLine(); // Enter na ultima linha: cria e foca a proxima.
					} else {
						focusRequest = Math.min(index + 1, lines.size() - 1);
						rebuildWidgets();
					}
					return true;
				}
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		// Esc tambem salva: perder o que foi digitado numa tecla errada seria pior.
		confirm();
	}
}
