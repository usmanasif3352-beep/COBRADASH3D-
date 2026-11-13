import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { createAuthMiddleware } from './middleware/auth.js';
import { createAdminMiddleware } from './middleware/adminAuth.js';
import { createApiRateLimiter, createStrictWriteLimiter } from './middleware/rateLimit.js';
import { errorHandler, notFoundHandler } from './middleware/errorHandler.js';
import { createHealthRouter } from './routes/healthRoutes.js';
import { createPlayerRouter } from './routes/playerRoutes.js';
import { createRoomRouter } from './routes/roomRoutes.js';
import { createPurchaseRouter } from './routes/purchaseRoutes.js';
import { createAdminRouter } from './routes/adminRoutes.js';
import { PlayerService } from './services/playerService.js';
import { PurchaseService } from './services/purchaseService.js';
import { AntiCheatMonitor } from './services/antiCheat.js';
import { RoomManager } from './game/RoomManager.js';

export function createBackendApp({
  config,
  firebaseAuth,
  db,
  playVerifier = null
}) {
  const app = express();
  app.set('trust proxy', config.trustProxy === 'true' ? true : Number(config.trustProxy) || 1);

  app.use(helmet());
  app.use(
    cors({
      origin: config.allowedOrigins.includes('*') ? true : config.allowedOrigins,
      methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS']
    })
  );
  app.use(express.json({ limit: '64kb' }));

  const apiLimiter = createApiRateLimiter(60_000, 120);
  const writeLimiter = createStrictWriteLimiter();

  const playerService = new PlayerService(db);
  const purchaseService = new PurchaseService({ db, config, playerService, playVerifier });
  const antiCheat = new AntiCheatMonitor({ playerService });
  const roomManager = new RoomManager({ config, playerService, antiCheat });

  const requireAuth = createAuthMiddleware({ firebaseAuth, playerService });
  const requireAdmin = createAdminMiddleware({ config, firebaseAuth, playerService });

  app.use('/', createHealthRouter({ roomManager, db }));
  app.use('/api/v1', apiLimiter);
  app.use('/api/v1', createPlayerRouter({ playerService, requireAuth, writeLimiter }));
  app.use('/api/v1', createRoomRouter({ roomManager, requireAuth, writeLimiter }));
  app.use('/api/v1', createPurchaseRouter({ purchaseService, requireAuth, writeLimiter }));
  app.use('/api/v1/admin', createAdminRouter({ roomManager, playerService, requireAdmin }));

  app.use(notFoundHandler);
  app.use(errorHandler);

  return {
    app,
    services: {
      playerService,
      purchaseService,
      antiCheat,
      roomManager
    }
  };
}
