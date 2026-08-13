<div align="center">

# 🏗️ BuildAid
**A ferramenta definitiva de auxílio a construção e canais de música sincronizada para Minecraft.**

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg?style=for-the-badge&logo=fabric)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

*Um mod **100% client-side** para Minecraft 26.2 (Fabric) projetado para construtores, arquitetos e jogadores que constroem juntos.*

[✨ Funcionalidades](#-funcionalidades) • [🎵 Sistema de Música](#-canais-de-música-sincronizada) • [⌨️ Teclas de Atalho](#️-teclas-de-atalho) • [📥 Instalação](#-instalação) • [🚀 Servidor de Relay](#-servidor-de-música-companion)

</div>

---

## 🌟 Visão Geral

O **BuildAid** transforma a experiência de construir no Minecraft. Chega de dar **Alt+Tab** para conferir plantas, vídeos e imagens de referência. Tudo o que você precisa fica integrado diretamente dentro do jogo, com controle de opacidade, guias geométricos 3D e um sistema de **canais de música sincronizada** estilo Discord para você escutar com seus amigos em qualquer servidor!

> [!NOTE]
> **100% Client-Side:** Funciona em mundos locais (*Singleplayer*) e em **qualquer servidor multiplayer** (Vanilla, Realms, Hypixel, Spigot, Paper, etc.) sem exigir nenhum mod ou plugin instalado no servidor.

---

## ✨ Funcionalidades

### 🖼️ Painéis de Imagens de Referência Flutuantes
- **Múltiplos Painéis:** Abra quantas abas de imagens precisar, cada uma com posição, zoom e opacidade independentes.
- **Ajuste na Tela sem Pausar:** Arraste, redimensione e dê zoom nas imagens em tempo real enquanto joga.
- **Modo Overlay de Tela Cheia (Ghost Mode):** Projete a imagem semitransparente na tela inteira, perfeita para decalque de pixel-art, fachadas e curvas complexas.
- **Importação Instantânea:** Cole direto do **Clipboard** (`Ctrl+V`), abra arquivos do Windows ou baixe por link direto.

### 🎵 Canais de Música Sincronizada
- **Escute Junto com Amigos:** Conecte-se em salas estilo canais de voz do Discord.
- **Detecção Automática do Servidor:** Ao entrar em qualquer servidor de Minecraft, o mod junta automaticamente todos os jogadores daquele servidor no mesmo canal!
- **Suporte a YouTube e Áudio Web:** Cole links de vídeos, lives do YouTube ou web rádios direto na fila comum.
- **Mini Player HUD:** Acompanhe o que está tocando com uma barra de progresso elegante e discreta na tela do jogo.

### 🌐 Hologramas 3D no Mundo
- Projete imagens de referência como planos dentro do mundo 3D em escala real de blocos.
- Trave a proporção original da imagem e oriente para Norte, Sul, Leste, Oeste ou deitado no chão.

### 📐 Guias Geométricos 3D (11 Formas)
Gabaritos visuais em blocos com contagem automática de materiais e visualização por camadas (Y):
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

### 📦 Seleção de Área Inteligente
- Marque **Canto 1** e **Canto 2** mirando nos blocos (`M`) sem interferir no uso normal do mouse.
- Medição em tempo real: largura, altura, profundidade, volume total de blocos e comprimento da diagonal.

### 🏁 Grade de Construção & HUD Informativo
- **Grid no Chão:** Grade alinhada aos blocos com destaque visual para bordas de chunks (a cada 16 blocos).
- **HUD Informativo:** Coordenadas XYZ, direção da mira, bioma atual, nível de iluminação (bloco/céu), hora do mundo e medidor de FPS.

---

## ⌨️ Teclas de Atalho

Por padrão, apenas **quatro teclas** vêm configuradas de fábrica para evitar conflitos com outros mods:

| Tecla | Função |
|---|---|
| <kbd>G</kbd> | **Abre o Menu Principal do BuildAid** (Imagens, Painéis, Hologramas, Formas, Seleção, HUD, Música) |
| <kbd>H</kbd> | Mostrar / Ocultar os painéis de referência |
| <kbd>B</kbd> | Ligar / Desligar o modo de seleção de área |
| <kbd>M</kbd> | Marcar canto da seleção (olhando para um bloco) |

> [!TIP]
> Todas as outras ações (Play/Pause de música, Pular faixa, Alterar volume, Overlay, Grade, Mover formas) podem ser acessadas pelo menu ou receber atalhos personalizados em **Opções → Controles → BuildAid**.

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
2. Baixe o **BuildAid** e coloque na pasta `.minecraft/mods` junto com a **Fabric API** e o **Cloth Config**.
3. Inicie o jogo e divirta-se!

---

## 🚀 Servidor de Música Companion

O BuildAid inclui um servidor de relay de sincronização leve baseado em **Node.js**:

- **Local:** Dê dois cliques no arquivo `iniciar_servidor.bat` na pasta do mod.
- **Nuvem Gratuita (Render.com / Glitch / Railway):** O repositório já inclui o arquivo `render.yaml` pronto para deploy com 1 clique.

---

## 📄 Licença

Distribuído sob a licença **MIT**. Consulte o arquivo [LICENSE](LICENSE) para obter mais informações.

<div align="center">
Desenvolvido por <b>FoxoBr</b> com foco em desempenho, elegância e experiência de construção.
</div>
