package com.foxo.buildaid;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Fatia 2 da deteccao de mod: comando /buildaidplayers no cliente.
 *
 * <p>Lista quem esta com o BuildAid na mesma conexao (segundo o servidor,
 * via pacote PlayersS2C) e compara com os jogadores online para mostrar
 * tambem quem NAO tem o mod.
 */
public final class ModPlayersCommand {

    private ModPlayersCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands
                        .literal("buildaidplayers")
                        .executes(ctx -> {
                            showPlayers();
                            return 1;
                        })));
    }

    /** Monta e mostra a lista de jogadores com/sem mod na action bar e chat. */
    private static void showPlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) {
            Feedback.error("buildaid.msg.no_players_with_mod");
            return;
        }

        List<String> online = new ArrayList<>();
        client.getConnection().getOnlinePlayers()
                .forEach(info -> online.add(info.getProfile().name()));

        List<String> withMod = new ArrayList<>();
        List<String> withoutMod = new ArrayList<>();
        for (String name : online) {
            if (com.foxo.buildaid.net.ModDetection.isPlayerWithMod(name)) {
                withMod.add(name);
            } else {
                withoutMod.add(name);
            }
        }

        if (withMod.isEmpty()) {
            Feedback.info("buildaid.msg.no_players_with_mod");
        } else {
            Feedback.info("buildaid.msg.mod_detected", withMod.size());
            for (String name : withMod) {
                Feedback.info("buildaid.msg.player_with_mod", name);
            }
        }
    }
}
