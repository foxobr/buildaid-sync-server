package com.foxo.buildaid.image;

import com.foxo.buildaid.BuildAid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Biblioteca em disco das imagens de referencia.
 *
 * <p>Todas as fontes (URL, clipboard, seletor de arquivo) desembocam aqui: os bytes sao
 * validados, gravados em {@code config/buildaid/images/} e indexados em
 * {@code config/buildaid/library.json}. Sem isso, imagem colada do clipboard sumiria ao
 * fechar o jogo.
 *
 * <p>Classe pura Java (sem dependencia de classes do Minecraft) e sincronizada, porque as
 * importacoes acontecem em threads de fundo.
 */
public final class ImageStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

	/** Lado maior da miniatura, em pixels. */
	private static final int THUMB_SIZE = 128;

	/** Teto rigido na importacao. Acima disso a imagem e reduzida antes de ser guardada. */
	private static final int IMPORT_MAX_DIMENSION = 8192;

	private final Path rootDir;
	private final Path imagesDir;
	private final Path thumbsDir;
	private final Path indexFile;
	private final List<RefImage> entries = new ArrayList<>();

	public ImageStore(Path rootDir) {
		this.rootDir = rootDir;
		this.imagesDir = rootDir.resolve("images");
		this.thumbsDir = this.imagesDir.resolve("thumbs");
		this.indexFile = rootDir.resolve("library.json");
	}

	public synchronized void load() {
		try {
			Files.createDirectories(imagesDir);
		} catch (IOException e) {
			BuildAid.LOGGER.error("Nao consegui criar {}", imagesDir, e);
		}

		entries.clear();
		if (!Files.isRegularFile(indexFile)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8)) {
			List<RefImage> loaded = GSON.fromJson(reader, new TypeToken<List<RefImage>>() {
			}.getType());
			if (loaded != null) {
				// Descarta entradas cujo arquivo sumiu (usuario apagou na mao).
				for (RefImage img : loaded) {
					if (img != null && img.fileName() != null && Files.isRegularFile(imagesDir.resolve(img.fileName()))) {
						entries.add(img);
					}
				}
			}
		} catch (Exception e) {
			BuildAid.LOGGER.error("library.json invalido, comecando do zero", e);
		}
	}

	public synchronized void save() {
		try {
			Files.createDirectories(rootDir);
			try (Writer writer = Files.newBufferedWriter(indexFile, StandardCharsets.UTF_8)) {
				GSON.toJson(entries, writer);
			}
		} catch (IOException e) {
			BuildAid.LOGGER.error("Nao consegui salvar {}", indexFile, e);
		}
	}

	public synchronized List<RefImage> all() {
		return List.copyOf(entries);
	}

	public synchronized Optional<RefImage> byId(String id) {
		return entries.stream().filter(e -> e.id().equals(id)).findFirst();
	}

	public Path fileOf(RefImage image) {
		return imagesDir.resolve(image.fileName());
	}

	public Path imagesDir() {
		return imagesDir;
	}

	public Path thumbFileOf(RefImage image) {
		return thumbsDir.resolve(image.id() + ".png");
	}

	/**
	 * Caminho da miniatura, gerando-a se ainda nao existir.
	 *
	 * <p>Bloqueia (le e reescala a imagem cheia) -- chame de uma thread de fundo. Bibliotecas
	 * criadas na v1 nao tem miniatura, entao a primeira abertura da galeria as gera.
	 */
	public Path ensureThumbnail(RefImage image) throws IOException {
		Path thumb = thumbFileOf(image);
		if (Files.isRegularFile(thumb)) {
			return thumb;
		}

		BufferedImage source = ImageIO.read(fileOf(image).toFile());
		if (source == null) {
			throw new IOException("Nao consegui decodificar " + image.fileName() + " para gerar a miniatura");
		}

		Files.createDirectories(thumbsDir);
		writeThumbnail(source, thumb);
		source.flush();
		return thumb;
	}

	/**
	 * Reescala mantendo a proporcao e grava em PNG.
	 *
	 * <p>Feito com AWT em vez de {@code NativeImage}: aqui ja estamos fora da render thread e sem
	 * contexto grafico, e o Graphics2D funciona sem display (ao contrario do clipboard de imagem).
	 */
	private static void writeThumbnail(BufferedImage source, Path dest) throws IOException {
		int width = source.getWidth();
		int height = source.getHeight();
		float factor = Math.min(THUMB_SIZE / (float) width, THUMB_SIZE / (float) height);
		// Nunca aumenta: imagem menor que a miniatura fica no tamanho original.
		factor = Math.min(factor, 1.0f);

		int targetWidth = Math.max(1, Math.round(width * factor));
		int targetHeight = Math.max(1, Math.round(height * factor));

		BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		g.dispose();

		ImageIO.write(scaled, "png", dest.toFile());
		scaled.flush();
	}

	/**
	 * Importa bytes de imagem, guardando sempre um PNG RGBA de 8 bits.
	 *
	 * <p><b>Por que normalizar:</b> a validacao aqui usa ImageIO (decodificador do Java), mas quem
	 * transforma o arquivo em textura depois e o {@code NativeImage}, que usa STB. Os dois
	 * suportam conjuntos diferentes de formatos -- JPEG progressivo e CMYK, por exemplo, o ImageIO
	 * le e o STB nao. Guardar os bytes originais fazia a imagem entrar na biblioteca e nunca
	 * aparecer na tela. Regravando como PNG simples, o STB sempre recebe algo que sabe ler.
	 *
	 * <p>O id continua sendo o sha1 do conteudo <b>original</b>: colar duas vezes a mesma imagem
	 * cai na mesma entrada, independentemente da normalizacao.
	 *
	 * @throws IOException se os bytes nao forem uma imagem decodificavel
	 */
	public RefImage importBytes(byte[] data, String displayName, String source) throws IOException {
		if (data == null || data.length == 0) {
			throw new IOException("Nenhum dado de imagem recebido");
		}
		if (data.length > MAX_FILE_BYTES) {
			throw new IOException("Arquivo de " + (data.length / (1024 * 1024))
					+ " MB passa do limite de " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB");
		}

		BufferedImage decoded;
		try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
			decoded = ImageIO.read(in);
		}
		if (decoded == null) {
			throw new IOException("Formato de imagem nao reconhecido (use PNG, JPG, GIF ou BMP)");
		}

		String id = sha1(data);

		synchronized (this) {
			Optional<RefImage> existing = byId(id);
			if (existing.isPresent()) {
				decoded.flush();
				return existing.get();
			}
		}

		BufferedImage normalized = normalize(decoded);
		int width = normalized.getWidth();
		int height = normalized.getHeight();

		String fileName = id + ".png";
		Files.createDirectories(imagesDir);
		ImageIO.write(normalized, "png", imagesDir.resolve(fileName).toFile());

		// Miniatura ja sai pronta na importacao -- a galeria nunca precisa reescalar em cena.
		try {
			Files.createDirectories(thumbsDir);
			writeThumbnail(normalized, thumbsDir.resolve(id + ".png"));
		} catch (Exception e) {
			// Sem miniatura a galeria ainda funciona: ela gera sob demanda depois.
			BuildAid.LOGGER.warn("Nao consegui gerar a miniatura de {}", fileName, e);
		} finally {
			if (normalized != decoded) {
				decoded.flush();
			}
			normalized.flush();
		}

		String name = (displayName == null || displayName.isBlank()) ? fileName : displayName;
		RefImage image = new RefImage(id, name, fileName, width, height, System.currentTimeMillis(), source);

		synchronized (this) {
			entries.add(image);
			save();
		}

		BuildAid.LOGGER.info("Imagem importada: {} ({}x{}, fonte={})", name, width, height, source);
		return image;
	}

	public RefImage importFile(Path path, String source) throws IOException {
		byte[] data = Files.readAllBytes(path);
		String fileName = path.getFileName().toString();
		return importBytes(data, fileName, source);
	}

	/**
	 * Troca o nome de exibicao. {@link RefImage} e um record, entao renomear e substituir a
	 * entrada na lista -- o arquivo em disco e o id nao mudam.
	 */
	public synchronized void rename(String id, String newName) {
		if (newName == null || newName.isBlank()) {
			return;
		}
		for (int i = 0; i < entries.size(); i++) {
			RefImage image = entries.get(i);
			if (image.id().equals(id)) {
				entries.set(i, new RefImage(image.id(), newName, image.fileName(),
						image.width(), image.height(), image.addedAt(), image.source()));
				save();
				return;
			}
		}
	}

	public synchronized void remove(String id) {
		Optional<RefImage> found = byId(id);
		if (found.isEmpty()) {
			return;
		}
		RefImage image = found.get();
		entries.remove(image);
		try {
			Files.deleteIfExists(imagesDir.resolve(image.fileName()));
			Files.deleteIfExists(thumbFileOf(image));
		} catch (IOException e) {
			BuildAid.LOGGER.warn("Nao consegui apagar {}", image.fileName(), e);
		}
		save();
	}

	private static String sha1(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			return HexFormat.of().formatHex(digest.digest(data));
		} catch (Exception e) {
			// SHA-1 e obrigatorio em toda JVM; se faltar, algo esta muito errado.
			throw new IllegalStateException("SHA-1 indisponivel", e);
		}
	}

	/**
	 * Converte para ARGB de 8 bits e aplica o teto de dimensao.
	 *
	 * <p>Devolve a propria imagem de entrada quando ela ja esta no formato desejado, para nao
	 * gastar uma copia a toa no caso comum (PNG normal vindo do clipboard).
	 */
	private static BufferedImage normalize(BufferedImage source) {
		int width = source.getWidth();
		int height = source.getHeight();

		float factor = Math.min(1.0f, Math.min(
				IMPORT_MAX_DIMENSION / (float) width,
				IMPORT_MAX_DIMENSION / (float) height));

		if (factor >= 1.0f && source.getType() == BufferedImage.TYPE_INT_ARGB) {
			return source;
		}

		int targetWidth = Math.max(1, Math.round(width * factor));
		int targetHeight = Math.max(1, Math.round(height * factor));

		BufferedImage out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		g.dispose();

		if (targetWidth != width || targetHeight != height) {
			BuildAid.LOGGER.info("Imagem reduzida na importacao: {}x{} -> {}x{}",
					width, height, targetWidth, targetHeight);
		}
		return out;
	}
}
