import dotenv from 'dotenv';

dotenv.config();

function intEnv(name, fallback, min = 1, max = 1_000_000) {
  const raw = process.env[name];
  if (!raw) return fallback;
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}

export function loadConfig(overrides = {}) {
  const maxPlayersPerMatch = intEnv('MAX_PLAYERS_PER_MATCH', 16, 2, 64);
  const defaultMaxPlayers = Math.min(
    maxPlayersPerMatch,
    intEnv('DEFAULT_MAX_PLAYERS', 12, 2, maxPlayersPerMatch)
  );

  return {
    nodeEnv: process.env.NODE_ENV || 'development',
    port: intEnv('PORT', 8080, 1, 65535),
    trustProxy: process.env.TRUST_PROXY ?? '1',
    allowedOrigins: (process.env.ALLOWED_ORIGINS || '*')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
    firebaseProjectId: process.env.FIREBASE_PROJECT_ID || 'gen-lang-client-0779493844',
    firestoreDatabaseId:
      process.env.FIRESTORE_DATABASE_ID ||
      'ai-studio-android-cobradas-7d14e11d-d08c-4f3a-a51f-0889d495ef61',
    firebaseServiceAccountJson: process.env.FIREBASE_SERVICE_ACCOUNT_JSON || '',
    androidPackageName: process.env.ANDROID_PACKAGE_NAME || 'com.aistudio.cobradash3d.xkwmqt',
    googlePlayServiceAccountJson: process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || '',
    maxPlayersPerMatch,
    defaultMaxPlayers,
    tickRateHz: intEnv('TICK_RATE_HZ', 30, 10, 60),
    snapshotRateHz: intEnv('SNAPSHOT_RATE_HZ', 20, 5, 30),
    reconnectGraceMs: intEnv('RECONNECT_GRACE_MS', 15000, 3000, 120000),
    idleRoomTtlMs: intEnv('IDLE_ROOM_TTL_MS', 300000, 15000, 3600000),
    inputRateLimitPerSec: intEnv('INPUT_RATE_LIMIT_PER_SEC', 45, 10, 120),
    adminUids: new Set(
      (process.env.ADMIN_UIDS || '')
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
    ),
    adminApiKey: process.env.ADMIN_API_KEY || '',
    ...overrides
  };
}
