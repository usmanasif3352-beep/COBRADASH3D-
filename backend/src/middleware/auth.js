import { ApiError } from './errorHandler.js';

export function extractBearerToken(headerValue) {
  if (!headerValue || typeof headerValue !== 'string') return null;
  const parts = headerValue.split(' ');
  if (parts.length !== 2 || parts[0].toLowerCase() !== 'bearer') return null;
  return parts[1].trim() || null;
}

export async function verifyPlayerToken(token, { firebaseAuth, playerService }) {
  if (!token) {
    throw new ApiError(401, 'UNAUTHORIZED', 'Firebase ID token is required');
  }

  let decoded;
  try {
    decoded = await firebaseAuth.verifyIdToken(token, true);
  } catch (err) {
    throw new ApiError(401, 'INVALID_TOKEN', `Firebase token verification failed: ${err?.message || 'invalid'}`);
  }

  if (!decoded?.uid) {
    throw new ApiError(401, 'INVALID_TOKEN', 'Decoded token missing uid');
  }

  // Explicitly block anonymous Firebase tokens if ever encountered
  if (decoded.firebase?.sign_in_provider === 'anonymous') {
    throw new ApiError(403, 'ANONYMOUS_FORBIDDEN', 'Anonymous sessions cannot access online multiplayer');
  }

  // Enforce server-side moderation bans/suspensions
  const moderation = await playerService.getModerationStatus(decoded.uid);
  if (moderation?.banned) {
    const untilText = moderation.banExpiresAt ? ` until ${moderation.banExpiresAt}` : ' permanently';
    throw new ApiError(
      403,
      'PLAYER_BANNED',
      `Account suspended${untilText}: ${moderation.reason || 'Policy violation'}`
    );
  }

  return {
    uid: decoded.uid,
    email: decoded.email || null,
    name: decoded.name || 'CobraPilot',
    claims: decoded,
    muted: Boolean(moderation?.muted)
  };
}

export function createAuthMiddleware({ firebaseAuth, playerService }) {
  return async function requireAuth(req, _res, next) {
    try {
      const token = extractBearerToken(req.headers.authorization);
      req.user = await verifyPlayerToken(token, { firebaseAuth, playerService });
      next();
    } catch (err) {
      next(err);
    }
  };
}
