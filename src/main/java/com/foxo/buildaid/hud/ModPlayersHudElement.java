package com.foxo.buildaid.hud;

import com.foxo.buildaid.net.ModDetection;
import com.foxo.buildaid.screen.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

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
        // Prefixo de marca na cor de acento do tema global (respeita uiTheme) + texto traduzido.
        int accent = Theme.accent();
        Component brand = Component.literal("[BuildAid] ");
        Component rest = Component.translatable("buildaid.hud.mod_players", total);
        int brandW = font.width(brand);
        int textWidth = brandW + font.width(rest);

        int padX = Theme.PAD;       // padding horizontal dinamico via Theme
        int padY = Theme.PAD / 2;   // padding vertical: metade do horizontal
        int minBoxW = 80;           // largura minima para nao colapsar com texto curto
        int boxW = Math.max(minBoxW, textWidth + padX * 2);
        int boxH = Math.max(18, font.lineHeight + padY * 2 + 2);
        
        int x = graphics.guiWidth() - boxW - 4;
        
        int y = 6;
        int infoBottom = InfoHudElement.topRightBottom();
        if (infoBottom >= 0 && y < infoBottom) {
            y = infoBottom + Theme.PAD / 2; // espacamento dinamico em vez de 4 fixo
        }

        Theme.statusChipBg(graphics, x, y, boxW, boxH, accent, 0.5f);
        // Centraliza verticalmente usando lineHeight real
        int textY = y + (boxH - font.lineHeight) / 2;
        graphics.text(font, brand, x + padX, textY, accent, true);
        graphics.text(font, rest, x + padX + brandW, textY, 0xFFFFFF, true);
    }
}
