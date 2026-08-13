package com.foxo.buildaid.config;

import com.foxo.buildaid.BuildAid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuracao persistida em {@code config/buildaid.json}.
 *
 * <p>POJO puro serializado com Gson (que ja vem com o Minecraft). O menu do mod e a tela do
 * Cloth Config escrevem nestes mesmos campos.
 */
public final class BuildAidConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static BuildAidConfig instance;

	/** Varios paineis podem ficar na tela ao mesmo tempo, cada um com sua imagem. */
	public List<Panel> panels = new ArrayList<>();

	/** Varios hologramas podem existir ao mesmo tempo, cada um com sua imagem e posicao. */
	public List<Hologram> holograms = new ArrayList<>();

	/** Varias formas podem existir ao mesmo tempo. */
	public List<Shape> shapes = new ArrayList<>();

	public Ghost ghost = new Ghost();
	public Cache cache = new Cache();
	public Grid grid = new Grid();
	public Selection selection = new Selection();
	public InfoHud infoHud = new InfoHud();
	public Music music = new Music();

	/** Imagem em foco: usada pelo overlay de tela cheia e como alvo padrao na galeria. */
	public String activeImageId = null;

	/** Secao da v1, lida so para migrar. */
	public LegacyBuildKit buildKit = null;
	/** Painel unico das versoes anteriores, lido so para migrar para {@link #panels}. */
	public Panel panel = null;
	/** Holograma unico da 1.1.0, lido so para migrar para {@link #holograms}. */
	public Hologram hologram = null;
	/** Forma unica da 1.1.0, lida so para migrar para {@link #shapes}. */
	public Shape shape = null;

	public static final class Panel {
		/** Imagem deste painel. Cada painel tem a sua. */
		public String imageId = null;
		public boolean visible = true;
		public int x = 16;
		public int y = 16;
		public int width = 260;
		public int height = 180;
		/** 0.0 = invisivel, 1.0 = opaco. */
		public float opacity = 0.85f;
		public boolean showBackground = true;
		public boolean showBorder = true;
		/** Travado: o modo de ajuste ignora este painel, para nao arrasta-lo sem querer. */
		public boolean locked = false;
		/** Zoom da imagem dentro do painel. */
		public float imageScale = 1.0f;
		/** Deslocamento (pan) da imagem dentro do painel, em pixels de tela. */
		public float imageOffsetX = 0.0f;
		public float imageOffsetY = 0.0f;

		public Panel() {
		}

		public Panel(String imageId, int x, int y) {
			this.imageId = imageId;
			this.x = x;
			this.y = y;
		}

		public Panel copy() {
			Panel c = new Panel();
			c.imageId = imageId;
			c.visible = visible;
			c.x = x;
			c.y = y;
			c.width = width;
			c.height = height;
			c.opacity = opacity;
			c.showBackground = showBackground;
			c.showBorder = showBorder;
			c.locked = locked;
			c.imageScale = imageScale;
			c.imageOffsetX = imageOffsetX;
			c.imageOffsetY = imageOffsetY;
			return c;
		}
	}

	public static final class Ghost {
		public boolean enabled = false;
		public float opacity = 0.25f;
	}

	public static final class Cache {
		/** Quantas imagens em resolucao cheia ficam na GPU ao mesmo tempo. */
		public int maxTextures = 16;
		/** Miniaturas sao pequenas, entao cabem muitas mais. */
		public int maxThumbnails = 64;
		/** Acima disso a imagem e reduzida antes de virar textura (limite de GPU). */
		public int maxDimension = 4096;
	}

	public static final class Grid {
		public boolean enabled = false;
		/** Quantos blocos de raio o grid cobre a partir do jogador. */
		public int radius = 24;
		/** ARGB das linhas comuns. */
		public int lineColor = 0x60FFFFFF;
		/** ARGB das bordas de chunk (a cada 16 blocos). */
		public int chunkColor = 0xC04A9EFF;
	}

	public static final class Selection {
		/** Enquanto false, nada e desenhado e marcar canto nao faz nada. */
		public boolean modeEnabled = false;
		public int boxColor = 0xFF4A9EFF;
		public int corner1Color = 0xFF4A9EFF;
		public int corner2Color = 0xFFFF9A3C;
	}

	/** Plano de imagem projetado dentro do mundo. */
	public static final class Hologram {
		public boolean enabled = true;
		public String imageId = null;
		public int x = 0;
		public int y = 64;
		public int z = 0;
		/** Tamanho em blocos. */
		public int widthBlocks = 8;
		public int heightBlocks = 6;
		/** 0=norte, 1=leste, 2=sul, 3=oeste, 4=deitado no chao. */
		public int facing = 0;
		public float opacity = 0.8f;
		public boolean placed = false;
		/** Com isto ligado, mexer na largura recalcula a altura pela proporcao real da imagem. */
		public boolean keepAspect = true;

		public Hologram copy() {
			Hologram c = new Hologram();
			c.enabled = enabled;
			c.imageId = imageId;
			c.x = x;
			c.y = y;
			c.z = z;
			c.widthBlocks = widthBlocks;
			c.heightBlocks = heightBlocks;
			c.facing = facing;
			c.opacity = opacity;
			c.placed = placed;
			c.keepAspect = keepAspect;
			return c;
		}
	}

	/** Guia de forma geometrica. */
	public static final class Shape {
		public boolean enabled = true;
		/** Nome do ShapeType. Texto para o JSON sobreviver a mudancas na ordem do enum. */
		public String type = "BOX";
		public int x = 0;
		public int y = 64;
		public int z = 0;
		public int width = 9;
		public int height = 9;
		public int depth = 9;
		public boolean hollow = true;
		/** Espessura da casca quando oco, em blocos. */
		public int thickness = 1;
		/** Rotacao em graus no eixo Y. 45 deixa a forma na diagonal. */
		public int rotation = 0;
		/** Escada espiral: quantos blocos de altura uma volta completa sobe. */
		public int pitch = 12;
		public int fillColor = 0x404A9EFF;
		public int lineColor = 0xFF4A9EFF;
		public boolean placed = false;

		/** Copia imutavel para a geracao da malha ler fora da render thread com seguranca. */
		public Shape copy() {
			Shape c = new Shape();
			c.enabled = enabled;
			c.type = type;
			c.x = x;
			c.y = y;
			c.z = z;
			c.width = width;
			c.height = height;
			c.depth = depth;
			c.hollow = hollow;
			c.thickness = thickness;
			c.rotation = rotation;
			c.pitch = pitch;
			c.fillColor = fillColor;
			c.lineColor = lineColor;
			c.placed = placed;
			return c;
		}
	}

	public static final class InfoHud {
		public boolean enabled = false;
		public int x = 4;
		public int y = 4;
		public boolean showCoords = true;
		public boolean showDirection = true;
		public boolean showBiome = true;
		public boolean showFps = true;
		public boolean showLight = true;
		public boolean showTime = true;
		public boolean showSelection = true;
	}

	public static final class Music {
		public String serverUrl = "wss://buildaid-sync-server.onrender.com";
		public String roomId = "";
		public boolean autoServerRoom = true;
		public int volume = 50;
		public boolean hudEnabled = true;
		public int hudX = 4;
		public int hudY = 100;
	}

	/** Formato da v1. Campos em 0 significam "nao informado". */
	public static final class LegacyBuildKit {
		public boolean gridEnabled;
		public int gridRadius;
		public int gridColor;
		public int chunkColor;
		public int selectionColor;
	}

	public static BuildAidConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("buildaid.json");
	}

	/** Pasta com a biblioteca de imagens: {@code config/buildaid/}. */
	public static Path dataDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(BuildAid.MOD_ID);
	}

	// ---------------------------------------------------------------- paineis

	/** Ids de imagem que estao na tela agora -- o cache nunca deve despejar estas. */
	public Set<String> imagesInUse() {
		Set<String> ids = new HashSet<>();
		if (activeImageId != null) {
			ids.add(activeImageId);
		}
		for (Panel p : panels) {
			if (p.visible && p.imageId != null) {
				ids.add(p.imageId);
			}
		}
		// Percorrer a lista inteira importa: proteger so um holograma faria o cache despejar
		// a imagem de outro que esta desenhado, e ele piscaria.
		for (Hologram h : holograms) {
			if (h.enabled && h.imageId != null) {
				ids.add(h.imageId);
			}
		}
		return ids;
	}

	/** Cria um painel novo, deslocado do ultimo para nao ficar exatamente por cima. */
	public Panel addPanel(String imageId) {
		int offset = panels.size() * 24;
		Panel created = new Panel(imageId, 16 + offset, 16 + offset);
		panels.add(created);
		return created;
	}

	public Hologram addHologram(String imageId) {
		Hologram created = new Hologram();
		created.imageId = imageId;
		holograms.add(created);
		return created;
	}

	public Shape addShape() {
		Shape created = new Shape();
		shapes.add(created);
		return created;
	}

	// ---------------------------------------------------------------- io

	private static BuildAidConfig load() {
		Path file = configFile();
		if (!Files.isRegularFile(file)) {
			BuildAidConfig fresh = new BuildAidConfig();
			fresh.save();
			return fresh;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			BuildAidConfig loaded = GSON.fromJson(reader, BuildAidConfig.class);
			if (loaded == null) {
				return new BuildAidConfig();
			}
			loaded.fillMissingSections();
			loaded.migrateFromV1();
			loaded.migrateSingleInstances();
			loaded.clamp();
			return loaded;
		} catch (Exception e) {
			BuildAid.LOGGER.error("buildaid.json invalido, usando padroes", e);
			return new BuildAidConfig();
		}
	}

	/** Um JSON editado a mao (ou de uma versao antiga) pode vir com secoes faltando. */
	private void fillMissingSections() {
		if (panels == null) {
			panels = new ArrayList<>();
		}
		if (holograms == null) {
			holograms = new ArrayList<>();
		}
		if (shapes == null) {
			shapes = new ArrayList<>();
		}
		if (ghost == null) {
			ghost = new Ghost();
		}
		if (cache == null) {
			cache = new Cache();
		}
		if (grid == null) {
			grid = new Grid();
		}
		if (selection == null) {
			selection = new Selection();
		}
		if (infoHud == null) {
			infoHud = new InfoHud();
		}
	}

	/** Traz as preferencias da v1 (secao {@code buildKit}) para as secoes novas. */
	private void migrateFromV1() {
		if (buildKit == null) {
			return;
		}

		grid.enabled = buildKit.gridEnabled;
		if (buildKit.gridRadius > 0) {
			grid.radius = buildKit.gridRadius;
		}
		// Cor zerada = campo ausente no JSON antigo; nesse caso o padrao novo e melhor.
		if (buildKit.gridColor != 0) {
			grid.lineColor = buildKit.gridColor;
		}
		if (buildKit.chunkColor != 0) {
			grid.chunkColor = buildKit.chunkColor;
		}
		if (buildKit.selectionColor != 0) {
			selection.boxColor = buildKit.selectionColor;
			selection.corner1Color = buildKit.selectionColor;
		}

		buildKit = null;
		BuildAid.LOGGER.info("Config da v1 migrada para o formato novo");
	}

	/** Painel, holograma e forma unicos das versoes anteriores viram o primeiro item da lista. */
	private void migrateSingleInstances() {
		if (panel != null) {
			if (panels.isEmpty()) {
				panel.imageId = activeImageId;
				panels.add(panel);
				BuildAid.LOGGER.info("Painel unico migrado para a lista");
			}
			panel = null;
		}

		if (hologram != null) {
			// So migra o que foi de fato colocado -- um holograma nunca usado nao vira entrada.
			if (holograms.isEmpty() && hologram.placed) {
				holograms.add(hologram);
				BuildAid.LOGGER.info("Holograma unico migrado para a lista");
			}
			hologram = null;
		}

		if (shape != null) {
			if (shapes.isEmpty() && shape.placed) {
				shapes.add(shape);
				BuildAid.LOGGER.info("Forma unica migrada para a lista");
			}
			shape = null;
		}
	}

	public void save() {
		clamp();
		try {
			Path file = configFile();
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			BuildAid.LOGGER.error("Nao consegui salvar a config", e);
		}
	}

	/** Mantem os valores dentro de faixas seguras (protege contra JSON editado na mao). */
	public void clamp() {
		for (Panel p : panels) {
			p.width = Math.clamp(p.width, 48, 4096);
			p.height = Math.clamp(p.height, 48, 4096);
			p.opacity = Math.clamp(p.opacity, 0.0f, 1.0f);
			p.imageScale = Math.clamp(p.imageScale, 0.05f, 20.0f);
		}
		ghost.opacity = Math.clamp(ghost.opacity, 0.0f, 1.0f);
		cache.maxTextures = Math.clamp(cache.maxTextures, 1, 64);
		cache.maxThumbnails = Math.clamp(cache.maxThumbnails, 8, 512);
		cache.maxDimension = Math.clamp(cache.maxDimension, 256, 8192);
		// Teto de 64 no raio: o grid emite ~(2r+1)*2 linhas por tick.
		grid.radius = Math.clamp(grid.radius, 4, 64);

		for (Hologram h : holograms) {
			h.widthBlocks = Math.clamp(h.widthBlocks, 1, 256);
			h.heightBlocks = Math.clamp(h.heightBlocks, 1, 256);
			h.facing = Math.clamp(h.facing, 0, 4);
			h.opacity = Math.clamp(h.opacity, 0.0f, 1.0f);
		}

		for (Shape s : shapes) {
			s.width = Math.clamp(s.width, 1, 256);
			s.height = Math.clamp(s.height, 1, 256);
			s.depth = Math.clamp(s.depth, 1, 256);
			s.thickness = Math.clamp(s.thickness, 1, 32);
			s.rotation = Math.clamp(s.rotation, 0, 359);
			s.pitch = Math.clamp(s.pitch, 2, 64);
		}
	}
}
