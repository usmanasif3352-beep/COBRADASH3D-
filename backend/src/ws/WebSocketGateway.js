import { WebSocketServer } from 'ws';
import { extractBearerToken, verifyPlayerToken } from '../middleware/auth.js';
import { SocketTokenBucket } from '../middleware/rateLimit.js';

export function attachWebSocketGateway({
  httpServer,
  config,
  firebaseAuth,
  playerService,
  roomManager,
  antiCheat
}) {
  const wss = new WebSocketServer({
    noServer: true,
    maxPayload: 8 * 1024
  });

  httpServer.on('upgrade', async (req, socket, head) => {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    if (url.pathname !== '/arena' && url.pathname !== '/ws') {
      socket.write('HTTP/1.1 404 Not Found\r\n\r\n');
      socket.destroy();
      return;
    }

    const token =
      extractBearerToken(req.headers.authorization) || url.searchParams.get('token') || null;

    if (!token) {
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
      return;
    }

    let user;
    try {
      user = await verifyPlayerToken(token, { firebaseAuth, playerService });
    } catch {
      socket.write('HTTP/1.1 403 Forbidden\r\n\r\n');
      socket.destroy();
      return;
    }

    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit('connection', ws, req, { user, url });
    });
  });

  wss.on('connection', async (ws, _req, { user, url }) => {
    const rateLimiter = new SocketTokenBucket(config.inputRateLimitPerSec, config.inputRateLimitPerSec * 2);
    const action = (url.searchParams.get('action') || 'matchmake').toLowerCase();
    const modeId = (url.searchParams.get('mode') || 'RANKED_MULTIPLAYER').toUpperCase();
    const arenaId = url.searchParams.get('arena') || 'jungle_arena';
    const roomParam = (url.searchParams.get('room') || '').trim().toUpperCase();
    const reconnectToken = url.searchParams.get('reconnectToken') || null;
    const requestedMaxPlayers = url.searchParams.get('maxPlayers');

    let nickname = user.name || 'CobraPilot';
    let skinId = url.searchParams.get('skin') || 'royal_cobra';

    try {
      const overview = await playerService.getProfileOverview(user.uid);
      if (overview?.profile?.nickname) nickname = overview.profile.nickname;
      if (overview?.profile?.selectedSkinId) skinId = overview.profile.selectedSkinId;
    } catch {
      // keep token display name fallback
    }

    let room;
    try {
      if (action === 'create') {
        room = roomManager.createRoom({
          modeId,
          arenaId,
          isPrivate: true,
          hostUid: user.uid,
          maxPlayers: requestedMaxPlayers
        });
      } else if (action === 'join' || action === 'reconnect') {
        room = roomManager.getRoom(roomParam);
      } else {
        room = roomManager.findOrCreateMatchmakingRoom({
          modeId,
          arenaId,
          maxPlayers: requestedMaxPlayers
        });
      }
    } catch (err) {
      ws.send(JSON.stringify({ type: 'error', code: err.code || 'ROOM_ERROR', message: err.message }));
      ws.close(1008, err.message);
      return;
    }

    const joinResult = room.addOrReconnectPlayer({
      uid: user.uid,
      nickname,
      skinId,
      reconnectToken,
      ws
    });

    if (joinResult.error) {
      ws.send(JSON.stringify({ type: 'error', code: joinResult.error, message: 'Unable to enter room' }));
      ws.close(1008, joinResult.error);
      return;
    }

    ws.send(
      JSON.stringify({
        type: 'welcome',
        uid: user.uid,
        roomCode: room.roomCode,
        modeId: room.mode.id,
        arenaId: room.arena.id,
        players: room.players.size,
        maxPlayers: room.maxPlayers,
        reconnected: joinResult.reconnected,
        reconnectToken: joinResult.player.reconnectToken,
        obstacles: room.obstacles
      })
    );

    ws.on('message', (raw) => {
      if (!rateLimiter.consume(1)) {
        ws.send(JSON.stringify({ type: 'error', code: 'RATE_LIMITED', message: 'Input rate limit exceeded' }));
        return;
      }

      let packet;
      try {
        packet = JSON.parse(raw.toString('utf8'));
      } catch {
        ws.send(JSON.stringify({ type: 'error', code: 'BAD_JSON', message: 'Invalid JSON frame' }));
        return;
      }

      if (packet?.type === 'ping') {
        ws.send(JSON.stringify({ type: 'pong', clientTs: packet.ts || Date.now(), serverTs: Date.now() }));
        return;
      }

      const player = room.players.get(user.uid);
      const check = antiCheat.validatePacket(user.uid, packet, player ? player.lastInputSeq : -1);
      if (!check.ok) {
        ws.send(
          JSON.stringify({
            type: 'error',
            code: check.reason,
            message: check.detail || 'Anti-cheat validation failed'
          })
        );
        if (check.kick) {
          room.removePlayer(user.uid);
          ws.close(1008, 'Disconnected by anti-cheat');
        }
        return;
      }

      switch (packet.type) {
        case 'input':
          room.applyPlayerInput(user.uid, {
            yaw: packet.yaw,
            boost: packet.boost,
            seq: packet.seq
          });
          break;
        case 'ability':
          room.triggerPlayerAbility(user.uid);
          break;
        case 'respawn':
          room.triggerPlayerRespawn(user.uid);
          break;
        default:
          break;
      }
    });

    ws.on('close', () => {
      room.markDisconnected(user.uid);
    });
  });

  return wss;
}
