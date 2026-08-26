package com.foxo.buildaid.screen;

import com.foxo.buildaid.BuildAidClient;
import com.foxo.buildaid.Feedback;
import com.foxo.buildaid.build.AreaSelection;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.config.GlobalUndo;
import com.foxo.buildaid.config.ClothConfigScreen;
import com.foxo.buildaid.hud.RefRenderer;
import com.foxo.buildaid.image.ImageLibrary;
import com.foxo.buildaid.image.RefImage;
import com.foxo.buildaid.screen.widget.ModButton;
import com.foxo.buildaid.screen.widget.ModSlider;
import com.foxo.buildaid.screen.widget.ModToggle;
import com.foxo.buildaid.shape.ShapeGuide;
import com.foxo.buildaid.build.Blueprint;
import com.foxo.buildaid.build.LitematicReader;
import com.foxo.buildaid.image.source.NativeFilePicker;
import com.foxo.buildaid.shape.ShapeType;
import com.foxo.buildaid.world.ImageHologram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Menu principal do mod: uma tecla, uma tela, tudo dentro.
 *
 * <p>Substitui a antiga divisao entre "gerenciador de imagens" e a tela de opcoes. O Cloth Config
 * continua existindo para os ajustes finos e e alcancado pelo botao do rodape.
 *
 * <p>O desenho e todo proprio ({@link Theme}) em vez do sistema de abas do vanilla, porque o
 * visual pedido foge do estilo do jogo.
 */
public class BuildAidMenuScreen extends Screen {
	private enum TabId {
		IMAGENS("images"),
		PAINEL("panel"),
		HOLOGRAMA("hologram"),
		FORMAS("shape"),
		SELECAO("selection"),
		CORES("colors"),
		RANDOMIZADOR("randomizer"),
		HUD("hud"),
		PERFIS("profiles");

		private final String key;

		TabId(String key) {
			this.key = key;
		}

		Component title() {
			return Component.translatable("buildaid.tab." + key);
		}
	}

	private static final int SIDEBAR_WIDTH = 86;
	private static final int HEADER_HEIGHT = 26;
	private static final int FOOTER_HEIGHT = 30;
	private static final int TAB_HEIGHT = 22;

	private static final int TILE_WIDTH = 84;
	private static final int TILE_HEIGHT = 74;
	private static final int TILE_GAP = 6;
	private static final int PANEL_ROW = 20;

	/** Sobrevive ao fechar a tela: reabrir cai na mesma aba. */
	private static TabId lastTab = TabId.IMAGENS;

	private final BuildAidConfig config = BuildAidConfig.get();
	private final ImageLibrary library = BuildAidClient.library;

	private TabId activeTab = lastTab;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int contentX;
	private int contentY;
	private int contentWidth;
	private int contentHeight;

	// Galeria de imagens
	private int galleryX;
	private int galleryY;
	private int galleryWidth;
	private int galleryHeight;
	private int galleryScroll;

	// Lista de paineis + anotacoes (lista unica, com divisor entre os grupos)
	private int panelListX;
	private int panelListY;
	private int panelListWidth;
	private int panelListHeight;
	/** Indice na lista unificada: 0..panels-1 sao paineis, o resto sao anotacoes. */
	private int selectedEntry = -1;
	private static int lastPanelTabSelection = -1;

	// Hologramas e Formas
	private int instanceListX;
	private int instanceListY;
	private int instanceListWidth;
	private int instanceListHeight;
	private int selectedHologram = -1;
	private static int lastSelectedHologram = -1;
	private int selectedShape = -1;
	private static int lastSelectedShape = -1;

	private String selectedId = null;

	// Lista de materiais de formas
	private int materialsX;
	private int materialsY;
	private int materialsWidth;
	private int materialsHeight;
	private int materialsScroll;

	// Aba Selecao
	private List<AreaSelection.MaterialCount> scannedMaterials = new ArrayList<>();
	private int scannedMaterialsScroll;

	// Aba Cores: estado sobrevive a fechar a tela, como o texto de busca.
	private static float pickerHue = 210.0f / 360.0f;
	private static float pickerSat = 0.72f;
	private static float pickerVal = 0.92f;

	/** Blocos sugeridos para a cor atual, do mais proximo ao mais distante. */
	private List<net.minecraft.world.level.block.Block> suggestions = List.of();
	private int cachedSuggestionRgb = -1;
	private int selectedSuggestion = -1;
	private int suggestX;
	private int suggestY;
	private int suggestWidth;
	private int suggestHeight;
	private com.foxo.buildaid.screen.widget.ColorPickerWidget colorPicker;
	/** Campo do codigo hex da cor; espelhado com o seletor. */
	private EditBox hexBox;

	// Aba Randomizador: estado de scroll e geometria da lista.
	private int randomizerScroll;
	private int randomizerListX;
	private int randomizerListY;
	private int randomizerListWidth;
	private int randomizerListHeight;
	private static final int RANDOMIZER_ROW = 22;

	// Aba Imagens (helper)
	private EditBox searchBox;
	private static String searchText = "";
	private List<RefImage> images = List.of();

	public BuildAidMenuScreen() {
		// Nome do mod, nao traduzido de proposito.
		super(Component.literal("BuildAid"));
	}

	@Override
	public boolean isPauseScreen() {
		// Nao pausa: da para mexer na referencia sem interromper um servidor.
		return false;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(460, this.width - 32);
		panelHeight = Math.min(292, this.height - 32);
		panelX = (this.width - panelWidth) / 2;
		panelY = (this.height - panelHeight) / 2;

		contentX = panelX + SIDEBAR_WIDTH + Theme.PAD;
		contentY = panelY + HEADER_HEIGHT + Theme.PAD;
		contentWidth = panelWidth - SIDEBAR_WIDTH - Theme.PAD * 2;
		contentHeight = panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - Theme.PAD * 2;

		// Estado transitorio da aba Perfis: recomeca limpo a cada (re)construcao.
		noteKey = null;
		profileDeleteIndex = -1;
		profileDeleteHover = false;

		buildFooter();

		switch (activeTab) {
			case IMAGENS -> buildImagesTab();
			case PAINEL -> buildPanelTab();
			case HOLOGRAMA -> buildHologramTab();
			case FORMAS -> buildShapeTab();
			case SELECAO -> buildSelectionTab();
			case CORES -> buildColorsTab();
			case RANDOMIZADOR -> buildRandomizerTab();
			case HUD -> buildHudTab();
			case PERFIS -> buildProfilesTab();
		}
	}

	private void switchTab(TabId tab) {
		if (activeTab == tab) {
			return;
		}
		activeTab = tab;
		lastTab = tab;
		rebuildWidgets();
	}

	// ------------------------------------------------------------------ abas

	private void buildFooter() {
		int footerY = panelY + panelHeight - FOOTER_HEIGHT + 5;

		ModButton undoBtn = new ModButton(panelX + Theme.PAD, footerY, 110, 20,
				Component.translatable("buildaid.menu.undo"), ModButton.Style.NORMAL, () -> {
			if (!GlobalUndo.undo()) {
				Feedback.info("buildaid.msg.undo_empty");
			} else {
				Feedback.info("buildaid.msg.undo_done");
				// Algumas abas guardam o indice selecionado; recompoe para nao apontar fora da lista.
				rebuildWidgets();
			}
		});
		undoBtn.active = GlobalUndo.hasHistory();
		addRenderableWidget(undoBtn);

		addRenderableWidget(new ModButton(panelX + panelWidth - Theme.PAD - 70, footerY, 70, 20,
				Component.translatable("buildaid.menu.close"), ModButton.Style.NORMAL, this::onClose));
	}

	private void buildImagesTab() {
		refreshImages();
		if (selectedId == null) {
			selectedId = config.activeImageId;
		}

		int y = contentY;
		int third = (contentWidth - Theme.PAD * 2) / 3;

		// Adicionar imagem: as tres fontes na mesma linha. A URL abre um dialogo proprio em vez
		// de ocupar uma linha inteira com uma caixa de texto que quase nunca e usada.
		addRenderableWidget(new ModButton(contentX, y, third, 20,
				Component.translatable("buildaid.menu.paste"), ModButton.Style.NORMAL, () -> {
			BuildAidClient.importer.fromClipboard();
			onClose();
		}));
		addRenderableWidget(new ModButton(contentX + third + Theme.PAD, y, third, 20,
				Component.translatable("buildaid.menu.open_file"), ModButton.Style.NORMAL, () -> {
			BuildAidClient.importer.fromFilePicker();
			onClose();
		}));
		addRenderableWidget(new ModButton(contentX + (third + Theme.PAD) * 2, y, third, 20,
				Component.translatable("buildaid.menu.download"), ModButton.Style.NORMAL, this::promptUrl));

		y += 26;
		searchBox = new EditBox(this.font, contentX, y, contentWidth, 20,
				Component.translatable("buildaid.menu.search"));
		searchBox.setMaxLength(64);
		searchBox.setHint(Component.translatable("buildaid.menu.search_hint"));
		searchBox.setValue(searchText);
		searchBox.setResponder(value -> {
			searchText = value;
			refreshImages();
			galleryScroll = 0;
		});
		addRenderableWidget(searchBox);

		y += 26;
		galleryX = contentX;
		galleryY = y;
		galleryWidth = contentWidth;
		galleryHeight = Math.max(TILE_HEIGHT, contentY + contentHeight - y - 26);

		int actionsY = galleryY + galleryHeight + 6;
		boolean hasSelection = selectedId != null;
		int quarter = (contentWidth - Theme.PAD * 3) / 4;

		ModButton use = new ModButton(contentX, actionsY, quarter, 20,
				Component.translatable("buildaid.menu.use"), ModButton.Style.PRIMARY, this::useSelected);
		use.active = hasSelection;
		addRenderableWidget(use);

		ModButton newPanel = new ModButton(contentX + quarter + Theme.PAD, actionsY, quarter, 20,
				Component.translatable("buildaid.menu.use_new_panel"), ModButton.Style.NORMAL, this::useAsNewPanel);
		newPanel.active = hasSelection;
		addRenderableWidget(newPanel);

		ModButton rename = new ModButton(contentX + (quarter + Theme.PAD) * 2, actionsY, quarter, 20,
				Component.translatable("buildaid.menu.rename"), ModButton.Style.NORMAL, this::promptRename);
		rename.active = hasSelection;
		addRenderableWidget(rename);

		ModButton remove = new ModButton(contentX + (quarter + Theme.PAD) * 3, actionsY, quarter, 20,
				Component.translatable("buildaid.menu.remove"), ModButton.Style.DANGER, this::removeSelected);
		remove.active = hasSelection;
		addRenderableWidget(remove);

		clampGalleryScroll();
	}

	/** Aplica o filtro de busca sobre a biblioteca. Sem texto, mostra tudo. */
	private void refreshImages() {
		List<RefImage> all = BuildAidClient.store.all();
		if (searchText == null || searchText.isBlank()) {
			images = all;
			return;
		}
		String needle = searchText.toLowerCase(java.util.Locale.ROOT);
		images = all.stream()
				.filter(image -> image.displayName().toLowerCase(java.util.Locale.ROOT).contains(needle))
				.toList();
	}

	private void promptUrl() {
		this.minecraft.setScreenAndShow(new TextPromptScreen(this,
				Component.translatable("buildaid.menu.download"),
				Component.translatable("buildaid.menu.url_hint"),
				"",
				url -> BuildAidClient.importer.fromUrl(url)));
	}

	private void promptRename() {
		if (selectedId == null) {
			return;
		}
		BuildAidClient.store.byId(selectedId).ifPresent(image ->
				this.minecraft.setScreenAndShow(new TextPromptScreen(this,
						Component.translatable("buildaid.menu.rename"),
						Component.translatable("buildaid.menu.rename_hint"),
						image.displayName(),
						newName -> BuildAidClient.store.rename(image.id(), newName))));
	}

