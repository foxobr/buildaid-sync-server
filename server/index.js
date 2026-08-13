const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');
const https = require('https');

const PORT = process.env.PORT || 3000;

// Estado das salas em memoria: roomId -> { currentTrack, queue, isPlaying, startedEpochMs, pausedPositionMs, members: Map(ws -> username) }
const rooms = new Map();

function getOrCreateRoom(roomId) {
  if (!rooms.has(roomId)) {
    rooms.set(roomId, {
      roomId,
      currentTrack: {},
      queue: [],
      isPlaying: false,
      startedEpochMs: 0,
      pausedPositionMs: 0,
      members: new Map()
    });
  }
  return rooms.get(roomId);
}

function broadcastRoomState(room) {
  const membersList = Array.from(room.members.values());
  const payload = JSON.stringify({
    type: 'SYNC_STATE',
    state: {
      roomId: room.roomId,
      currentTrack: room.currentTrack || {},
      queue: room.queue || [],
      isPlaying: !!room.isPlaying,
      startedEpochMs: room.startedEpochMs || 0,
      pausedPositionMs: room.pausedPositionMs || 0,
      members: membersList
    }
  });

  for (const [clientWs] of room.members.entries()) {
    if (clientWs.readyState === WebSocket.OPEN) {
      try {
        clientWs.send(payload);
      } catch (err) {
        console.error(`[WS] Falha ao enviar para cliente:`, err.message);
      }
    }
  }
}

// ---------------------------------------------------------------- Servidor HTTP

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      name: 'BuildAid Music & Channels Sync Server',
      status: 'online',
      activeRooms: rooms.size,
      time: new Date().toISOString()
    }, null, 2));
    return;
  }

  if (url.pathname === '/api/resolve') {
    const videoId = url.searchParams.get('v');
    if (!videoId) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Parâmetro v (videoId) obrigatório' }));
      return;
    }

    resolveYouTubeAudio(videoId)
      .then(info => {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(info));
      })
      .catch(err => {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: err.message || 'Falha ao resolver vídeo' }));
      });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Endpoint não encontrado' }));
});

// ---------------------------------------------------------------- Servidor WebSocket

const wss = new WebSocketServer({ server });

