import crypto from 'node:crypto';
import { ApiError } from './errorHandler.js';
import { extractBearerToken, verifyPlayerToken } from './auth.js';

function timingSafeEquals(a, b) {
  if (!a || !b) return false;
  const bufA = Buffer.from(String(a), 'utf8');
  const bufB = Buffer.from(String(b), 'utf8');
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

export function createAdminMiddleware({ config, firebaseAuth, playerService }) {
  return async function requireAdmin(req, _res, next) {
    try {
      const providedKey = req.headers['x-admin-key'];
      if (config.adminApiKey && config.adminApiKey !== 'change-me-to-a-long-random-secret') {
        if (providedKey && timingSafeEquals(providedKey, config.adminApiKey)) {
          req.admin = { actor: 'admin_api_key', via: 'key' };
          return next();
        }
      }

      const token = extractBearerToken(req.headers.authorization);
      if (token) {
        const user = await verifyPlayerToken(token, { firebaseAuth, playerService });
        if (user.claims?.admin === true || config.adminUids.has(user.uid)) {
          req.admin = { actor: user.uid, via: 'firebase_claim' };
          return next();
        }
      }

      throw new ApiError(403, 'ADMIN_FORBIDDEN', 'Valid admin credentials required');
    } catch (err) {
      next(err);
    }
  };
}
