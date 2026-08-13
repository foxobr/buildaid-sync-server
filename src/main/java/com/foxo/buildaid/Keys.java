package com.foxo.buildaid;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Teclas do mod, todas remapeaveis em Opcoes -> Controles, categoria "BuildAid".
 *
 * <p>So <b>quatro</b> vem com tecla de fabrica: as que se usa no meio da construcao sem querer
 * parar. Todo o resto e registrado <b>sem tecla</b> -- a acao continua disponivel pelo menu, e
 * quem quiser atalho escolhe o seu. Assim o mod nao rouba teclas de outros mods na instalacao.
 */
public final class Keys {
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BuildAid.MOD_ID, "main"));

	// --- com tecla padrao ---
	public static KeyMapping openMenu;
	public static KeyMapping togglePanel;
	public static KeyMapping toggleSelection;
	public static KeyMapping markCorner;

	// --- sem tecla padrao (disponiveis no menu) ---
	public static KeyMapping editPanel;
	public static KeyMapping toggleGhost;
	public static KeyMapping cycleImage;
	public static KeyMapping pasteClipboard;
	public static KeyMapping openFile;
	public static KeyMapping opacityUp;
	public static KeyMapping opacityDown;
	public static KeyMapping clearSelection;
	public static KeyMapping toggleGrid;
	public static KeyMapping toggleInfoHud;
	public static KeyMapping placeHologram;
	public static KeyMapping placeShape;
	public static KeyMapping selectTarget;
	public static KeyMapping grabTarget;
	public static KeyMapping rotateTarget;
	public static KeyMapping musicTogglePlay;
	public static KeyMapping musicSkip;
	public static KeyMapping musicVolumeUp;
	public static KeyMapping musicVolumeDown;
	public static KeyMapping musicToggleHud;

	private Keys() {
	}

	public static void register() {
		openMenu = bound("open_menu", GLFW.GLFW_KEY_G);
		togglePanel = bound("toggle_panel", GLFW.GLFW_KEY_H);
		toggleSelection = bound("toggle_selection", GLFW.GLFW_KEY_B);
		markCorner = bound("mark_corner", GLFW.GLFW_KEY_M);

		editPanel = unbound("edit_panel");
		toggleGhost = unbound("toggle_ghost");
		cycleImage = unbound("cycle_image");
		pasteClipboard = unbound("paste_clipboard");
		openFile = unbound("open_file");
		opacityUp = unbound("opacity_up");
		opacityDown = unbound("opacity_down");
		clearSelection = unbound("clear_selection");
		toggleGrid = unbound("toggle_grid");
		toggleInfoHud = unbound("toggle_info_hud");
		placeHologram = unbound("place_hologram");
		placeShape = unbound("place_shape");
		selectTarget = unbound("select_target");
		grabTarget = unbound("grab_target");
		rotateTarget = unbound("rotate_target");
		musicTogglePlay = unbound("music_toggle_play");
		musicSkip = unbound("music_skip");
		musicVolumeUp = unbound("music_volume_up");
		musicVolumeDown = unbound("music_volume_down");
		musicToggleHud = unbound("music_toggle_hud");
	}

	private static KeyMapping bound(String name, int glfwKey) {
		return create(name, glfwKey);
	}

	/** InputConstants.UNKNOWN e o codigo -1: aparece como "nao atribuido" na tela de controles. */
	private static KeyMapping unbound(String name) {
		return create(name, InputConstants.UNKNOWN.getValue());
	}

	private static KeyMapping create(String name, int glfwKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + BuildAid.MOD_ID + "." + name,
				InputConstants.Type.KEYSYM,
				glfwKey,
				CATEGORY
		));
	}
}