wss.on('connection', (ws) => {
  let userRoomId = null;
  let userName = 'Player';

  ws.on('message', (messageBuffer) => {
    try {
      const data = JSON.parse(messageBuffer.toString());
      const { type } = data;

      switch (type) {
        case 'JOIN_ROOM': {
          const newRoomId = (data.roomId || '').trim();
          userName = (data.playerName || 'Player').trim();

          // Sair da sala anterior se existir
          if (userRoomId && rooms.has(userRoomId)) {
            const oldRoom = rooms.get(userRoomId);
            oldRoom.members.delete(ws);
            broadcastRoomState(oldRoom);
            if (oldRoom.members.size === 0) rooms.delete(userRoomId);
          }

          userRoomId = newRoomId;
          if (userRoomId) {
            const room = getOrCreateRoom(userRoomId);
            room.members.set(ws, userName);
            broadcastRoomState(room);
            console.log(`[WS] ${userName} entrou na sala "${userRoomId}"`);
          }
          break;
        }

        case 'ADD_QUEUE': {
          if (!userRoomId || !rooms.has(userRoomId)) return;
          const room = rooms.get(userRoomId);
          const track = data.track;
          if (!track || !track.title) return;

          room.queue.push(track);

          // Se nao ha nada tocando, inicia automaticamente
          if (!room.currentTrack || !room.currentTrack.title) {
            room.currentTrack = track;
            room.isPlaying = true;
            room.startedEpochMs = Date.now();
            room.pausedPositionMs = 0;
          }

          broadcastRoomState(room);
          break;
        }

        case 'REMOVE_QUEUE': {
          if (!userRoomId || !rooms.has(userRoomId)) return;
          const room = rooms.get(userRoomId);
          const idx = data.index;
          if (idx >= 0 && idx < room.queue.length) {
            room.queue.splice(idx, 1);
            broadcastRoomState(room);
          }
          break;
        }

        case 'PLAY_INDEX': {
          if (!userRoomId || !rooms.has(userRoomId)) return;
          const room = rooms.get(userRoomId);
          const idx = data.index;
          if (idx >= 0 && idx < room.queue.length) {
            room.currentTrack = room.queue[idx];
            room.isPlaying = true;
            room.startedEpochMs = Date.now();
            room.pausedPositionMs = 0;
            broadcastRoomState(room);
          }
          break;
        }

        case 'TOGGLE_PLAY': {
          if (!userRoomId || !rooms.has(userRoomId)) return;
          const room = rooms.get(userRoomId);

          if (!room.currentTrack || !room.currentTrack.title) {
            if (room.queue.length > 0) {
              room.currentTrack = room.queue[0];
              room.isPlaying = true;
              room.startedEpochMs = Date.now();
              room.pausedPositionMs = 0;
            }
          } else {
            if (room.isPlaying) {
              room.isPlaying = false;
              room.pausedPositionMs = Math.max(0, Date.now() - room.startedEpochMs);
            } else {
              room.isPlaying = true;
              room.startedEpochMs = Date.now() - (room.pausedPositionMs || 0);
            }
          }

          broadcastRoomState(room);
          break;
        }

        case 'SKIP': {
          if (!userRoomId || !rooms.has(userRoomId)) return;
          const room = rooms.get(userRoomId);

          if (room.queue.length > 0) {
            // Remove a faixa atual da fila e toca a proxima
            const currentIdx = room.queue.findIndex(t => t.id === room.currentTrack.id);
            if (currentIdx >= 0) {
              room.queue.splice(currentIdx, 1);
            }

            if (room.queue.length > 0) {
              room.currentTrack = room.queue[0];
              room.isPlaying = true;
              room.startedEpochMs = Date.now();
              room.pausedPositionMs = 0;
            } else {
              room.currentTrack = {};
              room.isPlaying = false;
              room.startedEpochMs = 0;
              room.pausedPositionMs = 0;
            }
          } else {
            room.currentTrack = {};
            room.isPlaying = false;
          }

          broadcastRoomState(room);
          break;
        }

        case 'CHAT': {
          if (!userRoomId || !rooms.has(userRoomId)) return;
          const room = rooms.get(userRoomId);
          const msgPayload = JSON.stringify({
            type: 'CHAT_BROADCAST',
            sender: userName,
            message: String(data.message || '')
          });
          for (const [c] of room.members.entries()) {
            if (c.readyState === WebSocket.OPEN) c.send(msgPayload);
          }
          break;
        }
      }
    } catch (err) {
      console.error('[WS] Erro ao processar mensagem:', err);
    }
  });

  ws.on('close', () => {
    if (userRoomId && rooms.has(userRoomId)) {
      const room = rooms.get(userRoomId);
      room.members.delete(ws);
      console.log(`[WS] ${userName} saiu da sala "${userRoomId}"`);
      broadcastRoomState(room);
      if (room.members.size === 0) {
        rooms.delete(userRoomId);
      }
    }
  });
});

// ---------------------------------------------------------------- Resolucao de Audio YouTube

async function resolveYouTubeAudio(videoId) {
  const instances = [
    'https://invidious.nerdvpn.de',
    'https://inv.tux.pizza',
    'https://invidious.protokolla.fi',
    'https://vid.puffyan.us',
    'https://invidious.drgns.space'
  ];

  for (const base of instances) {
    try {
      const response = await fetch(`${base}/api/v1/videos/${videoId}`, {
        headers: { 'User-Agent': 'BuildAid-SyncServer/1.0', 'Accept': 'application/json' },
        signal: AbortSignal.timeout(4000)
      });
      if (response.ok) {
        const data = await response.json();
        if (data && data.title) {
          let streamUrl = '';
          if (Array.isArray(data.adaptiveFormats)) {
            const audioFormat = data.adaptiveFormats.find(f => (f.type || '').includes('audio/'));
            if (audioFormat && audioFormat.url) streamUrl = audioFormat.url;
          }
          if (!streamUrl && Array.isArray(data.formatStreams) && data.formatStreams.length > 0) {
            streamUrl = data.formatStreams[0].url || '';
          }

          return {
            id: videoId,
            title: data.title || 'YouTube',
            author: data.author || 'YouTube',
            durationSeconds: data.lengthSeconds || 0,
            streamUrl: streamUrl,
            thumbnailUrl: `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`
          };
        }
      }
    } catch (ignored) {
    }
  }

  return {
    id: videoId,
    title: `YouTube (${videoId})`,
    author: 'YouTube',
    durationSeconds: 0,
    streamUrl: '',
    thumbnailUrl: `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`
  };
}

server.listen(PORT, () => {
  console.log(`[BuildAid Sync Server] Rodando na porta ${PORT}`);
  console.log(`[BuildAid Sync Server] WebSocket: ws://localhost:${PORT}`);
  console.log(`[BuildAid Sync Server] HTTP Status: http://localhost:${PORT}`);
});
