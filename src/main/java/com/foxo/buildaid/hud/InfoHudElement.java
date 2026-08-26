package com.foxo.buildaid.hud;

import com.foxo.buildaid.build.AreaSelection;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.screen.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD compacto com as informacoes que mais importam enquanto se constroi.
 * Suporta ancoragem nos 4 cantos, temas de cores e estilos de fundo.
 */
public final class InfoHudElement implements HudElement {
	private static final int SELECTION_COLOR = 0xFFECBE5A;
	private static final int DANGER_COLOR = 0xFFFF5555;
	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 4;

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

		List<HudLine> lines = buildLines(client, config);
		if (lines.isEmpty()) {
			return;
		}

		int widest = 0;
		for (HudLine line : lines) {
			widest = Math.max(widest, client.font.width(line.text()));
		}

		int boxWidth = widest + PADDING * 2;
		int boxHeight = lines.size() * LINE_HEIGHT + PADDING * 2;

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();

		int x = switch (config.corner) {
			case 1, 3 -> guiWidth - boxWidth - 4;
			default -> 4;
		};

		int y = switch (config.corner) {
			case 2, 3 -> guiHeight - boxHeight - 4;
			default -> 4;
		};

		// 1. Desenho do fundo
		renderBackground(graphics, x, y, boxWidth, boxHeight, config.bgStyle);

		int primaryColor = resolveColor(config.colorTheme);

