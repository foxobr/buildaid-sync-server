package com.foxo.buildaid;

import com.foxo.buildaid.build.AreaSelection;
import com.foxo.buildaid.build.WorldGizmos;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.hud.GhostOverlayElement;
import com.foxo.buildaid.hud.InfoHudElement;
import com.foxo.buildaid.hud.RefPanelElement;
import com.foxo.buildaid.image.ImageImporter;
import com.foxo.buildaid.image.ImageLibrary;
import com.foxo.buildaid.image.ImageStore;
import com.foxo.buildaid.image.RefImage;
import com.foxo.buildaid.screen.BuildAidMenuScreen;
import com.foxo.buildaid.screen.RefEditScreen;
import com.foxo.buildaid.shape.ShapeGuide;
import com.foxo.buildaid.world.ImageHologram;
import com.foxo.buildaid.world.WorldManipulator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Ponto de entrada client-side. O mod nao registra nada no servidor nem envia pacotes.
 */
public class BuildAidClient implements ClientModInitializer {
	public static ImageStore store;
	public static ImageLibrary library;
	public static ImageImporter importer;

	@Override
	public void onInitializeClient() {
		store = new ImageStore(BuildAidConfig.dataDir());
		store.load();
		library = new ImageLibrary(store);
		importer = new ImageImporter(store);

		Keys.register();
		ImageHologram.register(library);
		ShapeGuide.register();

		// addFirst = desenhado antes de tudo, ou seja, atras da mira/hotbar/chat.
		HudElementRegistry.addFirst(id("ghost_overlay"), new GhostOverlayElement(library));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("reference_panel"),
				new RefPanelElement(library));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("info_hud"),
				new InfoHudElement());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("music_hud"),
				new com.foxo.buildaid.hud.MusicHudElement());

		com.foxo.buildaid.audio.AudioPlayer.get().setVolume(BuildAidConfig.get().music.volume / 100.0f);

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			com.foxo.buildaid.net.music.MusicSyncClient.get().autoJoinCurrentServer(client);
		});

		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			com.foxo.buildaid.net.music.MusicSyncClient.get().onServerLeave();
		});

		ClientTickEvents.END_CLIENT_TICK.register(BuildAidClient::onEndTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			library.unloadAll();
			com.foxo.buildaid.audio.AudioPlayer.get().stop();
			com.foxo.buildaid.net.music.MusicSyncClient.get().disconnect();
		});

		BuildAid.LOGGER.info("BuildAid pronto ({} imagem(ns) na biblioteca)", store.all().size());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BuildAid.MOD_ID, path);
	}

	private static void onEndTick(Minecraft client) {
		BuildAidConfig config = BuildAidConfig.get();
		boolean dirty = false;

		// --- teclas com atalho de fabrica ---

		while (Keys.openMenu.consumeClick()) {
			client.setScreenAndShow(new BuildAidMenuScreen());
		}

		while (Keys.togglePanel.consumeClick()) {
			// Interruptor geral: se qualquer painel estiver visivel, esconde todos; senao mostra todos.
			boolean anyVisible = config.panels.stream().anyMatch(p -> p.visible);
			for (BuildAidConfig.Panel p : config.panels) {
				p.visible = !anyVisible;
			}
			dirty = true;
		}

		while (Keys.toggleSelection.consumeClick()) {
			AreaSelection.toggleMode();
		}

		while (Keys.markCorner.consumeClick()) {
			AreaSelection.markCorner(client);
		}

		// --- teclas sem atalho de fabrica (tambem acessiveis pelo menu) ---

		while (Keys.editPanel.consumeClick()) {
			client.setScreenAndShow(new RefEditScreen(library));
		}

		while (Keys.toggleGhost.consumeClick()) {
			config.ghost.enabled = !config.ghost.enabled;
			Feedback.info("buildaid.msg.ghost", Feedback.state(config.ghost.enabled));
			dirty = true;
		}

		while (Keys.cycleImage.consumeClick()) {
			dirty |= cycleImage(config);
		}

		while (Keys.pasteClipboard.consumeClick()) {
			importer.fromClipboard();
		}

		while (Keys.openFile.consumeClick()) {
			importer.fromFilePicker();
		}

		while (Keys.opacityUp.consumeClick()) {
			adjustOpacity(config, 0.05f);
			dirty = true;
		}

		while (Keys.opacityDown.consumeClick()) {
			adjustOpacity(config, -0.05f);
			dirty = true;
		}

		while (Keys.clearSelection.consumeClick()) {
			AreaSelection.clear();
		}

		while (Keys.toggleGrid.consumeClick()) {
			config.grid.enabled = !config.grid.enabled;
			Feedback.info("buildaid.msg.grid", Feedback.state(config.grid.enabled));
			dirty = true;
		}

		while (Keys.toggleInfoHud.consumeClick()) {
			config.infoHud.enabled = !config.infoHud.enabled;
			Feedback.info("buildaid.msg.info_hud", Feedback.state(config.infoHud.enabled));
			dirty = true;
		}

		while (Keys.placeHologram.consumeClick()) {
			ImageHologram.placeNewAtCrosshair(client);
		}

		while (Keys.placeShape.consumeClick()) {
			ShapeGuide.placeNewAtCrosshair(client);
		}

		while (Keys.selectTarget.consumeClick()) {
			WorldManipulator.selectNext(client);
		}

		while (Keys.rotateTarget.consumeClick()) {
			WorldManipulator.rotate(1);
		}

		while (Keys.musicTogglePlay.consumeClick()) {
			com.foxo.buildaid.net.music.MusicSyncClient.get().togglePlay();
		}

		while (Keys.musicSkip.consumeClick()) {
			com.foxo.buildaid.net.music.MusicSyncClient.get().skip();
		}

		while (Keys.musicVolumeUp.consumeClick()) {
			config.music.volume = Math.clamp(config.music.volume + 5, 0, 100);
			com.foxo.buildaid.audio.AudioPlayer.get().setVolume(config.music.volume / 100.0f);
			Feedback.info("buildaid.msg.music_volume", config.music.volume);
			dirty = true;
		}

		while (Keys.musicVolumeDown.consumeClick()) {
			config.music.volume = Math.clamp(config.music.volume - 5, 0, 100);
			com.foxo.buildaid.audio.AudioPlayer.get().setVolume(config.music.volume / 100.0f);
			Feedback.info("buildaid.msg.music_volume", config.music.volume);
			dirty = true;
		}

		while (Keys.musicToggleHud.consumeClick()) {
			config.music.hudEnabled = !config.music.hudEnabled;
			Feedback.info("buildaid.msg.music_hud_state", Feedback.state(config.music.hudEnabled));
			dirty = true;
		}

		// Segurar para arrastar: precisa ser lido todo tick, nao por clique.
		WorldManipulator.tick(client);

		if (dirty) {
			config.save();
		}

		// Emitido todo tick: os gizmos valem ate o proximo tick.
		WorldGizmos.emit(client);
	}

	/** Passa o primeiro painel para a proxima imagem da biblioteca. */
	private static boolean cycleImage(BuildAidConfig config) {
		List<RefImage> all = store.all();
		if (all.isEmpty()) {
			Feedback.error("buildaid.msg.library_empty");
			return false;
		}

		BuildAidConfig.Panel panel = config.panels.isEmpty()
				? config.addPanel(null)
				: config.panels.getFirst();

		int current = -1;
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).id().equals(panel.imageId)) {
				current = i;
				break;
			}
		}

		RefImage next = all.get((current + 1) % all.size());
		panel.imageId = next.id();
		panel.visible = true;
		panel.imageScale = 1.0f;
		panel.imageOffsetX = 0.0f;
		panel.imageOffsetY = 0.0f;
		config.activeImageId = next.id();
		Feedback.info("buildaid.msg.image_now", next.displayName());
		return true;
	}

	/** Ajusta a opacidade do que estiver em uso: overlay tela cheia se ligado, senao os paineis. */
	private static void adjustOpacity(BuildAidConfig config, float delta) {
		if (config.ghost.enabled) {
			config.ghost.opacity = Math.clamp(config.ghost.opacity + delta, 0.0f, 1.0f);
			Feedback.info("buildaid.msg.opacity_ghost", Math.round(config.ghost.opacity * 100));
			return;
		}

		if (config.panels.isEmpty()) {
			return;
		}
		for (BuildAidConfig.Panel p : config.panels) {
			p.opacity = Math.clamp(p.opacity + delta, 0.0f, 1.0f);
		}
		Feedback.info("buildaid.msg.opacity_panel", Math.round(config.panels.getFirst().opacity * 100));
	}
}
