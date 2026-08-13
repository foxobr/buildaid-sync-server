package com.foxo.buildaid.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tela de ajustes finos, montada a mao com o {@code ConfigBuilder} do Cloth Config (em vez de
 * AutoConfig: menos magica de reflexao e menos coisa para quebrar quando o Cloth ou o Minecraft
 * mudarem).
 *
 * <p>E o segundo nivel: o dia a dia se faz no menu do mod, e aqui ficam as opcoes numericas.
 * Opacidade aparece como slider de 0 a 100% em vez de float de 0 a 1 -- e o mesmo valor, so que
 * legivel.
 */
public final class ClothConfigScreen {
	private ClothConfigScreen() {
	}

	private static Component text(String key) {
		return Component.translatable("buildaid.config." + key);
	}

	public static Screen create(Screen parent) {
		BuildAidConfig config = BuildAidConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("BuildAid"))
				.setSavingRunnable(config::save);

		ConfigEntryBuilder entries = builder.entryBuilder();

		buildPanelCategory(builder, entries, config);
		buildGhostCategory(builder, entries, config);
		buildBuildCategory(builder, entries, config);
		buildHudCategory(builder, entries, config);
		buildPerformanceCategory(builder, entries, config);

		return builder.build();
	}

	/**
	 * Ajuste fino do <b>primeiro</b> painel.
	 *
	 * <p>Agora que sao varios, editar todos por campos numericos aqui viraria uma tela enorme.
	 * O gerenciamento da lista fica no menu do mod (G); esta categoria serve para acertar valores
	 * exatos do painel principal.
	 */
	private static void buildPanelCategory(ConfigBuilder builder, ConfigEntryBuilder entries, BuildAidConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(text("cat_panel"));

		category.addEntry(entries.startTextDescription(text("panel_hint")).build());

		if (config.panels.isEmpty()) {
			category.addEntry(entries.startTextDescription(text("panel_none")).build());
			return;
		}

		BuildAidConfig.Panel panel = config.panels.getFirst();

		category.addEntry(entries.startBooleanToggle(text("panel_visible"), panel.visible)
				.setDefaultValue(true)
				.setSaveConsumer(value -> panel.visible = value)
				.build());

		category.addEntry(entries.startIntSlider(text("panel_opacity"), toPercent(panel.opacity), 0, 100)
				.setDefaultValue(85)
				.setSaveConsumer(value -> panel.opacity = fromPercent(value))
				.build());

		category.addEntry(entries.startIntField(text("pos_x"), panel.x)
				.setDefaultValue(16)
				.setSaveConsumer(value -> panel.x = value)
				.build());

		category.addEntry(entries.startIntField(text("pos_y"), panel.y)
				.setDefaultValue(16)
				.setSaveConsumer(value -> panel.y = value)
				.build());

		category.addEntry(entries.startIntField(text("width"), panel.width)
				.setDefaultValue(260)
				.setMin(48)
				.setMax(4096)
				.setSaveConsumer(value -> panel.width = value)
				.build());

		category.addEntry(entries.startIntField(text("height"), panel.height)
				.setDefaultValue(180)
				.setMin(48)
				.setMax(4096)
				.setSaveConsumer(value -> panel.height = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("panel_background"), panel.showBackground)
				.setDefaultValue(true)
				.setSaveConsumer(value -> panel.showBackground = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("panel_border"), panel.showBorder)
				.setDefaultValue(true)
				.setSaveConsumer(value -> panel.showBorder = value)
				.build());
	}

	private static void buildGhostCategory(ConfigBuilder builder, ConfigEntryBuilder entries, BuildAidConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(text("cat_ghost"));

		category.addEntry(entries.startTextDescription(text("ghost_hint")).build());

		category.addEntry(entries.startBooleanToggle(text("enabled"), config.ghost.enabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.ghost.enabled = value)
				.build());

		category.addEntry(entries.startIntSlider(text("ghost_opacity"),
						toPercent(config.ghost.opacity), 0, 100)
				.setDefaultValue(25)
				.setSaveConsumer(value -> config.ghost.opacity = fromPercent(value))
				.build());
	}

	private static void buildBuildCategory(ConfigBuilder builder, ConfigEntryBuilder entries, BuildAidConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(text("cat_build"));

		category.addEntry(entries.startTextDescription(text("build_hint")).build());

		category.addEntry(entries.startBooleanToggle(text("selection_mode"), config.selection.modeEnabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.selection.modeEnabled = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("grid_enabled"), config.grid.enabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.grid.enabled = value)
				.build());

		category.addEntry(entries.startIntSlider(text("grid_radius"), config.grid.radius, 4, 64)
				.setDefaultValue(24)
				.setTooltip(text("grid_radius_tip"))
				.setSaveConsumer(value -> config.grid.radius = value)
				.build());
	}

	private static void buildHudCategory(ConfigBuilder builder, ConfigEntryBuilder entries, BuildAidConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(text("cat_hud"));

		category.addEntry(entries.startBooleanToggle(text("enabled"), config.infoHud.enabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.infoHud.enabled = value)
				.build());

		category.addEntry(entries.startIntField(text("pos_x"), config.infoHud.x)
				.setDefaultValue(4)
				.setSaveConsumer(value -> config.infoHud.x = value)
				.build());

		category.addEntry(entries.startIntField(text("pos_y"), config.infoHud.y)
				.setDefaultValue(4)
				.setSaveConsumer(value -> config.infoHud.y = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("hud_coords"), config.infoHud.showCoords)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.infoHud.showCoords = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("hud_direction"), config.infoHud.showDirection)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.infoHud.showDirection = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("hud_biome"), config.infoHud.showBiome)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.infoHud.showBiome = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("hud_light"), config.infoHud.showLight)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.infoHud.showLight = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("hud_time"), config.infoHud.showTime)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.infoHud.showTime = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("hud_fps"), config.infoHud.showFps)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.infoHud.showFps = value)
				.build());

		category.addEntry(entries.startBooleanToggle(text("hud_selection"), config.infoHud.showSelection)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.infoHud.showSelection = value)
				.build());
	}

	private static void buildPerformanceCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
			BuildAidConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(text("cat_performance"));

		category.addEntry(entries.startIntSlider(text("max_textures"), config.cache.maxTextures, 1, 32)
				.setDefaultValue(8)
				.setTooltip(text("max_textures_tip"))
				.setSaveConsumer(value -> config.cache.maxTextures = value)
				.build());

		category.addEntry(entries.startIntSlider(text("max_thumbnails"), config.cache.maxThumbnails, 8, 256)
				.setDefaultValue(64)
				.setTooltip(text("max_thumbnails_tip"))
				.setSaveConsumer(value -> config.cache.maxThumbnails = value)
				.build());

		category.addEntry(entries.startIntSlider(text("max_dimension"), config.cache.maxDimension, 512, 8192)
				.setDefaultValue(4096)
				.setTooltip(text("max_dimension_tip"))
				.setSaveConsumer(value -> config.cache.maxDimension = value)
				.build());
	}

	private static int toPercent(float value) {
		return Math.clamp(Math.round(value * 100.0f), 0, 100);
	}

	private static float fromPercent(int percent) {
		return Math.clamp(percent / 100.0f, 0.0f, 1.0f);
	}
}
