package com.foxo.buildaid.config;

import com.foxo.buildaid.screen.BuildAidMenuScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Coloca o botao de configuracao do BuildAid no ModMenu.
 *
 * <p>Esta classe so e carregada se o ModMenu estiver instalado -- ele e quem pede o entrypoint
 * "modmenu". Por isso o ModMenu pode ficar como dependencia opcional.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// Abre o menu do mod, nao a tela do Cloth: o menu e a porta de entrada, e de la se
		// chega nas configuracoes avancadas pelo botao do rodape.
		return parent -> new BuildAidMenuScreen();
	}
}
