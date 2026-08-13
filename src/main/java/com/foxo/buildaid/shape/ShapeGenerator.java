package com.foxo.buildaid.shape;

import com.foxo.buildaid.config.BuildAidConfig;

/**
 * Transforma parametros de forma numa malha pronta para desenhar.
 *
 * <p>Sao dois passos separados de proposito:
 *
 * <ol>
 *   <li><b>Voxelizar</b> — decide quais posicoes de bloco fazem parte da forma. Matematica pura,
 *       sem nada de render.</li>
 *   <li><b>Malhar</b> — converte esses blocos em faces, <b>descartando as faces internas</b>
 *       (aquelas cujo vizinho tambem esta preenchido). Numa esfera oca de raio 20 isso derruba
 *       dezenas de milhares de faces invisiveis, e e o que permite desenhar tudo numa unica
 *       chamada sem derrubar o FPS.</li>
 * </ol>
 *
 * <p>Os voxels vivem num array plano de {@code boolean} indexado pela caixa envolvente, em vez de
 * um conjunto com hash: a busca por vizinho vira aritmetica de indice, sem boxing.
 */
public final class ShapeGenerator {
	/** Acima disso a forma e recusada -- protege contra parametros absurdos. */
	public static final int MAX_VOLUME = 2_000_000;

	/** Sombreamento por eixo: da a leitura de volume sem precisar desenhar aresta nenhuma. */
	private static final float SHADE_DOWN = 0.45f;
	private static final float SHADE_UP = 1.0f;
	private static final float SHADE_NORTH_SOUTH = 0.8f;
	private static final float SHADE_EAST_WEST = 0.62f;

	/**
	 * Malha pronta. As posicoes ficam em coordenadas locais da forma (canto minimo na origem),
	 * entao andar com a camera nao obriga a reconstruir nada.
	 *
	 * @param positions      12 floats por face (4 vertices x XYZ)
	 * @param colors         uma cor ARGB por face, ja sombreada
	 * @param blocksPerLayer quantos blocos em cada altura, do chao para cima -- e assim que se
	 *                       constroi de verdade, camada por camada
	 */
	public record Mesh(float[] positions, int[] colors, int[] blocksPerLayer, int faceCount,
			int blockCount, int sizeX, int sizeY, int sizeZ) {
		public static final Mesh EMPTY =
				new Mesh(new float[0], new int[0], new int[0], 0, 0, 0, 0, 0);

		public boolean isEmpty() {
			return faceCount == 0;
		}
	}

	private ShapeGenerator() {
	}

	public static Mesh build(BuildAidConfig.Shape config) {
		ShapeType type = ShapeType.parse(config.type);

		int width = Math.max(1, config.width);
		int height = Math.max(1, config.height);
		int depth = Math.max(1, config.depth);

		// Girar a forma aumenta a caixa que precisa ser varrida.
		double radians = Math.toRadians(config.rotation);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		int sizeX = (int) Math.ceil(Math.abs(width * cos) + Math.abs(depth * sin));
		int sizeZ = (int) Math.ceil(Math.abs(width * sin) + Math.abs(depth * cos));
		int sizeY = height;

		long volume = (long) sizeX * sizeY * sizeZ;
		if (volume <= 0 || volume > MAX_VOLUME) {
			return Mesh.EMPTY;
		}

		boolean[] filled = voxelize(type, width, height, depth, sizeX, sizeY, sizeZ, cos, sin,
				Math.max(2, config.pitch), Math.max(1, config.thickness));

		boolean[] visible = config.hollow
				? hollow(filled, sizeX, sizeY, sizeZ, config.thickness)
				: filled;

		return mesh(visible, sizeX, sizeY, sizeZ, config.fillColor);
	}

	// ---------------------------------------------------------------- voxels

