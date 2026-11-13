import { Router } from 'express';

export function createRoomRouter({ roomManager, requireAuth, writeLimiter }) {
  const router = Router();

  router.get('/rooms/public', requireAuth, (_req, res) => {
    res.json({
      rooms: roomManager.listPublicRooms()
    });
  });

  router.post('/rooms/create', requireAuth, writeLimiter, (req, res) => {
    const modeId = String(req.body?.modeId || 'PRIVATE_FRIENDS_MATCH').toUpperCase();
    const arenaId = String(req.body?.arenaId || 'jungle_arena');
    const isPrivate = req.body?.isPrivate !== false;
    const maxPlayers = req.body?.maxPlayers;

    const room = roomManager.createRoom({
      modeId,
      arenaId,
      isPrivate,
      hostUid: req.user.uid,
      maxPlayers
    });

    res.status(201).json({
      room: room.getSummary()
    });
  });

  router.post('/rooms/matchmake', requireAuth, writeLimiter, (req, res) => {
    const modeId = String(req.body?.modeId || 'RANKED_MULTIPLAYER').toUpperCase();
    const arenaId = String(req.body?.arenaId || 'jungle_arena');
    const maxPlayers = req.body?.maxPlayers;

    const room = roomManager.findOrCreateMatchmakingRoom({
      modeId,
      arenaId,
      maxPlayers
    });

    res.json({
      room: room.getSummary()
    });
  });

  router.get('/rooms/:roomCode', requireAuth, (req, res, next) => {
    try {
      const room = roomManager.getRoom(req.params.roomCode);
      res.json({
        room: room.getSummary()
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
