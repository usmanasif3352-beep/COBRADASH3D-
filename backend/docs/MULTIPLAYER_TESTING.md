# Multiplayer Test Procedures

## 1. Automated Unit & Integration Tests (Backend)
Run the automated Node.js test suite inside `backend/`:

```bash
cd backend
npm test
```

What this verifies automatically:
- `simulation.test.js`: 360° yaw turn clamping, boost drain, orb collection, server-authoritative score calculation, head-to-body collision detection, elimination orb drops, reconnection token verification, anti-cheat tamper rejection, and `MAX_PLAYERS_PER_MATCH` enforcement.
- `api.test.js`: Health endpoints, 401/403 token & anonymous-auth enforcement, atomic currency transactions, Google Play purchase token replay rejection, regional leaderboard updates, two real WebSocket clients receiving synchronized `world_snapshot`s in a private room, and Admin `BAN` disconnecting an active socket.

## 2. Two-Device / Two-Emulator Live Multiplayer Verification
1. Start the backend locally (`npm run dev` on port `8080`) or deploy to Cloud Run.
2. Launch **CobraDash3D** on Device/Emulator A (signed in with Google Account A) and Device/Emulator B (signed in with Google Account B).
3. Open **Multiplayer Lobby** on both devices, enter the backend HTTPS + WSS URLs, and tap **SAVE & TEST** (confirm `Connected` / health status succeeds).
4. On Device A, select **Private Friends Match** -> tap **Create Private Room** -> note the 6-character `roomCode`.
5. On Device B, enter the `roomCode` and tap **JOIN**. Confirm both devices display `players = 2`.
6. Tap **ENTER ONLINE ARENA** on both devices:
   - Steer Device A's joystick and verify Device A's cobra moves smoothly on Device B's 3D viewport.
   - Hold **BOOST** on Device B and observe boost trail orb drops and meter depletion.
   - Collide Device A's head into Device B's body and confirm server elimination triggers Device A's defeated overlay while Device B receives `+1 kill` and `+150` score.
   - Toggle Airplane Mode briefly (<15s) on Device B and restore connectivity to confirm seamless `reconnectToken` session recovery.
