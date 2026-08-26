package com.foxo.buildaid.shape;

import com.foxo.buildaid.BuildAid;
import com.foxo.buildaid.Feedback;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.world.WorldGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Guias de forma geometrica desenhados no mundo.
 *
 * <p>Cada forma tem sua malha, guardada num cache <b>por conjunto de parametros</b> -- duas formas
 * iguais compartilham a mesma malha de graca. A construcao acontece fora da render thread e a
 * malha antiga continua sendo desenhada enquanto a nova nao fica pronta, entao mexer num slider
 * nao trava nem pisca.
 *
 * <p>Ha um <b>teto global de faces</b>: cinco esferas ocas de raio 20 passam de 75 mil faces, e sem
 * limite da para pedir algo que derruba o jogo. Passando do teto, as formas excedentes deixam de
 * ser desenhadas e o jogador e avisado, em vez de o FPS morrer sem explicacao.
 *
 * <p>As coordenadas guardadas sao o <b>centro da base</b>: e o que faz sentido ao apontar para o
 * chao e pedir "esfera aqui".
 */
public final class ShapeGuide {
	/** Teto somado de faces desenhadas por frame, entre todas as formas visiveis. */
	public static final int MAX_TOTAL_FACES = 400_000;
	private static final int MAX_CACHED_MESHES = 16;

	private static final Map<String, ShapeGenerator.Mesh> MESHES = new ConcurrentHashMap<>();
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

