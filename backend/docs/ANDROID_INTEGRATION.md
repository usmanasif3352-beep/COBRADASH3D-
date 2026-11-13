# Android Integration Guide (CobraDash3D)

## 1. Configurable HTTPS & WSS Endpoints
In the Android client (`PlayerSettings` + `SettingsDataStore` + `MultiplayerLobbyScreen`):
- Configure `REST Base URL` (for example `https://cobradash3d-backend-xxxx.a.run.app`)
- Configure `WebSocket Endpoint` (for example `wss://cobradash3d-backend-xxxx.a.run.app/arena`)
- For local Android Emulator testing against a host machine running `npm run dev` on port `8080`:
  - REST Base URL: `http://10.0.2.2:8080`
  - WebSocket Endpoint: `ws://10.0.2.2:8080/arena`

`network_security_config.xml` keeps HTTPS/WSS strict globally while explicitly permitting cleartext localhost/emulator loopback (`10.0.2.2`, `127.0.0.1`, `localhost`) during development.

## 2. Authentication Flow
1. User signs in with Google via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) into Firebase Auth.
2. `CobraViewModel` obtains the fresh Firebase ID Token (`Firebase.auth.currentUser?.getIdToken(false)?.await()?.token`).
3. `OkHttpWebSocketMultiplayerService` attaches `Authorization: Bearer <token>` on `/health`, `/api/v1/*` REST calls, and the WebSocket `/arena` upgrade handshake.

## 3. Authoritative Gameplay Sync
- On `welcome`, the Android client receives `roomCode`, `reconnectToken`, and deterministic `obstacles`.
- During gameplay in online mode, the Android client **only** transmits player control intentions (`input` with `yaw`, `boost`, `seq`; `ability`; `respawn`) and periodically sends `ping` for live RTT ms.
- `CobraGLRenderer` renders the live authoritative `world_snapshot` (`snakes`, `segments`, `orbs`, `powerUps`, `currentRingRadius`, and `killFeed`) rather than local fake bots.
- On unexpected socket drop, `OkHttpWebSocketMultiplayerService` automatically attempts reconnect using the stored `roomCode` + `reconnectToken` within the server's grace window.

## 4. Play Billing Verification & Economy Sync
1. `BillingManager` purchases and acknowledges a Play SKU, emitting `BillingUiState.PurchasePendingVerification(productId, purchaseToken)`.
2. `CobraViewModel` submits `{ productId, purchaseToken }` to `POST /api/v1/purchases/verify` with the user's Firebase Bearer token.
3. The backend verifies with Google Play Developer API and returns the authoritative updated wallet (`coins`, `gems`, `vipActive`, `battlePassPremium`, `adsRemoved`), which immediately updates the UI.
