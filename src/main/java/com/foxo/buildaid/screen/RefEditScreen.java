package com.foxo.buildaid.screen;

import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.config.GlobalUndo;
import com.foxo.buildaid.hud.RefRenderer;
import com.foxo.buildaid.image.ImageLibrary;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Modo de ajuste dos paineis e das anotacoes.
 *
 * <p>Existe porque o HUD nao recebe mouse enquanto o jogo captura o cursor. Abrindo uma
 * {@link Screen} o cursor e liberado e da para arrastar, redimensionar e dar zoom/pan.
 *
 * <p>Com varios paineis, clicar seleciona o que estiver sob o cursor -- de cima para baixo, ja
 * que o ultimo da lista e o desenhado por cima. So o selecionado responde ao arrasto. Clicar fora
 * de todo painel pega uma anotacao, se houver uma ali: as notas sao post-its, e post-it se move
 * com a mao, nao com slider.
 *
 * <p>Nao pausa o jogo, entao da para ajustar a referencia sem interromper um servidor.
 */
public class RefEditScreen extends Screen {
	private static final int HANDLE_SIZE = 14;
	private static final int TEXT_COLOR = 0xFFF0F4FA;

	private enum Drag {
		NONE, MOVE, RESIZE, PAN, MOVE_NOTE
	}

	private final ImageLibrary library;
	private final BuildAidConfig config = BuildAidConfig.get();

	private Drag drag = Drag.NONE;
	private int selected;
	/** Anotacao selecionada ao clicar fora dos paineis (-1 = nenhuma). */
	private int selectedNote = -1;

	public RefEditScreen(ImageLibrary library) {
		super(Component.translatable("buildaid.edit.title"));
		this.library = library;
	}

	@Override
	protected void init() {
		selected = Math.clamp(selected, 0, Math.max(0, config.panels.size() - 1));
		selectedNote = Math.clamp(selectedNote, -1, Math.max(-1, config.notes.size() - 1));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Escurece de leve so para destacar os paineis -- o mundo continua visivel para alinhar.
		graphics.fill(0, 0, this.width, this.height, 0x66000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		List<BuildAidConfig.Panel> panels = config.panels;

		// Anotacoes primeiro: ficam atras dos paineis, igual na HUD de verdade.
		for (int i = 0; i < config.notes.size(); i++) {
			BuildAidConfig.Note note = config.notes.get(i);
			if (!note.visible || note.text == null || note.text.isBlank()) {
				continue;
			}
			int height = com.foxo.buildaid.hud.NoteHudElement.heightOf(this.font, note.text, note.width);
			int accent = Theme.NOTE_ACCENTS[Math.floorMod(note.colorPreset, Theme.NOTE_ACCENTS.length)];

			Theme.roundedRect(graphics, note.x, note.y, note.width, height, 3, 0xF010141B);
			Theme.roundedRect(graphics, note.x, note.y, 3, height, 1, accent);

			var lines = com.foxo.buildaid.hud.NoteHudElement.wrap(
					this.font, note.text, note.width - 15);
			for (int l = 0; l < lines.size(); l++) {
				graphics.text(this.font, lines.get(l), note.x + 9, note.y + 6 + l * 10,
						0xFFE8ECF2, false);
			}

			if (i == selectedNote && drag != Drag.MOVE_NOTE) {
				Theme.roundedOutline(graphics, note.x - 1, note.y - 1, note.width + 2, height + 2,
						Theme.RADIUS, accent);
			}
		}

		for (int i = 0; i < panels.size(); i++) {
			BuildAidConfig.Panel panel = panels.get(i);
			ImageLibrary.Loaded image = panel.imageId == null ? null : library.get(panel.imageId);

			// Opacidade forcada em 1.0: no modo de ajuste voce precisa enxergar as bordas.
			RefRenderer.drawPanel(graphics, panel, image, 1.0f);

			if (i == selected) {
				Theme.roundedOutline(graphics, panel.x - 1, panel.y - 1, panel.width + 2, panel.height + 2,
						Theme.RADIUS, Theme.ACCENT);
				// Alca de redimensionar, so no painel selecionado.
				graphics.fill(panel.x + panel.width - HANDLE_SIZE, panel.y + panel.height - HANDLE_SIZE,
						panel.x + panel.width, panel.y + panel.height, Theme.ACCENT);
			}

			if (image == null) {
				Component message = Component.translatable(panel.imageId == null
						? "buildaid.edit.no_image"
						: "buildaid.edit.loading");
				graphics.text(this.font, message, panel.x + 6, panel.y + 6, TEXT_COLOR, true);
			}

			String label = String.valueOf(i + 1);
			graphics.text(this.font, label, panel.x + 4, panel.y + panel.height - 11,
					i == selected ? Theme.ACCENT : Theme.TEXT_DIM, true);
		}

		if (panels.isEmpty()) {
			graphics.text(this.font, Component.translatable("buildaid.edit.no_panels"),
					this.width / 2 - 80, this.height / 2, TEXT_COLOR, true);
		}

		for (int i = 1; i <= 7; i++) {
			graphics.text(this.font, Component.translatable("buildaid.edit.help" + i),
					10, 10 + (i - 1) * 12, TEXT_COLOR, true);
		}
	}

	// ------------------------------------------------------------------ entrada

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		double mouseX = event.x();
		double mouseY = event.y();

		// A alca do painel ja selecionado tem prioridade sobre a selecao de outro painel.
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && inHandle(current(), mouseX, mouseY)) {
			drag = Drag.RESIZE;
			return true;
		}