	/**
	 * Gerar fora da render thread e o que evita travar o jogo: medido, uma esfera oca de raio 20
	 * leva ~240 ms para virar malha na primeira vez.
	 */
	private static final Executor MESH_POOL = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BuildAid-ShapeMesh");
		t.setDaemon(true);
		return t;
	});

	private static boolean warnedBudget;

	private ShapeGuide() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(ShapeGuide::collect);
	}

	// ---------------------------------------------------------------- cache

	private static String key(BuildAidConfig.Shape s) {
		// layerMode/activeLayer SAO de proposito DEIXADOS DE FORA: eles so filtram o desenho
		// (em collect), nunca a malha. Se entrassem na chave, arrastar o slider de camada
		// regeraria a malha inteira a cada tick -- e uma esfera oca de raio 20 leva ~240 ms.
		return s.type + "|" + s.width + "|" + s.height + "|" + s.depth + "|" + s.hollow
				+ "|" + s.thickness + "|" + s.rotation + "|" + s.pitch + "|" + s.fillColor
				+ "|" + s.colorPreset
				+ "|" + s.wireframe;
	}

	/** Malha pronta, ou {@code null} enquanto ela e construida em segundo plano. */
	public static ShapeGenerator.Mesh meshFor(BuildAidConfig.Shape config) {
		String cacheKey = key(config);
		ShapeGenerator.Mesh cached = MESHES.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		if (PENDING.add(cacheKey)) {
			BuildAidConfig.Shape snapshot = config.copy();
			MESH_POOL.execute(() -> {
				try {
					ShapeGenerator.Mesh generated = ShapeGenerator.build(snapshot);
					MESHES.put(cacheKey, generated);
				} finally {
					PENDING.remove(cacheKey);
				}
			});
		}
		return null;
	}

	public static int blockCount(BuildAidConfig.Shape config) {
		ShapeGenerator.Mesh mesh = MESHES.get(key(config));
		return mesh == null ? 0 : mesh.blockCount();
	}

	public static int faceCount(BuildAidConfig.Shape config) {
		ShapeGenerator.Mesh mesh = MESHES.get(key(config));
		return mesh == null ? 0 : mesh.faceCount();
	}

	/** Contagem de blocos por altura, para a lista de materiais. Vazio se ainda nao pronta. */
	public static int[] blocksPerLayer(BuildAidConfig.Shape config) {
		ShapeGenerator.Mesh mesh = MESHES.get(key(config));
		return mesh == null ? new int[0] : mesh.blocksPerLayer();
	}

	/**
	 * Se a face na altura {@code layer} deve ser desenhada, dado o modo de fatia.
	 *
	 * <p>Modos (ver {@code layerModeName} no menu): 0 = todas as camadas; 1 = so a camada
	 * ativa; 2 = da base ate a camada ativa (inclusive).
	 */
	private static boolean layerVisible(int mode, int layer, int activeLayer) {
		return switch (mode) {
			case 1 -> layer == activeLayer;
			case 2 -> layer <= activeLayer;
			default -> true;
		};
	}

	/** Esquece tudo (usado quando a config muda por fora). */
	public static void invalidate() {
		MESHES.clear();
	}

	/** Solta as malhas que nenhuma forma da config usa mais. */
	private static void evictUnused() {
		if (MESHES.size() <= MAX_CACHED_MESHES) {
			return;
		}
		Set<String> inUse = new HashSet<>();
		for (BuildAidConfig.Shape s : BuildAidConfig.get().shapes) {
			inUse.add(key(s));
		}
		MESHES.keySet().removeIf(k -> !inUse.contains(k));
	}

	// ---------------------------------------------------------------- colocacao

	/** Cria uma forma nova no bloco mirado. */
	public static void placeNewAtCrosshair(Minecraft client) {
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			Feedback.error("buildaid.msg.shape_need_block");
			return;
		}

		BuildAidConfig config = BuildAidConfig.get();
		BlockPos pos = hit.getBlockPos().relative(hit.getDirection());

		BuildAidConfig.Shape shape = config.addShape();
		shape.x = pos.getX();
		shape.y = pos.getY();
		shape.z = pos.getZ();
		shape.placed = true;
		config.save();

		Feedback.info("buildaid.msg.shape_placed", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
	}

	/** Move uma forma existente para o bloco mirado. */
	public static boolean moveToCrosshair(Minecraft client, BuildAidConfig.Shape shape) {
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
		shape.x = pos.getX();
		shape.y = pos.getY();
		shape.z = pos.getZ();
		shape.placed = true;
		return true;
	}

	// ---------------------------------------------------------------- desenho

	private static void collect(LevelRenderContext context) {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		int budget = MAX_TOTAL_FACES;
		boolean exceeded = false;

		for (BuildAidConfig.Shape config : BuildAidConfig.get().shapes) {
			if (!config.enabled || !config.placed) {
				continue;
			}

			ShapeGenerator.Mesh mesh = meshFor(config);
			if (mesh == null || mesh.isEmpty()) {
				continue;
			}

			if (mesh.faceCount() > budget) {
				exceeded = true;
				continue;
			}
			budget -= mesh.faceCount();

			// A malha nasce no canto minimo da caixa varrida; aqui ela e levada para o mundo,
			// centrada em X/Z sobre o bloco escolhido, com a base no Y escolhido.
			float offsetX = (float) (config.x + 0.5 - mesh.sizeX() / 2.0 - camera.x);
			float offsetY = (float) (config.y - camera.y);
			float offsetZ = (float) (config.z + 0.5 - mesh.sizeZ() / 2.0 - camera.z);
			boolean isWireframe = config.wireframe;
			int mode = config.layerMode;
			int active = config.activeLayer;
			int[] fl = mesh.faceLayer();
			float[] p = mesh.positions();
			int[] colors = mesh.colors();

			context.submitNodeCollector().submitCustomGeometry(
					context.poseStack(),
					RenderTypes.debugFilledBox(),
					(pose, consumer) -> {
						for (int face = 0; face < mesh.faceCount(); face++) {
							if (!layerVisible(mode, fl[face], active)) {
								continue;
							}
							int o = face * 12;
							int faceColor = isWireframe
									? (0x12000000 | (colors[face] & 0x00FFFFFF))
									: colors[face];
							WorldGeometry.coloredQuad(pose, consumer,
									p[o] + offsetX, p[o + 1] + offsetY, p[o + 2] + offsetZ,
									p[o + 3] + offsetX, p[o + 4] + offsetY, p[o + 5] + offsetZ,
									p[o + 6] + offsetX, p[o + 7] + offsetY, p[o + 8] + offsetZ,
									p[o + 9] + offsetX, p[o + 10] + offsetY, p[o + 11] + offsetZ,
									faceColor);
						}
					});
		}

		if (exceeded && !warnedBudget) {
			warnedBudget = true;
			Feedback.error("buildaid.msg.shape_budget");
		} else if (!exceeded) {
			warnedBudget = false;
		}

		evictUnused();
	}
}
