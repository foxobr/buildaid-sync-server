package com.foxo.buildaid.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * Emissao de geometria no mundo, compartilhada pelo holograma e pelas formas.
 *
 * <p><b>Sobre coordenadas:</b> as posicoes ja chegam aqui <i>relativas a camera</i>. O
 * deslocamento e assado nos vertices em vez de empilhado na {@link PoseStack} de proposito: o
 * {@code submitCustomGeometry} guarda o trabalho para executar depois, e depender de um
 * push/pop feito agora seria apostar em quando a matriz e lida.
 */
public final class WorldGeometry {
	/**
	 * Luz cheia (15 de bloco + 15 de ceu, empacotados). Guias nao devem escurecer de noite ou
	 * dentro de uma caverna -- eles nao sao parte do mundo.
	 */
	public static final int FULL_BRIGHT = 0xF000F0;

	private WorldGeometry() {
	}

	/**
	 * Emite um quad com textura duas vezes, com sentido de giro oposto, para ficar visivel dos
	 * dois lados. Sem isso o holograma sumiria ao passar para tras dele.
	 *
	 * <p>Cantos na ordem: inferior-esquerdo, superior-esquerdo, superior-direito,
	 * inferior-direito. Cada canto mantem sua UV nas duas passadas, entao o verso mostra a
	 * imagem espelhada -- que e como uma folha se comporta de verdade.
	 */
	public static void texturedQuadBothSides(PoseStack.Pose pose, VertexConsumer consumer,
			Vector3f bottomLeft, Vector3f topLeft, Vector3f topRight, Vector3f bottomRight,
			float nx, float ny, float nz, int argb) {
		texturedVertex(pose, consumer, bottomLeft, 0.0f, 1.0f, nx, ny, nz, argb);
		texturedVertex(pose, consumer, topLeft, 0.0f, 0.0f, nx, ny, nz, argb);
		texturedVertex(pose, consumer, topRight, 1.0f, 0.0f, nx, ny, nz, argb);
		texturedVertex(pose, consumer, bottomRight, 1.0f, 1.0f, nx, ny, nz, argb);

		texturedVertex(pose, consumer, bottomRight, 1.0f, 1.0f, -nx, -ny, -nz, argb);
		texturedVertex(pose, consumer, topRight, 1.0f, 0.0f, -nx, -ny, -nz, argb);
		texturedVertex(pose, consumer, topLeft, 0.0f, 0.0f, -nx, -ny, -nz, argb);
		texturedVertex(pose, consumer, bottomLeft, 0.0f, 1.0f, -nx, -ny, -nz, argb);
	}

	private static void texturedVertex(PoseStack.Pose pose, VertexConsumer consumer, Vector3f position,
			float u, float v, float nx, float ny, float nz, int argb) {
		consumer.addVertex(pose, position.x, position.y, position.z)
				.setColor(argb)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT)
				.setNormal(pose, nx, ny, nz);
	}

	/** Quad sem textura, so posicao e cor -- formato do {@code debugFilledBox}. */
	public static void coloredQuad(PoseStack.Pose pose, VertexConsumer consumer,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4,
			int argb) {
		consumer.addVertex(pose, x1, y1, z1).setColor(argb);
		consumer.addVertex(pose, x2, y2, z2).setColor(argb);
		consumer.addVertex(pose, x3, y3, z3).setColor(argb);
		consumer.addVertex(pose, x4, y4, z4).setColor(argb);
	}

	/** Segmento de linha -- formato do {@code lines}, que exige normal. */
	public static void line(PoseStack.Pose pose, VertexConsumer consumer,
			float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length == 0.0f) {
			return;
		}
		dx /= length;
		dy /= length;
		dz /= length;

		consumer.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, dx, dy, dz);
		consumer.addVertex(pose, x2, y2, z2).setColor(argb).setNormal(pose, dx, dy, dz);
	}

	/** ARGB branco com a opacidade pedida -- multiplica a textura sem alterar as cores. */
	public static int tint(float opacity) {
		int alpha = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
		return (alpha << 24) | 0x00FFFFFF;
	}
}
