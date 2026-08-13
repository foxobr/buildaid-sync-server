package com.foxo.buildaid.screen;

import com.foxo.buildaid.BuildAidClient;
import com.foxo.buildaid.Feedback;
import com.foxo.buildaid.audio.AudioPlayer;
import com.foxo.buildaid.audio.TrackInfo;
import com.foxo.buildaid.build.AreaSelection;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.config.ClothConfigScreen;
import com.foxo.buildaid.hud.RefRenderer;
import com.foxo.buildaid.image.ImageLibrary;
import com.foxo.buildaid.image.RefImage;
import com.foxo.buildaid.net.music.MusicPacket;
import com.foxo.buildaid.net.music.MusicSyncClient;
import com.foxo.buildaid.screen.widget.ModButton;
import com.foxo.buildaid.screen.widget.ModSlider;
import com.foxo.buildaid.screen.widget.ModToggle;
import com.foxo.buildaid.shape.ShapeGuide;
import com.foxo.buildaid.shape.ShapeType;
import com.foxo.buildaid.world.ImageHologram;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
		HUD("hud"),
		MUSICA("music");

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

	// Aba Imagens
	private List<RefImage> images = List.of();
	private String selectedId;
	private int galleryScroll;
	private int galleryX;
	private int galleryY;
	private int galleryWidth;
	private int galleryHeight;
	private EditBox searchBox;
	/** Sobrevive ao fechar a tela: reabrir mantem o filtro. */
	private static String searchText = "";

	// Aba Painel
	private static int lastSelectedPanel;
	private int selectedPanel = lastSelectedPanel;
	private int panelListX;
	private int panelListY;
	private int panelListWidth;
	private int panelListHeight;
	private static final int PANEL_ROW = 20;

	// Abas Holograma e Formas: mesma lista generica
	private static int lastSelectedHologram;
	private static int lastSelectedShape;
	private int selectedHologram = lastSelectedHologram;
	private int selectedShape = lastSelectedShape;
	private int instanceListX;
	private int instanceListY;
	private int instanceListWidth;
	private int instanceListHeight;

	// Lista de materiais da aba Formas
	private int materialsX;
	private int materialsY;
	private int materialsWidth;
	private int materialsHeight;
	private int materialsScroll;

	// Aba Musica
	private EditBox musicServerBox;
	private EditBox musicRoomBox;
	private EditBox musicUrlBox;
	private static String musicUrlDraft = "";

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

		buildFooter();

		switch (activeTab) {
			case IMAGENS -> buildImagesTab();
			case PAINEL -> buildPanelTab();
			case HOLOGRAMA -> buildHologramTab();
			case FORMAS -> buildShapeTab();
			case SELECAO -> buildSelectionTab();
			case HUD -> buildHudTab();
			case MUSICA -> buildMusicTab();
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

		addRenderableWidget(new ModButton(panelX + Theme.PAD, footerY, 150, 20,
				Component.translatable("buildaid.menu.advanced"), ModButton.Style.NORMAL,
				() -> this.minecraft.setScreenAndShow(ClothConfigScreen.create(this))));

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

	private void buildPanelTab() {
		selectedPanel = Math.clamp(selectedPanel, 0, Math.max(0, config.panels.size() - 1));
		lastSelectedPanel = selectedPanel;

		int listWidth = 166;
		int rightX = contentX + listWidth + Theme.PAD;
		int rightWidth = contentWidth - listWidth - Theme.PAD;

		panelListX = contentX;
		panelListY = contentY;
		panelListWidth = listWidth;
		panelListHeight = contentHeight - 26;

		// Botoes da lista
		int buttonsY = panelListY + panelListHeight + 6;
		int third = (listWidth - Theme.PAD * 2) / 3;
		addRenderableWidget(new ModButton(panelListX, buttonsY, third, 20,
				Component.translatable("buildaid.menu.panel_add"), ModButton.Style.PRIMARY, this::addPanel));

		ModButton duplicate = new ModButton(panelListX + third + Theme.PAD, buttonsY, third, 20,
				Component.translatable("buildaid.menu.panel_duplicate"), ModButton.Style.NORMAL, this::duplicatePanel);
		duplicate.active = !config.panels.isEmpty();
		addRenderableWidget(duplicate);

		ModButton remove = new ModButton(panelListX + (third + Theme.PAD) * 2, buttonsY, third, 20,
				Component.translatable("buildaid.menu.panel_remove"), ModButton.Style.DANGER, this::removePanel);
		remove.active = !config.panels.isEmpty();
		addRenderableWidget(remove);

		// Controles do painel selecionado
		BuildAidConfig.Panel panel = selectedPanel();
		int y = contentY;

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

		// Overlay de tela cheia: e global, nao pertence a nenhum painel.
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

	private BuildAidConfig.Panel selectedPanel() {
		return selectedPanel >= 0 && selectedPanel < config.panels.size()
				? config.panels.get(selectedPanel)
				: null;
	}

	private void addPanel() {
		config.addPanel(selectedId != null ? selectedId : config.activeImageId);
		selectedPanel = config.panels.size() - 1;
		config.save();
		rebuildWidgets();
	}

	private void duplicatePanel() {
		BuildAidConfig.Panel panel = selectedPanel();
		if (panel == null) {
			return;
		}
		BuildAidConfig.Panel copy = panel.copy();
		copy.x += 24;
		copy.y += 24;
		config.panels.add(copy);
		selectedPanel = config.panels.size() - 1;
		config.save();
		rebuildWidgets();
	}

	private void removePanel() {
		if (config.panels.isEmpty()) {
			return;
		}
		config.panels.remove(selectedPanel);
		selectedPanel = Math.clamp(selectedPanel, 0, Math.max(0, config.panels.size() - 1));
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
		addRenderableWidget(new ModButton(instanceListX, buttonsY, listWidth, 20,
				Component.translatable("buildaid.menu.shape_place"), ModButton.Style.PRIMARY, () -> {
			ShapeGuide.placeNewAtCrosshair(this.minecraft);
			onClose();
		}));

		ModButton fromSelection = new ModButton(instanceListX, buttonsY + 24, listWidth, 20,
				Component.translatable("buildaid.menu.shape_from_selection"), ModButton.Style.NORMAL,
				this::shapeFromSelection);
		fromSelection.active = AreaSelection.isComplete();
		addRenderableWidget(fromSelection);

		BuildAidConfig.Shape s = selectedShape();
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
		pitch.active = ShapeType.parse(s.type) == ShapeType.HELIX;
		addRenderableWidget(pitch);

		y += 28;
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

	/** Lista generica de instancias (hologramas ou formas), no molde da lista de paineis. */
	private void drawInstanceList(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			int count, int selected, IntFunction<String> label, String emptyKey) {
		Theme.roundedRect(graphics, instanceListX, instanceListY, instanceListWidth, instanceListHeight,
				Theme.SURFACE_SUNKEN);

		if (count == 0) {
			graphics.text(this.font, Component.translatable(emptyKey),
					instanceListX + 6, instanceListY + 8, Theme.TEXT_DIM, false);
			return;
		}

		int visibleRows = instanceListHeight / PANEL_ROW;
		for (int i = 0; i < count && i < visibleRows; i++) {
			int rowY = instanceListY + i * PANEL_ROW;
			boolean isSelected = i == selected;
			boolean hovered = mouseX >= instanceListX && mouseX <= instanceListX + instanceListWidth
					&& mouseY >= rowY && mouseY <= rowY + PANEL_ROW;

			graphics.fill(instanceListX, rowY, instanceListX + instanceListWidth, rowY + PANEL_ROW - 2,
					isSelected ? Theme.ACCENT_SOFT : hovered ? Theme.SURFACE_HOVER : 0x20FFFFFF);

			String text = this.font.plainSubstrByWidth((i + 1) + ". " + label.apply(i), instanceListWidth - 10);
			graphics.text(this.font, text, instanceListX + 5, rowY + 6,
					isSelected ? Theme.TEXT : Theme.TEXT_DIM, false);
		}
	}

	private void drawHologramInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		drawInstanceList(graphics, mouseX, mouseY, config.holograms.size(), selectedHologram,
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
		drawInstanceList(graphics, mouseX, mouseY, config.shapes.size(), selectedShape,
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
	 * <p>E o que transforma a guia em algo construível: quem constrói sobe camada por camada e
	 * precisa saber quanto vai em cada uma, nao so o total.
	 */
	private void drawMaterials(GuiGraphicsExtractor graphics, BuildAidConfig.Shape shape) {
		materialsX = contentX + instanceListWidth + Theme.PAD;
		materialsY = contentY + 148;
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
			graphics.text(this.font,
					Component.translatable("buildaid.menu.shape_layer", layer, layers[layer]),
					materialsX + 5, materialsY + 16 + i * 10, Theme.TEXT_DIM, false);
		}
	}

	private static Component facingName(int facing) {
		String key = switch (facing) {
			case 1 -> "east";
			case 2 -> "south";
			case 3 -> "west";
			case 4 -> "flat";
			default -> "north";
		};
		return Component.translatable("buildaid.facing." + key);
	}

	private void buildSelectionTab() {
		int y = contentY;

		addRenderableWidget(new ModToggle(contentX, y, contentWidth, 18,
				Component.translatable("buildaid.menu.selection_mode"),
				AreaSelection::isModeEnabled, AreaSelection::setModeEnabled));

		y += 78; // espaco do bloco de texto desenhado em extractRenderState
		addRenderableWidget(new ModButton(contentX, y, 130, 20,
				Component.translatable("buildaid.menu.clear_selection"), ModButton.Style.NORMAL,
				AreaSelection::clear));

		y += 32;
		addRenderableWidget(new ModToggle(contentX, y, contentWidth, 18,
				Component.translatable("buildaid.menu.grid"),
				() -> config.grid.enabled, value -> {
			config.grid.enabled = value;
			config.save();
		}));

		y += 24;
		addRenderableWidget(new ModSlider(contentX, y, contentWidth, 22,
				value -> Component.translatable("buildaid.menu.grid_radius", value),
				4, 64, config.grid.radius, value -> {
			config.grid.radius = value;
			config.save();
		}));
	}

	private void buildHudTab() {
		int columnWidth = (contentWidth - Theme.PAD) / 2;
		int y = contentY;

		addRenderableWidget(new ModToggle(contentX, y, contentWidth, 18,
				Component.translatable("buildaid.menu.info_hud"),
				() -> config.infoHud.enabled, value -> {
			config.infoHud.enabled = value;
			config.save();
		}));

		y += 26;
		int row = y;
		row = addHudToggle(contentX, row, columnWidth, "coords",
				() -> config.infoHud.showCoords, v -> config.infoHud.showCoords = v);
		row = addHudToggle(contentX, row, columnWidth, "direction",
				() -> config.infoHud.showDirection, v -> config.infoHud.showDirection = v);
		row = addHudToggle(contentX, row, columnWidth, "biome",
				() -> config.infoHud.showBiome, v -> config.infoHud.showBiome = v);

		int second = contentX + columnWidth + Theme.PAD;
		int row2 = y;
		row2 = addHudToggle(second, row2, columnWidth, "light",
				() -> config.infoHud.showLight, v -> config.infoHud.showLight = v);
		row2 = addHudToggle(second, row2, columnWidth, "time",
				() -> config.infoHud.showTime, v -> config.infoHud.showTime = v);
		row2 = addHudToggle(second, row2, columnWidth, "fps",
				() -> config.infoHud.showFps, v -> config.infoHud.showFps = v);

		addHudToggle(contentX, Math.max(row, row2) + 6, contentWidth, "selection",
				() -> config.infoHud.showSelection, v -> config.infoHud.showSelection = v);
	}

	private void buildMusicTab() {
		int colWidth = (contentWidth - Theme.PAD) / 2;
		int leftX = contentX;
		int rightX = contentX + colWidth + Theme.PAD;
		int y = contentY;

		// --- Coluna Esquerda: Conexao & Configuracoes ---
		addRenderableWidget(new ModToggle(leftX, y, colWidth, 18,
				Component.translatable("buildaid.menu.music_auto_server"),
				() -> config.music.autoServerRoom, val -> {
			config.music.autoServerRoom = val;
			config.save();
			rebuildWidgets();
		}));

		y += 22;
		if (config.music.autoServerRoom) {
			String srvName = MusicSyncClient.detectServerDisplayName(this.minecraft);
			addRenderableWidget(new ModButton(leftX, y, colWidth, 18,
					Component.literal("🌐 " + srvName), ModButton.Style.NORMAL, () -> {
				String user = this.minecraft.player != null ? this.minecraft.player.getName().getString() : "Player";
				MusicSyncClient.get().autoJoinCurrentServer(this.minecraft);
				rebuildWidgets();
			}));
		} else {
			musicRoomBox = new EditBox(this.font, leftX, y, colWidth, 18, Component.translatable("buildaid.menu.music_room"));
			musicRoomBox.setMaxLength(32);
			musicRoomBox.setHint(Component.translatable("buildaid.menu.music_room_hint"));
			musicRoomBox.setValue(config.music.roomId);
			musicRoomBox.setResponder(val -> {
				config.music.roomId = val;
				config.save();
			});
			addRenderableWidget(musicRoomBox);
		}

		y += 22;
		int halfBtn = (colWidth - Theme.PAD) / 2;
		addRenderableWidget(new ModButton(leftX, y, halfBtn, 18,
				Component.translatable("buildaid.menu.music_join"), ModButton.Style.PRIMARY, () -> {
			String user = this.minecraft.player != null ? this.minecraft.player.getName().getString() : "Player";
			if (config.music.autoServerRoom) {
				MusicSyncClient.get().autoJoinCurrentServer(this.minecraft);
			} else {
				MusicSyncClient.get().connectAndJoin(config.music.serverUrl, config.music.roomId, user);
			}
			rebuildWidgets();
		}));

		addRenderableWidget(new ModButton(leftX + halfBtn + Theme.PAD, y, halfBtn, 18,
				Component.translatable("buildaid.menu.music_leave"), ModButton.Style.NORMAL, () -> {
			MusicSyncClient.get().leaveRoom();
			rebuildWidgets();
		}));

		y += 22;
		if (config.music.serverUrl == null || config.music.serverUrl.isBlank() || config.music.serverUrl.contains("localhost")) {
			config.music.serverUrl = "wss://buildaid-sync-server.onrender.com";
			config.save();
		}
		musicServerBox = new EditBox(this.font, leftX, y, colWidth, 18, Component.translatable("buildaid.menu.music_server"));
		musicServerBox.setMaxLength(128);
		musicServerBox.setHint(Component.translatable("buildaid.menu.music_server_hint"));
		musicServerBox.setValue(config.music.serverUrl);
		musicServerBox.setResponder(val -> {
			config.music.serverUrl = val;
			config.save();
		});
		addRenderableWidget(musicServerBox);

		y += 22;
		addRenderableWidget(new ModSlider(leftX, y, colWidth, 20,
				val -> Component.translatable("buildaid.menu.music_volume", val),
				0, 100, config.music.volume, val -> {
			config.music.volume = val;
			AudioPlayer.get().setVolume(val / 100.0f);
			config.save();
		}));

		// --- Coluna Direita: Adicionar Musica & Controles de Reproducao ---
		int rightY = contentY;
		musicUrlBox = new EditBox(this.font, rightX, rightY, colWidth, 18, Component.translatable("buildaid.menu.music_url"));
		musicUrlBox.setMaxLength(256);
		musicUrlBox.setHint(Component.translatable("buildaid.menu.music_url_hint"));
		musicUrlBox.setValue(musicUrlDraft);
		musicUrlBox.setResponder(val -> musicUrlDraft = val);
		addRenderableWidget(musicUrlBox);

		rightY += 22;
		addRenderableWidget(new ModButton(rightX, rightY, colWidth, 18,
				Component.translatable("buildaid.menu.music_add_track"), ModButton.Style.PRIMARY, () -> {
			if (!musicUrlDraft.isBlank()) {
				MusicSyncClient.get().addTrack(musicUrlDraft);
				musicUrlDraft = "";
				if (musicUrlBox != null) musicUrlBox.setValue("");
				rebuildWidgets();
			}
		}));

		rightY += 22;
		addRenderableWidget(new ModButton(rightX, rightY, halfBtn, 20,
				Component.translatable("buildaid.menu.music_play_pause"), ModButton.Style.NORMAL, () -> {
			MusicSyncClient.get().togglePlay();
			rebuildWidgets();
		}));

		addRenderableWidget(new ModButton(rightX + halfBtn + Theme.PAD, rightY, halfBtn, 20,
				Component.translatable("buildaid.menu.music_skip"), ModButton.Style.NORMAL, () -> {
			MusicSyncClient.get().skip();
			rebuildWidgets();
		}));

		rightY += 24;
		addRenderableWidget(new ModToggle(rightX, rightY, colWidth, 18,
				Component.translatable("buildaid.menu.music_hud"),
				() -> config.music.hudEnabled, val -> {
			config.music.hudEnabled = val;
			config.save();
		}));
	}

	private void drawMusicInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int infoY = contentY + 114;
		int infoHeight = contentHeight - 114;

		MusicSyncClient sync = MusicSyncClient.get();
		MusicPacket.RoomState state = sync.getState();
		TrackInfo current = state.currentTrack();

		Theme.roundedRect(graphics, contentX, infoY, contentWidth, infoHeight, Theme.SURFACE_SUNKEN);
		Theme.roundedOutline(graphics, contentX, infoY, contentWidth, infoHeight, Theme.RADIUS, Theme.BORDER);

		String statusText;
		if (sync.isInRoom()) {
			String roomName = config.music.autoServerRoom ? MusicSyncClient.detectServerDisplayName(this.minecraft) : sync.getCurrentRoomId();
			statusText = "● " + Component.translatable("buildaid.menu.music_status_room", roomName, state.members().size()).getString();
		} else {
			statusText = "○ " + Component.translatable("buildaid.menu.music_status_solo").getString();
		}

		int statusColor = sync.isInRoom() ? Theme.ACCENT : Theme.TEXT_DIM;
		graphics.text(this.font, Component.literal(statusText), contentX + 6, infoY + 5, statusColor, false);

		// Lista de membros online
		if (sync.isInRoom() && !state.members().isEmpty()) {
			String membersStr = "👥 " + String.join(", ", state.members());
			membersStr = this.font.plainSubstrByWidth(membersStr, contentWidth - 12);
			graphics.text(this.font, Component.literal(membersStr), contentX + 6, infoY + 16, Theme.TEXT_DIM, false);
		}

		int trackY = sync.isInRoom() ? infoY + 28 : infoY + 18;

		if (!current.isEmpty()) {
			String title = "▶ " + current.title() + " (" + current.author() + ")";
			title = this.font.plainSubstrByWidth(title, contentWidth - 12);
			graphics.text(this.font, Component.literal(title), contentX + 6, trackY, Theme.TEXT, false);

			int queueY = trackY + 12;
			graphics.text(this.font, Component.translatable("buildaid.menu.music_queue_title", state.queue().size()), contentX + 6, queueY, Theme.TEXT_DIM, false);
			queueY += 10;

			for (int i = 0; i < state.queue().size() && i < 2; i++) {
				TrackInfo t = state.queue().get(i);
				String qItem = (i + 1) + ". " + t.title() + " [" + t.formattedDuration() + "]";
				qItem = this.font.plainSubstrByWidth(qItem, contentWidth - 16);
				graphics.text(this.font, Component.literal(qItem), contentX + 10, queueY, 0xFFCAD1DC, false);
				queueY += 10;
			}
		} else {
			graphics.text(this.font, Component.translatable("buildaid.menu.music_no_track"),
					contentX + 6, trackY, Theme.TEXT_DIM, false);
			graphics.text(this.font, Component.translatable("buildaid.menu.music_no_track_hint"),
					contentX + 6, trackY + 11, Theme.TEXT_DIM, false);
		}
	}

	private int addHudToggle(int x, int y, int width, String key,
			BooleanSupplier getter, Consumer<Boolean> setter) {
		addRenderableWidget(new ModToggle(x, y, width, 18,
				Component.translatable("buildaid.menu.hud_" + key), getter, value -> {
			setter.accept(value);
			config.save();
		}));
		return y + 22;
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
			case HUD -> {
			}
			case MUSICA -> drawMusicInfo(graphics, mouseX, mouseY);
		}

		// Widgets por ultimo, para ficarem por cima do cromo desenhado acima.
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = panelX + 4;
		int y = panelY + HEADER_HEIGHT + 6;

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
			y += TAB_HEIGHT + 2;
		}
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
		}

		graphics.disableScissor();
	}

	private void drawPanelList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Theme.roundedRect(graphics, panelListX, panelListY, panelListWidth, panelListHeight, Theme.SURFACE_SUNKEN);

		if (config.panels.isEmpty()) {
			graphics.text(this.font, Component.translatable("buildaid.menu.panel_none"),
					panelListX + 6, panelListY + 8, Theme.TEXT_DIM, false);
		} else {
			int visibleRows = panelListHeight / PANEL_ROW;
			for (int i = 0; i < config.panels.size() && i < visibleRows; i++) {
				BuildAidConfig.Panel panel = config.panels.get(i);
				int rowY = panelListY + i * PANEL_ROW;
				boolean isSelected = i == selectedPanel;
				boolean hovered = mouseX >= panelListX && mouseX <= panelListX + panelListWidth
						&& mouseY >= rowY && mouseY <= rowY + PANEL_ROW;

				graphics.fill(panelListX, rowY, panelListX + panelListWidth, rowY + PANEL_ROW - 2,
						isSelected ? Theme.ACCENT_SOFT : hovered ? Theme.SURFACE_HOVER : 0x20FFFFFF);

				String label = (i + 1) + ". " + imageNameOf(panel.imageId);
				label = this.font.plainSubstrByWidth(label, panelListWidth - 26);
				graphics.text(this.font, label, panelListX + 5, rowY + 6,
						panel.visible ? Theme.TEXT : Theme.TEXT_DISABLED, false);

				// Bolinha indicando visibilidade.
				Theme.roundedRect(graphics, panelListX + panelListWidth - 12, rowY + 7, 6, 6, 3,
						panel.visible ? Theme.ACCENT : Theme.TEXT_DISABLED);
			}

			int visible = panelListHeight / PANEL_ROW;
			if (config.panels.size() > visible) {
				graphics.text(this.font, "+" + (config.panels.size() - visible),
						panelListX + panelListWidth - 20, panelListY + panelListHeight - 10,
						Theme.TEXT_DIM, false);
			}
		}

		drawSelectedPanelPreview(graphics);
	}

	/** Previa do painel selecionado, no espaco que sobra abaixo dos controles. */
	private void drawSelectedPanelPreview(GuiGraphicsExtractor graphics) {
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
		int x = contentX;
		int y = contentY + 24;

		Theme.roundedRect(graphics, x, y, contentWidth, 50, Theme.SURFACE_SUNKEN);

		if (!AreaSelection.isModeEnabled()) {
			graphics.text(this.font, Component.translatable("buildaid.menu.selection_off1"),
					x + 6, y + 8, Theme.TEXT_DIM, false);
			graphics.text(this.font, Component.translatable("buildaid.menu.selection_off2"),
					x + 6, y + 20, Theme.TEXT_DIM, false);
			graphics.text(this.font, Component.translatable("buildaid.menu.selection_off3"),
					x + 6, y + 32, Theme.TEXT_DIM, false);
			return;
		}

		graphics.text(this.font,
				Component.translatable("buildaid.menu.corner1", AreaSelection.formatCorner(AreaSelection.corner1())),
				x + 6, y + 6, Theme.ACCENT, false);
		graphics.text(this.font,
				Component.translatable("buildaid.menu.corner2", AreaSelection.formatCorner(AreaSelection.corner2())),
				x + 6, y + 18, 0xFFFF9A3C, false);
		graphics.text(this.font, AreaSelection.dimensionsText(), x + 6, y + 32, Theme.TEXT, false);
	}

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
		for (TabId tab : TabId.values()) {
			if (mouseX >= tabX && mouseX <= tabX + SIDEBAR_WIDTH - 8 && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
				switchTab(tab);
				return true;
			}
			tabY += TAB_HEIGHT + 2;
		}

		if (activeTab == TabId.PAINEL && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
				&& mouseX >= panelListX && mouseX <= panelListX + panelListWidth
				&& mouseY >= panelListY && mouseY <= panelListY + panelListHeight) {
			int index = (int) ((mouseY - panelListY) / PANEL_ROW);
			if (index >= 0 && index < config.panels.size()) {
				selectedPanel = index;
				lastSelectedPanel = index;
				if (doubleClick) {
					// Clique duplo alterna a visibilidade -- atalho para apagar/acender rapido.
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

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean inGallery(double mouseX, double mouseY) {
		return mouseX >= galleryX && mouseX <= galleryX + galleryWidth
				&& mouseY >= galleryY && mouseY <= galleryY + galleryHeight;
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
}
