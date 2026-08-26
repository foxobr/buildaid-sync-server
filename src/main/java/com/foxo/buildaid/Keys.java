package com.foxo.buildaid;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    public static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BuildAid.MOD_ID, "main"));

    // --- com tecla padrao ---
    public static KeyMapping openMenu;

    // --- sem tecla padrao (disponiveis no menu) ---
    public static KeyMapping togglePanel;
    public static KeyMapping toggleSelection;
    public static KeyMapping markCorner;
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
    public static KeyMapping tapeMeasure;
    public static KeyMapping tapeRestore;
    public static KeyMapping tapeMaterials;
    public static KeyMapping tapePalette;
    public static KeyMapping tapeShoppingList;
    public static KeyMapping symmetryToggle;
    public static KeyMapping symmetryMode;
    public static KeyMapping progressCounter;
    public static KeyMapping toggleBlueprint;
    public static KeyMapping blueprintExport;
    public static KeyMapping blueprintImport;
    public static KeyMapping importLitematic;
    public static KeyMapping undo;
    public static KeyMapping materialList;
    public static KeyMapping layerNext;
    public static KeyMapping layerPrev;
    public static KeyMapping tapePin;
    public static KeyMapping tapeUnpin;
    public static KeyMapping verifyBlueprint;
    public static KeyMapping reportVerify;
    public static KeyMapping randomize;

    private Keys() {
    }

    public static void register() {
        openMenu = bound("open_menu", GLFW.GLFW_KEY_G);

        togglePanel = unbound("toggle_panel");
        toggleSelection = unbound("toggle_selection");
        markCorner = unbound("mark_corner");
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
        tapeMeasure = unbound("tape_measure");
        tapeRestore = unbound("tape_restore");
        tapeMaterials = unbound("tape_materials");
        tapePalette = unbound("tape_palette");
        tapeShoppingList = unbound("shopping_list");
        symmetryToggle = unbound("symmetry_toggle");
        symmetryMode = unbound("symmetry_mode");
        progressCounter = unbound("progress_counter");
        toggleBlueprint = unbound("toggle_blueprint");
        blueprintExport = unbound("blueprint_export");
        blueprintImport = unbound("blueprint_import");
        importLitematic = unbound("import_litematic");
        materialList = unbound("material_list");
        undo = unbound("undo");
        layerNext = unbound("layer_next");
        layerPrev = unbound("layer_prev");
        tapePin = unbound("tape_pin");
        tapeUnpin = unbound("tape_unpin");
        verifyBlueprint = unbound("verify_blueprint");
        reportVerify = unbound("report_verify");
        randomize = unbound("randomize");
    }

    /** InputConstants.UNKNOWN e o codigo -1: aparece como \"nao atribuido\" na tela de controles. */
    private static KeyMapping bound(String name, int glfwKey) {
        return create(name, glfwKey);
    }

    /** InputConstants.UNKNOWN e o codigo -1: aparece como \"nao atribuido\" na tela de controles. */
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