package com.foxo.buildaid.world;

import com.foxo.buildaid.Feedback;
import com.foxo.buildaid.Keys;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.config.GlobalUndo;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Move e gira hologramas e formas ja colocados.
 *
 * <p><b>Por que "com o olhar" e nao com o cursor:</b> durante o jogo o cursor fica capturado pela
 * camera. Alcas 3D arrastaveis com cursor livre exigiriam mixin para suprimir o giro da camera, e
 * e justamente a ausencia de mixin que faz este mod nao quebrar a cada atualizacao do Minecraft.
 * Entao o objeto acompanha a mira: na pratica voce arrasta com o mouse (o mouse move a mira, a
 * mira move o objeto), sem interceptar entrada nenhuma.
 *
 * <p>Com um alvo selecionado aparecem tres setas na origem dele. Mirando numa delas antes de
 * agarrar, o movimento fica travado naquele eixo -- o raio do olhar e projetado sobre a reta.
 */
public final class WorldManipulator {
	private static final int COLOR_X = 0xFFE05260;
	private static final int COLOR_Y = 0xFF5FD068;
	private static final int COLOR_Z = 0xFF4A9EFF;
	private static final int COLOR_TARGET = 0xFFFFD166;

	/** Comprimento das setas, em blocos. */
	private static final double HANDLE_LENGTH = 2.5;
	/** Distancia maxima entre o raio do olhar e a seta para considerar que ela foi mirada. */
	private static final double HANDLE_PICK_RADIUS = 0.6;
	private static final double SELECT_RANGE = 64.0;

	private enum Kind {
		HOLOGRAM, SHAPE
	}

	private static Kind kind;
	private static int index = -1;
	private static boolean grabbing;
	/** -1 = livre (segue a mira), 0/1/2 = travado em X/Y/Z. */
	private static int axis = -1;

	private WorldManipulator() {
	}

	// ---------------------------------------------------------------- alvo

	public static boolean hasTarget() {
		return position() != null;
	}

	/** Escolhe o alvo mais proximo da linha do olhar; repetindo, passa para o proximo. */
	public static void selectNext(Minecraft client) {
		if (client.player == null) {
			return;
		}

		BuildAidConfig config = BuildAidConfig.get();
		Vec3 eye = client.player.getEyePosition();
		Vec3 look = client.player.getViewVector(1.0f);

		Kind bestKind = null;
		int bestIndex = -1;
		double bestScore = Double.MAX_VALUE;

		for (int i = 0; i < config.holograms.size(); i++) {
			BuildAidConfig.Hologram h = config.holograms.get(i);
			if (!h.placed) {
				continue;
			}
			double score = score(eye, look, new Vec3(h.x + 0.5, h.y + 0.5, h.z + 0.5));
			if (score < bestScore && !(kind == Kind.HOLOGRAM && index == i)) {
				bestScore = score;
				bestKind = Kind.HOLOGRAM;
				bestIndex = i;
			}
		}

		for (int i = 0; i < config.shapes.size(); i++) {
			BuildAidConfig.Shape s = config.shapes.get(i);
			if (!s.placed) {
				continue;
			}
			double score = score(eye, look, new Vec3(s.x + 0.5, s.y + 0.5, s.z + 0.5));
			if (score < bestScore && !(kind == Kind.SHAPE && index == i)) {
				bestScore = score;
				bestKind = Kind.SHAPE;
				bestIndex = i;
			}
		}

		if (bestKind == null) {
			// Nada novo perto da mira: solta o alvo atual em vez de ficar preso nele.
			clearTarget();
			Feedback.error("buildaid.msg.manip_none");
			return;
		}

		kind = bestKind;
		index = bestIndex;
		Feedback.info(kind == Kind.HOLOGRAM ? "buildaid.msg.manip_hologram" : "buildaid.msg.manip_shape",
				index + 1);
	}

	public static void clearTarget() {
		kind = null;
		index = -1;
		grabbing = false;
		axis = -1;
	}

	/** Quanto mais perto da linha do olhar e mais perto do jogador, menor a pontuacao. */
	private static double score(Vec3 eye, Vec3 look, Vec3 point) {
		Vec3 delta = point.subtract(eye);
		double along = delta.dot(look);
		if (along < 0 || along > SELECT_RANGE) {
			return Double.MAX_VALUE;
		}
		double perpendicular = delta.subtract(look.scale(along)).length();
		return perpendicular + along * 0.05;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null || !hasTarget()) {
			return;
		}

