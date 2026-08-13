# BuildAid Sync Server 🎵

Servidor de sincronização de salas e canais de música para o mod **BuildAid (Minecraft)**.

Permite que amigos e membros de um servidor entrem em canais/salas compartilhadas para escutar músicas sincronizadas do YouTube, Web Rádios e links diretos de áudio enquanto constroem no Minecraft.

---

## 🚀 Como Executar Localmente

1. Tenha o [Node.js](https://nodejs.org) instalado (versão 18+).
2. Abra a pasta `server`:
   ```bash
   cd server
   npm install
   npm start
   ```
3. O servidor estará rodando em `ws://localhost:3000`.

---

## ☁️ Hospedagem Gratuita na Nuvem

Você pode hospedar este servidor gratuitamente em serviços como:
- **Render** (render.com - Web Service com Node.js)
- **Railway** (railway.app)
- **Glitch** (glitch.com)
- **Replit** (replit.com)

Basta apontar o repositório ou fazer upload dos arquivos da pasta `server`. O serviço fornecerá um endereço `wss://seu-servidor.onrender.com`.

---

## ⚙️ Configuração no Minecraft

1. No jogo, aperte **`G`** para abrir o menu do **BuildAid**.
2. Vá até a aba **Música**.
3. No campo **Servidor WebSocket**, digite o endereço do seu servidor (ex: `ws://localhost:3000` ou `wss://seu-servidor.onrender.com`).
4. No campo **Sala / Canal**, digite o nome da sala (ex: `amigos-construcao`) e clique em **Entrar**.
5. Todos os jogadores que colocarem o mesmo servidor e sala ouvirão as músicas sincronizadas em tempo real!