		// 2. Desenho das linhas de texto
		for (int i = 0; i < lines.size(); i++) {
			HudLine line = lines.get(i);
			int color = line.overrideColor() != 0 ? line.overrideColor() : primaryColor;
			boolean shadow = config.bgStyle == 1; // Sombra vanilla ativa quando sem caixa
			graphics.text(client.font, line.text(), x + PADDING, y + PADDING + i * LINE_HEIGHT, color, shadow);
		}
	}

	private static void renderBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int style) {
		switch (style) {
			case 0 -> { // Glassmorphism
				Theme.roundedRect(graphics, x, y, width, height, 4, 0x88121620);
				Theme.roundedOutline(graphics, x, y, width, height, 4, 0x554A9EFF);
			}
			case 1 -> { // Sombra Vanilla (sem caixa)
			}
			case 2 -> { // Alto Contraste
				graphics.fill(x, y, x + width, y + height, 0xE8080C14);
			}
		}
	}

	private static int resolveColor(int theme) {
		return switch (theme) {
			case 1 -> 0xFFF1C40F; // Ouro
			case 2 -> 0xFF2ECC71; // Esmeralda
			case 3 -> 0xFFF0F4FA; // Branco
			case 4 -> 0xFF9B59B6; // Roxo
			case 5 -> 0xFFE67E22; // Laranja
			default -> 0xFF4A9EFF; // Ciano
		};
	}

	private record HudLine(Component text, int overrideColor) {
	}

	private static boolean hasSelectionLine() {
		return AreaSelection.isModeEnabled() && AreaSelection.hasSelection();
	}

	private static List<HudLine> buildLines(Minecraft client, BuildAidConfig.InfoHud config) {
		List<HudLine> lines = new ArrayList<>();
		BlockPos pos = client.player.blockPosition();

		if (config.showCoords) {
			lines.add(new HudLine(Component.translatable("buildaid.hud.xyz",
					String.format("%.1f", client.player.getX()),
					String.format("%.1f", client.player.getY()),
					String.format("%.1f", client.player.getZ())), 0));
		}

		if (config.showDirection) {
			lines.add(new HudLine(Component.translatable("buildaid.hud.facing", describe(client.player.getDirection())), 0));
		}

		if (config.showAngles) {
			float yaw = (client.player.getYRot() % 360.0f + 360.0f) % 360.0f;
			float pitch = client.player.getXRot();
			lines.add(new HudLine(Component.translatable("buildaid.hud.angles",
					String.format("%.1f°", yaw),
					String.format("%.1f°", pitch)), 0));
		}

		if (config.showTargetDistance && client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			double dist = client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(hit.getBlockPos()));
			lines.add(new HudLine(Component.translatable("buildaid.hud.distance",
					String.format("%.1f", dist),
					hit.getBlockPos().getX() + ", " + hit.getBlockPos().getY() + ", " + hit.getBlockPos().getZ()), 0));
		}

		if (config.showTargetBlock && client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			var state = client.level.getBlockState(hit.getBlockPos());
			lines.add(new HudLine(Component.translatable("buildaid.hud.target_block", state.getBlock().getName()), 0));
		}

		if (config.showHeldCount && client.player != null) {
			ItemStack stack = client.player.getMainHandItem();
			if (!stack.isEmpty()) {
				int total = 0;
				for (int s = 0; s < client.player.getInventory().getContainerSize(); s++) {
					ItemStack is = client.player.getInventory().getItem(s);
					if (is.getItem() == stack.getItem()) {
						total += is.getCount();
					}
				}
				// Usa o tamanho maximo real da pilha (alguns itens empilham so ate 16)
				int stackMax = Math.max(1, stack.getMaxStackSize());
				int packs = total / stackMax;
				int rem = total % stackMax;
				String summary = packs > 0 && rem > 0 ? (total + " (" + packs + "p + " + rem + ")")
						: packs > 0 ? (total + " (" + packs + "p)") : String.valueOf(total);
				lines.add(new HudLine(Component.translatable("buildaid.hud.held_count", stack.getHoverName(), summary), 0));
			}
		}

		if (config.showDurability && client.player != null) {
			ItemStack stack = client.player.getMainHandItem();
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				int max = stack.getMaxDamage();
				int rem = max - stack.getDamageValue();
				int pct = Math.round((float) rem / max * 100);
				lines.add(new HudLine(Component.translatable("buildaid.hud.durability", stack.getHoverName(), rem, max, pct), 0));
			}
		}

		if (config.showBiome) {
			Component biome = client.level.getBiome(pos)
					.unwrapKey()
					.map(key -> (Component) Component.literal(key.identifier().getPath()))
					.orElse(Component.translatable("buildaid.hud.biome_unknown"));
			lines.add(new HudLine(Component.translatable("buildaid.hud.biome", biome), 0));
		}

		if (config.showLight) {
			int blockLight = client.level.getLightEngine()
					.getLayerListener(LightLayer.BLOCK)
					.getLightValue(pos);
			int skyLight = client.level.getLightEngine()
					.getLayerListener(LightLayer.SKY)
					.getLightValue(pos);
			if (blockLight == 0) {
				lines.add(new HudLine(Component.translatable("buildaid.hud.light_danger", skyLight), DANGER_COLOR));
			} else {
				lines.add(new HudLine(Component.translatable("buildaid.hud.light", blockLight, skyLight), 0));
			}
		}

		if (config.showTime) {
			long time = Math.floorMod(client.level.getLevelData().getGameTime(), 24000L);
			int hour = (int) ((time / 1000 + 6) % 24);
			int minute = (int) ((time % 1000) * 60 / 1000);
			lines.add(new HudLine(Component.translatable("buildaid.hud.time", String.format("%02d:%02d", hour, minute)), 0));
		}

		if (config.showFps) {
			lines.add(new HudLine(Component.translatable("buildaid.hud.fps", client.getFps()), 0));
		}

		if (config.showSelection && hasSelectionLine()) {
			lines.add(new HudLine(AreaSelection.dimensionsText(), SELECTION_COLOR));
		}

		if (com.foxo.buildaid.build.TapeMeasure.isActive()) {
			BlockPos start = com.foxo.buildaid.build.TapeMeasure.startPos();
			BlockPos end = com.foxo.buildaid.build.TapeMeasure.endPos();
			if (end == null && client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
					&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				end = hit.getBlockPos();
			}
			if (end != null) {
				int dx = Math.abs(end.getX() - start.getX()) + 1;
				int dy = Math.abs(end.getY() - start.getY()) + 1;
				int dz = Math.abs(end.getZ() - start.getZ()) + 1;
				double dist = Math.sqrt(
						Math.pow(end.getX() - start.getX(), 2) +
						Math.pow(end.getY() - start.getY(), 2) +
						Math.pow(end.getZ() - start.getZ(), 2));
				lines.add(new HudLine(Component.translatable("buildaid.hud.tape_measure",
						String.format("%.1f", dist), dx, dy, dz), 0xFF00FFCC));
			}
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
