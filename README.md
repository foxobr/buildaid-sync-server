# BuildAid

Mod **client-side** para **Minecraft 26.2 (Fabric)** que ajuda na hora de construir.

A funcionalidade principal são **painéis de imagens de referência com opacidade ajustável** — as
"abinhas" flutuantes que ficam sobre o jogo para você consultar referências enquanto constrói, sem
alt-tab. Dá para ter vários ao mesmo tempo, cada um com sua imagem.

Junto vêm **hologramas** (a imagem projetada dentro do mundo, medida em blocos), **guias de formas
geométricas** (esfera, cúpula, telhados e mais), uma **seleção de área** no estilo do Litematica, uma
grade alinhada aos blocos e um HUD de informações.

Tudo é operado por **um menu só**, aberto com uma tecla. Não envia nada para o servidor: funciona em
single-player e em qualquer servidor.

**Idiomas:** português (Brasil) e inglês. O mod segue automaticamente o idioma escolhido no
Minecraft — não há nada para configurar.

---

## Requisitos

| Item | Versão |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 ou superior |
| Java | **25** (o 26.1+ exige) |
| Fabric API | 0.156.0+26.2 |
| Cloth Config | 26.2.155 (obrigatório) |
| Mod Menu | 20.0.1 (opcional) |

## Instalação

1. Instale o Fabric Loader para o Minecraft 26.2.
2. Jogue na pasta `mods`:
   - `buildaid-1.0.0.jar`
   - Fabric API
   - Cloth Config
   - Mod Menu (opcional)

---

## Teclas

Só **quatro** teclas vêm configuradas — as que se usa no meio da construção sem querer parar:

| Tecla | O que faz |
|---|---|
| `G` | **Abre o menu** (imagens, painel, seleção, HUD) |
| `H` | Mostra/oculta o painel de referência |
| `B` | Liga/desliga o modo seleção |
| `M` | Marca um canto da seleção (mire num bloco) |

Todas as outras ações — overlay de tela cheia, próxima imagem, colar, abrir arquivo, opacidade,
limpar seleção, grade, HUD de informações, ajustar painéis, colocar holograma e colocar forma —
ficam **no menu**, e também aparecem em **Opções → Controles → BuildAid** *sem tecla atribuída*. Se
você usa alguma o tempo todo, é só dar uma tecla a ela. De fábrica o mod não ocupa nada além dessas
quatro, então não briga com outros mods.

---

## O menu

`G` abre o menu, dividido em sete abas:

**Imagens** — a galeria. Miniaturas de tudo que você já importou; clique para selecionar,
clique duplo para usar. Três formas de adicionar:

- **Colar do clipboard** — copie a imagem em qualquer lugar (navegador, Paint, print) e clique.
- **Abrir arquivo...** — janela do Windows para escolher o arquivo.
- **Link** — cole a URL na caixa e clique em *Baixar*.

**Painéis** — a lista dos painéis abertos. Adicione, duplique e remova; clique num para editar sua
visibilidade e opacidade, com prévia ao vivo. O botão **Ajustar na tela** entra no modo de arrastar.
Clique duplo numa linha acende/apaga aquele painel.

**Holograma** — projeta a imagem como um plano dentro do mundo: largura e altura em blocos, para
onde está virado, opacidade e o botão *Colocar onde estou mirando*.

**Formas** — o guia geométrico: escolha a forma, as três dimensões, oca ou cheia, a espessura da
casca e a rotação. *Colocar onde estou mirando* fixa no lugar.

**Seleção** — liga/desliga o modo, mostra os dois cantos e as dimensões, e limpa a seleção. Também
tem o grid e o raio dele.

**HUD** — o que aparece no HUD de informações (coordenadas, direção, bioma, luz, hora, FPS).

**Música** — canais e salas de música sincronizada estilo Discord. Conecte-se com amigos por código
de sala (ex: `amigos-123`), cole links do YouTube ou áudio na fila, controle o volume, pule faixas e
acompanhe pelo Mini Player HUD dentro do jogo.

