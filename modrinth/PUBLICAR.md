# Guia de publicação no Modrinth — BuildAid 1.1.0

Arquivo para enviar: **`build/libs/buildaid-1.1.0+26.2.jar`**

> Já publicou a 1.0.0? Então pule os passos 1 e 2 — vá direto para o **passo 3** e envie a versão
> nova no projeto que já existe. O texto da descrição continua valendo; vale só revisar o resumo se
> quiser citar hologramas e formas.

> ⚠️ **Envie SOMENTE esse arquivo.** A pasta `dist/` tem também Fabric API, Cloth Config e Mod Menu
> — aqueles jars foram só para você testar localmente. No Modrinth as dependências **não** se
> enviam: elas se **declaram** (passo 3). Subir os jars de outros autores junto quebra a regra da
> plataforma e faz o projeto ser rejeitado na revisão.

---

## 1. A janela "Creating a project"

| Campo | O que colocar |
|---|---|
| **Type** | `Mod` — não deixe em "Project" |
| **Name** | `BuildAid` |
| **URL** | `buildaid` (fica `modrinth.com/mod/buildaid`) |
| **Owner** | `Foxobr` |
| **Visibility** | `Public` |
| **Summary** | veja abaixo |

### Summary (máx. 256 caracteres)

**Em inglês** — recomendado, alcança muito mais gente:

```
Pin reference images over your screen with adjustable opacity, plus Litematica-style area selection and a building grid. Client-side, one menu, four keys.
```

**Em português**, se preferir:

```
Fixe imagens de referência sobre a tela com opacidade ajustável, mais seleção de área estilo Litematica e grade de construção. Client-side, um menu, quatro teclas.
```

---

## 2. Depois de criar — configurações do projeto

**Description (corpo da página):** cole o conteúdo inteiro de
[`descricao.md`](descricao.md). Ele já vem bilíngue: inglês primeiro, português logo abaixo.

**Categories:** marque `Utility`. Se houver uma opção como `Decoration` ou `Management` que você
ache que encaixa, pode marcar junto — mas `Utility` é a principal.

**Environments / Side:**
- Client: **required**
- Server: **unsupported**

Isso importa de verdade: é o que faz o mod aparecer nas buscas por mods client-side e evita que
alguém tente instalar num servidor.

**License:** `MIT`. O arquivo `LICENSE` já está no projeto com o seu nome.

**Links:** se um dia você subir o código no GitHub, preencha *Source* e *Issue tracker*. Sem
repositório, pode deixar em branco — não impede a aprovação.

**Gallery:** o Modrinth não exige, mas **coloque 2 ou 3 capturas de tela**. É o que mais influencia
alguém a baixar, e ajuda muito na revisão. Sugestões do que fotografar (F2 no jogo salva em
`.minecraft/screenshots`):

1. O painel de referência aberto sobre uma construção, com opacidade média — é o que vende o mod.
2. O menu (`G`) na aba Imagens, com a galeria cheia.
3. A seleção de área ligada, mostrando a caixa e os marcadores azul e laranja.

---

## 3. Enviar a versão (aba "Versions" → "Create version")

| Campo | Valor |
|---|---|
| **Version number** | `1.1.0+26.2` |
| **Version title** | `BuildAid 1.1.0 for Minecraft 26.2` |
| **Release channel** | `Release` |
| **Loaders** | `Fabric` |
| **Game versions** | `26.2` |
| **Arquivo** | `buildaid-1.1.0+26.2.jar` (só ele) |

### Dependências — não pule esta parte

| Dependência | Tipo |
|---|---|
| **Fabric API** | Required |
| **Cloth Config** | Required |
| **Mod Menu** | Optional |

Sem declarar isso, quem instalar pelo Modrinth App ou por um launcher não recebe as dependências
junto e o jogo abre com erro de "missing dependency". É o motivo nº 1 de review ruim em mod novo.

### Changelog

Cole o conteúdo de [`changelog-1.1.0.md`](changelog-1.1.0.md).

---

## 4. Antes de clicar em "Submit for review"

- [ ] Descrição colada e legível (confira as tabelas — o Modrinth renderiza markdown).
- [ ] Só o jar do BuildAid anexado.
- [ ] As três dependências declaradas.
- [ ] Client `required`, server `unsupported`.
- [ ] Licença MIT selecionada.
- [ ] Pelo menos uma imagem na galeria.

Uma observação honesta: o mod foi testado no ambiente de desenvolvimento (`runClient`) e o jar está
bem formado, mas **vale instalar o jar numa `.minecraft` de verdade e abrir o jogo uma vez** antes de
submeter. É um teste de dois minutos que pega qualquer surpresa de empacotamento.
