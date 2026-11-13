import { Router } from 'express';
import { ApiError } from '../middleware/errorHandler.js';

export function createAdminRouter({ roomManager, playerService, requireAdmin }) {
  const router = Router();

  router.use(requireAdmin);

  router.get('/overview', async (_req, res, next) => {
    try {
      const roomStats = roomManager.getStats();
      const recentFlags = await playerService.listRecentFlags(20);
      res.json({
        ...roomStats,
        recentFlagCount: recentFlags.length,
        recentFlags
      });
    } catch (err) {
      next(err);
    }
  });

  router.get('/rooms', (_req, res) => {
    res.json({
      rooms: roomManager.listAllRooms()
    });
  });

  router.delete('/rooms/:roomCode', (req, res) => {
    const closed = roomManager.closeRoom(req.params.roomCode);
    if (!closed) {
      throw new ApiError(404, 'ROOM_NOT_FOUND', 'Room does not exist');
    }
    res.json({ closed: true, roomCode: req.params.roomCode.toUpperCase() });
  });

  router.post('/players/:uid/moderate', async (req, res, next) => {
    try {
      const { action, reason, durationHours } = req.body || {};
      const result = await playerService.moderatePlayer({
        uid: req.params.uid,
        action: String(action || '').toUpperCase(),
        reason: String(reason || 'Admin moderation action'),
        durationHours: durationHours ? Number(durationHours) : null,
        actor: req.admin.actor
      });

      // Immediately disconnect banned player from any active simulation room
      if (result.action === 'BAN') {
        for (const room of roomManager.rooms.values()) {
          if (room.players.has(req.params.uid)) {
            const ws = room.sockets.get(req.params.uid);
            if (ws) ws.close(1008, 'Account suspended');
            room.removePlayer(req.params.uid);
          }
        }
      }

      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  router.post('/players/:uid/currency', async (req, res, next) => {
    try {
      const coinsDelta = Number.parseInt(req.body?.coinsDelta ?? 0, 10);
      const gemsDelta = Number.parseInt(req.body?.gemsDelta ?? 0, 10);
      const reason = String(req.body?.reason || 'ADMIN_ADJUSTMENT');

      const result = await playerService.executeCurrencyTransaction({
        uid: req.params.uid,
        coinsDelta,
        gemsDelta,
        reason,
        referenceId: req.admin.actor,
        metadata: { actor: req.admin.actor }
      });

      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  router.get('/flags', async (req, res, next) => {
    try {
      const limit = Number.parseInt(req.query.limit, 10) || 50;
      const flags = await playerService.listRecentFlags(limit);
      res.json({ flags });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
