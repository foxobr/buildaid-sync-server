# BuildAid 1.8.0

**Mod utilitário 100% client-side para Minecraft (Fabric 1.21/26.2, Java 25)** — auxilia construção, medição, referência visual e detecção de jogadores. Sem mixins, sem pacotes de rede, sem itens/blocos registrados: sobrevive bem a atualizações do jogo.

## 🛠️ Funcionalidades

### 📐 Ferramentas de Construção
- **Régua Tapesa (TapeMeasure)**: medição rápida de distâncias com laser visual
- **Grade/Simetria**: grade visual, espelhos, simetria radial e eixos de construção
- **Detector de Monstros**: avisa quando mobs estão próximos sem precisar olhar para trás

### 🎨 Referência Visual
- **Painéis de Imagem**: projeta imagens de referência no mundo ou como overlay de tela cheia
- **Hologramas**: planos de imagem 3D no mundo com 5 orientações
- **Notas Flutuantes**: post-its 3D com cores personalizáveis

### 🎯 Guias Geometria
- **Formas 3D**: 17 formas (caixa, esfera, cilindro, toro, curva Bézier, escada em espiral...)
- **Blueprints**: desenho livre → gera forma com contagem de blocos por camada
- **Importação .litematic**: converte schematics em ghost overlays

### ⚡ HUD Informativo
- **InfoHud**: coordenadas, bioma, luz, direção, FPS, distância ao alvo
- **Detecção de Mods**: indica jogadores com BuildAid no servidor
- **Temas de Cor**: 6 paletas diferentes (ciano, ouro, esmeralda, branco, roxo, laranja)

### 🎮 Configuração
- **Menu Principal (G)**: interface com abas para todas as funções
- **Configurações Avançadas**: via Cloth Config (integrado com ModMenu)
- **Performance Otimizada**: ajustes de GPU, JVM e Windows para máximo FPS

## 🚀 Instalação

1. Baixe o [**último release**](https://github.com/foxobr/buildaid/releases)
2. Instale os requisitos:
   - [Fabric Loader 0.16+](https://fabricmc.net/use/)
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Cloth Config](https://modrinth.com/mod/cloth-config)
   - [Mod Menu](https://modrinth.com/mod/modmenu) (opcional, para config fácil)
3. Coloque o `.jar` na pasta `mods/` do Minecraft

## ⚙️ Controles

- **G**: abrir menu principal
- **B**: ativar modo seleção (configurável)
- Todos os atalhos são configuráveis em `Options → Controles → BuildAid`

## 📋 Requisitos

| Component | Mínimo | Recomendado |
|-----------|--------|-------------|
| **Java** | Java 25 | Java 25 (Temurin) |
| **Fabric** | 1.21.26.2 | Snapshot mais recente |
| **GPU** | OpenGL 3.3 | AMD/NVIDIA com 4GB VRAM |
| **RAM** | 8GB | 16GB |

## 🐛 Reportar Bugs

Use os **[issues do GitHub](https://github.com/foxobr/buildaid/issues)** — inclua logs, screenshots e versão exata do Minecraft/Fabric.

## 📄 Licença

MIT — veja o [LICENSE](LICENSE).

---

**Versão 1.8.0** — foco em otimização de interface e performance. 
Compatível com: Minecraft 26.2, Fabric 1.21.26.2, Java 25.