const WebSocket = require('ws');
const http = require('http');

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('Walkie-Talkie Server Running\n');
});

const wss = new WebSocket.Server({ server });
const rooms = new Map(); // roomId -> Set<{ ws, name }>

wss.on('connection', (ws) => {
  let currentRoom = null;
  let clientName = 'Unknown';

  ws.on('message', (data, isBinary) => {
    // Audio binary — relay ke semua client lain di room
    if (isBinary) {
      if (currentRoom && rooms.has(currentRoom)) {
        rooms.get(currentRoom).forEach((client) => {
          if (client.ws !== ws && client.ws.readyState === WebSocket.OPEN) {
            client.ws.send(data, { binary: true });
          }
        });
      }
      return;
    }

    // Pesan kontrol (JSON)
    try {
      const msg = JSON.parse(data.toString());

      if (msg.type === 'join') {
        currentRoom = msg.room;
        clientName = msg.name || 'Unknown';

        if (!rooms.has(currentRoom)) rooms.set(currentRoom, new Set());
        rooms.get(currentRoom).add({ ws, name: clientName });

        // Beritahu semua di room bahwa ada yang join
        broadcastInfo(currentRoom, ws, `${clientName} joined the room`);
        console.log(`[${currentRoom}] ${clientName} joined. Total: ${rooms.get(currentRoom).size}`);
      }

      if (msg.type === 'ptt_start') {
        broadcastInfo(currentRoom, ws, `${clientName} is talking...`);
      }

      if (msg.type === 'ptt_stop') {
        broadcastInfo(currentRoom, ws, `${clientName} stopped talking`);
      }

    } catch (e) {
      console.error('Invalid message:', e);
    }
  });

  ws.on('close', () => {
    if (currentRoom && rooms.has(currentRoom)) {
      rooms.get(currentRoom).forEach((client) => {
        if (client.ws === ws) rooms.get(currentRoom).delete(client);
      });
      broadcastInfo(currentRoom, ws, `${clientName} left the room`);
      console.log(`[${currentRoom}] ${clientName} left.`);
    }
  });

  ws.on('error', (err) => console.error('WS error:', err));
});

function broadcastInfo(roomId, senderWs, message) {
  if (!roomId || !rooms.has(roomId)) return;
  const payload = JSON.stringify({ type: 'info', message });
  rooms.get(roomId).forEach((client) => {
    if (client.ws.readyState === WebSocket.OPEN) {
      client.ws.send(payload);
    }
  });
}

const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
  console.log(`Walkie-Talkie server running on port ${PORT}`);
});