	/**
	 * Aba Paineis unificada: a lista mostra os paineis de referencia e, abaixo de um divisor,
	 * as anotacoes. O painel direito troca os controles conforme o tipo do item selecionado --
	 * e o overlay de tela cheia (global) fica sempre visivel no fim.
	 */
	private void buildPanelTab() {
		int total = config.panels.size() + config.notes.size();
		selectedEntry = Math.clamp(lastPanelTabSelection, -1, Math.max(0, total - 1));
		lastPanelTabSelection = selectedEntry;

		int listWidth = 166;
		int rightX = contentX + listWidth + Theme.PAD;
		int rightWidth = contentWidth - listWidth - Theme.PAD;

		panelListX = contentX;
		panelListY = contentY;
		panelListWidth = listWidth;
		panelListHeight = contentHeight - 52;

		// Botoes da lista: linha 1 cria, linha 2 manipula o item selecionado.
		int buttonsY = panelListY + panelListHeight + 6;
		int halfList = (listWidth - Theme.PAD) / 2;
		addRenderableWidget(new ModButton(panelListX, buttonsY, halfList, 20,
				Component.translatable("buildaid.menu.panel_add"), ModButton.Style.PRIMARY, this::addPanel));

		addRenderableWidget(new ModButton(panelListX + halfList + Theme.PAD, buttonsY, halfList, 20,
				Component.translatable("buildaid.menu.note_new"), ModButton.Style.NORMAL, this::addNoteFromPanelTab));

		ModButton duplicate = new ModButton(panelListX, buttonsY + 24, halfList, 20,
				Component.translatable("buildaid.menu.panel_duplicate"), ModButton.Style.NORMAL, this::duplicateEntry);
		duplicate.active = hasSelectedEntry();
		addRenderableWidget(duplicate);

		ModButton remove = new ModButton(panelListX + halfList + Theme.PAD, buttonsY + 24, halfList, 20,
				Component.translatable("buildaid.menu.panel_remove"), ModButton.Style.DANGER, this::removeEntry);
		remove.active = hasSelectedEntry();
		addRenderableWidget(remove);

		// Controles do item selecionado
		int y = contentY;

		if (!entryIsNote()) {
			BuildAidConfig.Panel panel = selectedPanel();

			if (panel != null) {
				addRenderableWidget(new ModToggle(rightX, y, rightWidth, 18,
						Component.translatable("buildaid.menu.panel_visible"),
						() -> panel.visible, value -> {
					panel.visible = value;
					config.save();
				}));

				y += 24;
				addRenderableWidget(new ModSlider(rightX, y, rightWidth, 22,
						value -> Component.translatable("buildaid.menu.opacity", value),
						0, 100, Math.round(panel.opacity * 100), value -> {
					panel.opacity = value / 100.0f;
					config.save();
				}));

				y += 30;
				addRenderableWidget(new ModToggle(rightX, y, rightWidth, 18,
						Component.translatable("buildaid.menu.panel_locked"),
						() -> panel.locked, value -> {
					panel.locked = value;
					config.save();
				}));

				y += 24;
				addRenderableWidget(new ModButton(rightX, y, rightWidth, 20,
						Component.translatable("buildaid.menu.adjust"), ModButton.Style.PRIMARY,
						() -> this.minecraft.setScreenAndShow(new RefEditScreen(library))));
				y += 28;
			}
		} else {
			BuildAidConfig.Note note = selectedNote();

			if (note != null) {
				addRenderableWidget(new ModToggle(rightX, y, rightWidth, 18,
						Component.translatable("buildaid.menu.note_visible"),
						() -> note.visible, value -> {
					note.visible = value;
					config.save();
				}));

				y += 24;
				addRenderableWidget(new ModSlider(rightX, y, rightWidth, 22,
						value -> Component.translatable("buildaid.menu.note_opacity", value),
						10, 100, Math.round(note.opacity * 100), value -> {
					note.opacity = value / 100.0f;
					config.save();
				}));

				y += 28;
				int halfRight = (rightWidth - Theme.PAD) / 2;
				addRenderableWidget(new ModSlider(rightX, y, halfRight, 22,
						value -> Component.translatable("buildaid.menu.note_width", value),
						60, 400, note.width, value -> {
					note.width = value;
					config.save();
				}));
				addRenderableWidget(new ModSlider(rightX + halfRight + Theme.PAD, y, halfRight, 22,
						value -> Component.translatable("buildaid.menu.note_color", note.colorPreset + 1,
								Theme.NOTE_ACCENTS.length),
						0, Theme.NOTE_ACCENTS.length - 1, note.colorPreset, value -> {
					note.colorPreset = value;
					config.save();
				}));

				y += 28;
				addRenderableWidget(new ModButton(rightX, y, rightWidth, 20,
						Component.translatable("buildaid.menu.note_edit"), ModButton.Style.PRIMARY,
						this::openEditorForSelection));

				y += 24;
				// Post-it tambem se posiciona no modo de ajuste: clicar fora dos paineis o agarra.
				addRenderableWidget(new ModButton(rightX, y, rightWidth, 20,
						Component.translatable("buildaid.menu.adjust"), ModButton.Style.NORMAL,
						() -> this.minecraft.setScreenAndShow(new RefEditScreen(library))));
				y += 28;
			}
		}

		// Overlay de tela cheia: e global, nao pertence a nenhum painel nem anotacao.
		addRenderableWidget(new ModToggle(rightX, y, rightWidth, 18,
				Component.translatable("buildaid.menu.ghost"),
				() -> config.ghost.enabled, value -> {
			config.ghost.enabled = value;
			config.save();
		}));

		y += 24;
		addRenderableWidget(new ModSlider(rightX, y, rightWidth, 22,
				value -> Component.translatable("buildaid.menu.ghost_opacity", value),
				0, 100, Math.round(config.ghost.opacity * 100), value -> {
			config.ghost.opacity = value / 100.0f;
			config.save();
		}));
	}

	// ------------------------------------------------------------------ itens da aba Paineis

	private int entryCount() {
		return config.panels.size() + config.notes.size();
	}

	private boolean entryIsNote() {
		return selectedEntry >= config.panels.size() && selectedEntry < entryCount();
	}

	private boolean hasSelectedEntry() {
		return selectedEntry >= 0 && selectedEntry < entryCount();
	}

	private BuildAidConfig.Panel selectedPanel() {
		return !entryIsNote() && hasSelectedEntry()
				? config.panels.get(selectedEntry)
				: null;
	}

	private BuildAidConfig.Note selectedNote() {
		return entryIsNote()
				? config.notes.get(selectedEntry - config.panels.size())
				: null;
	}

	private void addNoteFromPanelTab() {
		config.addNote();
		selectedEntry = entryCount() - 1;
		lastPanelTabSelection = selectedEntry;
		config.save();
		rebuildWidgets();
	}

	private void duplicateEntry() {
		if (!hasSelectedEntry()) {
			return;
		}
		GlobalUndo.push();
		if (!entryIsNote()) {
			BuildAidConfig.Panel copy = selectedPanel().copy();
			copy.x += 24;
			copy.y += 24;
			config.panels.add(copy);
		} else {
			BuildAidConfig.Note copy = selectedNote().copy();
			copy.x += 20;
			copy.y += 20;
			config.notes.add(copy);
		}
		selectedEntry = entryCount() - 1;
		lastPanelTabSelection = selectedEntry;
		config.save();
		rebuildWidgets();
	}

	private void removeEntry() {
		if (!hasSelectedEntry()) {
			return;
		}
		GlobalUndo.push();
		if (!entryIsNote()) {
			config.panels.remove(selectedEntry);
		} else {
			config.notes.remove(selectedEntry - config.panels.size());
		}
		selectedEntry = Math.clamp(selectedEntry, -1, Math.max(-1, entryCount() - 1));
		lastPanelTabSelection = selectedEntry;
		config.save();
		rebuildWidgets();
	}

	private void openEditorForSelection() {
		BuildAidConfig.Note note = selectedNote();
		if (note == null) {
			return;
		}
		this.minecraft.setScreenAndShow(new NoteEditScreen(this, note.text, saved -> {
			note.text = saved;
			note.visible = true;
			config.save();
		}));
	}

	private void addPanel() {
		config.addPanel(selectedId != null ? selectedId : config.activeImageId);
		selectedEntry = config.panels.size() - 1;
		lastPanelTabSelection = selectedEntry;
		config.save();
		rebuildWidgets();
	}

	private void buildHologramTab() {
		selectedHologram = Math.clamp(selectedHologram, 0, Math.max(0, config.holograms.size() - 1));
		lastSelectedHologram = selectedHologram;

		int listWidth = 150;
		int rightX = contentX + listWidth + Theme.PAD;
		int rightWidth = contentWidth - listWidth - Theme.PAD;

		instanceListX = contentX;
		instanceListY = contentY;
		instanceListWidth = listWidth;
		instanceListHeight = contentHeight - 26;

		int buttonsY = instanceListY + instanceListHeight + 6;
		int half = (listWidth - Theme.PAD) / 2;
		addRenderableWidget(new ModButton(instanceListX, buttonsY, half, 20,
				Component.translatable("buildaid.menu.hologram_place"), ModButton.Style.PRIMARY, () -> {
			ImageHologram.placeNewAtCrosshair(this.minecraft);
			onClose();
		}));

		ModButton remove = new ModButton(instanceListX + half + Theme.PAD, buttonsY, half, 20,
				Component.translatable("buildaid.menu.panel_remove"), ModButton.Style.DANGER, () -> {
			if (!config.holograms.isEmpty()) {
				GlobalUndo.push();
				config.holograms.remove(selectedHologram);
				selectedHologram = Math.clamp(selectedHologram, 0, Math.max(0, config.holograms.size() - 1));
				config.save();
				rebuildWidgets();
			}
		});
		remove.active = !config.holograms.isEmpty();
		addRenderableWidget(remove);

		BuildAidConfig.Hologram h = selectedHologram();
		if (h == null) {
			return;
		}

		int y = contentY;
		addRenderableWidget(new ModToggle(rightX, y, rightWidth, 18,
				Component.translatable("buildaid.menu.hologram_enabled"),
				() -> h.enabled, value -> {
			h.enabled = value;
			config.save();
		}));

		y += 24;
		addRenderableWidget(new ModToggle(rightX, y, rightWidth, 18,
				Component.translatable("buildaid.menu.hologram_keep_aspect"),
				() -> h.keepAspect, value -> {
			h.keepAspect = value;
			ImageHologram.applyAspect(h);
			config.save();
			rebuildWidgets();
		}));

		y += 24;
		addRenderableWidget(new ModSlider(rightX, y, rightWidth, 22,
				value -> Component.translatable("buildaid.menu.hologram_width", value),
				1, 64, h.widthBlocks, value -> {
			h.widthBlocks = value;
			// Com a trava ligada, a altura acompanha e a imagem nunca estica.
			ImageHologram.applyAspect(h);
			config.save();
		}));

		y += 28;
		ModSlider height = new ModSlider(rightX, y, rightWidth, 22,
				value -> Component.translatable("buildaid.menu.hologram_height", value),
				1, 64, h.heightBlocks, value -> {
			h.heightBlocks = value;
			config.save();
		});
		height.active = !h.keepAspect;
		addRenderableWidget(height);

		y += 28;
		addRenderableWidget(new ModSlider(rightX, y, rightWidth, 22,
				value -> Component.translatable("buildaid.menu.hologram_opacity", value),
				0, 100, Math.round(h.opacity * 100), value -> {
			h.opacity = value / 100.0f;
			config.save();
		}));

		y += 28;
		int halfRight = (rightWidth - Theme.PAD) / 2;
		addRenderableWidget(new ModButton(rightX, y, halfRight, 20,
				Component.translatable("buildaid.menu.hologram_facing", facingName(h.facing)),
				ModButton.Style.NORMAL, () -> {
			h.facing = (h.facing + 1) % ImageHologram.FACING_COUNT;
			config.save();
			rebuildWidgets();
		}));

		addRenderableWidget(new ModButton(rightX + halfRight + Theme.PAD, y, halfRight, 20,
				Component.translatable("buildaid.menu.hologram_use_image"), ModButton.Style.NORMAL, () -> {
			h.imageId = selectedId != null ? selectedId : config.activeImageId;
			ImageHologram.applyAspect(h);
			config.save();
			rebuildWidgets();
		}));

		y += 24;
		addRenderableWidget(new ModButton(rightX, y, rightWidth, 20,
				Component.translatable("buildaid.menu.move_crosshair"), ModButton.Style.NORMAL, () -> {
			ImageHologram.moveToCrosshair(this.minecraft, h);
			config.save();
			rebuildWidgets();
		}));
	}

	private BuildAidConfig.Hologram selectedHologram() {
		return selectedHologram >= 0 && selectedHologram < config.holograms.size()
				? config.holograms.get(selectedHologram)
				: null;
	}

	private BuildAidConfig.Shape selectedShape() {
		return selectedShape >= 0 && selectedShape < config.shapes.size()
				? config.shapes.get(selectedShape)
				: null;
	}