		int hit = panelAt(mouseX, mouseY);
		if (hit < 0) {
			// Fora de todo painel: um post-it ali? Clicar numa anotacao a seleciona e move.
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				int noteHit = noteAt(mouseX, mouseY);
				if (noteHit >= 0) {
					selectedNote = noteHit;
					drag = Drag.MOVE_NOTE;
					return true;
				}
			}
			selectedNote = -1;
			return false;
		}
		selected = hit;
		selectedNote = -1;

		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			drag = Drag.MOVE;
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			drag = Drag.PAN;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (drag == Drag.MOVE_NOTE) {
			BuildAidConfig.Note note = currentNote();
			if (note != null) {
				note.x += (int) Math.round(dragX);
				note.y += (int) Math.round(dragY);
				note.x = Math.clamp(note.x, -note.width + 24, this.width - 24);
				note.y = Math.clamp(note.y, 0, Math.max(0, this.height - 24));
			}
			return true;
		}

		BuildAidConfig.Panel panel = current();
		if (drag == Drag.NONE || panel == null) {
			return super.mouseDragged(event, dragX, dragY);
		}

		switch (drag) {
			case MOVE -> {
				panel.x += (int) Math.round(dragX);
				panel.y += (int) Math.round(dragY);
				clampToScreen(panel);
			}
			case RESIZE -> {
				panel.width = Math.clamp(panel.width + (int) Math.round(dragX), 48, this.width);
				panel.height = Math.clamp(panel.height + (int) Math.round(dragY), 48, this.height);
			}
			case PAN -> {
				panel.imageOffsetX += (float) dragX;
				panel.imageOffsetY += (float) dragY;
			}
			default -> {
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drag != Drag.NONE) {
			drag = Drag.NONE;
			config.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		BuildAidConfig.Panel panel = current();
		if (panel != null && inPanel(panel, mouseX, mouseY) && scrollY != 0) {
			float factor = scrollY > 0 ? 1.1f : 1.0f / 1.1f;
			panel.imageScale = Math.clamp(panel.imageScale * factor, 0.05f, 20.0f);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// R/T/Delete agem no painel; com o foco numa anotacao, nao roubam a acao.
		BuildAidConfig.Panel panel = selectedNote >= 0 ? null : current();

		if (event.key() == GLFW.GLFW_KEY_R && panel != null) {
			panel.imageScale = 1.0f;
			panel.imageOffsetX = 0.0f;
			panel.imageOffsetY = 0.0f;
			config.save();
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_T && panel != null) {
			panel.rotation = (panel.rotation + 90) % 360;
			config.save();
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_DELETE && panel != null) {
			GlobalUndo.push();
			config.panels.remove(selected);
			selected = Math.clamp(selected, 0, Math.max(0, config.panels.size() - 1));
			config.save();
			return true;
		}

		// Com uma anotacao selecionada, Delete remove o post-it tambem.
		if (event.key() == GLFW.GLFW_KEY_DELETE && selectedNote >= 0 && selectedNote < config.notes.size()) {
			GlobalUndo.push();
			config.notes.remove(selectedNote);
			selectedNote = -1;
			drag = Drag.NONE;
			config.save();
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_TAB && !config.panels.isEmpty()) {
			selected = (selected + 1) % config.panels.size();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}

	// ------------------------------------------------------------------ apoio

	private BuildAidConfig.Panel current() {
		List<BuildAidConfig.Panel> panels = config.panels;
		return selected >= 0 && selected < panels.size() ? panels.get(selected) : null;
	}

	/** Anotacao sob o cursor, de cima para baixo (a ultima da lista desenha por cima). */
	private int noteAt(double mouseX, double mouseY) {
		for (int i = config.notes.size() - 1; i >= 0; i--) {
			BuildAidConfig.Note note = config.notes.get(i);
			if (!note.visible) {
				continue;
			}
			int height = com.foxo.buildaid.hud.NoteHudElement.heightOf(this.font, note.text, note.width);
			if (mouseX >= note.x && mouseX <= note.x + note.width
					&& mouseY >= note.y && mouseY <= note.y + height) {
				return i;
			}
		}
		return -1;
	}

	private BuildAidConfig.Note currentNote() {
		return selectedNote >= 0 && selectedNote < config.notes.size()
				? config.notes.get(selectedNote)
				: null;
	}

	/**
	 * Painel sob o cursor, do topo para o fundo (o ultimo da lista e o desenhado por cima).
	 * Paineis travados sao ignorados -- e para isso que a trava existe: deixar um de fundo fixo
	 * sem risco de arrasta-lo ao mirar em outro.
	 */
	private int panelAt(double mouseX, double mouseY) {
		List<BuildAidConfig.Panel> panels = config.panels;
		for (int i = panels.size() - 1; i >= 0; i--) {
			if (!panels.get(i).locked && inPanel(panels.get(i), mouseX, mouseY)) {
				return i;
			}
		}
		return -1;
	}

	/** Impede que o painel seja arrastado inteiramente para fora da tela. */
	private void clampToScreen(BuildAidConfig.Panel panel) {
		int margin = 24;
		panel.x = Math.clamp(panel.x, -panel.width + margin, this.width - margin);
		panel.y = Math.clamp(panel.y, 0, this.height - margin);
	}

	private static boolean inPanel(BuildAidConfig.Panel p, double mouseX, double mouseY) {
		return p != null && mouseX >= p.x && mouseX <= p.x + p.width
				&& mouseY >= p.y && mouseY <= p.y + p.height;
	}

	private static boolean inHandle(BuildAidConfig.Panel p, double mouseX, double mouseY) {
		return p != null && mouseX >= p.x + p.width - HANDLE_SIZE && mouseX <= p.x + p.width
				&& mouseY >= p.y + p.height - HANDLE_SIZE && mouseY <= p.y + p.height;
	}
}
