package com.foxo.buildaid.net;

import com.foxo.buildaid.BuildAid;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Fatia 1 da deteccao de mod: anuncio de presenca por pacote de rede.
 *
 * <p>O cliente anuncia "eu tenho o BuildAid" ao conectar; um servidor que tambem
 * tenha o mod responde com a lista de jogadores detectados. Contra servidor
 * vanilla (sem Fabric/BuildAid) o canal nao e negociado e {@link ClientPlayNetworking#canSend}
 * volta {@code false} -- o mod degrada em silencio, sem risco de kick.
 *
 * <p>Registrado no initializer do cliente; contra servidor sem o canal nada e enviado.
 */
public final class ModDetection {
	private ModDetection() {
	}

	// --- C2S: anuncio de presenca (versao do mod) ---

	public record PresenceC2S(String version) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<PresenceC2S> TYPE =
				new CustomPacketPayload.Type<>(com.foxo.buildaid.BuildAidClient.id("presence_c2s"));

		public static final StreamCodec<RegistryFriendlyByteBuf, PresenceC2S> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, PresenceC2S::version,
						PresenceC2S::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// --- S2C: lista de jogadores que tem o mod ---

	public record PlayersS2C(List<String> names) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<PlayersS2C> TYPE =
				new CustomPacketPayload.Type<>(com.foxo.buildaid.BuildAidClient.id("players_s2c"));

		public static final StreamCodec<RegistryFriendlyByteBuf, PlayersS2C> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PlayersS2C::names,
						PlayersS2C::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Jogadores com o mod conhecidos ate agora (atualizado pelo S2C). Fatia 2 consome. */
	public static final CopyOnWriteArraySet<String> playersWithMod = new CopyOnWriteArraySet<>();

	/** Registra codecs, receivers e o anuncio no login. Chamar uma vez no initializer. */
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(PresenceC2S.TYPE, PresenceC2S.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayersS2C.TYPE, PlayersS2C.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(PlayersS2C.TYPE, (payload, context) -> {
			List<String> names = payload.names();
			context.client().execute(() -> {
				playersWithMod.clear();
				playersWithMod.addAll(names);
				com.foxo.buildaid.Feedback.info("buildaid.msg.mod_detected", names.size());
			});
		});

		// Sair do mundo/servidor: limpa a lista para nao vazar entre conexoes.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> playersWithMod.clear());

		// Anuncia presenca ao entrar num mundo/servidor que declare o canal.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				client.execute(ModDetection::announcePresence));
	}

	/** True se o jogador (nick) anunciou presenca do BuildAid nesta conexao. */
	public static boolean isPlayerWithMod(String name) {
		return name != null && playersWithMod.contains(name);
	}

	/** Quantidade de jogadores com o mod conhecida agora (inclui o proprio). */
	public static int count() {
		return playersWithMod.size();
	}

	/**
	 * Envia o anuncio de presenca se (e somente se) o servidor declarou o canal.
	 * Nunca lanca: contra servidor vanilla apenas nao faz nada.
	 */
	public static void announcePresence() {
		try {
			if (ClientPlayNetworking.canSend(PresenceC2S.TYPE)) {
				String version = net.fabricmc.loader.api.FabricLoader.getInstance()
						.getModContainer(BuildAid.MOD_ID)
						.map(c -> c.getMetadata().getVersion().getFriendlyString())
						.orElse("desconhecida");
				ClientPlayNetworking.send(new PresenceC2S(version));
				BuildAid.LOGGER.debug("Presenca anunciada ao servidor");
			}
		} catch (RuntimeException e) {
			// Fora de jogo ou conexao encerrada no meio: ignora, proximo JOIN tenta de novo.
			BuildAid.LOGGER.debug("Anuncio de presenca ignorado: {}", e.toString());
		}
	}
}