	private void buildShapeTab() {
		selectedShape = Math.clamp(selectedShape, 0, Math.max(0, config.shapes.size() - 1));
		lastSelectedShape = selectedShape;

		int listWidth = 132;
		int rightX = contentX + listWidth + Theme.PAD;
		int rightWidth = contentWidth - listWidth - Theme.PAD;
		int halfRight = (rightWidth - Theme.PAD) / 2;

		instanceListX = contentX;
		instanceListY = contentY;
		instanceListWidth = listWidth;
		instanceListHeight = contentHeight - 52;

		int buttonsY = instanceListY + instanceListHeight + 6;
		int halfList = (listWidth - 4) / 2;

		addRenderableWidget(new ModButton(instanceListX, buttonsY, halfList, 20,
				Component.translatable("buildaid.menu.shape_place"), ModButton.Style.PRIMARY, () -> {
			ShapeGuide.placeNewAtCrosshair(this.minecraft);
			onClose();
		}));

		ModButton removeShapeBtn = new ModButton(instanceListX + halfList + 4, buttonsY, halfList, 20,
				Component.translatable("buildaid.menu.panel_remove"), ModButton.Style.DANGER, () -> {
			if (!config.shapes.isEmpty()) {
				GlobalUndo.push();
				config.shapes.remove(selectedShape);
				selectedShape = Math.clamp(selectedShape, 0, Math.max(0, config.shapes.size() - 1));
				config.save();
				rebuildWidgets();
			}
		});
		removeShapeBtn.active = !config.shapes.isEmpty();
		addRenderableWidget(removeShapeBtn);

		ModButton fromSelection = new ModButton(instanceListX, buttonsY + 24, halfList, 20,
				Component.translatable("buildaid.menu.shape_from_selection"), ModButton.Style.NORMAL,
				this::shapeFromSelection);
		fromSelection.active = AreaSelection.isComplete();
		addRenderableWidget(fromSelection);

		BuildAidConfig.Shape s = selectedShape();
		ModButton moveCrosshairBtn = new ModButton(instanceListX + halfList + 4, buttonsY + 24, halfList, 20,
				Component.translatable("buildaid.menu.move_crosshair"), ModButton.Style.NORMAL, () -> {
			if (s != null) {
				ShapeGuide.moveToCrosshair(this.minecraft, s);
				config.save();
				rebuildWidgets();
			}
		});
		moveCrosshairBtn.active = s != null;
		addRenderableWidget(moveCrosshairBtn);
		if (s == null) {
			return;
		}

		int y = contentY;
		addRenderableWidget(new ModButton(rightX, y, rightWidth, 20,
				Component.translatable("buildaid.menu.shape_type",
						Component.translatable(ShapeType.parse(s.type).translationKey())),
				ModButton.Style.NORMAL, () -> {
			s.type = ShapeType.parse(s.type).next().name();
			config.save();
			rebuildWidgets();
		}));

		y += 24;
		addRenderableWidget(new ModSlider(rightX, y, halfRight, 22,
				value -> Component.translatable("buildaid.menu.shape_width", value),
				1, 128, s.width, value -> {
			s.width = value;
			config.save();
		}));
		addRenderableWidget(new ModSlider(rightX + halfRight + Theme.PAD, y, halfRight, 22,
				value -> Component.translatable("buildaid.menu.shape_height", value),
				1, 128, s.height, value -> {
			s.height = value;
			config.save();
		}));

		y += 28;
		addRenderableWidget(new ModSlider(rightX, y, halfRight, 22,
				value -> Component.translatable("buildaid.menu.shape_depth", value),
				1, 128, s.depth, value -> {
			s.depth = value;
			config.save();
		}));
		addRenderableWidget(new ModSlider(rightX + halfRight + Theme.PAD, y, halfRight, 22,
				value -> Component.translatable("buildaid.menu.shape_rotation", value),
				0, 359, s.rotation, value -> {
			s.rotation = value;
			config.save();
		}));

		y += 28;
		addRenderableWidget(new ModSlider(rightX, y, halfRight, 22,
				value -> Component.translatable("buildaid.menu.shape_thickness", value),
				1, 8, s.thickness, value -> {
			s.thickness = value;
			config.save();
		}));

		// O passo so faz sentido na escada espiral.
		ModSlider pitch = new ModSlider(rightX + halfRight + Theme.PAD, y, halfRight, 22,
				value -> Component.translatable("buildaid.menu.shape_pitch", value),
				2, 64, s.pitch, value -> {
			s.pitch = value;
			config.save();
		});
		pitch.active = ShapeType.parse(s.type) == ShapeType.HELIX
				|| ShapeType.parse(s.type) == ShapeType.SPIRAL_STAIR;
		addRenderableWidget(pitch);

		y += 24;
		addRenderableWidget(new ModToggle(rightX, y, halfRight, 18,
				Component.translatable("buildaid.menu.shape_hollow"),
				() -> s.hollow, value -> {
			s.hollow = value;
			config.save();
		}));
		addRenderableWidget(new ModToggle(rightX + halfRight + Theme.PAD, y, halfRight, 18,
				Component.translatable("buildaid.menu.shape_enabled"),
				() -> s.enabled, value -> {
			s.enabled = value;
			config.save();
		}));

		// Fatiador de camadas (Layer Slicer)
		y += 24;
		addRenderableWidget(new ModButton(rightX, y, halfRight, 20,
				layerModeName(s.layerMode), ModButton.Style.NORMAL, () -> {
			s.layerMode = (s.layerMode + 1) % 3;
			config.save();
			rebuildWidgets();
		}));

		ModSlider layerSlider = new ModSlider(rightX + halfRight + Theme.PAD, y, halfRight, 20,
				value -> Component.translatable("buildaid.menu.shape_active_layer", value),
				0, Math.max(0, s.height - 1), Math.min(s.activeLayer, Math.max(0, s.height - 1)), value -> {
			s.activeLayer = value;
			config.save();
		});
		layerSlider.active = s.layerMode > 0;
		addRenderableWidget(layerSlider);

		// Seletor de cores e Wireframe
		y += 24;
		addRenderableWidget(new ModButton(rightX, y, halfRight, 20,
				colorPresetName(s.colorPreset), ModButton.Style.NORMAL, () -> {
			s.colorPreset = (s.colorPreset + 1) % 8;
			config.save();
			rebuildWidgets();
		}));

		addRenderableWidget(new ModToggle(rightX + halfRight + Theme.PAD, y, halfRight, 18,
				Component.translatable("buildaid.menu.shape_wireframe"),
				() -> s.wireframe, value -> {
			s.wireframe = value;
			config.save();
		}));
	}

	private static Component layerModeName(int mode) {
		return switch (mode) {
			case 1 -> Component.translatable("buildaid.menu.shape_layer_mode_single");
			case 2 -> Component.translatable("buildaid.menu.shape_layer_mode_upto");
			default -> Component.translatable("buildaid.menu.shape_layer_mode_all");
		};
	}

	private static Component colorPresetName(int preset) {
		return switch (preset) {
			case 1 -> Component.translatable("buildaid.menu.shape_color_emerald");
			case 2 -> Component.translatable("buildaid.menu.shape_color_gold");
			case 3 -> Component.translatable("buildaid.menu.shape_color_orange");
			case 4 -> Component.translatable("buildaid.menu.shape_color_ruby");
			case 5 -> Component.translatable("buildaid.menu.shape_color_purple");
			case 6 -> Component.translatable("buildaid.menu.shape_color_white");
			case 7 -> Component.translatable("buildaid.menu.shape_color_rainbow");
			default -> Component.translatable("buildaid.menu.shape_color_cyan");
		};
	}

	/** Encaixa a forma selecionada na caixa marcada com B/M. */
	private void shapeFromSelection() {
		if (!AreaSelection.isComplete()) {
			return;
		}

		BuildAidConfig.Shape target = selectedShape();
		if (target == null) {
			target = config.addShape();
			selectedShape = config.shapes.size() - 1;
		}

		BlockPos a = AreaSelection.corner1();
		BlockPos b = AreaSelection.corner2();

		target.width = AreaSelection.width();
		target.height = AreaSelection.height();
		target.depth = AreaSelection.depth();
		// A forma nasce centrada em X/Z com a base no Y minimo, que e como ela e desenhada.
		target.x = (a.getX() + b.getX()) / 2;
		target.y = Math.min(a.getY(), b.getY());
		target.z = (a.getZ() + b.getZ()) / 2;
		target.placed = true;
		target.enabled = true;

		config.save();
		rebuildWidgets();
		Feedback.info("buildaid.msg.shape_from_selection",
				target.width, target.height, target.depth);
	}

	/** Lista generica de instancias (hologramas, formas ou anotacoes). */
	private void drawInstanceList(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			int x, int y, int width, int height,
			int count, int selected, IntFunction<String> label, String emptyKey) {
		Theme.roundedRect(graphics, x, y, width, height, Theme.SURFACE_SUNKEN);

		if (count == 0) {
			graphics.text(this.font, Component.translatable(emptyKey), x + 6, y + 8, Theme.TEXT_DIM, false);
			return;
		}

		int visibleRows = height / PANEL_ROW;
		for (int i = 0; i < count && i < visibleRows; i++) {
			int rowY = y + i * PANEL_ROW;
			boolean isSelected = i == selected;
			boolean hovered = mouseX >= x && mouseX <= x + width
					&& mouseY >= rowY && mouseY <= rowY + PANEL_ROW;

			graphics.fill(x, rowY, x + width, rowY + PANEL_ROW - 2,
					isSelected ? Theme.ACCENT_SOFT : hovered ? Theme.SURFACE_HOVER : 0x20FFFFFF);

			String text = this.font.plainSubstrByWidth((i + 1) + ". " + label.apply(i), width - 10);
			graphics.text(this.font, text, x + 5, rowY + 6,
					isSelected ? Theme.TEXT : Theme.TEXT_DIM, false);
		}
	}

	private void drawHologramInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawInstanceList(graphics, mouseX, mouseY, instanceListX, instanceListY,
				instanceListWidth, instanceListHeight,
				config.holograms.size(), selectedHologram,
				i -> imageNameOf(config.holograms.get(i).imageId), "buildaid.menu.hologram_none");

		BuildAidConfig.Hologram h = selectedHologram();
		if (h == null) {
			return;
		}

