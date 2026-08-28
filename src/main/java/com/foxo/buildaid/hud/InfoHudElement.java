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
 *
 * <p>Quando {@code groupSections} esta ligado, as linhas sao agrupadas em tres
 * secões rotuladas -- Jogador, Mundo e Construção -- separadas por divisorias,
 * em vez de um bloco unico de texto. Isso evita a "parede de linhas" quando
 * muitos modulos estao ativos.
 */
public final class InfoHudElement implements HudElement {
	private static final int SELECTION_COLOR = 0xFFECBE5A;
	private static final int DANGER_COLOR = 0xFFFF5555;
	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 4;
	private static final int GAP = 2;

	// === PERF FIX #1: Cache de linhas HUD por tick ===
	// buildLines() aloca string.format + Component transalvel por frame.
	// Cache valido por tick inteiro (HUD nao muda mid-tick).
	private static List<HudLine> cachedLines = null;
	private static long cachedTick = -1;
	private static int cachedActiveModules = 0;

	// Identificadores de secão (usados so no modo agrupado).
	private static final int SECTION_PLAYER = 0;
	private static final int SECTION_WORLD = 1;
	private static final int SECTION_BUILD = 2;

	// Estado do ultimo desenho, usado por outros HUDs para nao se sobreporem
	// (ex.: o chip de jogadores com o mod, que tambem fica no topo direito).
	private static int lastBoxY = 0;
	private static int lastBoxH = 0;
	private static int lastCorner = 0;
	private static boolean lastVisible = false;

	/** Fundo (y + altura) da caixa quando este HUD esta no topo direito, ou -1. */
	public static int topRightBottom() {
		return (lastVisible && lastCorner == 1) ? (lastBoxY + lastBoxH) : -1;
	}

	/** Fundo (y + altura) da caixa quando este HUD esta no topo esquerdo, ou -1. */
	public static int topLeftBottom() {
		return (lastVisible && lastCorner == 0) ? (lastBoxY + lastBoxH) : -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		BuildAidConfig.InfoHud config = BuildAidConfig.get().infoHud;
		if (!config.enabled) {
			lastVisible = false;
			cachedLines = null; // invalida cache
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			lastVisible = false;
			cachedLines = null;
			return;
		}

		// === PERF FIX #1: Cache de linhas ===
		// Só reconstroí as linhas se mudou algo (tick avançou ou módulos ativos alteraram)
		long currentTick = client.level.getLevelData().getGameTime();
		int activeModules = countActiveModules(config);
		List<HudLine> lines;
		if (cachedLines != null && cachedTick == currentTick && cachedActiveModules == activeModules) {
			lines = cachedLines;
		} else {
			lines = buildLines(client, config);
			cachedLines = lines;
			cachedTick = currentTick;
			cachedActiveModules = activeModules;
		}
		if (lines.isEmpty()) {
			lastVisible = false;
			return;
		}

		boolean grouped = config.groupSections;
		List<DrawEntry> entries = buildDrawEntries(lines, grouped);

		int widest = 0;
		for (DrawEntry e : entries) {
			widest = Math.max(widest, client.font.width(e.text()));
		}

		int boxWidth = widest + PADDING * 2;
		int boxHeight = entries.size() * LINE_HEIGHT + (grouped ? (entries.size() - 1) * GAP : 0) + PADDING * 2 + 2;
		// === UI FIX #5: Grouped HUD box height undercount ===
		// Add space for section dividers drawn below headers
		if (grouped) {
			int headerCount = 0;
			for (DrawEntry e : entries) {
				if (e.type() == DrawEntry.TYPE_HEADER) headerCount++;
			}
			boxHeight += headerCount * (GAP + 1); // +1 for divider line height
		}

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

		// Guarda o estado para outros HUDs (ex.: chip de jogadores com mod) desviarem.
		lastBoxY = y;
		lastBoxH = boxHeight;
		lastCorner = config.corner;
		lastVisible = true;

		// 1. Desenho do fundo
		renderBackground(graphics, x, y, boxWidth, boxHeight, config.bgStyle);

		int primaryColor = resolveColor(config.colorTheme);

		// 2. Desenho das entradas (linhas e cabecalhos de secão)
		int cy = y + PADDING;
		for (int i = 0; i < entries.size(); i++) {
			DrawEntry e = entries.get(i);
			if (e.type() == DrawEntry.TYPE_HEADER) {
				// Cabecalho de secão: tom dim, com uma divisoria fina abaixo.
				graphics.text(client.font, e.text(), x + PADDING, cy, Theme.TEXT_DIM, false);
				Theme.divider(graphics, x + PADDING, cy + LINE_HEIGHT + 1, boxWidth - PADDING * 2);
			} else {
				int color = e.overrideColor() != 0 ? e.overrideColor() : primaryColor;
				boolean shadow = config.bgStyle == 1; // Sombra vanilla ativa quando sem caixa
				graphics.text(client.font, e.text(), x + PADDING, cy, color, shadow);
			}
			cy += LINE_HEIGHT + (grouped ? GAP : 0);
		}
	}

