package com.foxo.buildaid.image.source;

import com.foxo.buildaid.BuildAid;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Baixa uma imagem a partir de uma URL colada pelo jogador.
 *
 * <p>Roda sempre fora da render thread. Limites deliberados: so http/https, 10s de timeout,
 * 20 MB e Content-Type de imagem -- o suficiente para nao travar o cliente se o link estiver
 * ruim ou apontar para um arquivo enorme.
 */
public final class UrlSource {
	public static final long MAX_BYTES = 20L * 1024 * 1024;

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private UrlSource() {
	}

	public static boolean looksLikeUrl(String text) {
		if (text == null) {
			return false;
		}
		String t = text.trim().toLowerCase(Locale.ROOT);
		return t.startsWith("http://") || t.startsWith("https://");
	}

	/** Baixa os bytes da imagem. Bloqueia -- chame de uma thread de fundo. */
	public static byte[] download(String rawUrl) throws IOException, InterruptedException {
		URI uri = URI.create(rawUrl.trim());
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new IOException("So aceito links http:// ou https://");
		}

		// Muita CDN devolve 403 para User-Agent desconhecido, entao vamos com cara de navegador.
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent",
						"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
								+ "(KHTML, like Gecko) Chrome/140.0 Safari/537.36 BuildAid/1.0")
				.header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
				.GET()
				.build();

		HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream body = response.body()) {
			if (response.statusCode() / 100 != 2) {
				throw new IOException("Servidor respondeu HTTP " + response.statusCode());
			}

			String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);

			// Erro classico: colar o link da PAGINA (Pinterest, Google Imagens) em vez do link
			// da imagem. Vale detectar e dizer o que fazer, em vez de "formato nao reconhecido".
			if (contentType.startsWith("text/html")) {
				throw new IOException("PAGE_NOT_IMAGE");
			}

			// Fora esse caso, nao confiamos no Content-Type: varios servidores mandam
			// application/octet-stream para imagens. Quem decide e a decodificacao, depois.
			if (!contentType.isEmpty() && !contentType.startsWith("image/")) {
				BuildAid.LOGGER.info("Content-Type inesperado ({}), tentando decodificar assim mesmo", contentType);
			}

			long declared = response.headers().firstValueAsLong("content-length").orElse(-1L);
			if (declared > MAX_BYTES) {
				throw new IOException("Imagem grande demais: " + (declared / (1024 * 1024)) + " MB (limite 20 MB)");
			}

			return readCapped(body);
		}
	}

	/** Le o corpo abortando se passar do limite -- nao confia no Content-Length. */
	private static byte[] readCapped(InputStream in) throws IOException {
		byte[] buffer = new byte[8192];
		var out = new java.io.ByteArrayOutputStream();
		long total = 0;
		int read;
		while ((read = in.read(buffer)) != -1) {
			total += read;
			if (total > MAX_BYTES) {
				throw new IOException("Imagem passou do limite de 20 MB durante o download");
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}
}
