package com.foxo.buildaid;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Mensagens curtas para o jogador, sempre traduziveis.
 *
 * <p>Despachadas na render thread, porque as importacoes terminam em threads de fundo.
 */
public final class Feedback {
	private Feedback() {
	}

	/** Aviso passageiro na action bar -- nao polui o chat quando o jogador segura uma tecla. */
	public static void info(Component message) {
		send(message, "§b", true);
	}

	/** Erro vai para o chat, onde fica parado para o jogador conseguir ler. */
	public static void error(Component message) {
		send(message, "§c", false);
	}

	public static void info(String translationKey, Object... args) {
		info(Component.translatable(translationKey, args));
	}

	public static void error(String translationKey, Object... args) {
		error(Component.translatable(translationKey, args));
	}

	/** Liga/desliga: evita duas chaves de traducao para cada opcao. */
	public static Component state(boolean on) {
		return Component.translatable(on ? "buildaid.state.on" : "buildaid.state.off");
	}

	private static void send(Component message, String colorCode, boolean overlay) {
		Minecraft client = Minecraft.getInstance();
		Component full = Component.literal(colorCode + "[BuildAid] §f").append(message);

		client.execute(() -> {
			if (client.player == null) {
				BuildAid.LOGGER.info(message.getString());
			} else if (overlay) {
				client.player.sendOverlayMessage(full);
			} else {
				client.player.sendSystemMessage(full);
			}
		});
	}
}
