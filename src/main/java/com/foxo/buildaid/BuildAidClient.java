package com.foxo.buildaid;

import com.foxo.buildaid.build.AreaSelection;
import com.foxo.buildaid.build.Blueprint;
import com.foxo.buildaid.build.LitematicReader;
import com.foxo.buildaid.build.WorldGizmos;
import com.foxo.buildaid.config.BuildAidConfig;
import com.foxo.buildaid.config.GlobalUndo;
import com.foxo.buildaid.hud.GhostOverlayElement;
import com.foxo.buildaid.hud.InfoHudElement;
import com.foxo.buildaid.hud.RefPanelElement;
import com.foxo.buildaid.image.ImageImporter;
import com.foxo.buildaid.image.ImageLibrary;
import com.foxo.buildaid.image.ImageStore;
import com.foxo.buildaid.image.RefImage;
import com.foxo.buildaid.net.ModDetection;
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
import java.util.Set;

import net.minecraft.network.chat.Component;

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
		ModDetection.register();
		ModPlayersCommand.register();
		        ImageHologram.register(library);
		ShapeGuide.register();
		// A projecao 3D da paleta e desenhada pelo WorldGizmos, junto dos outros gizmos.

		// addFirst = desenhado antes de tudo, ou seja, atras da mira/hotbar/chat.
		HudElementRegistry.addFirst(id("ghost_overlay"), new GhostOverlayElement(library));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("reference_panel"),
				new RefPanelElement(library));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("info_hud"),
				new InfoHudElement());
		// Depois dos paineis: as anotacoes ficam por cima das referencias, atras do chat.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("notes"),
				new com.foxo.buildaid.hud.NoteHudElement());
		// Contagem de jogadores com o mod: por cima de tudo, perto do chat.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("mod_players_hud"),
				new com.foxo.buildaid.hud.ModPlayersHudElement());

		ClientTickEvents.END_CLIENT_TICK.register(BuildAidClient::onEndTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			library.unloadAll();
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

		while (Keys.layerNext.consumeClick()) {
			WorldManipulator.stepLayer(1);
		}

		while (Keys.layerPrev.consumeClick()) {
			WorldManipulator.stepLayer(-1);
		}

		while (Keys.tapeMeasure.consumeClick()) {
			com.foxo.buildaid.build.TapeMeasure.toggleOrMark(client);
		}

		while (Keys.tapeRestore.consumeClick()) {
			if (!com.foxo.buildaid.build.TapeMeasure.restoreLast(client)) {
				Feedback.error("buildaid.msg.tape_history_empty");
			}
		}

		// Fixa a medicao atual (ou a ultima) como marcador persistente no mundo.
		while (Keys.tapePin.consumeClick()) {
			if (com.foxo.buildaid.build.TapeMeasure.pinCurrent(client)) {
				Feedback.info("buildaid.msg.tape_pinned");
			} else {
				Feedback.error("buildaid.msg.tape_materials_none");
			}
		}

		// Remove o marcador mais recente.
		while (Keys.tapeUnpin.consumeClick()) {
			if (!com.foxo.buildaid.build.TapeMeasure.unpinLast(client)) {
				Feedback.error("buildaid.msg.tape_pins_empty");
			}
		}

		// Materiais da regua: mostra os blocos reais dentro da regiao medida.
		while (Keys.tapeMaterials.consumeClick()) {
			var mats = com.foxo.buildaid.build.TapeMeasure.scanRegionMaterials(client);
			if (mats.isEmpty()) {
				Feedback.error("buildaid.msg.tape_materials_none");
			} else {
				Feedback.info("buildaid.msg.tape_materials", mats.size());
				int shown = 0;
				for (var m : mats) {
					if (shown++ >= 8 && mats.size() > 9) {
						Feedback.info("buildaid.msg.tape_materials_more", mats.size() - shown + 1);
						break;
					}
					Feedback.info(m.name().getString() + ": " + m.stackSummary());
				}
			}
		}

		// Paleta da regua: equipa na hotbar os blocos encontrados na regiao medida.
		while (Keys.tapePalette.consumeClick()) {
		    if (client.player != null && com.foxo.buildaid.build.TapeMeasure.lastMeasurement() != null) {
		        com.foxo.buildaid.build.TapePalette.equipFromMeasurement(client);
		    } else {
		        Feedback.error("buildaid.msg.tape_materials_none");
		    }
		}

		// Simetria: alterna o modo de simetria na configuracao.
		while (Keys.symmetryToggle.consumeClick()) {
		    if (client.player != null) {
		        config.symmetry.enabled = !config.symmetry.enabled;
		        Feedback.info("buildaid.msg.symmetry_enabled", Feedback.state(config.symmetry.enabled));
		    }
		}

		// Simetria: cicla plano unico -> cruz quadrupla -> radial N eixos.
		while (Keys.symmetryMode.consumeClick()) {
			if (client.player != null) {
				config.symmetry.cycle();
				config.symmetry.enabled = true;
				config.save();
				if (config.symmetry.type == 1) {
					Feedback.info("buildaid.msg.symmetry_mode_radial", "R" + config.symmetry.arms);
				} else {
					Feedback.info("buildaid.msg.symmetry_mode",
						Component.translatable(config.symmetry.mode == 1
							? "buildaid.msg.symmetry_mode_quad"
							: "buildaid.msg.symmetry_mode_single"));
				}
			}
		}

		// Contador de progresso da construcao: mostra percentual de preenchimento da
		// regiao medida e lista cada tipo de bloco (com stacks/baus) numa mensagem
		// fixa no chat, para o jogador poder reler enquanto constroi.
				while (Keys.progressCounter.consumeClick()) {
					var m = com.foxo.buildaid.build.TapeMeasure.lastMeasurement();
					if (m == null) {
						Feedback.error("buildaid.msg.tape_history_empty");
					} else {
						var a = m.start();
						var b = m.end();
						if (a == null || b == null || client.level == null) {
							Feedback.error("buildaid.msg.tape_history_empty");
						} else {
						if (com.foxo.buildaid.build.RegionScanner.isTooLarge(a, b)) {
							Feedback.error("buildaid.msg.region_too_large");
							continue;
						}
						var counts = com.foxo.buildaid.build.RegionScanner.countBlocks(client, a, b);
						long volume = (long) (Math.abs(a.getX() - b.getX()) + 1)
								* (Math.abs(a.getY() - b.getY()) + 1)
								* (Math.abs(a.getZ() - b.getZ()) + 1);
						long filled = counts.values().stream().mapToInt(Integer::intValue).sum();
						int pct = volume == 0 ? 0 : (int) (filled * 100L / volume);
						int distinct = counts.size();
						// Cabecalho com volume, preenchimento e tipos distintos.
						Feedback.infoChat("buildaid.msg.progress_header",
								volume, filled, pct, distinct);
						if (filled == 0) {
							// Regiao vazia: nada a listar.
							Feedback.infoChat("buildaid.msg.progress_none");
						} else {
							// Cada tipo em linha propria, do mais comum para o menos comum.
							var sorted = counts.entrySet().stream()
									.sorted((x, y) -> Integer.compare(y.getValue(), x.getValue()))
									.toList();
							for (var entry : sorted) {
								net.minecraft.world.level.block.Block bk = entry.getKey();
								int count = entry.getValue();
								int stacks = (count + 63) / 64;
								int chests = (stacks + 26) / 27;
								String name = bk.getName().getString();
								Feedback.infoChat("buildaid.msg.progress_detail",
										name, count, stacks, chests);
							}
						}
						}
					}
				}

					// Blueprint: captura/mostra/exporta/importa a regiao medida como ghost.
					while (Keys.toggleBlueprint.consumeClick()) {
						if (client.player != null) {
							var measurement = com.foxo.buildaid.build.TapeMeasure.lastMeasurement();
							if (measurement != null && measurement.start() != null && measurement.end() != null) {
								if (Blueprint.current == null) {
									// Captura a regiao medida e projeta o ghost no mundo.
									var bp = com.foxo.buildaid.build.Blueprint.capture(
											client, measurement.start(), measurement.end());
									if (bp == null) {
										Feedback.error("buildaid.msg.region_too_large");
									} else {
										Blueprint.current = bp;
										Feedback.info("buildaid.msg.blueprint_toggled", Feedback.state(true));
									}
								} else {
									Blueprint.current = null;
									Feedback.info("buildaid.msg.blueprint_toggled", Feedback.state(false));
								}
							} else {
								Feedback.error("buildaid.msg.tape_history_empty");
							}
						}
					}

					while (Keys.blueprintExport.consumeClick()) {
						if (client.player != null) {
							// Exporta o blueprint atual (ou captura da medida, se houver e nao hover atual).
							com.foxo.buildaid.build.Blueprint bp = Blueprint.current;
							if (bp == null) {
								var measurement = com.foxo.buildaid.build.TapeMeasure.lastMeasurement();
								if (measurement != null && measurement.start() != null && measurement.end() != null) {
									bp = com.foxo.buildaid.build.Blueprint.capture(
											client, measurement.start(), measurement.end());
								}
							}
							if (bp == null) {
								Feedback.error("buildaid.msg.tape_history_empty");
							} else if (bp.blocks.isEmpty()) {
								Feedback.error("buildaid.msg.blueprint_empty");
							} else {
								String json = bp.toJson();
								boolean saved = com.foxo.buildaid.build.BlueprintIO.save(json);
								boolean copied = com.foxo.buildaid.build.Blueprint.copyText(json);
								if (saved && copied) {
									Feedback.info("buildaid.msg.blueprint_exported",
											bp.blocks.size(), "arquivo e area de transferencia");
								} else if (saved) {
									Feedback.info("buildaid.msg.blueprint_exported",
											bp.blocks.size(), "arquivo");
								} else if (copied) {
									Feedback.info("buildaid.msg.blueprint_exported",
											bp.blocks.size(), "area de transferencia");
								} else {
									Feedback.error("buildaid.msg.blueprint_export_failed");
								}
							}
						}
					}

					while (Keys.blueprintImport.consumeClick()) {
						if (client.player != null) {
							// Abre a caixa de texto; o JSON colado e validado antes de virar ghost.
							client.setScreenAndShow(new com.foxo.buildaid.screen.TextPromptScreen(
									null,
									net.minecraft.network.chat.Component.translatable("buildaid.menu.blueprint_import_title"),
									net.minecraft.network.chat.Component.translatable("buildaid.msg.blueprint_import_guide"),
									"",
									text -> {
										var bp = com.foxo.buildaid.build.Blueprint.fromJson(text);
										if (bp == null) {
											Feedback.error("buildaid.msg.blueprint_invalid");
										} else if (bp.blocks.isEmpty()) {
											Feedback.error("buildaid.msg.blueprint_empty");
										} else {
											Blueprint.current = bp;
											config.save();
											Feedback.info("buildaid.msg.blueprint_imported", bp.blocks.size());
												}
											}));
											}

											// Importar .litematic do disco: abre o seletor nativo e ancora o ghost no jogador.
											while (Keys.importLitematic.consumeClick()) {
											if (client.player != null) {
											var playerPos = client.player.blockPosition();
											// O dialogo bloqueia: roda numa worker thread e volta para a render thread.
											Thread picker = new Thread(() -> {
											String chosen = com.foxo.buildaid.image.source.NativeFilePicker.openLitematic();
											if (chosen == null || chosen.isBlank()) {
											return; // cancelou
											}
											Blueprint bp = LitematicReader.fromFile(java.nio.file.Path.of(chosen));
											client.execute(() -> {
											if (bp == null) {
											Feedback.error("buildaid.msg.litematic_failed");
											} else {
											bp.shiftTo(playerPos);
											Blueprint.current = bp;
											config.save();
											Feedback.info("buildaid.msg.litematic_imported", bp.blocks.size());
											}
											});
											}, "BuildAid-Litematic");
											picker.start();
											}
											}
				}

				// Verificador de construcao: liga/desliga o overlay que compara o blueprint
				// ativo contra o mundo (amarelo = falta, vermelho = errado/extra).
				while (Keys.verifyBlueprint.consumeClick()) {
					if (client.player != null) {
						if (com.foxo.buildaid.build.Blueprint.current == null) {
							Feedback.error("buildaid.msg.verify_no_blueprint");
						} else {
							config.verifier.enabled = !config.verifier.enabled;
							Feedback.info("buildaid.msg.verify_toggled", Feedback.state(config.verifier.enabled));
							config.save();
						}
					}
					}

									// Relatorio de verificacao: manda o andamento da construcao para o chat.
									while (Keys.reportVerify.consumeClick()) {
										com.foxo.buildaid.build.WorldGizmos.reportVerification(client);
									}

										// Paleta randomizadora: troca o item na mao por um bloco sorteado da lista.
									while (Keys.randomize.consumeClick()) {
				if (client.player != null) {
					com.foxo.buildaid.build.RandomizerPalette.roll(client);
				}
			}

			// Lista de materiais: precisa vs tem vs falta na regiao da ultima medicao.
			// Reusa o RegionScanner (unico ponto de scan, com teto de seguranca) em vez
			// de repetir o laco BlockPos.betweenClosed + getBlockState + isAir.
			while (Keys.materialList.consumeClick()) {
				var m = com.foxo.buildaid.build.TapeMeasure.lastMeasurement();
				if (m == null || m.start() == null || m.end() == null || client.level == null) {
					Feedback.error("buildaid.msg.tape_history_empty");
				} else if (com.foxo.buildaid.build.RegionScanner.isTooLarge(m.start(), m.end())) {
					Feedback.error("buildaid.msg.matlist_toobig");
				} else {
					var counts = com.foxo.buildaid.build.RegionScanner.countBlocks(client, m.start(), m.end());
					if (counts.isEmpty()) {
						Feedback.error("buildaid.msg.matlist_none");
					} else {
						Feedback.info("buildaid.msg.matlist_header", counts.size());
						counts.entrySet().stream()
								.sorted((x, y) -> Integer.compare(y.getValue(), x.getValue()))
								.limit(8)
								.forEach(e -> {
									var item = e.getKey().asItem();
									int tem = 0;
									for (int slot = 0; slot < 36; slot++) {
										if (client.player != null
												&& client.player.getInventory().getItem(slot).is(item)) {
											tem += client.player.getInventory().getItem(slot).getCount();
										}
									}
									Feedback.info("buildaid.msg.matlist_line",
											e.getKey().getName().getString(), e.getValue(),
											Math.max(0, e.getValue() - tem));
								});
					}
				}
			}

		// Desfazer global: restaura a configuracao para antes da ultima edicao estrutural.
		while (Keys.undo.consumeClick()) {
			if (!GlobalUndo.undo()) {
				Feedback.info("buildaid.msg.undo_empty");
			} else {
				Feedback.info("buildaid.msg.undo_done");
			}
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
