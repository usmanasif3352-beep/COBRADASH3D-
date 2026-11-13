import { Router } from 'express';

export function createPurchaseRouter({ purchaseService, requireAuth, writeLimiter }) {
  const router = Router();

  router.post('/purchases/verify', requireAuth, writeLimiter, async (req, res, next) => {
    try {
      const { productId, purchaseToken } = req.body || {};
      const result = await purchaseService.verifyAndFulfillPurchase({
        uid: req.user.uid,
        productId: String(productId || '').trim(),
        purchaseToken: String(purchaseToken || '').trim()
      });
      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  return router;
}
