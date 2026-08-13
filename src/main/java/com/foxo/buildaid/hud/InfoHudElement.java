package com.foxo.buildaid.hud;

import com.foxo.buildaid.build.AreaSelection;
import com.foxo.buildaid.config.BuildAidConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD compacto com as informacoes que mais importam enquanto se constroi.
 * Tudo lido do estado do cliente -- nenhum mixin, nenhum pacote de rede.
 */
public final class InfoHudElement implements HudElement {
	private static final int TEXT_COLOR = 0xFFF0F4FA;
	private static final int SELECTION_COLOR = 0xFFECBE5A;
	private static final int BACKGROUND = 0x90000000;
	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 3;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		BuildAidConfig.InfoHud config = BuildAidConfig.get().infoHud;
		if (!config.enabled) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		List<Component> lines = buildLines(client, config);
		if (lines.isEmpty()) {
			return;
		}

		int widest = 0;
		for (Component line : lines) {
			widest = Math.max(widest, client.font.width(line));
		}

		int x = config.x;
		int y = config.y;
		graphics.fill(x, y, x + widest + PADDING * 2, y + lines.size() * LINE_HEIGHT + PADDING * 2, BACKGROUND);

		for (int i = 0; i < lines.size(); i++) {
			// A ultima linha (medidas da selecao) fica destacada em ambar.
			boolean isSelectionLine = config.showSelection && i == lines.size() - 1 && hasSelectionLine();
			graphics.text(client.font, lines.get(i), x + PADDING, y + PADDING + i * LINE_HEIGHT,
					isSelectionLine ? SELECTION_COLOR : TEXT_COLOR, false);
		}
	}

	private static boolean hasSelectionLine() {
		return AreaSelection.isModeEnabled() && AreaSelection.hasSelection();
	}

	private static List<Component> buildLines(Minecraft client, BuildAidConfig.InfoHud config) {
		List<Component> lines = new ArrayList<>();
		BlockPos pos = client.player.blockPosition();

		if (config.showCoords) {
			lines.add(Component.translatable("buildaid.hud.xyz",
					String.format("%.1f", client.player.getX()),
					String.format("%.1f", client.player.getY()),
					String.format("%.1f", client.player.getZ())));
		}

		if (config.showDirection) {
			lines.add(Component.translatable("buildaid.hud.facing", describe(client.player.getDirection())));
		}

		if (config.showBiome) {
			Component biome = client.level.getBiome(pos)
					.unwrapKey()
					.map(key -> (Component) Component.literal(key.identifier().getPath()))
					.orElse(Component.translatable("buildaid.hud.biome_unknown"));
			lines.add(Component.translatable("buildaid.hud.biome", biome));
		}

		if (config.showLight) {
			int blockLight = client.level.getLightEngine()
					.getLayerListener(LightLayer.BLOCK)
					.getLightValue(pos);
			int skyLight = client.level.getLightEngine()
					.getLayerListener(LightLayer.SKY)
					.getLightValue(pos);
			// Luz de bloco < 1 e onde monstro nasce -- o numero que importa ao construir.
			lines.add(Component.translatable("buildaid.hud.light", blockLight, skyLight));
		}

		if (config.showTime) {
			long time = Math.floorMod(client.level.getLevelData().getGameTime(), 24000L);
			int hour = (int) ((time / 1000 + 6) % 24);
			int minute = (int) ((time % 1000) * 60 / 1000);
			lines.add(Component.translatable("buildaid.hud.time", String.format("%02d:%02d", hour, minute)));
		}

		if (config.showFps) {
			lines.add(Component.translatable("buildaid.hud.fps", client.getFps()));
		}

		// So aparece com o modo selecao ligado -- fora dele a ferramenta nao existe.
		if (config.showSelection && hasSelectionLine()) {
			lines.add(AreaSelection.dimensionsText());
		}

		return lines;
	}

	private static Component describe(Direction direction) {
		String key = switch (direction) {
			case NORTH -> "buildaid.dir.north";
			case SOUTH -> "buildaid.dir.south";
			case WEST -> "buildaid.dir.west";
			case EAST -> "buildaid.dir.east";
			case UP -> "buildaid.dir.up";
			case DOWN -> "buildaid.dir.down";
		};
		return Component.translatable(key);
	}
}
