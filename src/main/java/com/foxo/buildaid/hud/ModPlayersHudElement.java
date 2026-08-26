package com.foxo.buildaid.hud;

import com.foxo.buildaid.net.ModDetection;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Fatia 3 da deteccao de mod: HUD discreta com a contagem de jogadores com o BuildAid.
 *
 * <p>Aparece so quando ha ao menos um jogador com o mod (alem do proprio) e some
 * em servidor vanilla, onde a lista fica vazia. Fica no canto superior direito,
 * abaixo da area da hotbar/mira, com fundo escuro translucido no estilo do mod.
 */
public final class ModPlayersHudElement implements HudElement {

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int total = ModDetection.count();
        // 0 = vanilla/sem resposta; 1 = so o proprio; nada a mostrar nesses casos.
        if (total <= 1) {
            return;
        }

        Font font = client.font;
        // Prefixo de marca em ciano + texto traduzido (lang pt/en com paridade).
        Component chip = Component.literal("\u00A7b[BuildAid]\u00A7f ")
                .append(Component.translatable("buildaid.hud.mod_players", total));
        int textWidth = font.width(chip);

        int x = graphics.guiWidth() - textWidth - 12;
        int y = 6;

        graphics.fill(x - 5, y - 3, x + textWidth + 5, y + 11, 0x9010141B);
        graphics.text(font, chip, x, y, 0xFFFFFF, true);
    }
}