No rodapé, **Configurações avançadas** abre a tela do Cloth Config, com os ajustes finos (posição
exata, tamanho, cores, limites de cache).

---

## Como usar

### Referência de imagem

Adicione uma imagem pelo menu e ela aparece no painel. A opacidade é o que faz a referência virar
um "decalque" por cima da construção.

Para posicionar: menu → aba **Painel** → *Ajustar na tela*. O jogo **não** pausa, então dá para usar
em servidor:

- **Arrastar com o botão esquerdo** — move o painel
- **Arrastar o canto azul** — redimensiona
- **Arrastar com o botão direito** — move a imagem *dentro* do painel
- **Roda do mouse** — zoom
- **`R`** — volta zoom e posição ao normal
- **`Esc`** — salva e volta ao jogo

O **overlay de tela cheia** (aba Painel) projeta a imagem sobre a tela inteira com opacidade baixa,
atrás da HUD. É o modo bom para alinhar fachadas e pixel-art.

### Seleção de área

A seleção é um **modo**: com ele desligado nada é desenhado e `M` não faz nada. Nada aparece na sua
tela sem que você tenha pedido.

1. `B` (ou o interruptor na aba Seleção) liga o modo.
2. Mire num bloco e aperte `M` — marca o **canto 1** (marcador azul).
3. Mire em outro e aperte `M` — marca o **canto 2** (marcador laranja).

A caixa aparece entre os dois, com faces translúcidas e contorno, e os marcadores de canto ficam
visíveis mesmo através dela. As dimensões (`largura × altura × profundidade`, total de blocos e
diagonal) aparecem na aba Seleção e no HUD de informações.

O `M` seguinte recomeça pelo canto 1. `B` desliga o modo e tudo some — sem apagar os cantos.

Os cantos são marcados **por tecla**, nunca por clique: quebrar e colocar bloco continuam
funcionando normalmente mesmo com o modo ligado.

---

## Onde ficam os arquivos

```
.minecraft/config/
├─ buildaid.json              # todas as opções
└─ buildaid/
   ├─ library.json            # índice da biblioteca
   └─ images/
      ├─ <hash>.png           # as imagens importadas
      └─ thumbs/              # miniaturas da galeria (geradas sozinhas)
```

Uma config da versão anterior é migrada automaticamente na primeira execução — as imagens e as
preferências de grid continuam valendo.

---

## Desenvolvimento

`JAVA_HOME` precisa apontar para um **JDK 25**.

Compilar:

```bash
./gradlew build
```

Rodar o cliente de teste:

```bash
./gradlew runClient
```

O jar sai em `build/libs/buildaid-1.0.0.jar`.

### Notas técnicas do 26.2

O Minecraft 26.1 foi o primeiro release não-ofuscado, o que muda bastante coisa em relação a
tutoriais de versões antigas:

- **Sem Yarn e sem linha `mappings`** no `build.gradle` — usam-se os nomes oficiais da Mojang.
- `ResourceLocation` agora é **`Identifier`**; `GuiGraphics` é **`GuiGraphicsExtractor`**.
- `Screen#render` virou `Screen#extractRenderState`; widgets sobrescrevem
  `extractWidgetRenderState`; eventos de mouse/teclado passaram a ser objetos
  (`MouseButtonEvent`, `KeyEvent`).
- `Minecraft#setScreen` virou `setScreenAndShow`; `ResourceKey#location` virou `identifier`.
- OpenGL cru é proibido (o 26.2 tem backend Vulkan opcional). O grid e a caixa de seleção usam a
  API de **gizmos** do próprio jogo (`net.minecraft.gizmos`), que já passa pela abstração Blaze3D.
- A opacidade do painel não usa shader: a sobrecarga de `blit` com `int color` no fim aplica um
  tint ARGB, e o byte de alfa desse tint é o slider.
- Os cantos arredondados do menu são compostos por alguns `fill` empilhados, já que `fill` só
  desenha retângulo.

O mod não usa nenhum mixin, o que reduz bastante a chance de quebrar na 26.3.
