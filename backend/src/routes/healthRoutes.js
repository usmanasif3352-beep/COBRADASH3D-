import { Router } from 'express';

export function createHealthRouter({ roomManager, db = null }) {
  const router = Router();
  const startedAtMs = Date.now();

  router.get('/health', (_req, res) => {
    const stats = roomManager.getStats();
    res.json({
      status: 'ok',
      service: 'cobradash3d-multiplayer-backend',
      uptimeSec: Math.floor((Date.now() - startedAtMs) / 1000),
      ...stats,
      timestamp: new Date().toISOString()
    });
  });

  router.get('/health/ready', async (_req, res) => {
    try {
      if (db && typeof db.collection === 'function') {
        await db.collection('leaderboard_entries').limit(1).get();
      }
      res.json({ status: 'ready', firestore: 'reachable' });
    } catch (err) {
      res.status(503).json({
        status: 'degraded',
        error: err?.message || 'Firestore readiness check failed'
      });
    }
  });

  router.get('/metrics', (_req, res) => {
    const mem = process.memoryUsage();
    const stats = roomManager.getStats();
    res.json({
      uptimeSec: Math.floor((Date.now() - startedAtMs) / 1000),
      memoryRssMb: Math.round(mem.rss / (1024 * 1024)),
      heapUsedMb: Math.round(mem.heapUsed / (1024 * 1024)),
      ...stats
    });
  });

  return router;
}
