package com.foxo.buildaid.image;

/**
 * Metadados de uma imagem de referencia guardada na biblioteca local.
 * Nao segura pixels nem textura de GPU -- isso e responsabilidade da {@link ImageLibrary}.
 *
 * @param id          identificador estavel (sha1 do conteudo), usado tambem no Identifier da textura
 * @param displayName nome mostrado na interface
 * @param fileName    nome do arquivo dentro de config/buildaid/images/
 * @param width       largura original em pixels
 * @param height      altura original em pixels
 * @param addedAt     epoch millis de quando foi importada
 * @param source      de onde veio ("url", "clipboard", "file")
 */
public record RefImage(
		String id,
		String displayName,
		String fileName,
		int width,
		int height,
		long addedAt,
		String source
) {
	public float aspectRatio() {
		return height == 0 ? 1.0f : (float) width / (float) height;
	}
}
