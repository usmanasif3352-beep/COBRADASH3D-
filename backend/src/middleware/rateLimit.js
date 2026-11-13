import rateLimit from 'express-rate-limit';

export function createApiRateLimiter(windowMs = 60_000, max = 120) {
  return rateLimit({
    windowMs,
    max,
    standardHeaders: true,
    legacyHeaders: false,
    message: {
      error: {
        code: 'RATE_LIMITED',
        message: 'Too many requests. Please slow down and retry shortly.'
      }
    }
  });
}

export function createStrictWriteLimiter() {
  return createApiRateLimiter(60_000, 30);
}

/**
 * Per-socket token bucket rate limiter for real-time WebSocket frames.
 */
export class SocketTokenBucket {
  constructor(maxTokensPerSec = 45, burstCapacity = 60) {
    this.ratePerMs = maxTokensPerSec / 1000;
    this.capacity = burstCapacity;
    this.tokens = burstCapacity;
    this.lastRefillMs = Date.now();
  }

  consume(cost = 1) {
    const now = Date.now();
    const elapsed = Math.max(0, now - this.lastRefillMs);
    this.tokens = Math.min(this.capacity, this.tokens + elapsed * this.ratePerMs);
    this.lastRefillMs = now;
    if (this.tokens < cost) {
      return false;
    }
    this.tokens -= cost;
    return true;
  }
}
