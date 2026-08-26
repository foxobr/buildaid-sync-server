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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuracao persistida em {@code config/buildaid.json}.
 *
 * <p>POJO puro serializado com Gson (que ja vem com o Minecraft). O menu do mod e a tela do
 * Cloth Config escrevem nestes mesmos campos.
 */
public final class BuildAidConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile BuildAidConfig instance;

	/** Varios paineis podem ficar na tela ao mesmo tempo, cada um com sua imagem. */
	public List<Panel> panels = new ArrayList<>();

	/** Varios hologramas podem existir ao mesmo tempo, cada um com sua imagem e posicao. */
	public List<Hologram> holograms = new ArrayList<>();

	/** Varias formas podem existir ao mesmo tempo. */
	public List<Shape> shapes = new ArrayList<>();

	/** Blocos de anotacoes flutuantes: texto livre preso a um canto da tela. */
	public List<Note> notes = new ArrayList<>();

	public Ghost ghost = new Ghost();
	public Cache cache = new Cache();
	public Grid grid = new Grid();
	public Selection selection = new Selection();
	public DangerZone dangerZone = new DangerZone();
	public InfoHud infoHud = new InfoHud();
	public Symmetry symmetry = new Symmetry();
	public Verifier verifier = new Verifier();
	public Randomizer randomizer = new Randomizer();
	public List<CustomPalette> customPalettes = new ArrayList<>();

	/** Tema visual global da interface do mod (0=Ciano, 1=Esmeralda, 2=Ouro, 3=Rubi, 4=Ametista, 5=Neon, 6=Monocromatico). */
	public int uiTheme = 0;

	public static final class CustomPalette {
		public String name = "Paleta Personalizada";
		public List<String> blockPaths = new ArrayList<>();
	}

	/**
	 * Perfil de configuracao: um instantaneo nomeado de toda a configuracao.
	 *
	 * <p>Guardado em memoria e salvo num arquivo proprio por perfil
	 * ({@code config/buildaid/profiles/<nome>.json}). O mapa de fotos e ignorado de
	 * proposito - ele so aponta para imagens da biblioteca, que ja estao salvas a parte.
	 */
	public static final class Snapshot {
		public String name = "";
		public List<Panel> panels = new ArrayList<>();
		public List<Hologram> holograms = new ArrayList<>();
		public List<Shape> shapes = new ArrayList<>();
		public List<Note> notes = new ArrayList<>();
		public Ghost ghost = new Ghost();
		public Cache cache = new Cache();
		public Grid grid = new Grid();
		public Selection selection = new Selection();
		public DangerZone dangerZone = new DangerZone();
		public InfoHud infoHud = new InfoHud();
		public Symmetry symmetry = new Symmetry();
		public Verifier verifier = new Verifier();
		public Randomizer randomizer = new Randomizer();
		public int uiTheme = 0;
		/** Imagem em foco (overlay tela cheia / alvo padrao na galeria). */
		public String activeImageId = null;
		/** Nome do perfil ativo no momento. */
		public String activeProfile = null;
		/** Paletas de cor personalizadas. */
		public List<CustomPalette> customPalettes = new ArrayList<>();

		public static Snapshot copyOf(BuildAidConfig c) {
			Snapshot s = new Snapshot();
			s.name = c.activeProfile != null ? c.activeProfile : "";
			s.activeImageId = c.activeImageId;
			s.activeProfile = c.activeProfile;
			s.customPalettes = new ArrayList<>(c.customPalettes);
			s.panels = new ArrayList<>(c.panels.size());
			for (Panel p : c.panels) {
				s.panels.add(p.copy());
			}
			s.holograms = new ArrayList<>(c.holograms.size());
			for (Hologram h : c.holograms) {
				s.holograms.add(h.copy());
			}
			s.shapes = new ArrayList<>(c.shapes.size());
			for (Shape sh : c.shapes) {
				s.shapes.add(sh.copy());
			}
			s.notes = new ArrayList<>(c.notes.size());
			for (Note n : c.notes) {
				s.notes.add(n.copy());
			}
			s.ghost = c.ghost.copy();
			s.cache = c.cache.copy();
			s.grid = c.grid.copy();
			s.selection = c.selection.copy();
			s.dangerZone = c.dangerZone.copy();
			s.infoHud = c.infoHud.copy();
			s.symmetry = c.symmetry.copy();
			s.verifier = c.verifier.copy();
			s.randomizer = c.randomizer.copy();
			s.uiTheme = c.uiTheme;
			return s;
		}

		/** Sobrescreve a configuracao viva com este perfil. Nao toca no activeProfile. */
		public void applyTo(BuildAidConfig c) {
			c.panels = new ArrayList<>(panels.size());
			for (Panel p : panels) {
				c.panels.add(p.copy());
			}
			c.holograms = new ArrayList<>(holograms.size());
			for (Hologram h : holograms) {
				c.holograms.add(h.copy());
			}
			c.shapes = new ArrayList<>(shapes.size());
			for (Shape sh : shapes) {
				c.shapes.add(sh.copy());
			}
			c.notes = new ArrayList<>(notes.size());
			for (Note n : notes) {
				c.notes.add(n.copy());
			}
			c.ghost = ghost.copy();
			c.cache = cache.copy();
			c.grid = grid.copy();
			c.selection = selection.copy();
			c.dangerZone = dangerZone.copy();
			c.infoHud = infoHud.copy();
			c.symmetry = symmetry.copy();
			c.verifier = verifier.copy();
			c.randomizer = randomizer.copy();
			c.uiTheme = uiTheme;
			c.activeImageId = activeImageId;
			c.activeProfile = activeProfile;
			c.customPalettes = new ArrayList<>(customPalettes);
		}

		public void save() {
			if (name == null || name.isBlank()) {
				return;
			}
			try {
				Path file = profilesDir().resolve(sanitize(name) + ".json");
				Files.createDirectories(file.getParent());
				try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
					GSON.toJson(this, writer);
				}
			} catch (Exception e) {
				BuildAid.LOGGER.error("Nao consegui salvar o perfil " + name, e);
			}
		}

		public static Snapshot load(String name) {
			Path file = profilesDir().resolve(sanitize(name) + ".json");
			if (!Files.isRegularFile(file)) {
				return null;
			}
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Snapshot s = GSON.fromJson(reader, Snapshot.class);
				if (s == null) {
					return null;
				}
				s.name = name;
				s.fillMissing();
				return s;
			} catch (Exception e) {
				BuildAid.LOGGER.error("Perfil invalido: " + name, e);
				return null;
			}
		}

		private void fillMissing() {
			if (panels == null) panels = new ArrayList<>();
			if (holograms == null) holograms = new ArrayList<>();
			if (shapes == null) shapes = new ArrayList<>();
			if (notes == null) notes = new ArrayList<>();
			if (ghost == null) ghost = new Ghost();
			if (cache == null) cache = new Cache();
			if (grid == null) grid = new Grid();
			if (selection == null) selection = new Selection();
			if (dangerZone == null) dangerZone = new DangerZone();
			if (infoHud == null) infoHud = new InfoHud();
			if (symmetry == null) symmetry = new Symmetry();
			if (verifier == null) verifier = new Verifier();
			if (randomizer == null) randomizer = new Randomizer();
		}

		/** Nome de arquivo seguro: tira caracteres que complicam o sistema de arquivos. */
		static String sanitize(String name) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < name.length(); i++) {
				char ch = name.charAt(i);
				if (Character.isLetterOrDigit(ch) || ch == ' ' || ch == '-' || ch == '_') {
					sb.append(ch);
				} else {
					sb.append('_');
				}
			}
			String out = sb.toString().trim();
			return out.isEmpty() ? "perfil" : out;
		}
	}

	/**
	 * Lista de perfis disponiveis (nomes). Preenchida em {@link #load()} a partir da
	 * pasta de perfis; a tela de Perfis a re-le quando precisa de um refresh.
	 */
	public List<String> profileNames = new ArrayList<>();

	/** Nome do perfil ativo no momento, ou null se nenhum (config padrao da sessao). */
	public String activeProfile = null;

	public static final class DangerZone {
		public boolean enabled = false;
		public int radius = 16;

		public DangerZone copy() {
			DangerZone c = new DangerZone();
			c.enabled = enabled;
			c.radius = radius;
			return c;
		}
	}

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
		/** Rotacao da imagem em graus (0, 90, 180, 270). */
		public int rotation = 0;
		public boolean showGrid = false;
		public boolean flipHorizontal = false;

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
			c.rotation = rotation;
			return c;
		}
	}

	/**
	 * Bloco de anotacoes: um post-it flutuante na HUD, no espirito dos paineis de referencia.
	 *
	 * <p>A altura da caixa e calculada no desenho (cresce com o texto); o jogador controla
	 * posicao, largura, opacidade e a cor da barrinha lateral.
	 */
	public static final class Note {
		/** Texto livre; \n separa linhas. */
		public String text = "";
		public boolean visible = true;
		public int x = 16;
		public int y = 16;
		/** Largura da caixa em pixels de GUI; a altura acompanha o texto. */
		public int width = 160;
		/** 0.0 = invisivel, 1.0 = opaca. */
		public float opacity = 0.9f;
		/** Cor do acento: indice em {@code Theme.NOTE_ACCENTS}. */
		public int colorPreset = 0;

		public Note() {
		}

		public Note(String text, int x, int y) {
			this.text = text == null ? "" : text;
			this.x = x;
			this.y = y;
		}

		public Note copy() {
			Note c = new Note(text, x, y);
			c.visible = visible;
			c.width = width;
			c.opacity = opacity;
			c.colorPreset = colorPreset;
			return c;
		}
	}

	public static final class Symmetry {
		public boolean enabled = false;
		/** 0 = Eixo X (plano Norte-Sul), 1 = Eixo Z (plano Leste-Oeste). */
		public int axis = 0;
		/** 0 = plano unico (so um eixo) · 1 = cruz quadrupla (ambos os eixos, 4 quadrantes). So usado quando type == 0. */
		public int mode = 0;
		/** 0 = planar (plano unico ou cruz, conforme mode) · 1 = radial N eixos (tipo flor, em torno do jogador). */
		public int type = 0;
		/** Numero de planos de espelho radiais quando type == 1 (minimo 2). */
		public int arms = 6;
		public int position = 0;
		public int radius = 32;
		public int height = 32;
		public int color = 0x8055FF55;

		public Symmetry copy() {
			Symmetry c = new Symmetry();
			c.enabled = enabled;
			c.axis = axis;
			c.mode = mode;
			c.type = type;
			c.arms = arms;
			c.position = position;
			c.radius = radius;
			c.height = height;
			c.color = color;
			return c;
		}

		/** Cicla o modo de simetria: plano unico -> cruz quadrupla -> radial -> plano unico. */
		public void cycle() {
			if (type == 1) {
				type = 0;
				mode = 0;
			} else if (mode == 0) {
				mode = 1;
			} else {
				type = 1;
				mode = 1;
			}
		}

		/** Rotulo curto para o botao do menu: "1", "4" ou "R6" (radial com N bracos). */
		public String modeLabel() {
			if (type == 1) {
				return "R" + arms;
			}
			return mode == 1 ? "4" : "1";
		}
	}

	/**
	 * Verificador de construcao: compara o blueprint ativo (o "deveria estar aqui")
	 * contra os blocos reais do mundo e projeta um overlay (amarelo = falta, vermelho
	 * = errado/extra), no espirito do Schematic Verifier do Litematica.
	 */
	public static final class Verifier {
		public boolean enabled = false;
		/** ARGB do bloco que deveria estar aqui e nao esta (faltando). */
		public int missingColor = 0xFFFFFF00;
		/** ARGB do bloco no mundo diferente do esperado (errado ou extra). */
		public int wrongColor = 0xFFFF3333;

		public Verifier copy() {
			Verifier c = new Verifier();
			c.enabled = enabled;
			c.missingColor = missingColor;
			c.wrongColor = wrongColor;
			return c;
		}
	}

	/**
	 * Paleta randomizadora: lista de blocos (cada um com um peso) que a tecla
	 * sorteia para a mao. Inspirado no "randomizer bag" do Effortless Building,
	 * para superficies naturais (pedra/grama/terra misturadas, por exemplo).
	 *
	 * <p>O peso de cada entrada conta quantas vezes o bloco entra no sorteio,
	 * deixando raros mais provaveis sem duplicar linhas na interface.
	 */
	public static final class Randomizer {
		public boolean enabled = false;
		/** Em criativo, se true, so entrega blocos que o jogador ja tem no inventario. */
		public boolean restrictToInventory = true;
		public List<RandomizerEntry> entries = new ArrayList<>();

		public Randomizer copy() {
			Randomizer c = new Randomizer();
			c.enabled = enabled;
			c.restrictToInventory = restrictToInventory;
			c.entries = new ArrayList<>(entries.size());
			for (RandomizerEntry e : entries) {
				RandomizerEntry copy = new RandomizerEntry();
				copy.path = e.path;
				copy.weight = e.weight;
				c.entries.add(copy);
			}
			return c;
		}
	}

	/** Um bloco da paleta randomizada: id de registro (estavel entre idiomas) + peso. */
	public static final class RandomizerEntry {
		public String path = "minecraft:stone";
		public int weight = 1;
	}

	public static final class Ghost {
		public boolean enabled = false;
		public float opacity = 0.25f;

		public Ghost copy() {
			Ghost c = new Ghost();
			c.enabled = enabled;
			c.opacity = opacity;
			return c;
		}
	}

	public static final class Cache {
		/** Quantas imagens em resolucao cheia ficam na GPU ao mesmo tempo. */
		public int maxTextures = 16;
		/** Miniaturas sao pequenas, entao cabem muitas mais. */
		public int maxThumbnails = 64;
		/** Acima disso a imagem e reduzida antes de virar textura (limite de GPU). */
		public int maxDimension = 4096;

		public Cache copy() {
			Cache c = new Cache();
			c.maxTextures = maxTextures;
			c.maxThumbnails = maxThumbnails;
			c.maxDimension = maxDimension;
			return c;
		}
	}

	public static final class Grid {
		public boolean enabled = false;
		/** Quantos blocos de raio o grid cobre a partir do jogador. */
		public int radius = 24;
		/** ARGB das linhas comuns. */
		public int lineColor = 0x60FFFFFF;
		/** ARGB das bordas de chunk (a cada 16 blocos). */
		public int chunkColor = 0xC04A9EFF;
		public boolean lockY = false;
		public int fixedY = 64;

		public Grid copy() {
			Grid c = new Grid();
			c.enabled = enabled;
			c.radius = radius;
			c.lineColor = lineColor;
			c.chunkColor = chunkColor;
			c.lockY = lockY;
			c.fixedY = fixedY;
			return c;
		}
	}

	public static final class Selection {
		/** Enquanto false, nada e desenhado e marcar canto nao faz nada. */
		public boolean modeEnabled = false;
		public int boxColor = 0xFF4A9EFF;
		public int corner1Color = 0xFF4A9EFF;
		public int corner2Color = 0xFFFF9A3C;
		public boolean showCenter = true;

		public Selection copy() {
			Selection c = new Selection();
			c.modeEnabled = modeEnabled;
			c.boxColor = boxColor;
			c.corner1Color = corner1Color;
			c.corner2Color = corner2Color;
			c.showCenter = showCenter;
			return c;
		}
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
		/** Rotacao da imagem do holograma (0, 90, 180, 270). */
		public int rotation = 0;

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
			c.rotation = rotation;
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
		/** Modo de fatiador: 0 = todas as camadas, 1 = apenas camada ativa, 2 = ate a camada ativa. */
		public int layerMode = 0;
		/** Camada Y ativa para visualizacao isolada (0 ate height-1). */
		public int activeLayer = 0;
		/** Preset de cor: 0=Ciano, 1=Esmeralda, 2=Ouro, 3=Laranja, 4=Rubi, 5=Roxo, 6=Branco, 7=Arco-Iris. */
		public int colorPreset = 0;
		public boolean wireframe = false;

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
			c.layerMode = layerMode;
			c.activeLayer = activeLayer;
			c.colorPreset = colorPreset;
			c.wireframe = wireframe;
			return c;
		}
	}

	public static final class InfoHud {
		public boolean enabled = false;
		public int x = 4;
		public int y = 4;
		/** Canto da tela: 0 = Topo-Esquerdo, 1 = Topo-Direito, 2 = Fundo-Esquerdo, 3 = Fundo-Direito. */
		public int corner = 0;
		/** Estilo do fundo: 0 = Glassmorphism, 1 = Sombra Vanilla, 2 = Alto Contraste. */
		public int bgStyle = 0;
		/** Tema de cor do texto: 0 = Ciano, 1 = Dourado, 2 = Esmeralda, 3 = Branco, 4 = Roxo, 5 = Laranja. */
		public int colorTheme = 0;
		public boolean showCoords = true;
		public boolean showDirection = true;
		public boolean showBiome = true;
		public boolean showFps = true;
		public boolean showLight = true;
		public boolean showTime = true;
		public boolean showSelection = true;
		public boolean showTargetDistance = true;
		public boolean showAngles = true;
		public boolean showTargetBlock = true;
		public boolean showHeldCount = true;
		public boolean showDurability = true;

		public InfoHud copy() {
			InfoHud c = new InfoHud();
			c.enabled = enabled;
			c.x = x;
			c.y = y;
			c.corner = corner;
			c.bgStyle = bgStyle;
			c.colorTheme = colorTheme;
			c.showCoords = showCoords;
			c.showDirection = showDirection;
			c.showBiome = showBiome;
			c.showFps = showFps;
			c.showLight = showLight;
			c.showTime = showTime;
			c.showSelection = showSelection;
			c.showTargetDistance = showTargetDistance;
			c.showAngles = showAngles;
			c.showTargetBlock = showTargetBlock;
			c.showHeldCount = showHeldCount;
			c.showDurability = showDurability;
			return c;
		}
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

	/** Pasta dos perfis de configuracao: {@code config/buildaid/profiles/}. */
	public static Path profilesDir() {
		return dataDir().resolve("profiles");
	}

	/** Le os nomes dos perfis salvos em disco (sem recarregar o corpo de cada um). */
	public static List<String> loadProfiles() {
		List<String> names = new ArrayList<>();
		try (var stream = Files.list(profilesDir())) {
			stream.filter(p -> Files.isRegularFile(p)
					&& p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
				.forEach(p -> names.add(p.getFileName().toString()
					.substring(0, p.getFileName().toString().length() - 5)));
		} catch (Exception e) {
			// Pasta ainda nao existe: simplesmente nao ha perfis.
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Salva a configuracao viva como um perfil com o nome dado e marca como ativo. */
	public void saveProfile(String name) {
		if (name == null || name.isBlank()) {
			return;
		}
		Snapshot s = Snapshot.copyOf(this);
		s.name = name;
		s.save();
		activeProfile = name;
		save();
	}

	/** Carrega um perfil do disco e o aplica sobre a configuracao viva. */
	public boolean loadProfile(String name) {
		Snapshot s = Snapshot.load(name);
		if (s == null) {
			return false;
		}
		s.applyTo(this);
		activeProfile = name;
		clamp();
		save();
		return true;
	}

	/** Apaga o arquivo de um perfil do disco, se existir. */
	public void deleteProfile(String name) {
		try {
			Files.deleteIfExists(profilesDir().resolve(Snapshot.sanitize(name) + ".json"));
		} catch (Exception e) {
			BuildAid.LOGGER.error("Nao consegui apagar o perfil " + name, e);
		}
		if (name.equals(activeProfile)) {
			activeProfile = null;
			save();
		}
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
		GlobalUndo.push();
		int offset = panels.size() * 24;
		Panel created = new Panel(imageId, 16 + offset, 16 + offset);
		panels.add(created);
		return created;
	}

	public Hologram addHologram(String imageId) {
		GlobalUndo.push();
		Hologram created = new Hologram();
		created.imageId = imageId;
		holograms.add(created);
		return created;
	}

	public Shape addShape() {
		GlobalUndo.push();
		Shape created = new Shape();
		shapes.add(created);
		return created;
	}

	/** Cria uma anotacao nova, deslocada da ultima para nao nascer em cima dela. */
	public Note addNote() {
		GlobalUndo.push();
		int offset = notes.size() * 24;
		Note created = new Note("", 16 + offset, 16 + offset);
		notes.add(created);
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
			loaded.profileNames = loadProfiles();
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
		if (notes == null) {
			notes = new ArrayList<>();
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
		if (customPalettes == null) {
			customPalettes = new ArrayList<>();
		}
		if (dangerZone == null) {
			dangerZone = new DangerZone();
		}
		if (symmetry == null) {
			symmetry = new Symmetry();
		}
		if (verifier == null) {
			verifier = new Verifier();
		}
		if (randomizer == null) {
			randomizer = new Randomizer();
		}
		if (profileNames == null) {
			profileNames = new ArrayList<>();
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

		symmetry.axis = Math.clamp(symmetry.axis, 0, 1);
		symmetry.mode = Math.clamp(symmetry.mode, 0, 1);
		symmetry.type = Math.clamp(symmetry.type, 0, 1);
		symmetry.arms = Math.clamp(symmetry.arms, 2, 16);
		symmetry.radius = Math.clamp(symmetry.radius, 4, 128);
		symmetry.height = Math.clamp(symmetry.height, 4, 128);

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

		for (Note n : notes) {
			n.width = Math.clamp(n.width, 60, 1024);
			n.opacity = Math.clamp(n.opacity, 0.0f, 1.0f);
			n.colorPreset = Math.floorMod(n.colorPreset, 6);
			if (n.text != null && n.text.length() > 4000) {
				n.text = n.text.substring(0, 4000);
			}
		}

		for (RandomizerEntry e : randomizer.entries) {
			e.weight = Math.clamp(e.weight, 1, 64);
		}
	}
}
