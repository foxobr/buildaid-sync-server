package com.foxo.buildaid.hud;

import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.image.ImageLibrary;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Overlay "fantasma": projeta a imagem sobre a tela inteira com opacidade baixa, para alinhar
 * pixel-art e fachadas. Registrado como primeiro elemento do HUD, entao fica atras da mira,
 * hotbar e chat.
 */
public final class GhostOverlayElement implements HudElement {
	private final ImageLibrary library;

	public GhostOverlayElement(ImageLibrary library) {
		this.library = library;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		BuildAidConfig config = BuildAidConfig.get();
		if (!config.ghost.enabled || config.activeImageId == null) {
			return;
		}

		ImageLibrary.Loaded image = library.get(config.activeImageId);
		if (image == null) {
			return;
		}

		var window = Minecraft.getInstance().getWindow();
		RefRenderer.drawFullscreen(graphics, window.getGuiScaledWidth(), window.getGuiScaledHeight(),
				image, config.ghost.opacity);
	}
}