	private static boolean[] voxelize(ShapeType type, int width, int height, int depth,
			int sizeX, int sizeY, int sizeZ, double cos, double sin, int pitch, int thickness) {
		boolean[] filled = new boolean[sizeX * sizeY * sizeZ];

		double halfX = width / 2.0;
		double halfZ = depth / 2.0;

		for (int y = 0; y < sizeY; y++) {
			// Altura relativa, 0 na base e 1 no topo.
			double t = (y + 0.5) / height;
			// Recuo de telhado: 45 graus, um bloco para dentro a cada bloco de altura.
			double inset = y + 0.5;

			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					// Centraliza na caixa varrida e desfaz a rotacao: a forma e testada em
					// coordenadas giradas, entao o resultado continua alinhado a grade de blocos.
					double cx = x - sizeX / 2.0 + 0.5;
					double cz = z - sizeZ / 2.0 + 0.5;
					double lx = cx * cos + cz * sin;
					double lz = -cx * sin + cz * cos;

					if (inside(type, lx, lz, y, t, inset, halfX, halfZ, height, pitch, thickness)) {
						filled[index(x, y, z, sizeX, sizeY)] = true;
					}
				}
			}
		}
		return filled;
	}

	private static boolean inside(ShapeType type, double lx, double lz, int y, double t,
			double inset, double halfX, double halfZ, int height, int pitch, int thickness) {
		double ax = Math.abs(lx);
		double az = Math.abs(lz);

		return switch (type) {
			case BOX -> ax <= halfX && az <= halfZ;

			case CYLINDER -> ellipse(lx, lz, halfX, halfZ);

			case SPHERE -> {
				double halfY = height / 2.0;
				double ly = y - halfY + 0.5;
				yield sq(lx / halfX) + sq(ly / halfY) + sq(lz / halfZ) <= 1.0;
			}

			// Cupula: metade de cima de um elipsoide cuja altura inteira e o raio vertical.
			case DOME -> sq(lx / halfX) + sq((y + 0.5) / height) + sq(lz / halfZ) <= 1.0;

			case PYRAMID -> ax <= halfX * (1.0 - t) && az <= halfZ * (1.0 - t);

			case CONE -> {
				double shrink = 1.0 - t;
				yield shrink > 0 && ellipse(lx, lz, halfX * shrink, halfZ * shrink);
			}

			// Duas aguas: so os lados X sobem; o telhado corre ao longo de Z.
			case ROOF_GABLE -> ax <= halfX - inset && az <= halfZ;

			// Quatro aguas: recua nos dois eixos, entao o lado menor fecha antes e sobra a cumeeira.
			case ROOF_HIP -> ax <= halfX - inset && az <= halfZ - inset;

			// Escada espiral: a cada altura o degrau esta num angulo, e a celula entra se
			// estiver perto do ponto do helicoide naquele angulo. O passo diz quantos blocos
			// de altura uma volta completa sobe -- e o que define se da para subir.
			case HELIX -> {
				double angle = 2.0 * Math.PI * (y + 0.5) / pitch;
				double dx = lx - halfX * Math.cos(angle);
				double dz = lz - halfZ * Math.sin(angle);
				yield Math.sqrt(dx * dx + dz * dz) <= Math.max(1.0, thickness);
			}

			// Arco: meia elipse no plano X/Y esticada ao longo de Z. Oco vira o arco, cheio a abobada.
			case ARCH -> sq(lx / halfX) + sq((y + 0.5) / height) <= 1.0 && az <= halfZ;

			// Torus: distancia ao anel central menor que o raio menor.
			case TORUS -> {
				double majorRadius = (halfX + halfZ) / 2.0;
				double minorRadius = Math.max(1.0, thickness);
				double ringDistance = Math.sqrt(lx * lx + lz * lz) - majorRadius;
				double ly = y - height / 2.0 + 0.5;
				yield sq(ringDistance) + sq(ly) <= sq(minorRadius);
			}
		};
	}

	private static boolean ellipse(double lx, double lz, double halfX, double halfZ) {
		if (halfX <= 0 || halfZ <= 0) {
			return false;
		}
		return sq(lx / halfX) + sq(lz / halfZ) <= 1.0;
	}

	private static double sq(double v) {
		return v * v;
	}

	/**
	 * Casca da forma: o que sobra depois de tirar o miolo.
	 *
	 * <p>Erodir o solido {@code thickness} vezes e uniforme -- funciona igual para esfera,
	 * telhado ou caixa girada, sem precisar de uma formula de "oco" para cada tipo.
	 */
	private static boolean[] hollow(boolean[] filled, int sizeX, int sizeY, int sizeZ, int thickness) {
		boolean[] interior = filled.clone();
		for (int step = 0; step < Math.max(1, thickness); step++) {
			interior = erode(interior, sizeX, sizeY, sizeZ);
		}

		boolean[] shell = new boolean[filled.length];
		for (int i = 0; i < filled.length; i++) {
			shell[i] = filled[i] && !interior[i];
		}
		return shell;
	}

	/** Mantem so as celulas cujos seis vizinhos tambem estao preenchidos. */
	private static boolean[] erode(boolean[] source, int sizeX, int sizeY, int sizeZ) {
		boolean[] out = new boolean[source.length];
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					int i = index(x, y, z, sizeX, sizeY);
					if (!source[i]) {
						continue;
					}
					out[i] = solid(source, x - 1, y, z, sizeX, sizeY, sizeZ)
							&& solid(source, x + 1, y, z, sizeX, sizeY, sizeZ)
							&& solid(source, x, y - 1, z, sizeX, sizeY, sizeZ)
							&& solid(source, x, y + 1, z, sizeX, sizeY, sizeZ)
							&& solid(source, x, y, z - 1, sizeX, sizeY, sizeZ)
							&& solid(source, x, y, z + 1, sizeX, sizeY, sizeZ);
				}
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- malha

	private static Mesh mesh(boolean[] voxels, int sizeX, int sizeY, int sizeZ, int fillColor) {
		int blockCount = 0;
		int faceCount = 0;
		// Contagem por camada sai de graca no laco que ja percorre os voxels.
		int[] blocksPerLayer = new int[sizeY];

		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					if (!voxels[index(x, y, z, sizeX, sizeY)]) {
						continue;
					}
					blockCount++;
					blocksPerLayer[y]++;
					faceCount += exposedFaces(voxels, x, y, z, sizeX, sizeY, sizeZ);
				}
			}
		}

		if (faceCount == 0) {
			return Mesh.EMPTY;
		}

		float[] positions = new float[faceCount * 12];
		int[] colors = new int[faceCount];
		int face = 0;

		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					if (!voxels[index(x, y, z, sizeX, sizeY)]) {
						continue;
					}

					if (!solid(voxels, x, y - 1, z, sizeX, sizeY, sizeZ)) {
						face = writeFace(positions, colors, face, x, y, z, 0, fillColor, SHADE_DOWN);
					}
					if (!solid(voxels, x, y + 1, z, sizeX, sizeY, sizeZ)) {
						face = writeFace(positions, colors, face, x, y, z, 1, fillColor, SHADE_UP);
					}
					if (!solid(voxels, x, y, z - 1, sizeX, sizeY, sizeZ)) {
						face = writeFace(positions, colors, face, x, y, z, 2, fillColor, SHADE_NORTH_SOUTH);
					}
					if (!solid(voxels, x, y, z + 1, sizeX, sizeY, sizeZ)) {
						face = writeFace(positions, colors, face, x, y, z, 3, fillColor, SHADE_NORTH_SOUTH);
					}
					if (!solid(voxels, x - 1, y, z, sizeX, sizeY, sizeZ)) {
						face = writeFace(positions, colors, face, x, y, z, 4, fillColor, SHADE_EAST_WEST);
					}
					if (!solid(voxels, x + 1, y, z, sizeX, sizeY, sizeZ)) {
						face = writeFace(positions, colors, face, x, y, z, 5, fillColor, SHADE_EAST_WEST);
					}
				}
			}
		}

		return new Mesh(positions, colors, blocksPerLayer, faceCount, blockCount, sizeX, sizeY, sizeZ);
	}

	private static int exposedFaces(boolean[] v, int x, int y, int z, int sx, int sy, int sz) {
		int n = 0;
		if (!solid(v, x, y - 1, z, sx, sy, sz)) {
			n++;
		}
		if (!solid(v, x, y + 1, z, sx, sy, sz)) {
			n++;
		}
		if (!solid(v, x, y, z - 1, sx, sy, sz)) {
			n++;
		}
		if (!solid(v, x, y, z + 1, sx, sy, sz)) {
			n++;
		}
		if (!solid(v, x - 1, y, z, sx, sy, sz)) {
			n++;
		}
		if (!solid(v, x + 1, y, z, sx, sy, sz)) {
			n++;
		}
		return n;
	}

	/** Escreve os 4 vertices de uma face do cubo unitario em (x,y,z). */
	private static int writeFace(float[] positions, int[] colors, int face,
			int x, int y, int z, int side, int fillColor, float shade) {
		int o = face * 12;
		float x0 = x;
		float y0 = y;
		float z0 = z;
		float x1 = x + 1;
		float y1 = y + 1;
		float z1 = z + 1;

		switch (side) {
			case 0 -> put(positions, o, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
			case 1 -> put(positions, o, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
			case 2 -> put(positions, o, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
			case 3 -> put(positions, o, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
			case 4 -> put(positions, o, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
			default -> put(positions, o, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
		}

		colors[face] = shaded(fillColor, shade);
		return face + 1;
	}

	private static void put(float[] a, int o,
			float x1, float y1, float z1, float x2, float y2, float z2,
			float x3, float y3, float z3, float x4, float y4, float z4) {
		a[o] = x1;
		a[o + 1] = y1;
		a[o + 2] = z1;
		a[o + 3] = x2;
		a[o + 4] = y2;
		a[o + 5] = z2;
		a[o + 6] = x3;
		a[o + 7] = y3;
		a[o + 8] = z3;
		a[o + 9] = x4;
		a[o + 10] = y4;
		a[o + 11] = z4;
	}

	private static int shaded(int argb, float shade) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.clamp(Math.round(((argb >> 16) & 0xFF) * shade), 0, 255);
		int g = Math.clamp(Math.round(((argb >> 8) & 0xFF) * shade), 0, 255);
		int b = Math.clamp(Math.round((argb & 0xFF) * shade), 0, 255);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	// ---------------------------------------------------------------- indices

	private static int index(int x, int y, int z, int sizeX, int sizeY) {
		return (y * sizeX + x) + z * sizeX * sizeY;
	}

	private static boolean solid(boolean[] voxels, int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
		if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
			return false;
		}
		return voxels[index(x, y, z, sizeX, sizeY)];
	}
}