		int x = contentX + instanceListWidth + Theme.PAD;
		int y = contentY + 168;
		if (y + 12 > contentY + contentHeight) {
			return;
		}
		graphics.text(this.font, Component.translatable("buildaid.menu.hologram_pos",
				h.placed ? (h.x + ", " + h.y + ", " + h.z) : "--"), x, y, Theme.TEXT_DIM, false);
	}

	private void drawShapeInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawInstanceList(graphics, mouseX, mouseY, instanceListX, instanceListY,
				instanceListWidth, instanceListHeight,
				config.shapes.size(), selectedShape,
				i -> Component.translatable(ShapeType.parse(config.shapes.get(i).type).translationKey())
						.getString(),
				"buildaid.menu.shape_none");

		BuildAidConfig.Shape s = selectedShape();
		if (s == null) {
			return;
		}

		drawMaterials(graphics, s);
	}

	/**
	 * Lista de materiais por camada de altura.
	 *
	 * <p>E o que transforma a guia em algo constravel: quem constroi sobe camada por camada e
	 * precisa saber quanto vai em cada uma, nao so o total.
	 */
	private void drawMaterials(GuiGraphicsExtractor graphics, BuildAidConfig.Shape shape) {
		materialsX = contentX + instanceListWidth + Theme.PAD;
		materialsY = contentY + 172;
		materialsWidth = contentWidth - instanceListWidth - Theme.PAD;
		materialsHeight = contentY + contentHeight - materialsY;
		if (materialsHeight < 22) {
			return;
		}

		Theme.roundedRect(graphics, materialsX, materialsY, materialsWidth, materialsHeight,
				Theme.SURFACE_SUNKEN);

		int[] layers = ShapeGuide.blocksPerLayer(shape);
		if (layers.length == 0) {
			graphics.text(this.font, Component.translatable("buildaid.menu.shape_building"),
					materialsX + 5, materialsY + 5, Theme.TEXT_DIM, false);
			return;
		}

		graphics.text(this.font, Component.translatable("buildaid.menu.shape_total",
						ShapeGuide.blockCount(shape)),
				materialsX + 5, materialsY + 4, Theme.ACCENT, false);

		int rows = Math.max(0, (materialsHeight - 16) / 10);
		materialsScroll = Math.clamp(materialsScroll, 0, Math.max(0, layers.length - rows));

		for (int i = 0; i < rows && i + materialsScroll < layers.length; i++) {
			int layer = i + materialsScroll;
			boolean isHighlight = shape.layerMode == 1 && layer == shape.activeLayer;
			int color = isHighlight ? Theme.ACCENT : Theme.TEXT_DIM;
			String prefix = isHighlight ? "> " : "";
			graphics.text(this.font,
					Component.literal(prefix).append(Component.translatable("buildaid.menu.shape_layer", layer, layers[layer])),
					materialsX + 5, materialsY + 16 + i * 10, color, false);
		}
	}

	private static Component facingName(int facing) {
		String key = switch (facing) {
			case ImageHologram.FACING_NORTH -> "north";
			case ImageHologram.FACING_EAST -> "east";
			case ImageHologram.FACING_SOUTH -> "south";
			case ImageHologram.FACING_WEST -> "west";
			default -> "flat";
		};
		return Component.translatable("buildaid.facing." + key);
	}

	private void buildSelectionTab() {
		int leftWidth = 148;
		int rightX = contentX + leftWidth + Theme.PAD;
		int rightWidth = contentWidth - leftWidth - Theme.PAD;

		int y = contentY;

		// 1. Modo Selecao
		addRenderableWidget(new ModToggle(contentX, y, leftWidth, 18,
				Component.translatable("buildaid.menu.selection_mode"),
				AreaSelection::isModeEnabled, AreaSelection::setModeEnabled));

		y += 74; // espaco do box de info do canto1 e canto2
		int halfLeft = (leftWidth - 2) / 2;
		addRenderableWidget(new ModButton(contentX, y, halfLeft, 18,
				Component.translatable("buildaid.menu.clear_selection"), ModButton.Style.NORMAL, () -> {
			AreaSelection.clear();
			scannedMaterials.clear();
			rebuildWidgets();
		}));

		addRenderableWidget(new ModToggle(contentX + halfLeft + 2, y, halfLeft, 18,
				Component.translatable("buildaid.menu.selection_show_center"),
				() -> config.selection.showCenter, value -> {
			config.selection.showCenter = value;
			config.save();
		}));

		y += 22;
		ModButton scanBtn = new ModButton(contentX, y, halfLeft, 20,
				Component.translatable("buildaid.menu.scan_materials"), ModButton.Style.PRIMARY, () -> {
			scannedMaterials = AreaSelection.scanMaterials(this.minecraft);
			scannedMaterialsScroll = 0;
			rebuildWidgets();
		});
		scanBtn.active = AreaSelection.isComplete();
		addRenderableWidget(scanBtn);

		addRenderableWidget(new ModButton(contentX + halfLeft + 2, y, halfLeft, 20,
				Component.translatable(com.foxo.buildaid.build.TapeMeasure.isActive() ? "buildaid.menu.tape_clear" : "buildaid.menu.tape_start"),
				com.foxo.buildaid.build.TapeMeasure.isActive() ? ModButton.Style.PRIMARY : ModButton.Style.NORMAL, () -> {
			com.foxo.buildaid.build.TapeMeasure.toggleOrMark(this.minecraft);
			rebuildWidgets();
		}));

		// Marcadores fixos (pins) da regua: fixar a medicao atual / limpar tudo.
		y += 22;
		addRenderableWidget(new ModButton(contentX, y, halfLeft, 18,
				Component.translatable("buildaid.menu.tape_pin"), ModButton.Style.NORMAL, () -> {
			if (com.foxo.buildaid.build.TapeMeasure.pinCurrent(this.minecraft)) {
				Feedback.info("buildaid.msg.tape_pinned");
			} else {
				Feedback.error("buildaid.msg.tape_materials_none");
			}
			rebuildWidgets();
		}));
		addRenderableWidget(new ModButton(contentX + halfLeft + 2, y, halfLeft, 18,
				Component.translatable("buildaid.menu.tape_unpin_all"), ModButton.Style.NORMAL, () -> {
			com.foxo.buildaid.build.TapeMeasure.clearPins();
			rebuildWidgets();
		}));

		// Verificador de construcao: liga/desliga o overlay amarelo/vermelho contra o blueprint ativo.
		y += 22;
		var verifyBtn = new ModButton(contentX, y, leftWidth, 20,
				Component.translatable("buildaid.menu.verify", Feedback.state(config.verifier.enabled)),
				config.verifier.enabled ? ModButton.Style.PRIMARY : ModButton.Style.NORMAL, () -> {
			if (com.foxo.buildaid.build.Blueprint.current == null) {
				Feedback.error("buildaid.msg.verify_no_blueprint");
			} else {
				config.verifier.enabled = !config.verifier.enabled;
				config.save();
				rebuildWidgets();
			}
		});
				verifyBtn.active = com.foxo.buildaid.build.Blueprint.current != null;
				addRenderableWidget(verifyBtn);

				// Relatorio de verificacao: manda o andamento da construcao (corretos/faltando/errados) para o chat.
				y += 22;
				var reportBtn = new ModButton(contentX, y, leftWidth, 20,
						Component.translatable("buildaid.menu.report_verify"), ModButton.Style.NORMAL, () -> {
						if (com.foxo.buildaid.build.Blueprint.current == null) {
							Feedback.error("buildaid.msg.verify_no_blueprint");
						} else {
							com.foxo.buildaid.build.WorldGizmos.reportVerification(Minecraft.getInstance());
						}
					});
				reportBtn.active = com.foxo.buildaid.build.Blueprint.current != null;
				addRenderableWidget(reportBtn);

				// Importar .litematic: abre o seletor de arquivo nativo e converte para o blueprint ativo.
		// O dialogo bloqueia, entao roda numa worker thread e so ancoramos o ghost na render thread.
		y += 22;
		addRenderableWidget(new ModButton(contentX, y, leftWidth, 20,
				Component.translatable("buildaid.menu.import_litematic"), ModButton.Style.PRIMARY, () -> {
			importLitematicFromPicker();
		}));

		// Nudge / Ajuste
		y += 24;
		int third = (leftWidth - 4) / 3;
		addRenderableWidget(new ModButton(contentX, y, third, 18,
				Component.literal("X-"), ModButton.Style.NORMAL, () -> {
			AreaSelection.nudge(-1, 0, 0);
			rebuildWidgets();
		}));
		addRenderableWidget(new ModButton(contentX + third + 2, y, third, 18,
				Component.literal("Y-"), ModButton.Style.NORMAL, () -> {
			AreaSelection.nudge(0, -1, 0);
			rebuildWidgets();
		}));
		addRenderableWidget(new ModButton(contentX + (third + 2) * 2, y, third, 18,
				Component.literal("Z-"), ModButton.Style.NORMAL, () -> {
			AreaSelection.nudge(0, 0, -1);
			rebuildWidgets();
		}));

		y += 20;
		addRenderableWidget(new ModButton(contentX, y, third, 18,
				Component.literal("X+"), ModButton.Style.NORMAL, () -> {
			AreaSelection.nudge(1, 0, 0);
			rebuildWidgets();
		}));
		addRenderableWidget(new ModButton(contentX + third + 2, y, third, 18,
				Component.literal("Y+"), ModButton.Style.NORMAL, () -> {
			AreaSelection.nudge(0, 1, 0);
			rebuildWidgets();
		}));
		addRenderableWidget(new ModButton(contentX + (third + 2) * 2, y, third, 18,
				Component.literal("Z+"), ModButton.Style.NORMAL, () -> {
			AreaSelection.nudge(0, 0, 1);
			rebuildWidgets();
		}));

		y += 20;
		addRenderableWidget(new ModButton(contentX, y, halfLeft, 18,
				Component.translatable("buildaid.menu.expand_plus"), ModButton.Style.NORMAL, () -> {
			AreaSelection.expand(1, 1, 1);
			rebuildWidgets();
		}));
		addRenderableWidget(new ModButton(contentX + halfLeft + 2, y, halfLeft, 18,
				Component.translatable("buildaid.menu.expand_minus"), ModButton.Style.NORMAL, () -> {
			AreaSelection.expand(-1, -1, -1);
			rebuildWidgets();
		}));

		// Right Column: If scanned materials is not empty, show clear button on top. If empty, show Grid & Symmetry!
		int ry = contentY;
		if (!scannedMaterials.isEmpty()) {
			addRenderableWidget(new ModButton(rightX, ry, rightWidth, 18,
					Component.translatable("buildaid.menu.close_materials"), ModButton.Style.NORMAL, () -> {
				scannedMaterials.clear();
				rebuildWidgets();
			}));
		} else {
			// Grade de Construcao
			addRenderableWidget(new ModToggle(rightX, ry, rightWidth, 18,
					Component.translatable("buildaid.menu.grid"),
					() -> config.grid.enabled, value -> {
				config.grid.enabled = value;
				config.save();
			}));

			ry += 22;
			addRenderableWidget(new ModSlider(rightX, ry, rightWidth, 20,
					value -> Component.translatable("buildaid.menu.grid_radius", value),
					4, 64, config.grid.radius, value -> {
				config.grid.radius = value;
				config.save();
			}));

			ry += 24;
			int halfRight = (rightWidth - 2) / 2;
			addRenderableWidget(new ModToggle(rightX, ry, halfRight, 18,
					Component.translatable("buildaid.menu.grid_lock_y"),
					() -> config.grid.lockY, value -> {
				config.grid.lockY = value;
				config.save();
				rebuildWidgets();
			}));

			ModSlider fixedYSlider = new ModSlider(rightX + halfRight + 2, ry, halfRight, 20,
					value -> Component.translatable("buildaid.menu.grid_fixed_y", value),
					-64, 320, config.grid.fixedY, value -> {
				config.grid.fixedY = value;
				config.save();
			});
			fixedYSlider.active = config.grid.lockY;
			addRenderableWidget(fixedYSlider);

			// Plano de Simetria 3D
			ry += 26;
			addRenderableWidget(new ModToggle(rightX, ry, rightWidth, 18,
					Component.translatable("buildaid.menu.symmetry_enabled"),
					() -> config.symmetry.enabled, value -> {
				config.symmetry.enabled = value;
				config.save();
				rebuildWidgets();
			}));

			ry += 22;
			addRenderableWidget(new ModButton(rightX, ry, halfRight, 20,
					Component.translatable("buildaid.menu.symmetry_axis", config.symmetry.axis == 0 ? "X" : "Z"),
					ModButton.Style.NORMAL, () -> {
				config.symmetry.axis = (config.symmetry.axis + 1) % 2;
				config.save();
				rebuildWidgets();
			}));

			addRenderableWidget(new ModButton(rightX + halfRight + 2, ry, halfRight, 20,
					Component.translatable("buildaid.menu.symmetry_center"), ModButton.Style.NORMAL, () -> {
				if (AreaSelection.isComplete()) {
					BlockPos c1 = AreaSelection.corner1();
					BlockPos c2 = AreaSelection.corner2();
					config.symmetry.position = config.symmetry.axis == 0
							? (c1.getX() + c2.getX()) / 2
							: (c1.getZ() + c2.getZ()) / 2;
				} else if (this.minecraft.player != null) {
					config.symmetry.position = config.symmetry.axis == 0
							? this.minecraft.player.getBlockX()
							: this.minecraft.player.getBlockZ();
				}
				config.symmetry.enabled = true;
				config.save();
				rebuildWidgets();
			}));

			// Modo da simetria: plano unico (1 eixo), cruz quadrupla (4) ou radial (R6).
			ry += 22;
			addRenderableWidget(new ModButton(rightX, ry, rightWidth, 20,
					Component.translatable("buildaid.menu.symmetry_mode",
							config.symmetry.modeLabel()),
					ModButton.Style.NORMAL, () -> {
				config.symmetry.cycle();
				config.symmetry.enabled = true;
				config.save();
				rebuildWidgets();
			}));

			// Numero de bracos da simetria radial (so faz sentido quando type == 1).
			if (config.symmetry.type == 1) {
				ry += 22;
				addRenderableWidget(new ModButton(rightX, ry, halfRight, 20,
						Component.translatable("buildaid.menu.symmetry_arms", config.symmetry.arms),
						ModButton.Style.NORMAL, () -> {
					config.symmetry.arms = config.symmetry.arms <= 2 ? 16 : config.symmetry.arms - 1;
					config.symmetry.enabled = true;
					config.save();
					rebuildWidgets();
				}));
				addRenderableWidget(new ModButton(rightX + halfRight + 2, ry, halfRight, 20,
						Component.translatable("buildaid.menu.symmetry_arms_more"),
						ModButton.Style.NORMAL, () -> {
					config.symmetry.arms = config.symmetry.arms >= 16 ? 2 : config.symmetry.arms + 1;
					config.symmetry.enabled = true;
					config.save();
					rebuildWidgets();
				}));
			}

			// Detector de Spawn de Monstros
			ry += 24;
			addRenderableWidget(new ModToggle(rightX, ry, rightWidth, 18,
					Component.translatable("buildaid.menu.danger_zone"),
					() -> config.dangerZone.enabled, value -> {
				config.dangerZone.enabled = value;
				config.save();
			}));

			ry += 22;
			addRenderableWidget(new ModSlider(rightX, ry, rightWidth, 20,
					value -> Component.translatable("buildaid.menu.danger_radius", value),
					4, 32, config.dangerZone.radius, value -> {
				config.dangerZone.radius = value;
				config.save();
			}));
		}
	}

	// ------------------------------------------------------------------ apoio das anotacoes

	private void openNoteEditor(int index) {
		if (index < 0 || index >= config.notes.size()) {
			return;
		}
		BuildAidConfig.Note note = config.notes.get(index);
		this.minecraft.setScreenAndShow(new NoteEditScreen(this, note.text, saved -> {
			note.text = saved;
			note.visible = true;
			config.save();
		}));
	}

	private static String firstLineOf(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		int newline = text.indexOf('\n');
		return newline >= 0 ? text.substring(0, newline) : text;
	}

	// ------------------------------------------------------------------ aba Cores

	/** Altura de cada linha de sugestao de bloco. */
	private static final int SUGGEST_ROW = 14;
	private boolean syncingHex;

	// Faixa de temas (paletas de blocos curadas) da aba Cores.
	private int themeChipLeft;
	private int themeChipTop;
	private int themeChipSize = 16;
	private int themeChipGap = 2;
	private int[] themeChipX;
	private int[] themeChipY;
	private int hoveredTheme = -1;

	private void buildColorsTab() {
		int pickerWidth = Math.min(184, contentWidth);
		// Reserva espaco embaixo do seletor para a faixa de temas.
		int chipsArea = 30;
		int pickerH = Math.max(40, contentHeight - 12 - chipsArea);
		colorPicker = new com.foxo.buildaid.screen.widget.ColorPickerWidget(
				contentX, contentY + 12, pickerWidth, pickerH,
				this::onPickedColor);
		addRenderableWidget(colorPicker);

		// Geometria da faixa de temas: rotulo + uma fileira de swatches.
		int labelW = this.font.width(Component.translatable("buildaid.menu.theme_label").getString());
		themeChipLeft = contentX + labelW + 6;
		themeChipTop = contentY + 12 + pickerH + 8;
		int n = com.foxo.buildaid.build.BlockThemePalette.THEMES.size();
		themeChipX = new int[n];
		themeChipY = new int[n];
		for (int i = 0; i < n; i++) {
			themeChipX[i] = themeChipLeft + i * (themeChipSize + themeChipGap);
			themeChipY[i] = themeChipTop;
		}

		int rx = contentX + pickerWidth + Theme.PAD;
		int rw = Math.max(100, contentWidth - pickerWidth - Theme.PAD);
		int bottom = contentY + contentHeight;

		hexBox = new EditBox(this.font, rx, contentY + 34, rw, 16,
				Component.translatable("buildaid.menu.color_hex_hint"));
		hexBox.setMaxLength(7);
		hexBox.setHint(Component.translatable("buildaid.menu.color_hex_hint"));
		hexBox.setValue(String.format("#%06X", pickedRgb()));
		hexBox.setResponder(value -> {
			if (syncingHex) {
				return;
			}
			int rgb = parseHex(value);
			if (rgb >= 0) {
				float[] hsv = com.foxo.buildaid.screen.widget.ColorPickerWidget.rgbToHsv(rgb);
				pickerHue = hsv[0];
				pickerSat = hsv[1];
				pickerVal = hsv[2];
				syncingHex = true;
				try {
					if (colorPicker != null) {
						colorPicker.setHsv(hsv[0], hsv[1], hsv[2]);
					}
				} finally {
					syncingHex = false;
				}
				refreshSuggestions();
			}
		});
		addRenderableWidget(hexBox);

		int half = (rw - Theme.PAD) / 2;

		// So duas acoes: levar os blocos sugeridos para uma anotacao ou para a hotbar.
		ModButton exportBtn = new ModButton(rx, bottom - 26, half, 20,
				Component.translatable("buildaid.menu.export_note"), ModButton.Style.NORMAL,
				this::exportToNote);
		exportBtn.active = !suggestions.isEmpty();
		addRenderableWidget(exportBtn);

		ModButton equipBtn = new ModButton(rx + half + Theme.PAD, bottom - 26, half, 20,
				Component.translatable("buildaid.menu.palette_equip"),
				ModButton.Style.PRIMARY, this::equipSuggestedBlocks);
		equipBtn.active = !suggestions.isEmpty();
		addRenderableWidget(equipBtn);

		refreshSuggestions();
	}

	// ------------------------------------------------------------------ aba Randomizador

	/** Paleta randomizadora: monta a lista de blocos (com peso) e o botao de sortear. */
	private void buildRandomizerTab() {
		int y = contentY;

		// Atalho direto para sortear da lista atual.
		addRenderableWidget(new ModButton(contentX, y, contentWidth, 20,
				Component.translatable("buildaid.menu.randomize_now"), ModButton.Style.PRIMARY, () -> {
			if (this.minecraft.player != null) {
				com.foxo.buildaid.build.RandomizerPalette.roll(this.minecraft);
			}
			rebuildWidgets();
		}));

		y += 24;
		int half = (contentWidth - Theme.PAD) / 2;
		addRenderableWidget(new ModToggle(contentX, y, half, 18,
				Component.translatable("buildaid.menu.randomizer_enabled"),
				() -> config.randomizer.enabled, value -> {
			config.randomizer.enabled = value;
			config.save();
		}));
		addRenderableWidget(new ModToggle(contentX + half + Theme.PAD, y, half, 18,
				Component.translatable("buildaid.menu.randomizer_restrict"),
				() -> config.randomizer.restrictToInventory, value -> {
			config.randomizer.restrictToInventory = value;
			config.save();
		}));

		y += 22;
		addRenderableWidget(new ModButton(contentX, y, half, 20,
				Component.translatable("buildaid.menu.randomizer_add_held"), ModButton.Style.NORMAL, () -> {
			if (com.foxo.buildaid.build.RandomizerPalette.addHeld(this.minecraft)) {
				config.save();
			}
			rebuildWidgets();
		}));
		addRenderableWidget(new ModButton(contentX + half + Theme.PAD, y, half, 20,
				Component.translatable("buildaid.menu.randomizer_clear"), ModButton.Style.DANGER, () -> {
			config.randomizer.entries.clear();
			config.save();
			rebuildWidgets();
		}));

		// A lista propriamente dita e desenhada em drawRandomizerInfo (rolavel).
		randomizerListX = contentX;
		randomizerListY = y + 28;
		randomizerListWidth = contentWidth;
		randomizerListHeight = Math.max(0, (contentY + contentHeight) - randomizerListY - 4);
	}

	private void drawRandomizerInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable("buildaid.menu.randomizer_hint"),
				contentX, contentY + 2, Theme.TEXT_DIM, false);

		var entries = config.randomizer.entries;
		if (entries.isEmpty()) {
			Theme.roundedRect(graphics, randomizerListX, randomizerListY,
					randomizerListWidth, Math.max(20, randomizerListHeight), Theme.SURFACE_SUNKEN);
			graphics.text(this.font, Component.translatable("buildaid.menu.randomizer_empty"),
					randomizerListX + 8, randomizerListY + 6, Theme.TEXT_DIM, false);
			return;
		}

		clampRandomizerScroll();
		Theme.roundedRect(graphics, randomizerListX, randomizerListY,
				randomizerListWidth, randomizerListHeight, Theme.SURFACE_SUNKEN);

		graphics.enableScissor(randomizerListX, randomizerListY,
				randomizerListX + randomizerListWidth, randomizerListY + randomizerListHeight);

		int visible = randomizerListHeight / RANDOMIZER_ROW;
		int start = -randomizerScroll / RANDOMIZER_ROW;
		for (int i = Math.max(0, start); i < entries.size() && (i - start) < visible; i++) {
			var entry = entries.get(i);
			int rowY = randomizerListY + (i * RANDOMIZER_ROW) + randomizerScroll;
			int rowX = randomizerListX + 4;

			// Swatch da cor do bloco.
			var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
					.getValue(net.minecraft.resources.Identifier.tryParse(entry.path));
			int swatch = block != null && block != net.minecraft.world.level.block.Blocks.AIR
					? com.foxo.buildaid.build.BlockPaletteGenerator.getBlockColor(block) : 0x444444;
			graphics.fill(rowX, rowY + 4, rowX + 14, rowY + 18, 0xFF000000 | swatch);

			String name = block != null ? block.getName().getString()
					: entry.path.substring(entry.path.indexOf(':') + 1);
			graphics.text(this.font, this.font.plainSubstrByWidth(name, randomizerListWidth - 150),
					rowX + 20, rowY + 5, Theme.TEXT, false);

			// Controles por linha: [-] peso [+]   (X)
			int ctrlX = randomizerListX + randomizerListWidth - 92;
			drawListButton(graphics, ctrlX, rowY + 3, 18, 16, "-", i, 0);
			graphics.text(this.font, "x" + entry.weight, ctrlX + 22, rowY + 5,
					Theme.TEXT_DIM, false);
			drawListButton(graphics, ctrlX + 44, rowY + 3, 18, 16, "+", i, 1);
			drawListButton(graphics, ctrlX + 70, rowY + 3, 16, 16, "x", i, 2);
		}

		graphics.disableScissor();
	}

	/** Botao pequeno da lista (desenhado; o clique e tratado em mouseClicked). */
	private void drawListButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			String label, int row, int col) {
		Theme.roundedRect(graphics, x, y, w, h, Theme.SURFACE);
		graphics.text(this.font, label, x + (w - this.font.width(label)) / 2, y + 4,
				Theme.TEXT_DIM, false);
	}

	private void clampRandomizerScroll() {
		int rows = config.randomizer.entries.size() * RANDOMIZER_ROW;
		int view = randomizerListHeight;
		int max = Math.max(0, rows - view);
		randomizerScroll = Math.clamp(randomizerScroll, -max, 0);
	}

	private int pickedRgb() {
		return com.foxo.buildaid.screen.widget.ColorPickerWidget.hsvToRgb(pickerHue, pickerSat, pickerVal);
	}

	/** Callback do seletor: guarda o estado, espelha no campo hex e refaz as sugestoes. */
	private void onPickedColor() {
		pickerHue = colorPicker != null ? colorPicker.hue() : pickerHue;
		pickerSat = colorPicker != null ? colorPicker.saturation() : pickerSat;
		pickerVal = colorPicker != null ? colorPicker.value() : pickerVal;

		if (hexBox != null && !syncingHex) {
			syncingHex = true;
			try {
				hexBox.setValue(String.format("#%06X", pickedRgb()));
			} finally {
				syncingHex = false;
			}
		}
		refreshSuggestions();
	}

	/** Top blocos cuja MapColor fica mais perto da cor escolhida. */
	private void refreshSuggestions() {
		int rgb = pickedRgb();
		if (rgb == cachedSuggestionRgb) {
			return;
		}
		cachedSuggestionRgb = rgb;
		selectedSuggestion = 0;

		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;

		ensureColorCandidates();

		// Pesos perceptuais classicos: verde pesa mais, azul menos.
		record Candidate(net.minecraft.world.level.block.Block block, double dist) {
		}
		List<Candidate> ranked = new ArrayList<>(suggestionBlocks.length);
		for (int i = 0; i < suggestionBlocks.length; i++) {
			int col = suggestionBlockRgb[i];
			int cr = (col >> 16) & 0xFF;
			int cg = (col >> 8) & 0xFF;
			int cb = col & 0xFF;
			double dist = 0.30 * (cr - r) * (cr - r) + 0.59 * (cg - g) * (cg - g) + 0.11 * (cb - b) * (cb - b);
			ranked.add(new Candidate(suggestionBlocks[i], dist));
		}
		ranked.sort((a, c) -> Double.compare(a.dist(), c.dist()));
		suggestions = ranked.stream().limit(6).map(Candidate::block).toList();
	}

	/**
	 * Blocos elegiveis para sugestao, com as cores pre-calculadas uma unica vez -- o registro
	 * nao muda durante a sessao, entao refazer isso a cada arrasto seria puro desperdicio.
	 */
	private static net.minecraft.world.level.block.Block[] suggestionBlocks;
	private static int[] suggestionBlockRgb;

	private static void ensureColorCandidates() {
		if (suggestionBlocks != null) {
			return;
		}
		java.util.List<net.minecraft.world.level.block.Block> blocks = new ArrayList<>();
		java.util.List<Integer> colors = new ArrayList<>();
		for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
			if (block == net.minecraft.world.level.block.Blocks.AIR
					|| block == net.minecraft.world.level.block.Blocks.CAVE_AIR
					|| block == net.minecraft.world.level.block.Blocks.VOID_AIR
					|| block.asItem() == null) {
				continue;
			}
			blocks.add(block);
			colors.add(com.foxo.buildaid.build.BlockPaletteGenerator.getBlockColor(block));
		}
		suggestionBlocks = blocks.toArray(new net.minecraft.world.level.block.Block[0]);
		suggestionBlockRgb = colors.stream().mapToInt(Integer::intValue).toArray();
	}

	private static int parseHex(String text) {
		if (text == null) {
			return -1;
		}
		String t = text.trim().replace("#", "");
		if (t.length() != 6) {
			return -1;
		}
		try {
			return Integer.parseInt(t, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * Despeja o que a aba Cores produziu numa anotacao flutuante.
	 *
	 * <p>Prioridade: se ha um gradiente gerado, vai a sequencia completa dele; senao, vai a
	 * lista de blocos aproximados da cor atual. Os nomes sao resolvidos AGORA via getName(),
	 * que ja vem traduzido no idioma do jogo -- e por isso a nota continua legivel para quem
	 * joga em portugues ou em ingles.
	 */
	/**
	 * Despeja a lista de blocos aproximados da cor atual numa anotacao flutuante.
	 *
	 * <p>Os nomes sao resolvidos AGORA via getName(), que ja vem traduzido no idioma do jogo --
	 * e por isso a nota continua legivel para quem joga em portugues ou em ingles.
	 */
	private void exportToNote() {
		if (suggestions.isEmpty()) {
			return;
		}
		StringBuilder text = new StringBuilder();
		int rgb = pickedRgb();
		text.append(Component.translatable("buildaid.note.suggestions_header").getString())
				.append(String.format(" #%06X", rgb));
		for (int i = 0; i < suggestions.size(); i++) {
			text.append('\n').append(i == selectedSuggestion ? "\u25B8 " : "  ").append(i + 1).append(". ")
					.append(suggestions.get(i).getName().getString());
		}

		BuildAidConfig.Note note = config.addNote();
		note.text = text.toString();
		note.visible = true;
		config.save();

		// Vai direto para a aba Paineis, na lista unificada, com a nota nova selecionada.
		activeTab = TabId.PAINEL;
		lastTab = activeTab;
		selectedEntry = entryCount() - 1;
		lastPanelTabSelection = selectedEntry;
		rebuildWidgets();
		Feedback.info("buildaid.msg.exported_note");
	}

	/**
	 * Leva os blocos sugeridos para a hotbar.
	 *
	 * <p><b>Criativo:</b> entrega os itens direto nos primeiros slots. <b>Sobrevivencia:</b> nao
	 * da nada de graca -- percorre o inventario procurando quais dos blocos sugeridos o jogador
	 * JA TEM e organiza cada um encontrado num slot da hotbar, via o mesmo pacote de troca que o
	 * jogo usa ao apertar 1-9 com o mouse sobre um slot.
	 */
	private void equipSuggestedBlocks() {
		if (this.minecraft.player == null || suggestions.isEmpty()) {
			return;
		}
		var player = this.minecraft.player;
		var inventory = player.getInventory();

		if (player.isCreative()) {
			int count = Math.min(9, suggestions.size());
			for (int i = 0; i < count; i++) {
				var stack = new net.minecraft.world.item.ItemStack(suggestions.get(i).asItem());
				inventory.setItem(i, stack);
				if (this.minecraft.gameMode != null) {
					this.minecraft.gameMode.handleCreativeModeItemAdd(stack, 36 + i);
				}
			}
			Feedback.info("buildaid.msg.palette_equipped");
			return;
		}

		if (this.minecraft.gameMode == null) {
			return;
		}
		var menu = player.inventoryMenu;
		int placed = 0;
		for (net.minecraft.world.level.block.Block block : suggestions) {
			if (placed >= 9) {
				break;
			}
			var item = block.asItem();
			// Procura no inventario principal (9..35); o que ja esta na hotbar fica onde esta.
			int found = -1;
			for (int slot = 9; slot <= 35; slot++) {
				if (inventory.getItem(slot).is(item)) {
					found = slot;
					break;
				}
			}
			if (found >= 0) {
				this.minecraft.gameMode.handleContainerInput(
						menu.containerId, found, placed, net.minecraft.world.inventory.ContainerInput.SWAP, player);
				placed++;
			}
		}

		if (placed > 0) {
			Feedback.info("buildaid.msg.palette_organized", placed);
		} else {
			Feedback.error("buildaid.msg.palette_none_found");
		}
	}

	/**
	 * Carrega um tema (paleta de blocos curada) na aba Cores: as sugestoes viram os
	 * blocos do tema e o picker salta para a cor media do conjunto, para que o botao
	 * Equipar e o Exportar para nota ja operem sobre o tema.
	 */
	private void applyTheme(com.foxo.buildaid.build.BlockThemePalette.BlockTheme theme) {
		var blocks = theme.resolve();
		if (blocks.isEmpty()) {
			return;
		}
		suggestions = blocks;
		cachedSuggestionRgb = -1; // forca o rerank se o usuario voltar ao picker
		selectedSuggestion = 0;

		// Cor media do tema para dar contexto ao seletor (e ao Exportar).
		long sr = 0, sg = 0, sb = 0;
		for (var b : blocks) {
			int c = com.foxo.buildaid.build.BlockPaletteGenerator.getBlockColor(b);
			sr += (c >> 16) & 0xFF;
			sg += (c >> 8) & 0xFF;
			sb += c & 0xFF;
		}
		int n = blocks.size();
		int avg = ((int) (sr / n) << 16) | ((int) (sg / n) << 8) | (int) (sb / n);
		float[] hsv = com.foxo.buildaid.screen.widget.ColorPickerWidget.rgbToHsv(avg);
		pickerHue = hsv[0];
		pickerSat = hsv[1];
		pickerVal = hsv[2];
		if (colorPicker != null) {
			colorPicker.setHsv(hsv[0], hsv[1], hsv[2]);
		}
		if (hexBox != null) {
			syncingHex = true;
			try {
				hexBox.setValue(String.format("#%06X", avg));
			} finally {
				syncingHex = false;
			}
		}
		Feedback.info("buildaid.msg.theme_loaded", Component.translatable(theme.langKey()).getString());
	}

	private void drawColorsInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int pickerWidth = Math.min(184, contentWidth);
		int rx = contentX + pickerWidth + Theme.PAD;
		int rw = Math.max(100, contentWidth - pickerWidth - Theme.PAD);
		int bottom = contentY + contentHeight;

		graphics.text(this.font, Component.translatable("buildaid.menu.colors_hint"),
				contentX, contentY + 2, Theme.TEXT_DIM, false);

		// Faixa de temas: rotulo + swatches clicaveis (cada um a cor do tema).
		graphics.text(this.font, Component.translatable("buildaid.menu.theme_label"),
				contentX, themeChipTop + 4, Theme.TEXT_DIM, false);
		hoveredTheme = -1;
		for (int i = 0; i < com.foxo.buildaid.build.BlockThemePalette.THEMES.size(); i++) {
			var theme = com.foxo.buildaid.build.BlockThemePalette.THEMES.get(i);
			int cx = themeChipX[i];
			int cy = themeChipY[i];
			boolean hover = mouseX >= cx && mouseX <= cx + themeChipSize
					&& mouseY >= cy && mouseY <= cy + themeChipSize;
			if (hover) {
				hoveredTheme = i;
			}
			graphics.fill(cx - 1, cy - 1, cx + themeChipSize + 1, cy + themeChipSize + 1,
					hover ? Theme.ACCENT : 0xFF000000);
			graphics.fill(cx, cy, cx + themeChipSize, cy + themeChipSize, 0xFF000000 | theme.rgb());
		}

		// Amostra grande da cor atual, com o hex por cima em preto ou branco conforme o fundo.
		int rgb = pickedRgb();
		Theme.roundedRect(graphics, rx, contentY + 12, rw, 18, 3, 0xFF000000 | rgb);
		Theme.roundedOutline(graphics, rx, contentY + 12, rw, 18, 3, Theme.BORDER);
		float luminance = 0.299f * ((rgb >> 16) & 0xFF) / 255.0f
				+ 0.587f * ((rgb >> 8) & 0xFF) / 255.0f
				+ 0.114f * (rgb & 0xFF) / 255.0f;
		graphics.text(this.font, String.format("#%06X", rgb), rx + 5, contentY + 17,
				luminance > 0.5f ? 0xFF10141B : 0xFFF0F4FA, false);

		// Tooltip do tema em foco (em cima dos botoes, sem atrapalhar).
		if (hoveredTheme >= 0) {
			var theme = com.foxo.buildaid.build.BlockThemePalette.THEMES.get(hoveredTheme);
			String label = Component.translatable(theme.langKey()).getString();
			int tw = this.font.width(label) + 12;
			int tx = Math.min(themeChipX[hoveredTheme], contentX + contentWidth - tw);
			int ty = themeChipTop - 18;
			Theme.roundedRect(graphics, tx, ty, tw, 16, 3, Theme.SURFACE);
			Theme.roundedOutline(graphics, tx, ty, tw, 16, 3, Theme.BORDER);
			graphics.text(this.font, label, tx + 6, ty + 4, Theme.TEXT, false);
		}

		// Lista de sugestoes: rotulo + linhas, na faixa livre entre o campo hex e os botoes.
		graphics.text(this.font, Component.translatable("buildaid.menu.picked_suggestions"),
				rx, contentY + 54, Theme.TEXT_DIM, false);

		suggestX = rx;
		suggestY = contentY + 64;
		suggestWidth = rw;
		suggestHeight = Math.max(0, bottom - 34 - suggestY);
		if (suggestHeight < SUGGEST_ROW) {
			return;
		}

		Theme.roundedRect(graphics, suggestX, suggestY, suggestWidth, suggestHeight, Theme.SURFACE_SUNKEN);
		if (suggestions.isEmpty()) {
			return;
		}

		int visibleRows = suggestHeight / SUGGEST_ROW;
		for (int i = 0; i < suggestions.size() && i < visibleRows; i++) {
			net.minecraft.world.level.block.Block block = suggestions.get(i);
			int rowY = suggestY + i * SUGGEST_ROW;
			boolean isSelected = i == selectedSuggestion;
			boolean hovered = mouseX >= suggestX && mouseX <= suggestX + suggestWidth
					&& mouseY >= rowY && mouseY <= rowY + SUGGEST_ROW;

			graphics.fill(suggestX, rowY, suggestX + suggestWidth, rowY + SUGGEST_ROW,
					isSelected ? Theme.ACCENT_SOFT : hovered ? Theme.SURFACE_HOVER : 0x00000000);

			int swatch = com.foxo.buildaid.build.BlockPaletteGenerator.getBlockColor(block);
			graphics.fill(suggestX + 5, rowY + 3, suggestX + 13, rowY + 11, 0xFF000000 | swatch);
			String name = this.font.plainSubstrByWidth(block.getName().getString(), suggestWidth - 24);
			graphics.text(this.font, name, suggestX + 18, rowY + 3,
					isSelected ? Theme.TEXT : Theme.TEXT_DIM, false);
		}
	}


	private static Component hudCornerName(int corner) {
		return switch (corner) {
			case 1 -> Component.translatable("buildaid.menu.hud_corner_tr");
			case 2 -> Component.translatable("buildaid.menu.hud_corner_bl");
			case 3 -> Component.translatable("buildaid.menu.hud_corner_br");
			default -> Component.translatable("buildaid.menu.hud_corner_tl");
		};
	}

	private static Component hudBgStyleName(int style) {
		return switch (style) {
			case 1 -> Component.translatable("buildaid.menu.hud_style_vanilla");
			case 2 -> Component.translatable("buildaid.menu.hud_style_contrast");
			default -> Component.translatable("buildaid.menu.hud_style_glass");
		};
	}

	private static Component hudColorThemeName(int theme) {
		return switch (theme) {
			case 1 -> Component.translatable("buildaid.menu.hud_theme_gold");
			case 2 -> Component.translatable("buildaid.menu.hud_theme_emerald");
			case 3 -> Component.translatable("buildaid.menu.hud_theme_white");
			case 4 -> Component.translatable("buildaid.menu.hud_theme_purple");
			case 5 -> Component.translatable("buildaid.menu.hud_theme_orange");
			default -> Component.translatable("buildaid.menu.hud_theme_cyan");
		};
	}

	private void buildProfilesTab() {
		config.profileNames = BuildAidConfig.loadProfiles();
		boolean hasProfiles = !config.profileNames.isEmpty();

		int columnWidth = (contentWidth - Theme.PAD) / 2;
		int y = contentY;

		// Linha 1: salvar a configuracao atual como novo perfil.
		addRenderableWidget(new ModButton(contentX, y, contentWidth, 20,
				Component.translatable("buildaid.menu.profile_save_new"),
				ModButton.Style.PRIMARY, () -> promptProfileName()));

		// Linha 2: sobrescrever o perfil ativo com a configuracao atual.
		y += 24;
		ModButton update = new ModButton(contentX, y, columnWidth, 20,
				Component.translatable("buildaid.menu.profile_update"),
				ModButton.Style.NORMAL, () -> {
			if (config.activeProfile != null) {
				config.saveProfile(config.activeProfile);
				Feedback.infoChat("buildaid.msg.profile_saved", config.activeProfile);
				rebuildWidgets();
			}
		});
		update.active = config.activeProfile != null;
		addRenderableWidget(update);

		// Limpar o perfil ativo (volta para a configuracao padrao da sessao).
		addRenderableWidget(new ModButton(contentX + columnWidth + Theme.PAD, y, columnWidth, 20,
				Component.translatable("buildaid.menu.profile_clear"),
				ModButton.Style.NORMAL, () -> {
			config.activeProfile = null;
			config.save();
			Feedback.infoChat("buildaid.msg.profile_cleared");
			rebuildWidgets();
		}));

		// Dica de como os perfis funcionam.
		y += 26;
		graphicsNote(y, "buildaid.menu.profile_hint");

		// Lista de perfis: cada um com carregar / excluir.
		int listY = y + 22;
		int listHeight = contentY + contentHeight - listY - 4;
		profileListX = contentX;
		profileListY = listY;
		profileListWidth = contentWidth;
		profileListHeight = listHeight;
	}

	/** Escreve uma linha de dica (texto traduzivel) no meio do conteudo. */
	private void graphicsNote(int y, String key) {
		// Nao e widget: guardamos a coordenada para desenhar em extractRenderState.
		noteY = y;
		noteKey = key;
	}

	private int profileListX;
	private int profileListY;
	private int profileListWidth;
	private int profileListHeight;
	private int noteY;
	private String noteKey;

	private void promptProfileName() {
		this.minecraft.setScreenAndShow(new TextPromptScreen(this,
				Component.translatable("buildaid.menu.profile_save_new"),
				Component.translatable("buildaid.menu.profile_name_hint"),
				"",
				name -> {
			if (!name.isBlank()) {
				config.saveProfile(name);
				Feedback.infoChat("buildaid.msg.profile_saved", name);
				rebuildWidgets();
			}
		}));
	}
	private void buildHudTab() {
		int columnWidth = (contentWidth - Theme.PAD) / 2;
		int y = contentY;

		// Linha 1: Ligar/Desligar HUD e Tema de Cor
		addRenderableWidget(new ModToggle(contentX, y, columnWidth, 18,
				Component.translatable("buildaid.menu.info_hud"),
				() -> config.infoHud.enabled, value -> {
			config.infoHud.enabled = value;
			config.save();
		}));

		addRenderableWidget(new ModButton(contentX + columnWidth + Theme.PAD, y, columnWidth, 18,
				hudColorThemeName(config.infoHud.colorTheme), ModButton.Style.NORMAL, () -> {
			config.infoHud.colorTheme = (config.infoHud.colorTheme + 1) % 6;
			config.save();
			rebuildWidgets();
		}));

		// Linha 2: Canto da Tela & Estilo de Fundo
		y += 22;
		addRenderableWidget(new ModButton(contentX, y, columnWidth, 20,
				hudCornerName(config.infoHud.corner), ModButton.Style.NORMAL, () -> {
			config.infoHud.corner = (config.infoHud.corner + 1) % 4;
			config.save();
			rebuildWidgets();
		}));

		addRenderableWidget(new ModButton(contentX + columnWidth + Theme.PAD, y, columnWidth, 20,
				hudBgStyleName(config.infoHud.bgStyle), ModButton.Style.NORMAL, () -> {
			config.infoHud.bgStyle = (config.infoHud.bgStyle + 1) % 3;
			config.save();
			rebuildWidgets();
		}));

		// Tema Global da UI
		y += 24;
		addRenderableWidget(new ModButton(contentX, y, contentWidth, 20,
				Component.translatable("buildaid.menu.theme_ui", Theme.themeName(config.uiTheme)),
				ModButton.Style.PRIMARY, () -> {
			config.uiTheme = (config.uiTheme + 1) % 7;
			config.save();
			rebuildWidgets();
		}));

		// Modulos / Toggles
		y += 24;
		int row = y;
		row = addHudToggle(contentX, row, columnWidth, "coords",
				() -> config.infoHud.showCoords, v -> config.infoHud.showCoords = v);
		row = addHudToggle(contentX, row, columnWidth, "direction",
				() -> config.infoHud.showDirection, v -> config.infoHud.showDirection = v);
		row = addHudToggle(contentX, row, columnWidth, "angles",
				() -> config.infoHud.showAngles, v -> config.infoHud.showAngles = v);
		row = addHudToggle(contentX, row, columnWidth, "biome",
				() -> config.infoHud.showBiome, v -> config.infoHud.showBiome = v);
		row = addHudToggle(contentX, row, columnWidth, "target_block",
				() -> config.infoHud.showTargetBlock, v -> config.infoHud.showTargetBlock = v);

		int second = contentX + columnWidth + Theme.PAD;
		int row2 = y;
		row2 = addHudToggle(second, row2, columnWidth, "light",
				() -> config.infoHud.showLight, v -> config.infoHud.showLight = v);
		row2 = addHudToggle(second, row2, columnWidth, "time",
				() -> config.infoHud.showTime, v -> config.infoHud.showTime = v);
		row2 = addHudToggle(second, row2, columnWidth, "fps",
				() -> config.infoHud.showFps, v -> config.infoHud.showFps = v);
		row2 = addHudToggle(second, row2, columnWidth, "target_distance",
				() -> config.infoHud.showTargetDistance, v -> config.infoHud.showTargetDistance = v);
		row2 = addHudToggle(second, row2, columnWidth, "held_count",
				() -> config.infoHud.showHeldCount, v -> config.infoHud.showHeldCount = v);
		row2 = addHudToggle(second, row2, columnWidth, "durability",
				() -> config.infoHud.showDurability, v -> config.infoHud.showDurability = v);

		addHudToggle(contentX, Math.max(row, row2) + 2, contentWidth, "selection",
				() -> config.infoHud.showSelection, v -> config.infoHud.showSelection = v);
	}

	private int addHudToggle(int x, int y, int width, String key,
			BooleanSupplier getter, Consumer<Boolean> setter) {
		addRenderableWidget(new ModToggle(x, y, width, 18,
				Component.translatable("buildaid.menu.hud_" + key), getter, value -> {
			setter.accept(value);
			config.save();
		}));
		return y + 20;
	}

	// ------------------------------------------------------------------ desenho

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, Theme.SCRIM);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Theme.roundedRect(graphics, panelX, panelY, panelWidth, panelHeight, 4, Theme.BACKGROUND);
		Theme.roundedOutline(graphics, panelX, panelY, panelWidth, panelHeight, 4, Theme.BORDER);

		graphics.text(this.font, this.title, panelX + Theme.PAD + 3, panelY + 9, Theme.TEXT, false);
		graphics.text(this.font, activeTab.title(), panelX + SIDEBAR_WIDTH + Theme.PAD, panelY + 9,
				Theme.TEXT_DIM, false);
		Theme.divider(graphics, panelX + 1, panelY + HEADER_HEIGHT, panelWidth - 2);
		Theme.divider(graphics, panelX + 1, panelY + panelHeight - FOOTER_HEIGHT, panelWidth - 2);

		drawSidebar(graphics, mouseX, mouseY);

		switch (activeTab) {
			case IMAGENS -> drawGallery(graphics, mouseX, mouseY);
			case PAINEL -> drawPanelList(graphics, mouseX, mouseY);
			case HOLOGRAMA -> drawHologramInfo(graphics, mouseX, mouseY);
			case FORMAS -> drawShapeInfo(graphics, mouseX, mouseY);
			case SELECAO -> drawSelectionInfo(graphics);
			case CORES -> drawColorsInfo(graphics, mouseX, mouseY);
			case RANDOMIZADOR -> drawRandomizerInfo(graphics, mouseX, mouseY);
			case HUD -> {
			}
			case PERFIS -> drawProfilesInfo(graphics, mouseX, mouseY);
		}

		// Widgets por ultimo, para ficarem por cima do cromo desenhado acima.
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = panelX + 4;
		int y = panelY + HEADER_HEIGHT + 6;
		int step = sidebarStep();

		for (TabId tab : TabId.values()) {
			boolean active = tab == activeTab;
			boolean hovered = mouseX >= x && mouseX <= x + SIDEBAR_WIDTH - 8
					&& mouseY >= y && mouseY <= y + TAB_HEIGHT;

			if (active) {
				Theme.roundedRect(graphics, x, y, SIDEBAR_WIDTH - 8, TAB_HEIGHT, Theme.SURFACE);
				Theme.accentBar(graphics, x, y + 3, TAB_HEIGHT - 6);
			} else if (hovered) {
				Theme.roundedRect(graphics, x, y, SIDEBAR_WIDTH - 8, TAB_HEIGHT, Theme.SURFACE_HOVER);
			}

			graphics.text(this.font, tab.title(), x + 10, y + (TAB_HEIGHT - this.font.lineHeight) / 2 + 1,
					active ? Theme.TEXT : Theme.TEXT_DIM, false);
			y += step;
		}
	}

	/**
	 * Espacamento entre abas. Com nove abas, janelas baixas nao caberiam no passo cheio --
	 * comprimir aqui evita a ultima aba invadir o rodape.
	 */
	private int sidebarStep() {
		int available = panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 8;
		return Math.max(16, Math.min(TAB_HEIGHT + 2, available / TabId.values().length));
	}

	private void drawGallery(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Theme.roundedRect(graphics, galleryX, galleryY, galleryWidth, galleryHeight, Theme.SURFACE_SUNKEN);

		if (images.isEmpty()) {
			graphics.text(this.font, Component.translatable("buildaid.menu.gallery_empty"),
					galleryX + 10, galleryY + 12, Theme.TEXT_DIM, false);
			graphics.text(this.font, Component.translatable("buildaid.menu.gallery_empty_hint"),
					galleryX + 10, galleryY + 24, Theme.TEXT_DIM, false);
			return;
		}

		graphics.enableScissor(galleryX, galleryY, galleryX + galleryWidth, galleryY + galleryHeight);

		int columns = Math.max(1, (galleryWidth - TILE_GAP) / (TILE_WIDTH + TILE_GAP));
		for (int i = 0; i < images.size(); i++) {
			RefImage image = images.get(i);
			int column = i % columns;
			int rowIndex = i / columns;

			int tileX = galleryX + TILE_GAP + column * (TILE_WIDTH + TILE_GAP);
			int tileY = galleryY + TILE_GAP + rowIndex * (TILE_HEIGHT + TILE_GAP) - galleryScroll;

			// Fora da area visivel: nem desenha nem pede a textura.
			if (tileY + TILE_HEIGHT < galleryY || tileY > galleryY + galleryHeight) {
				continue;
			}

			boolean selected = image.id().equals(selectedId);
			boolean hovered = mouseX >= tileX && mouseX <= tileX + TILE_WIDTH
					&& mouseY >= tileY && mouseY <= tileY + TILE_HEIGHT
					&& mouseY >= galleryY && mouseY <= galleryY + galleryHeight;

			Theme.roundedRect(graphics, tileX, tileY, TILE_WIDTH, TILE_HEIGHT,
					selected ? Theme.ACCENT_SOFT : hovered ? Theme.SURFACE_HOVER : Theme.SURFACE);
			if (selected) {
				Theme.roundedOutline(graphics, tileX, tileY, TILE_WIDTH, TILE_HEIGHT, Theme.RADIUS, Theme.ACCENT);
			}

			ImageLibrary.Loaded thumb = library.getThumbnail(image.id());
			if (thumb != null) {
				RefRenderer.drawFittedIn(graphics, tileX + 3, tileY + 3, TILE_WIDTH - 6, TILE_HEIGHT - 18, thumb, 1.0f);
			} else {
				graphics.text(this.font, "...", tileX + TILE_WIDTH / 2 - 4, tileY + TILE_HEIGHT / 2 - 10,
						Theme.TEXT_DIM, false);
			}

			boolean isActive = image.id().equals(config.activeImageId);
			String name = this.font.plainSubstrByWidth(image.displayName(), TILE_WIDTH - 8);
			graphics.text(this.font, name, tileX + 4, tileY + TILE_HEIGHT - 12,
					isActive ? Theme.ACCENT : Theme.TEXT_DIM, false);

			// Tooltip com os dados que nao cabem no ladrilho.
			if (hovered) {
				graphics.setComponentTooltipForNextFrame(this.font,
						List.of(Component.literal(image.displayName()),
								Component.translatable("buildaid.menu.image_dims", image.width(), image.height()),
								Component.translatable("buildaid.menu.image_use_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)),
						mouseX, mouseY);
			}
		}

		graphics.disableScissor();
	}

	private void drawPanelList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Theme.roundedRect(graphics, panelListX, panelListY, panelListWidth, panelListHeight, Theme.SURFACE_SUNKEN);

		int total = entryCount();
		if (total == 0) {
			graphics.text(this.font, Component.translatable("buildaid.menu.panel_none"),
					panelListX + 6, panelListY + 8, Theme.TEXT_DIM, false);
			return;
		}

		int visibleRows = panelListHeight / PANEL_ROW;
		for (int i = 0; i < total && i < visibleRows; i++) {
			int rowY = panelListY + i * PANEL_ROW;
			boolean isSelected = i == selectedEntry;
			boolean hovered = mouseX >= panelListX && mouseX <= panelListX + panelListWidth
					&& mouseY >= rowY && mouseY <= rowY + PANEL_ROW;

			graphics.fill(panelListX, rowY, panelListX + panelListWidth, rowY + PANEL_ROW - 2,
					isSelected ? Theme.ACCENT_SOFT : hovered ? Theme.SURFACE_HOVER : 0x20FFFFFF);

			boolean noteRow = i >= config.panels.size();
			String label;
			if (noteRow) {
				BuildAidConfig.Note note = config.notes.get(i - config.panels.size());
				String first = firstLineOf(note.text);
				label = (first.isEmpty()
						? Component.translatable("buildaid.menu.note_empty").getString() : first);
				label = this.font.plainSubstrByWidth(label, panelListWidth - 26);
				graphics.text(this.font, label, panelListX + 5, rowY + 6,
						note.visible ? Theme.TEXT : Theme.TEXT_DISABLED, false);
				Theme.roundedRect(graphics, panelListX + panelListWidth - 12, rowY + 7, 6, 6, 3,
						note.visible ? Theme.NOTE_ACCENTS[Math.floorMod(note.colorPreset, Theme.NOTE_ACCENTS.length)]
								: Theme.TEXT_DISABLED);
			} else {
				BuildAidConfig.Panel panel = config.panels.get(i);
				label = this.font.plainSubstrByWidth((i + 1) + ". " + imageNameOf(panel.imageId),
						panelListWidth - 26);
				graphics.text(this.font, label, panelListX + 5, rowY + 6,
						panel.visible ? Theme.TEXT : Theme.TEXT_DISABLED, false);
				// Bolinha indicando visibilidade.
				Theme.roundedRect(graphics, panelListX + panelListWidth - 12, rowY + 7, 6, 6, 3,
						panel.visible ? Theme.ACCENT : Theme.TEXT_DISABLED);
			}

			// Divisor entre os dois grupos: o primeiro post-it marca a fronteira.
			if (!noteRow && i + 1 == config.panels.size() && !config.notes.isEmpty()) {
				int dividerY = rowY + PANEL_ROW - 1;
				graphics.fill(panelListX, dividerY, panelListX + panelListWidth, dividerY + 1, Theme.BORDER);
			}
		}

		int visible = panelListHeight / PANEL_ROW;
		if (total > visible) {
			graphics.text(this.font, "+" + (total - visible),
					panelListX + panelListWidth - 20, panelListY + panelListHeight - 10,
					Theme.TEXT_DIM, false);
		}

		drawSelectedPanelPreview(graphics);
	}

	/** Previa do painel selecionado, no espaco que sobra abaixo dos controles. */
	private void drawSelectedPanelPreview(GuiGraphicsExtractor graphics) {
		if (entryIsNote()) {
			return; // Anotacao nao tem imagem: a propria lista ja mostra o texto dela.
		}
		int previewX = contentX + 166 + Theme.PAD;
		int previewWidth = contentWidth - 166 - Theme.PAD;
		int previewY = contentY + 136;
		int previewHeight = contentY + contentHeight - previewY;
		if (previewHeight < 24) {
			return;
		}

		Theme.roundedRect(graphics, previewX, previewY, previewWidth, previewHeight, Theme.SURFACE_SUNKEN);
		Theme.roundedOutline(graphics, previewX, previewY, previewWidth, previewHeight, Theme.RADIUS, Theme.BORDER);

		BuildAidConfig.Panel panel = selectedPanel();
		ImageLibrary.Loaded image = panel == null || panel.imageId == null ? null : library.get(panel.imageId);
		if (image == null) {
			graphics.text(this.font, Component.translatable("buildaid.menu.preview_none"),
					previewX + 5, previewY + previewHeight / 2 - 4, Theme.TEXT_DIM, false);
			return;
		}

		// Mesma opacidade do painel de verdade, para a previa valer alguma coisa.
		RefRenderer.drawFittedIn(graphics, previewX + 3, previewY + 3, previewWidth - 6, previewHeight - 6,
				image, panel.opacity);
	}

	private String imageNameOf(String imageId) {
		if (imageId == null) {
			return Component.translatable("buildaid.menu.panel_no_image").getString();
		}
		return BuildAidClient.store.byId(imageId)
				.map(RefImage::displayName)
				.orElse(Component.translatable("buildaid.menu.panel_no_image").getString());
	}

	private void drawSelectionInfo(GuiGraphicsExtractor graphics) {
		int leftWidth = 148;
		int rightX = contentX + leftWidth + Theme.PAD;
		int rightWidth = contentWidth - leftWidth - Theme.PAD;

		int x = contentX;
		int y = contentY + 22;

		Theme.roundedRect(graphics, x, y, leftWidth, 50, Theme.SURFACE_SUNKEN);

		if (!AreaSelection.isModeEnabled()) {
			graphics.text(this.font, Component.translatable("buildaid.menu.selection_off1"),
					x + 4, y + 8, Theme.TEXT_DIM, false);
			graphics.text(this.font, Component.translatable("buildaid.menu.selection_off2"),
					x + 4, y + 20, Theme.TEXT_DIM, false);
			graphics.text(this.font, Component.translatable("buildaid.menu.selection_off3"),
					x + 4, y + 32, Theme.TEXT_DIM, false);
		} else {
			graphics.text(this.font,
					Component.translatable("buildaid.menu.corner1", AreaSelection.formatCorner(AreaSelection.corner1())),
					x + 4, y + 6, Theme.ACCENT, false);
			graphics.text(this.font,
					Component.translatable("buildaid.menu.corner2", AreaSelection.formatCorner(AreaSelection.corner2())),
					x + 4, y + 18, 0xFFFF9A3C, false);
			String dims = this.font.plainSubstrByWidth(AreaSelection.dimensionsText().getString(), leftWidth - 8);
			graphics.text(this.font, dims, x + 4, y + 32, Theme.TEXT, false);
		}

		if (!scannedMaterials.isEmpty()) {
			int matY = contentY + 22;
			int matHeight = contentHeight - 22;
			Theme.roundedRect(graphics, rightX, matY, rightWidth, matHeight, Theme.SURFACE_SUNKEN);
			graphics.text(this.font, Component.translatable("buildaid.menu.materials_title", scannedMaterials.size()),
					rightX + 6, matY + 5, Theme.ACCENT, false);

			int rows = Math.max(1, (matHeight - 18) / 10);
			scannedMaterialsScroll = Math.clamp(scannedMaterialsScroll, 0, Math.max(0, scannedMaterials.size() - rows));

			for (int i = 0; i < rows && i + scannedMaterialsScroll < scannedMaterials.size(); i++) {
				var mat = scannedMaterials.get(i + scannedMaterialsScroll);
				String line = this.font.plainSubstrByWidth(mat.name().getString() + ": " + mat.stackSummary(), rightWidth - 10);
				graphics.text(this.font, line, rightX + 6, matY + 18 + i * 10, Theme.TEXT_DIM, false);
			}
		}
	}

	// ------------------------------------------------------------------ perfis

	private void drawProfilesInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// Linha de dica gravada em buildProfilesTab().
		if (noteKey != null) {
			graphics.text(this.font, Component.translatable(noteKey),
					contentX, noteY, Theme.TEXT_DIM, false);
		}

		if (config.profileNames.isEmpty()) {
			graphics.text(this.font, Component.translatable("buildaid.menu.profile_none"),
					profileListX + 6, profileListY + 8, Theme.TEXT_DIM, false);
			return;
		}

		Theme.roundedRect(graphics, profileListX, profileListY, profileListWidth, profileListHeight, Theme.SURFACE_SUNKEN);

		int colWidth = (profileListWidth - Theme.PAD) / 2;
		int rowHeight = 26;
		for (int i = 0; i < config.profileNames.size(); i++) {
			String name = config.profileNames.get(i);
			int column = i % 2;
			int rowIndex = i / 2;
			int rowX = profileListX + (colWidth + Theme.PAD) * column;
			int rowY = profileListY + 6 + rowIndex * (rowHeight + 4);

			boolean active = name.equals(config.activeProfile);
			boolean hovered = mouseX >= rowX && mouseX <= rowX + colWidth
					&& mouseY >= rowY && mouseY <= rowY + rowHeight;

			graphics.fill(rowX, rowY, rowX + colWidth, rowY + rowHeight,
					active ? Theme.ACCENT_SOFT : hovered ? Theme.SURFACE_HOVER : 0x20FFFFFF);

			// Nome do perfil, ou "ativo" ao lado dele.
			String label = this.font.plainSubstrByWidth(name, colWidth - 56);
			graphics.text(this.font, label, rowX + 6, rowY + 6,
					active ? Theme.ACCENT : Theme.TEXT, false);
			if (active) {
				graphics.text(this.font, Component.translatable("buildaid.menu.profile_active"),
						rowX + 6, rowY + 15, Theme.ACCENT, false);
			}

			// Botao excluir (X) no canto direito da linha.
			drawProfileDelete(graphics, rowX + colWidth - 18, rowY + 4,
					mouseX, mouseY, i);
		}
	}

	private void drawProfileDelete(GuiGraphicsExtractor graphics, int x, int y,
			int mouseX, int mouseY, int index) {
		boolean hovered = mouseX >= x && mouseX <= x + 14 && mouseY >= y && mouseY <= y + 14;
		Theme.roundedRect(graphics, x, y, 14, 14, 3, hovered ? Theme.DANGER : Theme.SURFACE);
		graphics.text(this.font, "X", x + 4, y + 3, hovered ? Theme.TEXT : Theme.TEXT_DIM, false);
		profileDeleteX = x;
		profileDeleteY = y;
		profileDeleteHover = hovered;
		profileDeleteIndex = index;
	}

	private int profileDeleteX;
	private int profileDeleteY;
	private boolean profileDeleteHover;
	private int profileDeleteIndex;

	// ------------------------------------------------------------------ entrada

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		double mouseX = event.x();
		double mouseY = event.y();

		// Barra lateral
		int tabX = panelX + 4;
		int tabY = panelY + HEADER_HEIGHT + 6;
		int step = sidebarStep();
		for (TabId tab : TabId.values()) {
			if (mouseX >= tabX && mouseX <= tabX + SIDEBAR_WIDTH - 8 && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
				switchTab(tab);
				return true;
			}
			tabY += step;
		}

		// Lista unificada da aba Paineis: paineis primeiro, anotacoes depois do divisor.
		if (activeTab == TabId.PAINEL && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& mouseX >= panelListX && mouseX <= panelListX + panelListWidth
				&& mouseY >= panelListY && mouseY <= panelListY + panelListHeight) {
			int index = (int) ((mouseY - panelListY) / PANEL_ROW);
			if (index >= 0 && index < entryCount()) {
				selectedEntry = index;
				lastPanelTabSelection = index;
				if (doubleClick) {
					if (entryIsNote()) {
						// Duplo clique no post-it abre o editor de texto.
						openNoteEditor(index - config.panels.size());
						return true;
					}
					// No painel, alterna a visibilidade -- atalho para apagar/acender rapido.
					BuildAidConfig.Panel panel = config.panels.get(index);
					panel.visible = !panel.visible;
					config.save();
				}
				rebuildWidgets();
				return true;
			}
		}

		// Listas de holograma e forma compartilham a mesma geometria.
		if ((activeTab == TabId.HOLOGRAMA || activeTab == TabId.FORMAS)
				&& event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& mouseX >= instanceListX && mouseX <= instanceListX + instanceListWidth
				&& mouseY >= instanceListY && mouseY <= instanceListY + instanceListHeight) {
			int index = (int) ((mouseY - instanceListY) / PANEL_ROW);
			int count = activeTab == TabId.HOLOGRAMA ? config.holograms.size() : config.shapes.size();
			if (index >= 0 && index < count) {
				if (activeTab == TabId.HOLOGRAMA) {
					selectedHologram = index;
					lastSelectedHologram = index;
				} else {
					selectedShape = index;
					lastSelectedShape = index;
					materialsScroll = 0;
				}
				rebuildWidgets();
				return true;
			}
		}

		// Sugestoes de bloco da aba Cores: clique seleciona a linha.
		if (activeTab == TabId.CORES && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& suggestHeight > 0
				&& mouseX >= suggestX && mouseX <= suggestX + suggestWidth
				&& mouseY >= suggestY && mouseY <= suggestY + suggestHeight) {
			int index = (int) ((mouseY - suggestY) / SUGGEST_ROW);
			int visibleRows = suggestHeight / SUGGEST_ROW;
			if (index >= 0 && index < Math.min(suggestions.size(), visibleRows)) {
				selectedSuggestion = index;
				rebuildWidgets();
				return true;
			}
		}

		// Faixa de temas da aba Cores: clique num swatch carrega aquele tema.
		if (activeTab == TabId.CORES && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& themeChipX != null) {
			for (int i = 0; i < themeChipX.length; i++) {
				int cx = themeChipX[i];
				int cy = themeChipY[i];
				if (hit(mouseX, mouseY, cx, cy, themeChipSize, themeChipSize)) {
					applyTheme(com.foxo.buildaid.build.BlockThemePalette.THEMES.get(i));
					rebuildWidgets();
					return true;
				}
			}
		}

		// Paleta randomizadora: cliques nos botoes por linha da lista (- peso + remover).
		if (activeTab == TabId.RANDOMIZADOR && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& randomizerListHeight > 0 && !config.randomizer.entries.isEmpty()
				&& mouseX >= randomizerListX && mouseX <= randomizerListX + randomizerListWidth
				&& mouseY >= randomizerListY && mouseY <= randomizerListY + randomizerListHeight) {
			clampRandomizerScroll();
			int visible = randomizerListHeight / RANDOMIZER_ROW;
			int start = -randomizerScroll / RANDOMIZER_ROW;
			for (int i = Math.max(0, start); i < config.randomizer.entries.size() && (i - start) < visible; i++) {
				int rowY = randomizerListY + (i * RANDOMIZER_ROW) + randomizerScroll;
				int ctrlX = randomizerListX + randomizerListWidth - 92;
				// Mesmos retangulos desenhados em drawRandomizerInfo.
				if (hit(mouseX, mouseY, ctrlX, rowY + 3, 18, 16)) {
					var e = config.randomizer.entries.get(i);
					e.weight = Math.max(1, e.weight - 1);
					config.save();
					rebuildWidgets();
					return true;
				}
				if (hit(mouseX, mouseY, ctrlX + 44, rowY + 3, 18, 16)) {
					var e = config.randomizer.entries.get(i);
					e.weight = Math.min(64, e.weight + 1);
					config.save();
					rebuildWidgets();
					return true;
				}
				if (hit(mouseX, mouseY, ctrlX + 70, rowY + 3, 16, 16)) {
					config.randomizer.entries.remove(i);
					config.save();
					rebuildWidgets();
					return true;
				}
			}
		}

		if (activeTab == TabId.IMAGENS && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& inGallery(mouseX, mouseY)) {
			RefImage clicked = tileAt(mouseX, mouseY);
			if (clicked != null) {
				selectedId = clicked.id();
				library.clearFailure(selectedId);
				if (doubleClick) {
					useSelected();
				} else {
					rebuildWidgets(); // liga os botoes Usar/Remover
				}
				return true;
			}
		}

		if (activeTab == TabId.PERFIS && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			// Botao excluir (X) tem prioridade sobre a linha inteira.
			if (profileDeleteIndex >= 0 && profileDeleteHover
					&& mouseX >= profileDeleteX && mouseX <= profileDeleteX + 14
					&& mouseY >= profileDeleteY && mouseY <= profileDeleteY + 14) {
				String toRemove = config.profileNames.get(profileDeleteIndex);
				config.deleteProfile(toRemove);
				config.profileNames.remove(toRemove);
				Feedback.infoChat("buildaid.msg.profile_deleted", toRemove);
				rebuildWidgets();
				return true;
			}

			// Clicar na linha carrega o perfil (substitui a configuracao viva).
			if (!config.profileNames.isEmpty()
					&& mouseX >= profileListX && mouseX <= profileListX + profileListWidth
					&& mouseY >= profileListY && mouseY <= profileListY + profileListHeight) {
				int colWidth = (profileListWidth - Theme.PAD) / 2;
				int rowHeight = 26;
				int localY = (int) mouseY - profileListY - 6;
				if (localY >= 0) {
					int rowIndex = localY / (rowHeight + 4);
					int column = mouseX <= profileListX + colWidth ? 0 : 1;
					int index = rowIndex * 2 + column;
					if (index >= 0 && index < config.profileNames.size()) {
						String name = config.profileNames.get(index);
						if (config.loadProfile(name)) {
							Feedback.infoChat("buildaid.msg.profile_loaded", name);
							rebuildWidgets();
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (activeTab == TabId.SELECAO && !scannedMaterials.isEmpty()
				&& mouseX >= contentX + 148 + Theme.PAD && mouseX <= contentX + contentWidth
				&& mouseY >= contentY && mouseY <= contentY + contentHeight) {
			scannedMaterialsScroll -= (int) Math.signum(scrollY);
			return true;
		}

		if (activeTab == TabId.FORMAS && mouseX >= materialsX && mouseX <= materialsX + materialsWidth
				&& mouseY >= materialsY && mouseY <= materialsY + materialsHeight) {
			materialsScroll -= (int) Math.signum(scrollY);
			return true;
		}

		if (activeTab == TabId.IMAGENS && inGallery(mouseX, mouseY)) {
			galleryScroll -= (int) Math.signum(scrollY) * (TILE_HEIGHT + TILE_GAP) / 2;
			clampGalleryScroll();
			return true;
		}

		if (activeTab == TabId.RANDOMIZADOR && randomizerListHeight > 0
				&& mouseX >= randomizerListX && mouseX <= randomizerListX + randomizerListWidth
				&& mouseY >= randomizerListY && mouseY <= randomizerListY + randomizerListHeight) {
			randomizerScroll -= (int) Math.signum(scrollY) * RANDOMIZER_ROW;
			clampRandomizerScroll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean inGallery(double mouseX, double mouseY) {
		return mouseX >= galleryX && mouseX <= galleryX + galleryWidth
				&& mouseY >= galleryY && mouseY <= galleryY + galleryHeight;
	}

	private static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private RefImage tileAt(double mouseX, double mouseY) {
		int columns = Math.max(1, (galleryWidth - TILE_GAP) / (TILE_WIDTH + TILE_GAP));
		int localX = (int) (mouseX - galleryX - TILE_GAP);
		int localY = (int) (mouseY - galleryY - TILE_GAP + galleryScroll);
		if (localX < 0 || localY < 0) {
			return null;
		}

		int column = localX / (TILE_WIDTH + TILE_GAP);
		int row = localY / (TILE_HEIGHT + TILE_GAP);
		if (column >= columns || localX % (TILE_WIDTH + TILE_GAP) > TILE_WIDTH
				|| localY % (TILE_HEIGHT + TILE_GAP) > TILE_HEIGHT) {
			return null;
		}

		int index = row * columns + column;
		return index >= 0 && index < images.size() ? images.get(index) : null;
	}

	private void clampGalleryScroll() {
		int columns = Math.max(1, (galleryWidth - TILE_GAP) / (TILE_WIDTH + TILE_GAP));
		int rows = (images.size() + columns - 1) / columns;
		int contentPixels = rows * (TILE_HEIGHT + TILE_GAP) + TILE_GAP;
		galleryScroll = Math.clamp(galleryScroll, 0, Math.max(0, contentPixels - galleryHeight));
	}

	// ------------------------------------------------------------------ acoes

	/** Troca a imagem do painel em foco -- ou cria o primeiro, se ainda nao houver nenhum. */
	private void useSelected() {
		if (selectedId == null) {
			return;
		}
		BuildAidClient.store.byId(selectedId).ifPresent(image -> {
			BuildAidConfig.Panel panel = selectedPanel();
			if (panel != null) {
				BuildAidClient.importer.setPanelImage(panel, image);
			} else {
				BuildAidClient.importer.activate(image);
			}
			onClose();
		});
	}

	/** Abre a imagem num painel novo, sem mexer nos que ja estao na tela. */
	private void useAsNewPanel() {
		if (selectedId == null) {
			return;
		}
		BuildAidClient.store.byId(selectedId).ifPresent(image -> {
			BuildAidClient.importer.activate(image);
			onClose();
		});
	}

	private void removeSelected() {
		if (selectedId == null) {
			return;
		}

		GlobalUndo.push();
		String removedId = selectedId;
		BuildAidClient.store.remove(removedId);
		library.unload(removedId);

		// Sem isto, paineis e holograma ficariam apontando para uma imagem que nao existe mais.
		for (BuildAidConfig.Panel panel : config.panels) {
			if (removedId.equals(panel.imageId)) {
				panel.imageId = null;
			}
		}
		if (removedId.equals(config.activeImageId)) {
			config.activeImageId = null;
		}
		for (BuildAidConfig.Hologram hologram : config.holograms) {
			if (removedId.equals(hologram.imageId)) {
				hologram.imageId = null;
			}
		}
		config.save();

		selectedId = null;
		rebuildWidgets();
		Feedback.info("buildaid.msg.image_removed");
	}

	/** Abre o seletor de arquivo nativo, le o .litematic e ancora o ghost no jogador.
	 *  O dialogo bloqueia, entao a escolha/leitura roda numa worker thread e o resultado
	 *  so volta para a render thread (onde ancoramos e ligamos o blueprint). */
	private void importLitematicFromPicker() {
		var client = Minecraft.getInstance();
		if (client.player == null) {
			Feedback.error("buildaid.msg.tape_history_empty");
			return;
		}
		var playerPos = client.player.blockPosition();
		new Thread(() -> {
			String chosen = NativeFilePicker.openLitematic();
			if (chosen == null || chosen.isBlank()) {
				return; // usuario cancelou
			}
			Blueprint bp = LitematicReader.fromFile(java.nio.file.Path.of(chosen));
			client.execute(() -> {
				if (bp == null) {
					Feedback.error("buildaid.msg.litematic_failed");
				} else {
					bp.shiftTo(playerPos);
					Blueprint.current = bp;
					config.save();
					Feedback.info("buildaid.msg.litematic_imported", bp.blocks.size());
					rebuildWidgets();
				}
			});
		}, "BuildAid-Litematic").start();
	}
}
