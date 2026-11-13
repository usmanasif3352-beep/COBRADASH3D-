import { Router } from 'express';
import { ApiError } from '../middleware/errorHandler.js';

// Server-authoritative cosmetic shop catalog for coin/gem spending
const SERVER_SKIN_PRICES = {
  fire_dragon: { coins: 400, gems: 0 },
  ice_serpent: { coins: 400, gems: 0 },
  electric_viper: { coins: 600, gems: 0 },
  golden_cobra: { coins: 0, gems: 45 },
  diamond_snake: { coins: 0, gems: 75 },
  rainbow_serpent: { coins: 900, gems: 0 },
  neon_snake: { coins: 500, gems: 0 },
  shadow_cobra: { coins: 750, gems: 0 },
  crystal_snake: { coins: 0, gems: 60 },
  phoenix_cobra: { coins: 0, gems: 95 }
};

export function createPlayerRouter({ playerService, requireAuth, writeLimiter }) {
  const router = Router();

  router.get('/players/me', requireAuth, async (req, res, next) => {
    try {
      const overview = await playerService.getProfileOverview(req.user.uid);
      res.json(overview);
    } catch (err) {
      next(err);
    }
  });

  router.get('/players/wallet', requireAuth, async (req, res, next) => {
    try {
      const wallet = await playerService.getOrCreateWallet(req.user.uid, req.user.name);
      res.json({ wallet });
    } catch (err) {
      next(err);
    }
  });

  router.put('/players/region', requireAuth, writeLimiter, async (req, res, next) => {
    try {
      const updated = await playerService.updateRegion(req.user.uid, req.body?.region);
      res.json(updated);
    } catch (err) {
      next(err);
    }
  });

  router.get('/players/transactions', requireAuth, async (req, res, next) => {
    try {
      const limit = Number.parseInt(req.query.limit, 10) || 30;
      const transactions = await playerService.getTransactionHistory(req.user.uid, limit);
      res.json({ transactions });
    } catch (err) {
      next(err);
    }
  });

  router.get('/players/matches', requireAuth, async (req, res, next) => {
    try {
      const limit = Number.parseInt(req.query.limit, 10) || 25;
      const matches = await playerService.getMatchHistory(req.user.uid, limit);
      res.json({ matches });
    } catch (err) {
      next(err);
    }
  });

  router.post('/players/economy/unlock-skin', requireAuth, writeLimiter, async (req, res, next) => {
    try {
      const skinId = String(req.body?.skinId || '').trim();
      const price = SERVER_SKIN_PRICES[skinId];
      if (!price) {
        throw new ApiError(400, 'INVALID_SKIN_PURCHASE', `Skin '${skinId}' is not purchasable with virtual currency`);
      }

      const txResult = await playerService.executeCurrencyTransaction({
        uid: req.user.uid,
        coinsDelta: -price.coins,
        gemsDelta: -price.gems,
        reason: 'UNLOCK_SKIN',
        referenceId: skinId,
        metadata: { skinId }
      });

      const userRef = playerService.db.collection('users').document(req.user.uid);
      const userSnap = await userRef.get();
      if (userSnap.exists) {
        const existingSkins = Array.isArray(userSnap.data().unlockedSkinIds)
          ? userSnap.data().unlockedSkinIds
          : [];
        if (!existingSkins.includes(skinId)) {
          await userRef.update({ unlockedSkinIds: [...existingSkins, skinId].slice(0, 60) });
        }
      }

      res.json({
        unlockedSkinId: skinId,
        ...txResult
      });
    } catch (err) {
      next(err);
    }
  });

  router.get('/leaderboard/global', requireAuth, async (req, res, next) => {
    try {
      const limit = Number.parseInt(req.query.limit, 10) || 50;
      const entries = await playerService.getGlobalLeaderboard(limit);
      res.json({ region: 'GLOBAL', entries });
    } catch (err) {
      next(err);
    }
  });

  router.get('/leaderboard/regional/:region', requireAuth, async (req, res, next) => {
    try {
      const limit = Number.parseInt(req.query.limit, 10) || 50;
      const region = String(req.params.region || 'GLOBAL').toUpperCase();
      const entries = await playerService.getRegionalLeaderboard(region, limit);
      res.json({ region, entries });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
