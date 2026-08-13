package com.foxo.buildaid.world;

import com.foxo.buildaid.BuildAidClient;
import com.foxo.buildaid.Feedback;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.image.ImageLibrary;
import com.foxo.buildaid.image.RefImage;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Planos de imagem projetados dentro do mundo, medidos em blocos.
 *
 * <p>Diferente dos paineis, que vivem colados na tela, o holograma fica parado no lugar enquanto
 * voce anda em volta -- e o que permite usar a referencia como gabarito de uma parede inteira.
 * Da para ter varios ao mesmo tempo, cada um com sua imagem e orientacao.
 *
 * <p>Desenhado com {@code submitCustomGeometry} + {@link RenderTypes#entityTranslucent}, que e a
 * forma suportada de colocar geometria com textura no mundo no 26.2 (OpenGL cru nao e mais
 * permitido, ja que a versao saiu com backend Vulkan opcional).
 */
public final class ImageHologram {
	/** Norte, leste, sul, oeste e deitado no chao. */
	public static final int FACING_NORTH = 0;
	public static final int FACING_EAST = 1;
	public static final int FACING_SOUTH = 2;
	public static final int FACING_WEST = 3;
	public static final int FACING_FLAT = 4;
	public static final int FACING_COUNT = 5;

	private static ImageLibrary library;

	private ImageHologram() {
	}

	public static void register(ImageLibrary imageLibrary) {
		library = imageLibrary;
		LevelRenderEvents.COLLECT_SUBMITS.register(ImageHologram::collect);
	}

	// ---------------------------------------------------------------- colocacao

	/** Cria um holograma no bloco mirado, virado para o jogador. */
	public static void placeNewAtCrosshair(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			Feedback.error("buildaid.msg.hologram_need_block");
			return;
		}

		BuildAidConfig config = BuildAidConfig.get();
		BlockPos pos = hit.getBlockPos().relative(hit.getDirection());

		BuildAidConfig.Hologram hologram = config.addHologram(config.activeImageId);
		hologram.x = pos.getX();
		hologram.y = pos.getY();
		hologram.z = pos.getZ();
		hologram.facing = facingFromPlayer(client.player.getDirection());
		hologram.placed = true;
		applyAspect(hologram);
		config.save();

		Feedback.info("buildaid.msg.hologram_placed", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
	}

	/** Move um holograma existente para o bloco mirado. */
	public static boolean moveToCrosshair(Minecraft client, BuildAidConfig.Hologram hologram) {
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
		hologram.x = pos.getX();
		hologram.y = pos.getY();
		hologram.z = pos.getZ();
		hologram.placed = true;
		return true;
	}

	/**
	 * Recalcula a altura pela proporcao real da imagem.
	 *
	 * <p>Sem isto, largura 10 e altura 7 numa imagem 16:9 estica a referencia e nada avisa --
	 * que era exatamente o problema.
	 */
	public static void applyAspect(BuildAidConfig.Hologram hologram) {
		if (!hologram.keepAspect || hologram.imageId == null) {
			return;
		}
		RefImage image = BuildAidClient.store.byId(hologram.imageId).orElse(null);
		if (image == null || image.height() <= 0) {
			return;
		}
		float ratio = image.aspectRatio();
		if (ratio > 0) {
			hologram.heightBlocks = Math.clamp(Math.round(hologram.widthBlocks / ratio), 1, 256);
		}
	}

	/** O plano fica de frente para quem colocou, entao a imagem nasce legivel. */
	private static int facingFromPlayer(Direction playerFacing) {
		return switch (playerFacing) {
			case NORTH -> FACING_SOUTH;
			case SOUTH -> FACING_NORTH;
			case EAST -> FACING_WEST;
			case WEST -> FACING_EAST;
			default -> FACING_NORTH;
		};
	}

	// ---------------------------------------------------------------- desenho

	private static void collect(LevelRenderContext context) {
		if (library == null) {
			return;
		}

		Vec3 camera = context.levelState().cameraRenderState.pos;

		for (BuildAidConfig.Hologram hologram : BuildAidConfig.get().holograms) {
			if (!hologram.enabled || !hologram.placed || hologram.imageId == null) {
				continue;
			}

			ImageLibrary.Loaded image = library.get(hologram.imageId);
			if (image == null) {
				continue; // ainda carregando
			}

			Vector3f[] corners = corners(hologram, camera);
			Vector3f normal = normal(hologram.facing);
			int argb = WorldGeometry.tint(hologram.opacity);

			context.submitNodeCollector().submitCustomGeometry(
					context.poseStack(),
					RenderTypes.entityTranslucent(image.textureId()),
					(pose, consumer) -> WorldGeometry.texturedQuadBothSides(pose, consumer,
							corners[0], corners[1], corners[2], corners[3],
							normal.x, normal.y, normal.z, argb));
		}
	}

	/**
	 * Os quatro cantos ja em coordenadas relativas a camera, na ordem
	 * inferior-esquerdo, superior-esquerdo, superior-direito, inferior-direito.
	 */
	private static Vector3f[] corners(BuildAidConfig.Hologram h, Vec3 camera) {
		float x = (float) (h.x - camera.x);
		float y = (float) (h.y - camera.y);
		float z = (float) (h.z - camera.z);
		float w = h.widthBlocks;
		float t = h.heightBlocks;

		return switch (h.facing) {
			case FACING_EAST -> new Vector3f[] {
					new Vector3f(x, y, z), new Vector3f(x, y + t, z),
					new Vector3f(x, y + t, z + w), new Vector3f(x, y, z + w) };
			case FACING_SOUTH -> new Vector3f[] {
					new Vector3f(x + w, y, z), new Vector3f(x + w, y + t, z),
					new Vector3f(x, y + t, z), new Vector3f(x, y, z) };
			case FACING_WEST -> new Vector3f[] {
					new Vector3f(x, y, z + w), new Vector3f(x, y + t, z + w),
					new Vector3f(x, y + t, z), new Vector3f(x, y, z) };
			case FACING_FLAT -> new Vector3f[] {
					new Vector3f(x, y, z + t), new Vector3f(x, y, z),
					new Vector3f(x + w, y, z), new Vector3f(x + w, y, z + t) };
			default -> new Vector3f[] {
					new Vector3f(x, y, z), new Vector3f(x, y + t, z),
					new Vector3f(x + w, y + t, z), new Vector3f(x + w, y, z) };
		};
	}

	private static Vector3f normal(int facing) {
		return switch (facing) {
			case FACING_EAST -> new Vector3f(1.0f, 0.0f, 0.0f);
			case FACING_SOUTH -> new Vector3f(0.0f, 0.0f, 1.0f);
			case FACING_WEST -> new Vector3f(-1.0f, 0.0f, 0.0f);
			case FACING_FLAT -> new Vector3f(0.0f, 1.0f, 0.0f);
			default -> new Vector3f(0.0f, 0.0f, -1.0f);
		};
	}
}
