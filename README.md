<div align="center">

# 🏗️ BuildAid 1.2.9
**A ferramenta definitiva de auxílio a construção e canais de música sincronizada para Minecraft.**

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg?style=for-the-badge&logo=fabric)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![LavaPlayer](https://img.shields.io/badge/Audio-LavaPlayer%202.2.7-purple.svg?style=for-the-badge)](https://github.com/lavalink-devs/lavaplayer)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

*Um mod **100% client-side** para Minecraft 26.2 (Fabric) projetado para construtores, arquitetos e amigos que jogam e constroem juntos.*

[✨ Funcionalidades](#-funcionalidades) • [🎵 Motor de Música LavaPlayer](#-motor-de-música-lavaplayer--canais-sincronizados) • [⌨️ Teclas de Atalho](#️-teclas-de-atalho) • [📥 Instalação](#-instalação) • [🌐 Servidor na Nuvem](#-servidor-de-sincronização-na-nuvem)

</div>

---

## 🌟 Visão Geral

O **BuildAid** transforma a experiência de jogar e construir no Minecraft. Chega de dar **Alt+Tab** para conferir plantas, tutoriais, vídeos ou trocar de música no navegador. Tudo fica integrado diretamente dentro do jogo: painéis flutuantes de imagens, guias geométricos 3D com cálculo de blocos, sobreposição de pixel-art e um motor de **música sincronizada com LavaPlayer** (YouTube, SoundCloud, Web Rádios e arquivos de áudio) para você escutar com seus amigos em qualquer servidor!

> [!NOTE]
> **100% Client-Side:** Funciona no *Singleplayer* e em **qualquer servidor multiplayer** (Vanilla, Realms, Hypixel, Spigot, Paper, etc.) sem exigir nenhum mod ou plugin instalado no servidor.

---

## ✨ Funcionalidades

### 🎵 Motor de Música LavaPlayer & Canais Sincronizados
- **Suporte Nativo ao YouTube:** Cole links de vídeos, músicas oficiais VEVO, podcasts, transmissões ao vivo, shorts e playlists. O mod decodifica o áudio em tempo real com qualidade cristalina de 48.000 Hz Stereo.
- **Multi-Plataformas:** Suporte nativo a **SoundCloud**, **Twitch**, **Bandcamp**, **Web Rádios 24/7 (Icecast/Shoutcast)** e arquivos diretos de áudio (`.mp3`, `.ogg`, `.flac`, `.wav`).
- **Canais por Servidor Automáticos:** Ao entrar em qualquer servidor de Minecraft, o mod reúne automaticamente todos os jogadores daquele servidor no mesmo canal de áudio sincronizado.
- **Salas Privadas Personalizadas:** Crie salas secretas com códigos personalizados para escutar apenas com seu grupo de amigos.
- **Mini Player HUD:** Widget flutuante discreto com nome da faixa, autor, sala, tempo decorrido e barra de progresso.

### 🖼️ Painéis de Imagens de Referência Flutuantes
- **Múltiplos Painéis Independentes:** Abra quantas abas de imagens precisar, cada uma com posição, escala e opacidade próprias.
- **Ajuste na Tela sem Pausar:** Arraste, redimensione e dê zoom nas imagens em tempo real enquanto joga.
- **Modo Overlay de Tela Cheia (Ghost Mode):** Projete a imagem semitransparente na tela inteira, ideal para decalque de pixel-art, fachadas e curvas complexas.
- **Importação Instantânea:** Cole direto da Área de Transferência (`Ctrl+V`), abra arquivos do seu computador ou baixe por URL da web.

### 🌐 Hologramas 3D no Mundo
- Projete imagens de referência como plantas dentro do mundo 3D em escala real de blocos (1:1).
- Trave a proporção original da imagem e oriente para Norte, Sul, Leste, Oeste ou deitado no chão.

### 📐 Guias Geométricos 3D (11 Formas)
Gabaritos visuais em blocos com contagem automática de materiais e visualização por camadas de altura (Y):
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
- Marque **Canto 1** e **Canto 2** mirando nos blocos (<kbd>M</kbd>) sem interferir no uso normal do mouse.
- Medição em tempo real: largura, altura, profundidade, volume total de blocos e comprimento da diagonal.

### 🏁 Grade de Construção & HUD Informativo
- **Grid no Chão:** Grade alinhada aos blocos com destaque visual para bordas de chunks (a cada 16 blocos).
- **HUD Informativo:** Coordenadas XYZ, direção da mira, bioma atual, nível de iluminação (bloco/céu), hora do mundo e medidor de FPS.

---

## ⌨️ Teclas de Atalho

| Tecla | Função |
|---|---|
| <kbd>G</kbd> | **Abre o Menu Principal do BuildAid** (Imagens, Hologramas, Formas, Seleção, HUD, Música) |
| <kbd>H</kbd> | Mostrar / Ocultar os painéis de referência |
| <kbd>B</kbd> | Ligar / Desligar o modo de seleção de área |
| <kbd>M</kbd> | Marcar canto da seleção (olhando para um bloco) |

> [!TIP]
> Todas as ações (Play/Pause de música, Pular faixa, Alterar volume, Overlay, Grade, Mover formas) podem ser configuradas em **Opções → Controles → BuildAid**.

---

## 🌐 Servidor de Sincronização na Nuvem

O mod já vem configurado de fábrica para se conectar automaticamente ao servidor oficial de sincronização na nuvem:

```text
wss://buildaid-sync-server.onrender.com
```

- **Hospedagem 24/7:** Hospedado no Render.com com WebSockets de baixa latência.
- **Sem necessidade de configuração:** Instale o mod e comece a escutar na hora!

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
2. Baixe o arquivo **`buildaid-1.2.5+26.2.jar`**.
3. Coloque o `.jar` na sua pasta `.minecraft/mods` junto com o **Cloth Config** e a **Fabric API**.
4. Inicie o jogo e aperte <kbd>G</kbd> para abrir o menu!

---

## 📄 Licença

Distribuído sob a licença **MIT**. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

Desenvolvido com carinho por **FoxoBr**.
