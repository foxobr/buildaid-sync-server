package com.foxo.buildaid.config;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Desfazer global: restaura a configuracao viva para o estado anterior de qualquer
 * edicao estrutural (adicionar/remover/excluir paineis, anotacoes, hologramas, formas
 * e a imagem em foco).
 *
 * <p>O historico e mantido em memoria (nao persiste entre reinicios do jogo) e cobre
 * ate {@link #LIMIT} passos. Cada mutacao que queremos poder desfazer chama
 * {@link #push()} ANTES de alterar a configuracao -- assim o topo da pilha guarda o
 * estado "antes" daquela edicao.
 */
public final class GlobalUndo {

	private static final int LIMIT = 16;

	private static final Deque<BuildAidConfig.Snapshot> history = new ArrayDeque<>();

	private GlobalUndo() {
	}

	/** Guarda o estado atual da configuracao (antes da proxima mutacao). */
	public static void push() {
		history.addFirst(BuildAidConfig.Snapshot.copyOf(BuildAidConfig.get()));
		while (history.size() > LIMIT) {
			history.removeLast();
		}
	}

	/**
	 * Restaura a configuracao para o ultimo estado salvo.
	 *
	 * @return true se havia algo para desfazer, false se o historico estava vazio.
	 */
	public static boolean undo() {
		BuildAidConfig.Snapshot previous = history.pollFirst();
		if (previous == null) {
			return false;
		}
		previous.applyTo(BuildAidConfig.get());
		BuildAidConfig.get().clamp();
		BuildAidConfig.get().save();
		return true;
	}

	/** Limpa o historico (por exemplo, ao trocar de mundo). */
	public static void clear() {
		history.clear();
	}

	public static boolean hasHistory() {
		return !history.isEmpty();
	}
}
