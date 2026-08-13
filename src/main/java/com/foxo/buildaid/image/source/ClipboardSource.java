package com.foxo.buildaid.image.source;

import com.foxo.buildaid.BuildAid;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Le imagem do clipboard do sistema.
 *
 * <p>O GLFW so devolve texto do clipboard, entao imagem exige AWT. Isso e seguro no Windows,
 * mas AWT e GLFW brigam no macOS -- por isso a funcao fica desligada la. Texto (caminho de
 * arquivo ou URL) continua funcionando em todo lugar, porque vem do proprio GLFW.
 *
 * <p>Duas armadilhas do Windows sao tratadas aqui, e as duas produzem o sintoma "colei e nao
 * aconteceu nada":
 *
 * <ol>
 *   <li><b>Clipboard ocupado.</b> O Windows deixa um app por vez abrir o clipboard. Se outro
 *       programa estiver com ele naquele instante, o AWT lanca IllegalStateException. Por isso
 *       as tentativas repetidas.</li>
 *   <li><b>Alfa zerado.</b> Navegadores costumam colocar a imagem como DIB de 32 bits com o
 *       byte de alfa sem uso (zero). O Java le isso como ARGB e devolve uma imagem
 *       inteiramente transparente -- a colagem "funciona", mas nada aparece na tela.</li>
 * </ol>
 */
public final class ClipboardSource {
	private static final int ATTEMPTS = 5;
	private static final long RETRY_DELAY_MS = 60;

	private ClipboardSource() {
	}

	/** AWT so e usado onde sabidamente convive com o GLFW do Minecraft. */
	public static boolean imageSupported() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		return !os.contains("mac");
	}

	/**
	 * Devolve os bytes PNG da imagem no clipboard, ou {@code null} se nao houver imagem.
	 * Bloqueia -- chame de uma thread de fundo.
	 */
	public static byte[] readImage() throws Exception {
		if (!imageSupported()) {
			throw new UnsupportedOperationException("clipboard image not supported on this OS");
		}

		System.setProperty("java.awt.headless", "false");
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

		IllegalStateException busy = null;
		for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
			try {
				return extract(clipboard);
			} catch (IllegalStateException e) {
				// "cannot open system clipboard": outro app esta com ele aberto. Tentar de novo
				// resolve na esmagadora maioria das vezes.
				busy = e;
				BuildAid.LOGGER.debug("Clipboard ocupado (tentativa {}/{})", attempt, ATTEMPTS);
				Thread.sleep(RETRY_DELAY_MS * attempt);
			}
		}

		throw busy;
	}

	private static byte[] extract(Clipboard clipboard) throws Exception {
		if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
			try {
				Image image = (Image) clipboard.getData(DataFlavor.imageFlavor);
				if (image != null) {
					return toPngBytes(image);
				}
			} catch (IllegalStateException e) {
				throw e; // clipboard ocupado: deixa a camada de cima tentar de novo
			} catch (Exception e) {
				// Flavor anunciado mas ilegivel. Ainda pode haver um arquivo copiado.
				BuildAid.LOGGER.debug("imageFlavor presente mas ilegivel, tentando lista de arquivos", e);
			}
		}

		// Windows Explorer copia arquivo como lista de arquivos, nao como imagem.
		if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
			Object data = clipboard.getData(DataFlavor.javaFileListFlavor);
			if (data instanceof List<?> files && !files.isEmpty() && files.getFirst() instanceof File file) {
				return Files.readAllBytes(file.toPath());
			}
		}

		// Nenhum formato util: registra o que havia, que e o que responde "por que nao colou?".
		BuildAid.LOGGER.info("Clipboard sem imagem. Formatos disponiveis: {}",
				Arrays.toString(clipboard.getAvailableDataFlavors()));
		return null;
	}

	private static byte[] toPngBytes(Image image) throws Exception {
		BufferedImage buffered = toBufferedImage(image);

		if (isFullyTransparent(buffered)) {
			// Imagem inteira com alfa 0 nunca e intencional -- e o DIB do Windows sem alfa.
			BuildAid.LOGGER.info("Imagem do clipboard veio sem canal alfa valido; tornando opaca");
			forceOpaque(buffered);
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(buffered, "png", out)) {
			throw new IllegalStateException("could not encode clipboard image as PNG");
		}
		BuildAid.LOGGER.debug("Clipboard: {} bytes de PNG", out.size());
		return out.toByteArray();
	}

	private static BufferedImage toBufferedImage(Image image) {
		if (image instanceof BufferedImage bi && bi.getType() == BufferedImage.TYPE_INT_ARGB) {
			return bi;
		}

		Image loaded = forceLoad(image);

		int width = loaded.getWidth(null);
		int height = loaded.getHeight(null);
		if (width <= 0 || height <= 0) {
			throw new IllegalStateException("clipboard image has invalid size (" + width + "x" + height + ")");
		}

		BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(loaded, 0, 0, null);
		g.dispose();
		return copy;
	}

	/**
	 * Espera a imagem terminar de carregar.
	 *
	 * <p>O AWT entrega a imagem do clipboard de forma assincrona: ate os pixels chegarem,
	 * {@code getWidth(null)} devolve <b>-1</b>. O codigo lia esse -1 como tamanho invalido e
	 * abortava a colagem com "erro de tamanho da imagem" -- sem que houvesse nada de errado com a
	 * imagem. O construtor do {@link ImageIcon} bloqueia ate o carregamento terminar (usa um
	 * MediaTracker por dentro), que e o jeito curto de resolver isso.
	 */
	private static Image forceLoad(Image image) {
		try {
			return new ImageIcon(image).getImage();
		} catch (Exception e) {
			BuildAid.LOGGER.debug("Nao consegui forcar o carregamento da imagem do clipboard", e);
			return image;
		}
	}

	/** Sai no primeiro pixel opaco, entao no caso normal custa praticamente nada. */
	private static boolean isFullyTransparent(BufferedImage image) {
		if (!image.getColorModel().hasAlpha()) {
			return false;
		}
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					return false;
				}
			}
		}
		return true;
	}

	private static void forceOpaque(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setRGB(x, y, image.getRGB(x, y) | 0xFF000000);
			}
		}
	}
}
