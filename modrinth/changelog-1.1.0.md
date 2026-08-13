## Fixes

**Some images imported but never showed up.** The import path validated with ImageIO (Java's
decoder) and stored the original bytes, but the texture upload used NativeImage/STB — two decoders
with different format support. Progressive JPEGs (very common on the web) and CMYK images passed the
first and failed the second, so they landed in the library and stayed invisible. Every image is now
normalized to an 8-bit RGBA PNG on import, with an ImageIO fallback for older libraries.

**Pasting gave an "image size" error.** AWT hands over clipboard images asynchronously:
`getWidth()` returns -1 until the pixels arrive, and that -1 was read as an invalid size. The paste
now waits for the image to finish loading before measuring.

## New

**Multiple reference panels.** Several images on screen at once, each with its own position, size
and opacity. Add, duplicate, remove and lock them from the Panels tab. A locked panel is skipped by
the adjust mode, so a background reference can't be dragged by accident.

**Image holograms — now several at a time.** Project images as planes inside the world, sized in
blocks and locked to a spot, so they stay put while you walk around them. **Keep proportions** stops
the image from stretching when the panel shape doesn't match the picture.

**Grab and move what you placed.** Select a hologram or shape, hold the grab key and it follows your
crosshair, snapped to the block grid. Three coloured arrows let you constrain the movement to a
single axis, and rotation steps in 15°.

**Geometric shape guides — eleven of them.** Box, cylinder, sphere, dome, pyramid, cone, gable roof,
hip roof, and now **spiral staircase**, **arch/vault** and **torus**. Solid or hollow with adjustable
shell thickness, rotatable — including 45° for diagonals, while staying aligned to the block grid.
Several shapes can exist at the same time.

**Fit a shape to your selection.** Mark an area with the selection tool and the shape snaps to
exactly that box.

**Material list per layer.** How many blocks each height level needs, plus the total. That is how
you actually build — layer by layer.

**Gallery that scales.** Rename images and filter them by name.

## Under the hood

A hollow sphere of radius 20 is 4,242 blocks. The guide culls faces hidden between neighbouring
blocks (25,452 down to 15,084), caches each mesh so it is rebuilt only when a parameter changes,
builds it on a background thread so sliders stay smooth, and draws everything in a single draw call.
A global face budget stops several large shapes from tanking the frame rate silently.

Still no mixins, still fully client-side.

---

## Correções

**Algumas imagens importavam mas nunca apareciam.** A importação validava com ImageIO (o
decodificador do Java) e guardava os bytes originais, mas quem transformava em textura era o
NativeImage/STB — dois decodificadores com suportes diferentes. JPEG progressivo (muito comum na
web) e CMYK passavam no primeiro e falhavam no segundo, então entravam na biblioteca e ficavam
invisíveis. Agora toda imagem é normalizada para PNG RGBA de 8 bits na importação, com um fallback
via ImageIO para bibliotecas antigas.

**Colar dava erro de tamanho da imagem.** O AWT entrega a imagem do clipboard de forma assíncrona:
`getWidth()` devolve -1 até os pixels chegarem, e esse -1 era lido como tamanho inválido. Agora a
colagem espera a imagem terminar de carregar antes de medir.

## Novidades

**Vários painéis de referência.** Várias imagens na tela ao mesmo tempo, cada uma com posição,
tamanho e opacidade próprios. Adicione, duplique, remova e trave na aba Painéis. Painel travado é
ignorado pelo modo de ajuste, então uma referência de fundo não é arrastada sem querer.

**Hologramas de imagem — agora vários.** Projete imagens como planos dentro do mundo, medidos em
blocos e presos a um lugar, parados enquanto você anda em volta. A opção **manter proporção** impede
que a imagem estique quando o formato do plano não bate com o da figura.

**Agarrar e mover o que já foi colocado.** Selecione um holograma ou forma, segure a tecla de agarrar
e ele acompanha a sua mira, grudado na grade de blocos. Três setas coloridas travam o movimento num
único eixo, e a rotação anda de 15 em 15 graus.

**Guias de formas geométricas — onze delas.** Caixa, cilindro, esfera, cúpula, pirâmide, cone,
telhado de duas e de quatro águas, e agora **escada espiral**, **arco/abóbada** e **torus**. Cheias
ou ocas com espessura ajustável, giráveis — inclusive 45° para diagonais, continuando alinhadas à
grade. Várias formas podem existir ao mesmo tempo.

**Encaixar a forma na seleção.** Marque uma área com a ferramenta de seleção e a forma se ajusta
exatamente àquela caixa.

**Lista de materiais por camada.** Quantos blocos vão em cada altura, mais o total. É assim que se
constrói de verdade — camada por camada.

**Galeria que aguenta o volume.** Renomear imagens e filtrar por nome.

## Por dentro

Uma esfera oca de raio 20 tem 4.242 blocos. O guia descarta as faces escondidas entre blocos
vizinhos (de 25.452 para 15.084), guarda cada malha em cache para só refazer quando um parâmetro
muda, constrói em segundo plano para os sliders ficarem fluidos, e desenha tudo numa única chamada.
Um teto global de faces impede que várias formas grandes derrubem o FPS em silêncio.

Continua sem mixins e totalmente client-side.
