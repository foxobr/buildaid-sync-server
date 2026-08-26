<div align="center">

# 🏗️ BuildAid 1.5.0
**A ferramenta definitiva e ultraleve de auxílio à construção para Minecraft.**

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg?style=for-the-badge&logo=fabric)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

*Um mod **100% client-side** para Minecraft 26.2 (Fabric) projetado para construtores, arquitetos e jogadores que levam suas construções a sério.*

[✨ Funcionalidades](#-funcionalidades) • [📐 Guias Geométricos 3D](#-guias-geométricos-3d-15-formas) • [⌨️ Teclas de Atalho](#️-teclas-de-atalho) • [📥 Instalação](#-instalação)

</div>

---

## 🌟 Visão Geral

O **BuildAid** transforma a experiência de planejar e construir no Minecraft. Chega de dar **Alt+Tab** para conferir plantas, tutoriais ou calculadoras de círculos e esferas na web. Tudo fica integrado diretamente dentro do jogo: painéis flutuantes de imagens de referência, hologramas 3D no mundo em escala real, guias geométricos com **fatiador de camadas (Layer Slicer)** e **contagem de blocos**, sobreposição de pixel-art (Ghost Mode) e seleção de área inteligente com medições em tempo real!

> [!NOTE]
> **100% Client-Side & Ultraleve:** Funciona no *Singleplayer* e em **qualquer servidor multiplayer** (Vanilla, Realms, Hypixel, Spigot, Paper, etc.) sem exigir nenhum mod ou plugin instalado no servidor. O jar do mod possui apenas ~150 KB e zero impacto na performance!

---

## ✨ Funcionalidades

### 🖼️ Painéis de Imagens de Referência Flutuantes
- **Múltiplos Painéis Independentes:** Abra quantas abas de imagens precisar, cada uma com posição, escala e opacidade próprias.
- **Ajuste na Tela sem Pausar:** Arraste, redimensione, dê zoom e rotacione (<kbd>T</kbd> para girar 90°) as imagens em tempo real enquanto joga.
- **Modo Overlay de Tela Cheia (Ghost Mode):** Projete a imagem semitransparente na tela inteira, ideal para decalque de pixel-art, fachadas e curvas complexas.
- **Importação Instantânea:** Cole direto da Área de Transferência (`Ctrl+V`), abra arquivos do seu computador ou baixe por URL da web.

### 🌐 Hologramas 3D no Mundo
- Projete imagens de referência como plantas dentro do mundo 3D em escala real de blocos (1:1).
- Trave a proporção original da imagem e oriente para Norte, Sul, Leste, Oeste ou deitado no chão.
- Movimente ou posicione olhando para os blocos com o cursor.

### 📐 Guias Geométricos 3D (18 Formas)
Gabaritos visuais em blocos com contagem automática de materiais e **Fatiador de Camadas (Layer Slicer)** para construir passo a passo no Survival:
- 📦 **Caixa / Cubo**
- 🔘 **Cilindro**
- 🌐 **Esfera**
- 🏛️ **Cúpula / Domo**
- 🔺 **Pirâmide**
- 🍦 **Cone**
- 🏠 **Telhado duas águas**
- 🛖 **Telhado quatro águas**
- 🌀 **Escada espiral (Hélice)**
- 🌉 **Arco / Abóbada**
- 🍩 **Torus (Anel)**
- ⬡ **Hexágono (6 lados)**
- 🛑 **Octógono (8 lados)**
- ⭕ **Círculo 2D no Chão**
- 💠 **Diamante / Losango**
- ⛪ **Arco Gótico / Ogival** — *Novo!*
- 🚇 **Túnel / Cilindro Horizontal** — *Novo!*
- 📐 **Prisma Triangular / Rampa** — *Novo!*

#### 🍰 Fatiador de Camadas (Layer Slicer)
- **Modo Todas as Camadas:** Exibe a estrutura 3D completa.
- **Modo Apenas Camada Y:** Isola e exibe exclusivamente a camada selecionada no slider para construção fácil camada por camada.
- **Modo Até a Camada Y:** Exibe a construção do chão até a altura atual.
- **Modo Aramado (Wireframe):** Visualize apenas as linhas de contorno das formas para construir o interior com visibilidade desobstruída.
- **Paleta de Cores:** Escolha entre 8 estilos visuais (Ciano, Esmeralda, Ouro, Laranja, Rubi, Roxo, Branco e o exclusivo **Modo Arco-Íris** com cores por camada!).

### 📦 Seleção de Área, Escaneador de Materiais & Ferramentas
- **📋 Escaneador de Materiais do Mundo:** Escaneie qualquer construção ou área selecionada no mundo para obter a contagem exata de todos os blocos necessários (em unidades e *packs* de 64!).
- **🎯 Localizador de Centro Exato (Center Finder):** Destaca o centro exato da sua sala ou parede (1 bloco para ímpar ou 2 blocos para par) para nunca mais errar o alinhamento de portas e lustres.
- **📏 Régua Rápida de Medição (Tape Measure):** Meça vãos e distâncias em tempo real com um feixe laser e contagem de blocos nos eixos XYZ (`ΔX`, `ΔY`, `ΔZ`).
- **🎛️ Ajuste Fino & Expansão:** Botões rápidos na tela para mover a caixa selecionada nos eixos (+X, -X, +Y, -Y, +Z, -Z) e expandir/encolher sem precisar remarcar os cantos.
- **Encaixar Forma na Seleção:** Gera qualquer gabarito geométrico exatamente dentro da sua área marcada com 1 clique.

### 🎨 Cores, Gradientes & Projeção 3D (aba Cores)
- **🖌️ Seletor de Cores estilo programa de pintura:** quadrado de saturação/brilho, barra de matiz e campo `#hex` sincronizados.
- **🔍 Sugestão automática de blocos:** ao escolher uma cor, o mod lista os blocos mais próximos com amostra de cor — um clique define o **1º** ou o **último bloco do gradiente**, e `⚡ Gerar` monta a sequência completa.
- **🗒️ Exportar p/ nota:** transforma o gradiente (ou a lista de sugestões) numa **anotação flutuante** na tela, com os nomes dos blocos no idioma do jogo.
- **👁️ Projeção 3D da paleta no chão do mundo** (eixo X ou Z) e botão para equipar os blocos direto na hotbar (Criativo).

### 🗒️ Anotações Flutuantes
- Post-its de texto livre presos à tela, com vários ao mesmo tempo — cada um com posição, largura, opacidade e cor da barrinha lateral próprios.
- Editor multilinha dedicado: <kbd>Enter</kbd> cria linha, `x` remove, <kbd>Esc</kbd> salva.
- Gerenciadas na **aba Painéis** junto com as imagens (lista única com divisor), e arrastáveis direto no modo "Ajustar na tela", igual aos painéis.

### 🏁 Grade, Plano de Simetria, Detector de Monstros & Super HUD
- **👾 Detector de Spawn de Monstros (Light 0 Overlay):** Projeta cruzes vermelhas nos blocos escuros onde monstros podem nascer para iluminar vilas e bases com 100% de segurança.
- **🪞 Plano de Simetria 3D:** Projeta uma parede/espelho de simetria luminoso no mundo (nos eixos X ou Z) para construções perfeitamente espelhadas.
- **📏 Trava de Altura da Grade:** Fixe a grade do chão em uma coordenada Y específica.
- **🎨 Super HUD Totalmente Customizável:**
  - **4 Cantos de Ancoragem:** Superior Esquerdo, Superior Direito, Inferior Esquerdo, Inferior Direito.
  - **3 Estilos de Fundo:** Glassmorphism Translúcido, Sombra Vanilla e Alto Contraste.
  - **6 Temas de Cor:** Ciano, Dourado, Esmeralda, Branco, Roxo e Laranja.
  - **🚨 Alerta de Escuridão:** Destaca em vermelho `⚠️ Luz: Bloco 0 (Perigo!)` se estiver em ponto de spawn.
  - **Novos Módulos:** **Bloco Mirado**, **Contador de Blocos no Inventário**, **Durabilidade da Ferramenta** e **Linha da Régua**.

---

## ⌨️ Teclas de Atalho

| Tecla | Função |
|---|---|
| <kbd>G</kbd> | **Única tecla de fábrica: abre o Menu Principal do BuildAid** (Imagens, Cores, Gradientes, Anotações, HUD...) |

> [!IMPORTANT]
> **Todas as outras ações saem sem atalho de fábrica** — o mod não rouba nenhuma tecla de outros mods. Quem quiser atalho para (Mostrar/ocultar painéis, Modo seleção, Marcar canto, Régua, Colocar forma/holograma etc.) escolhe as suas em **Opções → Controles → BuildAid**.

---

## 📥 Requisitos & Instalação

### Requisitos

| Dependência | Versão Mínima |
|---|---|
| **Minecraft** | `26.2` |
| **Fabric Loader** | `>= 0.19.3` |
| **Java** | **Java 25** *(exigido pelo Minecraft 26.1+)* |
| **Fabric API** | `0.156.0+26.2` ou superior |
| **Cloth Config** | `26.2.155` *(obrigatório)* |
| **Mod Menu** | `20.0.1` *(opcional)* |

### Como Instalar
1. Instale o **Fabric Loader** para o Minecraft 26.2.
2. Baixe o arquivo **`buildaid-1.5.0+26.2.jar`**.
3. Coloque o `.jar` na sua pasta `.minecraft/mods` junto com o **Cloth Config** e a **Fabric API**.
4. Inicie o jogo e aperte <kbd>G</kbd> para abrir o menu!

---

## 📄 Licença

Distribuído sob a licença **MIT**. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

Desenvolvido com carinho por **FoxoBr**.
