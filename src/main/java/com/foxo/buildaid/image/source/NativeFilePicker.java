package com.foxo.buildaid.image.source;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Abre a janela nativa de "Abrir arquivo" do sistema.
 *
 * <p>O {@code lwjgl-tinyfd} ja vem no classpath do Minecraft 26.2, entao da para chamar direto,
 * sem reflexao. A chamada BLOQUEIA ate o usuario escolher -- nunca chame da render thread ou o
 * jogo congela junto com o dialogo.
 */
public final class NativeFilePicker {
	private static final String[] PATTERNS = {"*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"};

	private NativeFilePicker() {
	}

	/** Devolve o caminho escolhido, ou {@code null} se o usuario cancelou. */
	public static String open() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer filters = stack.mallocPointer(PATTERNS.length);
			for (String pattern : PATTERNS) {
				filters.put(stack.UTF8(pattern));
			}
			filters.flip();

			return TinyFileDialogs.tinyfd_openFileDialog(
					"BuildAid - escolher imagem de referencia",
					"",
					filters,
					"Imagens (png, jpg, gif, bmp)",
					false
			);
		}
	}

	/** Variante para abrir um schematic do Litematica (.litematic). Mesma regra:
	 *  bloqueia a thread que chamar, entao so usar fora da render thread. */
	public static String openLitematic() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			String[] patterns = {"*.litematic"};
			PointerBuffer filters = stack.mallocPointer(patterns.length);
			for (String pattern : patterns) {
				filters.put(stack.UTF8(pattern));
			}
			filters.flip();

			return TinyFileDialogs.tinyfd_openFileDialog(
					"BuildAid - importar schematic (.litematic)",
					"",
					filters,
					"Schematic Litematica (*.litematic)",
					false
			);
		}
	}
}
