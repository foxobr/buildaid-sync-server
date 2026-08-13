package com.foxo.buildaid.hud;

import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.image.ImageLibrary;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Os paineis de referencia flutuantes, desenhados logo antes do chat.
 *
 * <p>Percorre a lista inteira: da para ter varios na tela ao mesmo tempo, cada um com sua imagem,
 * posicao, tamanho e opacidade. Sao desenhados na ordem da lista, entao o ultimo fica por cima.
 */
public final class RefPanelElement implements HudElement {
	private final ImageLibrary library;

	public RefPanelElement(ImageLibrary library) {
		this.library = library;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		for (BuildAidConfig.Panel panel : BuildAidConfig.get().panels) {
			if (!panel.visible || panel.imageId == null) {
				continue;
			}

			// Devolve null enquanto carrega -- o painel simplesmente aparece um frame depois,
			// em vez de travar o jogo esperando o disco.
			ImageLibrary.Loaded image = library.get(panel.imageId);
			if (image == null) {
				continue;
			}

			RefRenderer.drawPanel(graphics, panel, image, -1.0f);
		}
	}
}