	/** Converte as linhas em entradas de desenho, inserindo cabecalhos de secão quando agrupado. */
	private static List<DrawEntry> buildDrawEntries(List<HudLine> lines, boolean grouped) {
		List<DrawEntry> out = new ArrayList<>();
		if (!grouped) {
			for (HudLine l : lines) {
				out.add(new DrawEntry(DrawEntry.TYPE_LINE, l.text(), l.overrideColor()));
			}
			return out;
		}

		int lastSection = -1;
		for (HudLine l : lines) {
			if (l.section() != lastSection) {
				out.add(new DrawEntry(DrawEntry.TYPE_HEADER, sectionLabel(l.section()), 0));
				lastSection = l.section();
			}
			out.add(new DrawEntry(DrawEntry.TYPE_LINE, l.text(), l.overrideColor()));
		}
		return out;
	}

	private static Component sectionLabel(int section) {
		return switch (section) {
			case SECTION_WORLD -> Component.translatable("buildaid.hud.section_world");
			case SECTION_BUILD -> Component.translatable("buildaid.hud.section_build");
			default -> Component.translatable("buildaid.hud.section_player");
		};
	}

	private static void renderBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int style) {
		switch (style) {
			case 0 -> { // Glassmorphism -- contorno na cor do tema escolhido
				Theme.hudChip(graphics, x, y, width, height, Theme.withAlpha(primaryColorForBg(), 0.333f));
			}
			case 1 -> { // Sombra Vanilla (sem caixa)
			}
			case 2 -> { // Alto Contraste
				graphics.fill(x, y, x + width, y + height, 0xE8080C14);
			}
		}
	}

	/** Cor de contorno do fundo glassmorphism: combina com resolveColor(colorTheme). */
	private static int primaryColorForBg() {
		// Espelha resolveColor() sem depender do estado da caixa.
		return resolveColor(BuildAidConfig.get().infoHud.colorTheme);
	}

	private static int resolveColor(int theme) {
		return switch (theme) {
			case 1 -> 0xFFF1C40F; // Ouro
			case 2 -> 0xFF2ECC71; // Esmeralda
			case 3 -> 0xFFF0F4FA; // Branco
			case 4 -> 0xFFC084FC; // Roxo (alto contraste per review)
			case 5 -> 0xFFE67E22; // Laranja
			default -> 0xFF4A9EFF; // Ciano
		};
	}

	private record HudLine(Component text, int overrideColor, int section) {
	}

	private record DrawEntry(int type, Component text, int overrideColor) {
		static final int TYPE_LINE = 0;
		static final int TYPE_HEADER = 1;
	}

	private static boolean hasSelectionLine() {
		return AreaSelection.isModeEnabled() && AreaSelection.hasSelection();
	}

	/** Conta módulos HUD ativos — usado como cache key (evita rebuild se nada mudou). */
	private static int countActiveModules(BuildAidConfig.InfoHud config) {
		int count = 0;
		if (config.showCoords) count++;
		if (config.showDirection) count++;
		if (config.showAngles) count++;
		if (config.showTargetDistance || config.showTargetBlock) count++;
		if (config.showHeldCount) count++;
		if (config.showDurability) count++;
		if (config.showBiome) count++;
		if (config.showLight) count++;
		if (config.showTime) count++;
		if (config.showFps) count++;
		if (hasSelectionLine()) count++;
		if (com.foxo.buildaid.build.TapeMeasure.isActive()) count++;
		return count;
	}

	private static List<HudLine> buildLines(Minecraft client, BuildAidConfig.InfoHud config) {
		List<HudLine> lines = new ArrayList<>();
		BlockPos pos = client.player.blockPosition();

		if (config.showCoords) {
			lines.add(new HudLine(Component.translatable("buildaid.hud.xyz",
							String.format("%.1f", client.player.getX()),
							String.format("%.1f", client.player.getY()),
							String.format("%.1f", client.player.getZ())), 0, SECTION_PLAYER));
		}

		if (config.showDirection) {
			lines.add(new HudLine(Component.translatable("buildaid.hud.facing", describe(client.player.getDirection())), 0, SECTION_PLAYER));
		}

		if (config.showAngles) {
			float yaw = (client.player.getYRot() % 360.0f + 360.0f) % 360.0f;
			float pitch = client.player.getXRot();
			lines.add(new HudLine(Component.translatable("buildaid.hud.angles",
							String.format("%.1f°", yaw),
							String.format("%.1f°", pitch)), 0, SECTION_PLAYER));
		}

		if (config.showTargetDistance && client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			double dist = client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(hit.getBlockPos()));
			lines.add(new HudLine(Component.translatable("buildaid.hud.distance",
							String.format("%.1f", dist),
							hit.getBlockPos().getX() + ", " + hit.getBlockPos().getY() + ", " + hit.getBlockPos().getZ()), 0, SECTION_WORLD));
		}

		if (config.showTargetBlock && client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			var state = client.level.getBlockState(hit.getBlockPos());
			lines.add(new HudLine(Component.translatable("buildaid.hud.target_block", state.getBlock().getName()), 0, SECTION_WORLD));
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
				String packUnit = Component.translatable("buildaid.hud.pack_unit").getString();
				String summary = packs > 0 && rem > 0 ? (total + " (" + packs + packUnit + " + " + rem + ")")
						: packs > 0 ? (total + " (" + packs + packUnit + ")") : String.valueOf(total);
				// Nome do item pode ser enorme (encantado/customizado): limita a largura da linha.
				String name = stack.getHoverName().getString();
				int maxItem = Math.max(40, 220 - client.font.width(Component.literal(summary)));
				if (client.font.width(Component.literal(name)) > maxItem) {
					name = client.font.plainSubstrByWidth(name, maxItem - 6) + "...";
				}
				lines.add(new HudLine(Component.translatable("buildaid.hud.held_count", name, summary), 0, SECTION_PLAYER));
			}
		}

		if (config.showDurability && client.player != null) {
			ItemStack stack = client.player.getMainHandItem();
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				int max = stack.getMaxDamage();
				int rem = max - stack.getDamageValue();
				int pct = Math.round((float) rem / max * 100);
				// Nome do item pode ser grande: trunca para a linha nao estourar a largura.
				String name = stack.getHoverName().getString();
				int maxItem = Math.max(40, 200 - client.font.width(Component.translatable("buildaid.hud.durability", "", rem, max, pct)));
				if (client.font.width(Component.literal(name)) > maxItem) {
					name = client.font.plainSubstrByWidth(name, maxItem - 6) + "...";
				}
				lines.add(new HudLine(Component.translatable("buildaid.hud.durability", name, rem, max, pct), 0, SECTION_PLAYER));
			}
		}

		if (config.showBiome) {
			Component biome = client.level.getBiome(pos)
					.unwrapKey()
					.map(key -> (Component) Component.literal(key.identifier().getPath()))
					.orElse(Component.translatable("buildaid.hud.biome_unknown"));
			lines.add(new HudLine(Component.translatable("buildaid.hud.biome", biome), 0, SECTION_WORLD));
		}

		if (config.showLight) {
			int blockLight = client.level.getLightEngine()
					.getLayerListener(LightLayer.BLOCK)
					.getLightValue(pos);
			int skyLight = client.level.getLightEngine()
					.getLayerListener(LightLayer.SKY)
					.getLightValue(pos);
			if (blockLight == 0) {
				lines.add(new HudLine(Component.translatable("buildaid.hud.light_danger", skyLight), DANGER_COLOR, SECTION_WORLD));
			} else {
				lines.add(new HudLine(Component.translatable("buildaid.hud.light", blockLight, skyLight), 0, SECTION_WORLD));
			}
		}

		if (config.showTime) {
			long time = Math.floorMod(client.level.getLevelData().getGameTime(), 24000L);
			int hour = (int) ((time / 1000 + 6) % 24);
			int minute = (int) ((time % 1000) * 60 / 1000);
			lines.add(new HudLine(Component.translatable("buildaid.hud.time", String.format("%02d:%02d", hour, minute)), 0, SECTION_WORLD));
		}

		if (config.showFps) {
			lines.add(new HudLine(Component.translatable("buildaid.hud.fps", client.getFps()), 0, SECTION_WORLD));
		}

		if (config.showSelection && hasSelectionLine()) {
			lines.add(new HudLine(AreaSelection.dimensionsText(), SELECTION_COLOR, SECTION_BUILD));
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
								String.format("%.1f", dist), dx, dy, dz), 0xFF00FFCC, SECTION_BUILD));
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
