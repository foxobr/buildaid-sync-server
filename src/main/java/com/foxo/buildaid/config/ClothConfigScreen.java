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
 * <p>E o segundo nivel: o dia a dia se faz no menu do mod, e aqui ficam os numeros e cores que
 * nao precisam de interacao com o mundo. Fundo transparente e rodape globalizado deixam a tela
 * com a cara do resto do mod, e cada categoria agrupa o que pertence junto -- grade, selecao,
 * simetria e detector moram dentro de "Construcao" como subcategorias.
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
				.setSavingRunnable(config::save)
				.transparentBackground();
		builder.setGlobalized(true);
		builder.setGlobalizedExpanded(false);

		ConfigEntryBuilder entries = builder.entryBuilder();

		buildAppearanceCategory(builder, entries, config);
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
				.setTooltip(text("panel_visible_tip"))
				.setSaveConsumer(value -> panel.visible = value)
				.build());

		category.addEntry(entries.startIntSlider(text("panel_opacity"), toPercent(panel.opacity), 0, 100)
				.setDefaultValue(85)
				.setSaveConsumer(value -> panel.opacity = fromPercent(value))
				.build());

		category.addEntry(entries.startIntSlider(text("panel_rotation"), panel.rotation, 0, 359)
				.setDefaultValue(0)
				.setTooltip(text("panel_rotation_tip"))
				.setSaveConsumer(value -> panel.rotation = value)
				.build());

		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> geometry =
				new java.util.ArrayList<>();
		geometry.add(entries.startIntField(text("pos_x"), panel.x)
				.setDefaultValue(16)
				.setSaveConsumer(value -> panel.x = value)
				.build());
		geometry.add(entries.startIntField(text("pos_y"), panel.y)
				.setDefaultValue(16)
				.setSaveConsumer(value -> panel.y = value)
				.build());
		geometry.add(entries.startIntField(text("width"), panel.width)
				.setDefaultValue(260)
				.setMin(48)
				.setMax(4096)
				.setSaveConsumer(value -> panel.width = value)
				.build());
		geometry.add(entries.startIntField(text("height"), panel.height)
				.setDefaultValue(180)
				.setMin(48)
				.setMax(4096)
				.setSaveConsumer(value -> panel.height = value)
				.build());
		category.addEntry(entries.startSubCategory(text("panel_geometry"), geometry).build());

		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> style =
				new java.util.ArrayList<>();
		style.add(entries.startBooleanToggle(text("panel_background"), panel.showBackground)
				.setDefaultValue(true)
				.setSaveConsumer(value -> panel.showBackground = value)
				.build());
		style.add(entries.startBooleanToggle(text("panel_border"), panel.showBorder)
				.setDefaultValue(true)
				.setSaveConsumer(value -> panel.showBorder = value)
				.build());
		style.add(entries.startBooleanToggle(text("panel_grid_overlay"), panel.showGrid)
				.setDefaultValue(false)
				.setTooltip(text("panel_grid_overlay_tip"))
				.setSaveConsumer(value -> panel.showGrid = value)
				.build());
		style.add(entries.startBooleanToggle(text("panel_flip_h"), panel.flipHorizontal)
				.setDefaultValue(false)
				.setSaveConsumer(value -> panel.flipHorizontal = value)
				.build());
		style.add(entries.startBooleanToggle(text("panel_locked"), panel.locked)
				.setDefaultValue(false)
				.setTooltip(text("panel_locked_tip"))
				.setSaveConsumer(value -> panel.locked = value)
				.build());
		category.addEntry(entries.startSubCategory(text("panel_style"), style).build());
	}

	private static void buildAppearanceCategory(ConfigBuilder builder, ConfigEntryBuilder entries, BuildAidConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(text("cat_appearance"));

		category.addEntry(entries.startTextDescription(text("appearance_hint")).build());

		String[] themeNames = new String[7];
		for (int i = 0; i < themeNames.length; i++) {
			themeNames[i] = com.foxo.buildaid.screen.Theme.themeName(i).getString();
		}
		category.addEntry(entries.startSelector(text("ui_theme"), themeNames,
						themeNames[Math.floorMod(config.uiTheme, themeNames.length)])
				.setDefaultValue(themeNames[0])
				.setTooltip(text("ui_theme_tip"))
				.setSaveConsumer(value -> {
					for (int i = 0; i < themeNames.length; i++) {
						if (themeNames[i].equals(value)) {
							config.uiTheme = i;
						}
					}
				})
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

		// ---------------- Grade ----------------
		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> grid =
				new java.util.ArrayList<>();
		grid.add(entries.startBooleanToggle(text("grid_enabled"), config.grid.enabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.grid.enabled = value)
				.build());
		grid.add(entries.startIntSlider(text("grid_radius"), config.grid.radius, 4, 64)
				.setDefaultValue(24)
				.setTooltip(text("grid_radius_tip"))
				.setSaveConsumer(value -> config.grid.radius = value)
				.build());
		grid.add(entries.startBooleanToggle(text("grid_lock_y"), config.grid.lockY)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.grid.lockY = value)
				.build());
		grid.add(entries.startIntField(text("grid_fixed_y"), config.grid.fixedY)
				.setDefaultValue(64)
				.setSaveConsumer(value -> config.grid.fixedY = value)
				.build());
		grid.add(entries.startColorField(text("grid_line_color"), config.grid.lineColor)
				.setAlphaMode(true)
				.setDefaultValue(config.grid.lineColor)
				.setTooltip(text("grid_line_color_tip"))
				.setSaveConsumer(value -> config.grid.lineColor = value)
				.build());
		grid.add(entries.startColorField(text("grid_chunk_color"), config.grid.chunkColor)
				.setAlphaMode(true)
				.setDefaultValue(config.grid.chunkColor)
				.setTooltip(text("grid_chunk_color_tip"))
				.setSaveConsumer(value -> config.grid.chunkColor = value)
				.build());
		category.addEntry(entries.startSubCategory(text("sub_grid"), grid).build());

		// ---------------- Selecao ----------------
		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> selection =
				new java.util.ArrayList<>();
		selection.add(entries.startBooleanToggle(text("selection_mode"), config.selection.modeEnabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.selection.modeEnabled = value)
				.build());
		selection.add(entries.startColorField(text("sel_box_color"), config.selection.boxColor)
				.setAlphaMode(true)
				.setDefaultValue(config.selection.boxColor)
				.setSaveConsumer(value -> config.selection.boxColor = value)
				.build());
		selection.add(entries.startColorField(text("sel_corner1_color"), config.selection.corner1Color)
				.setAlphaMode(true)
				.setDefaultValue(config.selection.corner1Color)
				.setTooltip(text("sel_corner_tip"))
				.setSaveConsumer(value -> config.selection.corner1Color = value)
				.build());
		selection.add(entries.startColorField(text("sel_corner2_color"), config.selection.corner2Color)
				.setAlphaMode(true)
				.setDefaultValue(config.selection.corner2Color)
				.setSaveConsumer(value -> config.selection.corner2Color = value)
				.build());
		selection.add(entries.startBooleanToggle(text("selection_show_center"), config.selection.showCenter)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.selection.showCenter = value)
				.build());
		category.addEntry(entries.startSubCategory(text("sub_selection"), selection).build());

		// ---------------- Simetria ----------------
		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> symmetry =
				new java.util.ArrayList<>();
		symmetry.add(entries.startBooleanToggle(text("symmetry_enabled"), config.symmetry.enabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.symmetry.enabled = value)
				.build());
		symmetry.add(entries.startSelector(text("symmetry_axis"),
					new String[] {"X", "Z"},
					config.symmetry.axis == 0 ? "X" : "Z")
				.setDefaultValue("X")
				.setTooltip(text("symmetry_axis_tip"))
				.setSaveConsumer(value -> config.symmetry.axis = "X".equals(value) ? 0 : 1)
				.build());
		symmetry.add(entries.startSelector(text("symmetry_mode"),
				new String[] {"1", "4"},
				config.symmetry.mode == 1 ? "4" : "1")
				.setDefaultValue("1")
				.setTooltip(text("symmetry_mode_tip"))
				.setSaveConsumer(value -> config.symmetry.mode = "4".equals(value) ? 1 : 0)
				.build());
		symmetry.add(entries.startSelector(text("symmetry_type"),
				new String[] {"planar", "radial"},
				config.symmetry.type == 1 ? "radial" : "planar")
				.setDefaultValue("planar")
				.setTooltip(text("symmetry_type_tip"))
				.setSaveConsumer(value -> config.symmetry.type = "radial".equals(value) ? 1 : 0)
				.build());
		symmetry.add(entries.startIntSlider(text("symmetry_arms"), config.symmetry.arms, 2, 16)
				.setDefaultValue(6)
				.setTooltip(text("symmetry_arms_tip"))
				.setSaveConsumer(value -> config.symmetry.arms = value)
				.build());
		symmetry.add(entries.startIntField(text("symmetry_position"), config.symmetry.position)
				.setDefaultValue(0)
				.setSaveConsumer(value -> config.symmetry.position = value)
				.build());
		symmetry.add(entries.startIntSlider(text("symmetry_radius"), config.symmetry.radius, 4, 128)
				.setDefaultValue(32)
				.setSaveConsumer(value -> config.symmetry.radius = value)
				.build());
		symmetry.add(entries.startIntSlider(text("symmetry_height"), config.symmetry.height, 4, 128)
				.setDefaultValue(32)
				.setSaveConsumer(value -> config.symmetry.height = value)
				.build());
		symmetry.add(entries.startColorField(text("symmetry_color"), config.symmetry.color)
				.setAlphaMode(true)
				.setDefaultValue(config.symmetry.color)
				.setSaveConsumer(value -> config.symmetry.color = value)
				.build());
		category.addEntry(entries.startSubCategory(text("sub_symmetry"), symmetry).build());

		// ---------------- Detector de monstros ----------------
		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> danger =
				new java.util.ArrayList<>();
		danger.add(entries.startBooleanToggle(text("danger_zone"), config.dangerZone.enabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.dangerZone.enabled = value)
				.build());
		danger.add(entries.startIntSlider(text("danger_radius"), config.dangerZone.radius, 4, 32)
				.setDefaultValue(16)
				.setTooltip(text("danger_radius_tip"))
				.setSaveConsumer(value -> config.dangerZone.radius = value)
				.build());
		category.addEntry(entries.startSubCategory(text("sub_danger"), danger).build());

		// ---------------- Verificador de construcao ----------------
		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> verifier =
				new java.util.ArrayList<>();
		verifier.add(entries.startBooleanToggle(text("verifier_enabled"), config.verifier.enabled)
				.setDefaultValue(false)
				.setTooltip(text("verifier_enabled_tip"))
				.setSaveConsumer(value -> config.verifier.enabled = value)
				.build());
		verifier.add(entries.startColorField(text("verifier_missing_color"), config.verifier.missingColor)
				.setDefaultValue(0xFFFFFF00)
				.setAlphaMode(true)
				.setTooltip(text("verifier_missing_color_tip"))
				.setSaveConsumer(value -> config.verifier.missingColor = value)
				.build());
		verifier.add(entries.startColorField(text("verifier_wrong_color"), config.verifier.wrongColor)
				.setDefaultValue(0xFFFF3333)
				.setAlphaMode(true)
				.setTooltip(text("verifier_wrong_color_tip"))
				.setSaveConsumer(value -> config.verifier.wrongColor = value)
				.build());
		category.addEntry(entries.startSubCategory(text("sub_verifier"), verifier).build());

		// ---------------- Paleta randomizadora ----------------
		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> randomizer =
				new java.util.ArrayList<>();
		randomizer.add(entries.startBooleanToggle(text("randomizer_enabled"), config.randomizer.enabled)
				.setDefaultValue(false)
				.setTooltip(text("randomizer_enabled_tip"))
				.setSaveConsumer(value -> config.randomizer.enabled = value)
				.build());
		randomizer.add(entries.startBooleanToggle(text("randomizer_restrict"), config.randomizer.restrictToInventory)
				.setDefaultValue(true)
				.setTooltip(text("randomizer_restrict_tip"))
				.setSaveConsumer(value -> config.randomizer.restrictToInventory = value)
				.build());
		randomizer.add(entries.startTextDescription(text("randomizer_hint")).build());
		category.addEntry(entries.startSubCategory(text("sub_randomizer"), randomizer).build());
	}

	private static void buildHudCategory(ConfigBuilder builder, ConfigEntryBuilder entries, BuildAidConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(text("cat_hud"));

		category.addEntry(entries.startBooleanToggle(text("enabled"), config.infoHud.enabled)
				.setDefaultValue(false)
				.setSaveConsumer(value -> config.infoHud.enabled = value)
				.build());

		String[] cornerNames = {
				text("hud_corner_tl").getString(),
				text("hud_corner_tr").getString(),
				text("hud_corner_bl").getString(),
				text("hud_corner_br").getString()
		};
		category.addEntry(entries.startSelector(text("hud_corner_sel"), cornerNames,
						cornerNames[Math.floorMod(config.infoHud.corner, cornerNames.length)])
				.setDefaultValue(cornerNames[0])
				.setSaveConsumer(value -> {
					for (int i = 0; i < cornerNames.length; i++) {
						if (cornerNames[i].equals(value)) {
							config.infoHud.corner = i;
						}
					}
				})
				.build());

		String[] styleNames = {
				text("hud_style_glass").getString(),
				text("hud_style_vanilla").getString(),
				text("hud_style_contrast").getString()
		};
		category.addEntry(entries.startSelector(text("hud_bg_style_sel"), styleNames,
						styleNames[Math.floorMod(config.infoHud.bgStyle, styleNames.length)])
				.setDefaultValue(styleNames[0])
				.setSaveConsumer(value -> {
					for (int i = 0; i < styleNames.length; i++) {
						if (styleNames[i].equals(value)) {
							config.infoHud.bgStyle = i;
						}
					}
				})
				.build());

		String[] themeNames = {
				text("hud_theme_cyan").getString(),
				text("hud_theme_gold").getString(),
				text("hud_theme_emerald").getString(),
				text("hud_theme_white").getString(),
				text("hud_theme_purple").getString(),
				text("hud_theme_orange").getString()
		};
		category.addEntry(entries.startSelector(text("hud_color_theme_sel"), themeNames,
						themeNames[Math.floorMod(config.infoHud.colorTheme, themeNames.length)])
				.setDefaultValue(themeNames[0])
				.setSaveConsumer(value -> {
					for (int i = 0; i < themeNames.length; i++) {
						if (themeNames[i].equals(value)) {
							config.infoHud.colorTheme = i;
						}
					}
				})
				.build());

		java.util.List<me.shedaniel.clothconfig2.api.AbstractConfigListEntry> modules =
				new java.util.ArrayList<>();
		modules.add(hudToggle(entries, "hud_coords", config.infoHud.showCoords, v -> config.infoHud.showCoords = v));
		modules.add(hudToggle(entries, "hud_direction", config.infoHud.showDirection, v -> config.infoHud.showDirection = v));
		modules.add(hudToggle(entries, "hud_angles", config.infoHud.showAngles, v -> config.infoHud.showAngles = v));
		modules.add(hudToggle(entries, "hud_biome", config.infoHud.showBiome, v -> config.infoHud.showBiome = v));
		modules.add(hudToggle(entries, "hud_target_block", config.infoHud.showTargetBlock, v -> config.infoHud.showTargetBlock = v));
		modules.add(hudToggle(entries, "hud_target_distance", config.infoHud.showTargetDistance, v -> config.infoHud.showTargetDistance = v));
		modules.add(hudToggle(entries, "hud_light", config.infoHud.showLight, v -> config.infoHud.showLight = v));
		modules.add(hudToggle(entries, "hud_time", config.infoHud.showTime, v -> config.infoHud.showTime = v));
		modules.add(hudToggle(entries, "hud_fps", config.infoHud.showFps, v -> config.infoHud.showFps = v));
		modules.add(hudToggle(entries, "hud_held_count", config.infoHud.showHeldCount, v -> config.infoHud.showHeldCount = v));
		modules.add(hudToggle(entries, "hud_durability", config.infoHud.showDurability, v -> config.infoHud.showDurability = v));
		modules.add(hudToggle(entries, "hud_selection", config.infoHud.showSelection, v -> config.infoHud.showSelection = v));
		category.addEntry(entries.startSubCategory(text("hud_modules"), modules).build());
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> hudToggle(
			ConfigEntryBuilder entries, String key, boolean current, java.util.function.Consumer<Boolean> setter) {
		return entries.startBooleanToggle(text(key), current)
				.setDefaultValue(true)
				.setSaveConsumer(setter)
				.build();
	}

	private static void buildPerformanceCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
			BuildAidConfig config) {
		ConfigCategory category = builder.getOrCreateCategory(text("cat_performance"));

		category.addEntry(entries.startIntSlider(text("max_textures"), config.cache.maxTextures, 1, 32)
				.setDefaultValue(16)
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
