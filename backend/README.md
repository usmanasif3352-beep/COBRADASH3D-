# CobraDash3D – Real Multiplayer Backend

Authoritative Node.js + Express + WebSocket + Firebase Admin + Cloud Firestore backend for **CobraDash3D – Mobile Ultimate Edition**.

## Folder Structure

```text
backend/
├── Dockerfile
├── .dockerignore
├── .env.example
├── .gitignore
├── package.json
├── README.md
├── docs/
│   ├── API.md
│   ├── ANDROID_INTEGRATION.md
│   └── MULTIPLAYER_TESTING.md
├── src/
│   ├── index.js                      # HTTP + WS server bootstrap & graceful shutdown
│   ├── app.js                        # Express app wiring, Helmet, CORS, rate limiters
│   ├── config/
│   │   ├── env.js                    # Validated environment & match capacity config
│   │   └── firebase.js               # Firebase Admin SDK + named Firestore database
│   ├── middleware/
│   │   ├── adminAuth.js              # Admin API key & Firebase admin claim guard
│   │   ├── auth.js                   # Firebase ID token verification & ban enforcement
│   │   ├── errorHandler.js           # Production-safe JSON error responses
│   │   └── rateLimit.js              # Express & WebSocket token-bucket rate limiters
│   ├── game/
│   │   ├── catalog.js                # Authoritative arenas, modes, power-ups, SKUs, regions
│   │   ├── RoomManager.js            # Public rooms, private room codes, matchmaking, TTL
│   │   └── SimulationRoom.js         # 30Hz physics, collisions, scoring, reconnect grace
│   ├── routes/
│   │   ├── adminRoutes.js            # Admin oversight, room closing, bans, currency audit
│   │   ├── healthRoutes.js           # /health, /health/ready, /metrics
│   │   ├── playerRoutes.js           # Profile, wallet, region, history, global/regional LB
│   │   ├── purchaseRoutes.js         # Google Play purchaseToken verification
│   │   └── roomRoutes.js             # Public rooms, create room, matchmaking, room status
│   ├── services/
│   │   ├── antiCheat.js              # Packet tamper checks, speed anomalies & strike flags
│   │   ├── playerService.js          # Authoritative wallets, Firestore transactions, LB
│   │   └── purchaseService.js        # Google Play Developer API validation & replay lock
│   └── ws/
│       └── WebSocketGateway.js       # Authenticated /arena WebSocket room transport
└── test/
    ├── helpers/inMemoryStore.js      # Isolated test DB/Auth harness (unit tests only)
    ├── api.test.js                   # HTTP + WebSocket end-to-end integration tests
    └── simulation.test.js            # Physics, collision, reconnect, anti-cheat tests
```

## Installation

```bash
cd backend
npm install
cp .env.example .env
```

Edit `backend/.env` and configure your server credentials:
- For local development with a Firebase service account JSON file, set `GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json` (never commit this file).
- Set `ADMIN_API_KEY` to a cryptographically strong random secret.

## Local Testing

```bash
cd backend
npm test
npm run dev
```

Verify health locally:

```bash
curl http://127.0.0.1:8080/health
```

## Google Cloud Run Deployment

Cloud Run natively terminates TLS and upgrades HTTPS/WSS traffic (`https://...` and `wss://...`) automatically to port `8080`.

1. Enable required services:
   ```bash
   gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com androidpublisher.googleapis.com
   ```
2. Set Cloud Run runtime secrets via Secret Manager (never bake keys into the image):
   ```bash
   gcloud secrets create cobradash-admin-key --data-file=- <<< "REPLACE_WITH_STRONG_SECRET"
   ```
3. Deploy directly from `backend/` using Cloud Build:
   ```bash
   cd backend
   gcloud run deploy cobradash3d-backend \
     --source . \
     --region asia-southeast1 \
     --platform managed \
     --allow-unauthenticated \
     --min-instances 1 \
     --max-instances 10 \
     --concurrency 200 \
     --timeout 3600 \
     --session-affinity \
     --set-env-vars "NODE_ENV=production,FIREBASE_PROJECT_ID=gen-lang-client-0779493844,FIRESTORE_DATABASE_ID=ai-studio-android-cobradas-7d14e11d-d08c-4f3a-a51f-0889d495ef61,MAX_PLAYERS_PER_MATCH=16,ANDROID_PACKAGE_NAME=com.aistudio.cobradash3d.xkwmqt" \
     --set-secrets "ADMIN_API_KEY=cobradash-admin-key:latest"
   ```
   > **Important:** Use `--timeout 3600` and `--session-affinity` on Cloud Run so long-lived WebSocket arena connections remain pinned to the container hosting that room. For multi-instance horizontal scaling beyond one stateful node, shard rooms by region or route room codes through a Redis/Agones allocator.

4. Ensure the Cloud Run service account has IAM roles:
   - `roles/datastore.user` (Cloud Firestore)
   - `roles/firebaseauth.viewer` (Firebase Auth token verification)
   - Google Play Console API access linked to the same service account for `androidpublisher` purchase verification.
