package com.foxo.buildaid.image;

import com.foxo.buildaid.BuildAid;
import com.foxo.buildaid.config.BuildAidConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Cache de texturas na GPU para as imagens de referencia.
 *
 * <p>Divisao de trabalho deliberada: ler o arquivo e decodificar o PNG acontece em thread de
 * fundo (nao toca em GL), mas criar a {@link DynamicTexture} e registrar no TextureManager tem
 * que ser na render thread -- por isso o {@code thenAcceptAsync(..., Minecraft.getInstance())},
 * ja que o Minecraft e um {@link Executor} que despacha na render thread.
 *
 * <p>Sao <b>dois</b> caches independentes: um de imagens em resolucao cheia e outro de
 * miniaturas. Se fossem um so, rolar a galeria despejaria justamente a imagem que esta na tela.
 */
public final class ImageLibrary {
	/** Metadados de uma textura ja pronta para desenhar. */
	public record Loaded(Identifier textureId, int width, int height) {
	}

	private static final Executor IO_POOL = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BuildAid-ImageIO");
		t.setDaemon(true);
		return t;
	});

	/** Um cache. As colecoes de textura sao tocadas so na render thread. */
	private static final class Slot {
		final String pathPrefix;
		/** accessOrder=true para virar LRU. */
		final LinkedHashMap<String, DynamicTexture> textures = new LinkedHashMap<>(16, 0.75f, true);
		final Map<String, Loaded> metadata = new LinkedHashMap<>();
		final Set<String> loading = ConcurrentHashMap.newKeySet();
		final Set<String> failed = ConcurrentHashMap.newKeySet();

		Slot(String pathPrefix) {
			this.pathPrefix = pathPrefix;
		}
	}

	private final ImageStore store;
	private final Slot full = new Slot("ref/");
	private final Slot thumbnails = new Slot("thumb/");

	public ImageLibrary(ImageStore store) {
		this.store = store;
	}

	/**
	 * Textura em resolucao cheia, ou {@code null} se ainda nao estiver carregada.
	 * Na primeira chamada dispara o carregamento assincrono -- o render pula um frame ou dois
	 * em vez de travar esperando IO.
	 */
	public Loaded get(String imageId) {
		return access(full, imageId, false);
	}

	/** Miniatura para a galeria. Mesmo contrato do {@link #get}. */
	public Loaded getThumbnail(String imageId) {
		return access(thumbnails, imageId, true);
	}

	public boolean hasFailed(String imageId) {
		return imageId != null && (full.failed.contains(imageId) || thumbnails.failed.contains(imageId));
	}

	/** Permite tentar de novo depois de um erro (ex.: usuario corrigiu o arquivo). */
	public void clearFailure(String imageId) {
		full.failed.remove(imageId);
		thumbnails.failed.remove(imageId);
	}

	private Loaded access(Slot slot, String imageId, boolean thumbnail) {
		if (imageId == null) {
			return null;
		}

		Loaded ready = slot.metadata.get(imageId);
		if (ready != null) {
			slot.textures.get(imageId); // marca uso no LRU
			return ready;
		}

		if (!slot.loading.contains(imageId) && !slot.failed.contains(imageId)) {
			beginLoad(slot, imageId, thumbnail);
		}
		return null;
	}

	private void beginLoad(Slot slot, String imageId, boolean thumbnail) {
		RefImage image = store.byId(imageId).orElse(null);
		if (image == null) {
			slot.failed.add(imageId);
			return;
		}

		slot.loading.add(imageId);
		int maxDimension = BuildAidConfig.get().cache.maxDimension;

		CompletableFuture
				.supplyAsync(() -> {
					try {
						// ensureThumbnail gera a miniatura na hora se a biblioteca vier da v1.
						Path path = thumbnail ? store.ensureThumbnail(image) : store.fileOf(image);
						NativeImage decoded = decode(Files.readAllBytes(path));
						return thumbnail ? decoded : downscaleIfNeeded(decoded, maxDimension);
					} catch (Exception e) {
						throw new RuntimeException("Falha ao decodificar " + image.displayName(), e);
					}
				}, IO_POOL)
				.thenAcceptAsync(nativeImage -> upload(slot, imageId, image, nativeImage), Minecraft.getInstance())
				.exceptionally(error -> {
					BuildAid.LOGGER.error("Nao consegui carregar a imagem {}", imageId, error);
					slot.loading.remove(imageId);
					slot.failed.add(imageId);
					return null;
				});
	}

	/** Roda na render thread. */
	private void upload(Slot slot, String imageId, RefImage image, NativeImage nativeImage) {
		try {
			int width = nativeImage.getWidth();
			int height = nativeImage.getHeight();

			Identifier textureId = Identifier.fromNamespaceAndPath(BuildAid.MOD_ID, slot.pathPrefix + imageId);
			// A DynamicTexture assume a posse da NativeImage e a fecha junto com ela.
			DynamicTexture texture = new DynamicTexture(() -> "BuildAid " + image.displayName(), nativeImage);

			Minecraft.getInstance().getTextureManager().register(textureId, texture);
			slot.textures.put(imageId, texture);
			slot.metadata.put(imageId, new Loaded(textureId, width, height));

			evictIfNeeded(slot);

			if (slot == full) {
				// INFO de proposito: e a primeira coisa que se olha quando "a imagem nao aparece".
				BuildAid.LOGGER.info("Textura pronta: {} ({}x{})", image.displayName(), width, height);
			}
		} catch (Exception e) {
			BuildAid.LOGGER.error("Falha ao subir a textura {}", imageId, e);
			slot.failed.add(imageId);
			nativeImage.close();
		} finally {
			slot.loading.remove(imageId);
		}
	}

	/**
	 * Decodifica os bytes numa {@link NativeImage}.
	 *
	 * <p>Tenta primeiro o STB, que e o decodificador do proprio Minecraft. Se ele recusar o
	 * arquivo, cai para o ImageIO e monta a imagem pixel a pixel. Isso importa para bibliotecas
	 * criadas antes da normalizacao na importacao, que podem ter JPEG progressivo ou CMYK
	 * guardado -- formatos que o ImageIO le e o STB nao. Sem este fallback, aquelas imagens
	 * ficavam na biblioteca sem nunca aparecer na tela.
	 */
	private static NativeImage decode(byte[] data) throws IOException {
		try {
			return NativeImage.read(data);
		} catch (Exception stbRefused) {
			BuildAid.LOGGER.info("STB nao leu a imagem, tentando com o ImageIO ({})", stbRefused.getMessage());
			return fromImageIO(data);
		}
	}

	private static NativeImage fromImageIO(byte[] data) throws IOException {
		BufferedImage source;
		try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
			source = ImageIO.read(in);
		}
		if (source == null) {
			throw new IOException("nenhum decodificador conseguiu ler esta imagem");
		}

		int width = source.getWidth();
		int height = source.getHeight();
		NativeImage image = new NativeImage(width, height, false);
		// setPixel espera ARGB, exatamente o que getRGB devolve -- sem troca de canais.
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setPixel(x, y, source.getRGB(x, y));
			}
		}
		source.flush();

		BuildAid.LOGGER.info("Imagem recuperada pelo ImageIO ({}x{})", width, height);
		return image;
	}

	/** Reduz imagens gigantes antes de virarem textura -- evita estourar o limite da GPU. */
	private static NativeImage downscaleIfNeeded(NativeImage source, int maxDimension) {
		int width = source.getWidth();
		int height = source.getHeight();
		if (width <= maxDimension && height <= maxDimension) {
			return source;
		}

		float factor = Math.min((float) maxDimension / width, (float) maxDimension / height);
		int targetWidth = Math.max(1, Math.round(width * factor));
		int targetHeight = Math.max(1, Math.round(height * factor));

		NativeImage scaled = new NativeImage(targetWidth, targetHeight, false);
		source.resizeSubRectTo(0, 0, width, height, scaled);
		source.close();

		BuildAid.LOGGER.info("Imagem reduzida de {}x{} para {}x{}", width, height, targetWidth, targetHeight);
		return scaled;
	}

	private void evictIfNeeded(Slot slot) {
		BuildAidConfig config = BuildAidConfig.get();
		int max = slot == full ? config.cache.maxTextures : config.cache.maxThumbnails;

		// Com varios paineis na tela, proteger apenas a "imagem ativa" faria o cache despejar
		// justamente o que esta sendo desenhado, e o painel piscaria a cada frame. Por isso a
		// protecao cobre todas as imagens em uso (paineis visiveis + holograma + ativa).
		Set<String> inUse = slot == full ? config.imagesInUse() : Set.of();

		Iterator<Map.Entry<String, DynamicTexture>> it = slot.textures.entrySet().iterator();
		while (slot.textures.size() > max && it.hasNext()) {
			Map.Entry<String, DynamicTexture> eldest = it.next();
			if (inUse.contains(eldest.getKey())) {
				continue;
			}
			it.remove();
			releaseTexture(slot, eldest.getKey());
		}
	}

	private void releaseTexture(Slot slot, String imageId) {
		Loaded loaded = slot.metadata.remove(imageId);
		if (loaded != null) {
			// release() desregistra e fecha a textura (que por sua vez fecha a NativeImage).
			Minecraft.getInstance().getTextureManager().release(loaded.textureId());
		}
	}

	/** Libera uma imagem especifica dos dois caches (ex.: apagada da biblioteca). */
	public void unload(String imageId) {
		for (Slot slot : List.of(full, thumbnails)) {
			slot.textures.remove(imageId);
			releaseTexture(slot, imageId);
			slot.failed.remove(imageId);
		}
	}

	/** Libera tudo. Chamado ao sair do jogo. */
	public void unloadAll() {
		for (Slot slot : List.of(full, thumbnails)) {
			for (String id : new ArrayList<>(slot.metadata.keySet())) {
				releaseTexture(slot, id);
			}
			slot.textures.clear();
			slot.failed.clear();
		}
	}
}
