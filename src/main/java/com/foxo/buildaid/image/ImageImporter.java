package com.foxo.buildaid.image;

import com.foxo.buildaid.BuildAid;
import com.foxo.buildaid.Feedback;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.image.source.ClipboardSource;
import com.foxo.buildaid.image.source.NativeFilePicker;
import com.foxo.buildaid.image.source.UrlSource;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Orquestra as tres fontes de imagem. Todo o trabalho pesado (rede, IO, dialogo nativo) roda
 * fora da render thread; so a troca da imagem ativa volta para ela.
 */
public final class ImageImporter {
	private static final Executor WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BuildAid-Import");
		t.setDaemon(true);
		return t;
	});

	private final ImageStore store;

	public ImageImporter(ImageStore store) {
		this.store = store;
	}

	/** Baixa de uma URL e passa a exibir. */
	public void fromUrl(String url) {
		WORKER.execute(() -> {
			try {
				Feedback.info("buildaid.msg.downloading");
				byte[] data = UrlSource.download(url);
				RefImage image = store.importBytes(data, nameFromUrl(url), "url");
				activate(image);
			} catch (Exception e) {
				// Sinalizado pelo UrlSource: o link e de uma pagina, nao da imagem.
				if ("PAGE_NOT_IMAGE".equals(e.getMessage())) {
					Feedback.error("buildaid.msg.page_not_image");
					return;
				}
				BuildAid.LOGGER.warn("Download falhou: {}", url, e);
				Feedback.error("buildaid.msg.download_failed", describe(e));
			}
		});
	}

	/** Cola do clipboard: imagem, ou texto que seja URL/caminho de arquivo. */
	public void fromClipboard() {
		WORKER.execute(() -> {
			try {
				if (!ClipboardSource.imageSupported()) {
					Feedback.error("buildaid.msg.clipboard_unsupported");
					return;
				}

				byte[] data = ClipboardSource.readImage();
				if (data != null) {
					RefImage image = store.importBytes(data, "Clipboard", "clipboard");
					activate(image);
					return;
				}

				// Sem imagem: tenta o texto do clipboard (GLFW, funciona em todo sistema).
				String text = readClipboardText();
				if (text == null || text.isBlank()) {
					Feedback.error("buildaid.msg.clipboard_empty");
					return;
				}
				if (UrlSource.looksLikeUrl(text)) {
					fromUrl(text);
					return;
				}

				// Path.of() estoura InvalidPathException em texto comum (ex.: HTML com '<'),
				// e essa excecao vazava como "Illegal char <" na cara do jogador.
				Path path = asPath(text);
				if (path != null && Files.isRegularFile(path)) {
					RefImage image = store.importFile(path, "file");
					activate(image);
					return;
				}
				Feedback.error("buildaid.msg.clipboard_no_image");
			} catch (Exception e) {
				BuildAid.LOGGER.warn("Colar do clipboard falhou", e);
				Feedback.error("buildaid.msg.paste_failed", describe(e));
			}
		});
	}

	/** Abre o seletor de arquivo nativo do sistema. */
	public void fromFilePicker() {
		WORKER.execute(() -> {
			try {
				String chosen = NativeFilePicker.open();
				if (chosen == null || chosen.isBlank()) {
					return; // usuario cancelou
				}
				RefImage image = store.importFile(Path.of(chosen), "file");
				activate(image);
			} catch (Throwable t) {
				// Throwable de proposito: falta de biblioteca nativa vem como UnsatisfiedLinkError.
				BuildAid.LOGGER.error("Seletor de arquivo nativo falhou", t);
				Feedback.error("buildaid.msg.picker_failed");
			}
		});
	}

	/** Teto de paineis criados automaticamente ao importar, para nao entulhar a tela. */
	private static final int AUTO_PANEL_LIMIT = 8;

	/**
	 * Passa a exibir a imagem.
	 *
	 * <p>Cada importacao abre um painel novo -- e o comportamento esperado agora que da para ter
	 * varias referencias na tela. Passando do teto, a imagem substitui a do ultimo painel em vez
	 * de continuar empilhando.
	 */
	public void activate(RefImage image) {
		Minecraft.getInstance().execute(() -> {
			BuildAidConfig config = BuildAidConfig.get();
			config.activeImageId = image.id();

			BuildAidConfig.Panel target;
			if (config.panels.size() >= AUTO_PANEL_LIMIT) {
				target = config.panels.getLast();
				target.imageId = image.id();
			} else {
				target = config.addPanel(image.id());
			}

			target.visible = true;
			// Imagem nova comeca centralizada e no zoom padrao.
			target.imageScale = 1.0f;
			target.imageOffsetX = 0.0f;
			target.imageOffsetY = 0.0f;

			config.save();
			Feedback.info("buildaid.msg.showing", image.displayName(), image.width(), image.height());
		});
	}

	/** Troca a imagem de um painel existente, sem criar outro. */
	public void setPanelImage(BuildAidConfig.Panel panel, RefImage image) {
		Minecraft.getInstance().execute(() -> {
			BuildAidConfig config = BuildAidConfig.get();
			panel.imageId = image.id();
			panel.visible = true;
			panel.imageScale = 1.0f;
			panel.imageOffsetX = 0.0f;
			panel.imageOffsetY = 0.0f;
			config.activeImageId = image.id();
			config.save();
			Feedback.info("buildaid.msg.showing", image.displayName(), image.width(), image.height());
		});
	}

	/** Devolve o texto como caminho, ou null se nao for um caminho valido no sistema. */
	private static Path asPath(String text) {
		try {
			return Path.of(text.trim().replace("\"", ""));
		} catch (InvalidPathException e) {
			return null;
		}
	}

	/**
	 * O clipboard de texto vem do GLFW, que so pode ser chamado na main thread -- por isso o
	 * salto para a render thread mesmo estando numa worker.
	 */
	private static String readClipboardText() {
		try {
			Minecraft client = Minecraft.getInstance();
			return CompletableFuture
					.supplyAsync(() -> client.keyboardHandler.getClipboard(), client)
					.get(2, TimeUnit.SECONDS);
		} catch (Exception e) {
			BuildAid.LOGGER.debug("Nao consegui ler o texto do clipboard", e);
			return null;
		}
	}

	private static String nameFromUrl(String url) {
		try {
			String path = java.net.URI.create(url).getPath();
			if (path != null && path.contains("/")) {
				String name = path.substring(path.lastIndexOf('/') + 1);
				if (!name.isBlank()) {
					return name;
				}
			}
		} catch (Exception ignored) {
			// cai no nome generico
		}
		return "Web";
	}

	private static String describe(Exception e) {
		String message = e.getMessage();
		return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
	}
}