		boolean down = Keys.grabTarget.isDown();

		if (down && !grabbing) {
			grabbing = true;
			// O eixo e decidido no instante em que agarra: o que estiver sob a mira manda.
			axis = hoveredAxis(client);
		} else if (!down && grabbing) {
			grabbing = false;
			axis = -1;
			BuildAidConfig.get().save();
		}

		if (grabbing) {
			follow(client);
		}
	}

	private static void follow(Minecraft client) {
		Vec3 target;

		if (axis < 0) {
			// Livre: gruda no bloco mirado.
			if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
					&& hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				var pos = hit.getBlockPos().relative(hit.getDirection());
				setPosition(pos.getX(), pos.getY(), pos.getZ());
			}
			return;
		}

		Vec3 origin = position();
		Vec3 direction = axisDirection(axis);
		double distance = closestPointOnAxis(origin, direction,
				client.player.getEyePosition(), client.player.getViewVector(1.0f));
		target = origin.add(direction.scale(distance));

		setPosition(
				(int) Math.round(axis == 0 ? target.x : origin.x),
				(int) Math.round(axis == 1 ? target.y : origin.y),
				(int) Math.round(axis == 2 ? target.z : origin.z));
	}

	/** Gira o alvo em passos; para holograma alterna a orientacao, para forma soma no yaw. */
	public static void rotate(int step) {
		BuildAidConfig config = BuildAidConfig.get();
		if (kind == Kind.HOLOGRAM) {
			BuildAidConfig.Hologram h = config.holograms.get(index);
			h.facing = Math.floorMod(h.facing + step, ImageHologram.FACING_COUNT);
		} else if (kind == Kind.SHAPE) {
			BuildAidConfig.Shape s = config.shapes.get(index);
			s.rotation = Math.floorMod(s.rotation + step * 15, 360);
		} else {
			return;
		}
		config.save();
	}

	/**
	 * Anda uma camada na forma mirada (layer-by-layer). Se o fatiador estiver desligado
	 * (modo todas as camadas), liga no modo "apenas a camada" para o passo fazer sentido.
	 */
	public static void stepLayer(int step) {
		if (kind != Kind.SHAPE || index < 0 || index >= BuildAidConfig.get().shapes.size()) {
			Feedback.error("buildaid.msg.manip_none");
			return;
		}
		BuildAidConfig.Shape s = BuildAidConfig.get().shapes.get(index);
		if (!s.placed) {
			Feedback.error("buildaid.msg.manip_none");
			return;
		}
		// Sem fatiador ligado, o primeiro passo entra no modo de camada unica.
		if (s.layerMode == 0) {
			s.layerMode = 1;
		}
		int maxLayer = Math.max(0, s.height - 1);
		s.activeLayer = Math.clamp(s.activeLayer + step, 0, maxLayer);
		BuildAidConfig.get().save();
		Feedback.info("buildaid.msg.layer_step", s.activeLayer, maxLayer);
	}

	/** Exclui o alvo selecionado (holograma ou forma) do mundo. */
	public static boolean deleteTarget() {
		if (!hasTarget()) {
			return false;
		}
		BuildAidConfig config = BuildAidConfig.get();
		if (kind == Kind.HOLOGRAM && index >= 0 && index < config.holograms.size()) {
			GlobalUndo.push();
			config.holograms.remove(index);
			Feedback.info("buildaid.msg.hologram_removed", index + 1);
		} else if (kind == Kind.SHAPE && index >= 0 && index < config.shapes.size()) {
			GlobalUndo.push();
			config.shapes.remove(index);
			Feedback.info("buildaid.msg.shape_removed", index + 1);
		} else {
			return false;
		}
		clearTarget();
		config.save();
		return true;
	}

	// ---------------------------------------------------------------- alcas

	/** Emite as setas de eixo. Pressupoe um coletor per-tick ja aberto. */
	public static void drawHandles(Minecraft client) {
		Vec3 origin = position();
		if (origin == null) {
			return;
		}

		int hovered = grabbing ? axis : hoveredAxis(client);

		Gizmos.cuboid(new AABB(origin.subtract(0.25, 0.25, 0.25), origin.add(0.25, 0.25, 0.25)),
				GizmoStyle.strokeAndFill(COLOR_TARGET, 2.0f, (COLOR_TARGET & 0x00FFFFFF) | 0x40000000))
				.setAlwaysOnTop();

		drawArrow(origin, axisDirection(0), COLOR_X, hovered == 0);
		drawArrow(origin, axisDirection(1), COLOR_Y, hovered == 1);
		drawArrow(origin, axisDirection(2), COLOR_Z, hovered == 2);
	}

	private static void drawArrow(Vec3 origin, Vec3 direction, int color, boolean highlighted) {
		Gizmos.arrow(origin, origin.add(direction.scale(HANDLE_LENGTH)), color,
				highlighted ? 4.0f : 2.0f).setAlwaysOnTop();
	}

	/** Qual seta esta sob a mira, ou -1 para nenhuma (movimento livre). */
	private static int hoveredAxis(Minecraft client) {
		Vec3 origin = position();
		if (origin == null || client.player == null) {
			return -1;
		}

		Vec3 eye = client.player.getEyePosition();
		Vec3 look = client.player.getViewVector(1.0f);

		int best = -1;
		double bestDistance = HANDLE_PICK_RADIUS;

		for (int a = 0; a < 3; a++) {
			Vec3 direction = axisDirection(a);
			double along = closestPointOnAxis(origin, direction, eye, look);
			if (along < 0 || along > HANDLE_LENGTH) {
				continue;
			}
			Vec3 pointOnAxis = origin.add(direction.scale(along));
			// Distancia do ponto ate a linha do olhar.
			Vec3 delta = pointOnAxis.subtract(eye);
			double projected = delta.dot(look);
			double distance = delta.subtract(look.scale(projected)).length();
			if (distance < bestDistance) {
				bestDistance = distance;
				best = a;
			}
		}

		return best;
	}

	/**
	 * Ponto do eixo mais proximo do raio do olhar, como distancia ao longo do eixo.
	 *
	 * <p>Aproximacao classica entre duas retas: P = origem + s·u e Q = olho + t·v.
	 */
	private static double closestPointOnAxis(Vec3 origin, Vec3 u, Vec3 rayOrigin, Vec3 v) {
		Vec3 w0 = origin.subtract(rayOrigin);
		double a = u.dot(u);
		double b = u.dot(v);
		double c = v.dot(v);
		double d = u.dot(w0);
		double e = v.dot(w0);
		double denominator = a * c - b * b;
		if (Math.abs(denominator) < 1.0e-6) {
			return 0.0;
		}
		return (b * e - c * d) / denominator;
	}

	private static Vec3 axisDirection(int a) {
		return switch (a) {
			case 0 -> new Vec3(1.0, 0.0, 0.0);
			case 1 -> new Vec3(0.0, 1.0, 0.0);
			default -> new Vec3(0.0, 0.0, 1.0);
		};
	}

	// ---------------------------------------------------------------- posicao do alvo

	private static Vec3 position() {
		BuildAidConfig config = BuildAidConfig.get();
		if (kind == Kind.HOLOGRAM && index >= 0 && index < config.holograms.size()) {
			BuildAidConfig.Hologram h = config.holograms.get(index);
			return new Vec3(h.x, h.y, h.z);
		}
		if (kind == Kind.SHAPE && index >= 0 && index < config.shapes.size()) {
			BuildAidConfig.Shape s = config.shapes.get(index);
			return new Vec3(s.x, s.y, s.z);
		}
		return null;
	}

	private static void setPosition(int x, int y, int z) {
		BuildAidConfig config = BuildAidConfig.get();
		if (kind == Kind.HOLOGRAM && index >= 0 && index < config.holograms.size()) {
			BuildAidConfig.Hologram h = config.holograms.get(index);
			h.x = x;
			h.y = y;
			h.z = z;
		} else if (kind == Kind.SHAPE && index >= 0 && index < config.shapes.size()) {
			BuildAidConfig.Shape s = config.shapes.get(index);
			// Sem invalidar a malha: ela vive em coordenadas locais e a posicao so entra como
			// deslocamento no desenho. Invalidar aqui reconstruiria a forma inteira a cada frame
			// de arrasto -- justamente o travamento que a malha em cache existe para evitar.
			s.x = x;
			s.y = y;
			s.z = z;
		}
	}
}
