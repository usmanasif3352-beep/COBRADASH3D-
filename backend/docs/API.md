# CobraDash3D Backend API Documentation

All authenticated endpoints require:
`Authorization: Bearer <firebase_id_token>`

Anonymous Firebase sessions and client-forged scores/balances are rejected.

## 1. Health & Monitoring
- `GET /health` — Service status, uptime, `activeRooms`, `totalConnectedPlayers`, `maxPlayersPerMatch`.
- `GET /health/ready` — Readiness probe verifying Cloud Firestore reachability.
- `GET /metrics` — Memory RSS, heap usage, room and player counts.

## 2. Player Profile, Wallet, Currency & Leaderboards
- `GET /api/v1/players/me` — Returns Firestore `profile` and authoritative `wallet`.
- `GET /api/v1/players/wallet` — Returns server-authoritative `{ coins, gems, vipActive, battlePassPremium, adsRemoved, eloRating, region }`.
- `PUT /api/v1/players/region` — Body `{ "region": "NA|EU|AS|SA|OC|AF" }`. Updates regional leaderboard bucket.
- `GET /api/v1/players/transactions?limit=30` — Immutable server currency transaction ledger.
- `GET /api/v1/players/matches?limit=25` — Player match history.
- `POST /api/v1/players/economy/unlock-skin` — Body `{ "skinId": "fire_dragon" }`. Deducts server-catalog coin/gem cost atomically via Firestore transaction.
- `GET /api/v1/leaderboard/global?limit=50` — Public global high-score leaderboard.
- `GET /api/v1/leaderboard/regional/:region?limit=50` — Regional leaderboard for `NA`, `EU`, `AS`, `SA`, `OC`, `AF`, or `GLOBAL`.

## 3. Rooms & Matchmaking
- `GET /api/v1/rooms/public` — Lists joinable public rooms.
- `POST /api/v1/rooms/create` — Body `{ "modeId": "PRIVATE_FRIENDS_MATCH", "arenaId": "cyber_city", "isPrivate": true, "maxPlayers": 12 }`. Returns 6-character `roomCode`.
- `POST /api/v1/rooms/matchmake` — Body `{ "modeId": "RANKED_MULTIPLAYER", "arenaId": "jungle_arena" }`. Allocates or joins an open public room.
- `GET /api/v1/rooms/:roomCode` — Returns live room summary.

## 4. Google Play Purchase Verification
- `POST /api/v1/purchases/verify` — Body `{ "productId": "cobradash_coins_500", "purchaseToken": "..." }`.
  - Checks SHA-256 token hash against `verified_purchases` to block replay attacks (`409` if used by another account).
  - Queries Google Play Developer API (`androidpublisher.googleapis.com`).
  - Atomically credits coins/gems/VIP/BattlePass/no-ads and appends a `currency_transactions` ledger entry.

## 5. Admin API
Requires either header `X-Admin-Key: <ADMIN_API_KEY>` or a Firebase ID token with custom claim `admin: true` / `ADMIN_UIDS` membership.
- `GET /api/v1/admin/overview` — Live room telemetry and recent anti-cheat flags.
- `GET /api/v1/admin/rooms` — All active public and private rooms.
- `DELETE /api/v1/admin/rooms/:roomCode` — Force-closes an active room.
- `POST /api/v1/admin/players/:uid/moderate` — Body `{ "action": "BAN|UNBAN|MUTE|UNMUTE", "reason": "...", "durationHours": 24 }`.
- `POST /api/v1/admin/players/:uid/currency` — Audited server currency grant/adjustment `{ "coinsDelta": 500, "gemsDelta": 0, "reason": "SUPPORT_COMPENSATION" }`.
- `GET /api/v1/admin/flags?limit=50` — Inspect server-recorded anti-cheat strikes.

## 6. Real-Time WebSocket Protocol (`/arena`)
Connect via:
`wss://<host>/arena?action=matchmake|create|join|reconnect&mode=RANKED_MULTIPLAYER&arena=jungle_arena&room=<CODE>&reconnectToken=<TOKEN>`
Header: `Authorization: Bearer <firebase_id_token>`

### Client -> Server Frames
- Input tick: `{ "type": "input", "yaw": 1.25, "boost": true, "seq": 42 }`
- Ability: `{ "type": "ability" }`
- Respawn (if mode allows): `{ "type": "respawn" }`
- Latency ping: `{ "type": "ping", "ts": 1700000000000 }`

### Server -> Client Frames
- `welcome`: `{ "type": "welcome", "uid", "roomCode", "modeId", "arenaId", "players", "maxPlayers", "reconnected", "reconnectToken", "obstacles" }`
- `world_snapshot` (20 Hz): `{ "type": "world_snapshot", "roomCode", "players", "elapsedSec", "timeRemainingSec", "currentRingRadius", "snakes": [...], "orbs": [...], "powerUps": [...], "killFeed": [...] }`
- `eliminated`: `{ "type": "eliminated", "uid", "reason", "canRespawn", "score", "kills" }`
- `match_result`: `{ "type": "match_result", "standings": [...] }`
- `pong`: `{ "type": "pong", "clientTs", "serverTs" }`
- `error`: `{ "type": "error", "code", "message" }`
