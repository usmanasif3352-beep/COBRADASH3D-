import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import WebSocket from 'ws';
import { loadConfig } from '../src/config/env.js';
import { createBackendApp } from '../src/app.js';
import { attachWebSocketGateway } from '../src/ws/WebSocketGateway.js';
import { TestFirebaseAuth, TestFirestoreDb } from './helpers/inMemoryStore.js';

function startTestServer({ playVerifier = null } = {}) {
  const db = new TestFirestoreDb();
  const firebaseAuth = new TestFirebaseAuth();
  const config = loadConfig({
    nodeEnv: 'test',
    port: 0,
    maxPlayersPerMatch: 6,
    defaultMaxPlayers: 6,
    adminApiKey: 'test-super-secret-admin-key',
    tickRateHz: 30,
    snapshotRateHz: 20,
    reconnectGraceMs: 4000
  });

  const { app, services } = createBackendApp({
    config,
    firebaseAuth,
    db,
    playVerifier
  });

  const server = http.createServer(app);
  const wss = attachWebSocketGateway({
    httpServer: server,
    config,
    firebaseAuth,
    playerService: services.playerService,
    roomManager: services.roomManager,
    antiCheat: services.antiCheat
  });

  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const addr = server.address();
      const baseHttp = `http://127.0.0.1:${addr.port}`;
      const baseWs = `ws://127.0.0.1:${addr.port}/arena`;
      resolve({
        baseHttp,
        baseWs,
        db,
        services,
        close: () =>
          new Promise((done) => {
            services.roomManager.shutdown();
            wss.close(() => server.close(done));
          })
      });
    });
  });
}

function nextWsMessage(ws, predicate) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Timed out waiting for WS message')), 4000);
    const onMessage = (raw) => {
      const msg = JSON.parse(raw.toString('utf8'));
      if (!predicate || predicate(msg)) {
        clearTimeout(timer);
        ws.off('message', onMessage);
        resolve(msg);
      }
    };
    ws.on('message', onMessage);
  });
}

test('HTTP Auth, Health, Rooms, Wallets, Play Purchase Replay Protection, Leaderboards & Admin Moderation', async () => {
  let mockPlayValid = true;
  const ctx = await startTestServer({
    playVerifier: async ({ productId }) => ({
      valid: mockPlayValid,
      orderId: `GPA.TEST.${productId}`
    })
  });

  try {
    // 1. Health endpoint
    const healthRes = await fetch(`${ctx.baseHttp}/health`);
    assert.equal(healthRes.status, 200);
    const healthJson = await healthRes.json();
    assert.equal(healthJson.status, 'ok');
    assert.equal(healthJson.maxPlayersPerMatch, 6);

    // 2. Unauthenticated & Anonymous token rejection
    const unauthRes = await fetch(`${ctx.baseHttp}/api/v1/players/wallet`);
    assert.equal(unauthRes.status, 401);

    const anonRes = await fetch(`${ctx.baseHttp}/api/v1/players/wallet`, {
      headers: { Authorization: 'Bearer token-anon' }
    });
    assert.equal(anonRes.status, 403);

    // 3. Server-side currency transaction (unlock skin & insufficient balance check)
    const walletRes = await fetch(`${ctx.baseHttp}/api/v1/players/wallet`, {
      headers: { Authorization: 'Bearer token-alice' }
    });
    assert.equal(walletRes.status, 200);
    const { wallet } = await walletRes.json();
    assert.equal(wallet.coins, 250);

    const failSkinRes = await fetch(`${ctx.baseHttp}/api/v1/players/economy/unlock-skin`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer token-alice',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ skinId: 'fire_dragon' }) // costs 400 coins
    });
    assert.equal(failSkinRes.status, 409);

    // 4. Purchase Verification & Replay Protection
    const verifyRes = await fetch(`${ctx.baseHttp}/api/v1/purchases/verify`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer token-alice',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        productId: 'cobradash_coins_500',
        purchaseToken: 'play_token_abcdef123456'
      })
    });
    assert.equal(verifyRes.status, 200);
    const verifyJson = await verifyRes.json();
    assert.equal(verifyJson.status, 'VERIFIED_AND_GRANTED');
    assert.equal(verifyJson.coins, 750);

    // Replay by Bob must be rejected
    const replayRes = await fetch(`${ctx.baseHttp}/api/v1/purchases/verify`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer token-bob',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        productId: 'cobradash_coins_500',
        purchaseToken: 'play_token_abcdef123456'
      })
    });
    assert.equal(replayRes.status, 409);

    // Unlock fire_dragon now that Alice has 750 coins
    const buySkinRes = await fetch(`${ctx.baseHttp}/api/v1/players/economy/unlock-skin`, {
      method: 'POST',
      headers: {
        Authorization: 'Bearer token-alice',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ skinId: 'fire_dragon' })
    });
    assert.equal(buySkinRes.status, 200);
    const buySkinJson = await buySkinRes.json();
    assert.equal(buySkinJson.coins, 350);

    // 5. Regional leaderboard update + query
    const regUpdate = await fetch(`${ctx.baseHttp}/api/v1/players/region`, {
      method: 'PUT',
      headers: {
        Authorization: 'Bearer token-alice',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ region: 'EU' })
    });
    assert.equal(regUpdate.status, 200);

    await ctx.services.playerService.recordAuthoritativeMatchResult({
      uid: 'uid_alice',
      nickname: 'AliceViper',
      skinId: 'fire_dragon',
      mode: 'RANKED_MULTIPLAYER',
      arenaId: 'jungle_arena',
      score: 1850,
      kills: 4,
      maxLength: 44,
      durationSeconds: 110,
      victory: true,
      roomCode: 'RM001'
    });

    const euLbRes = await fetch(`${ctx.baseHttp}/api/v1/leaderboard/regional/EU`, {
      headers: { Authorization: 'Bearer token-bob' }
    });
    assert.equal(euLbRes.status, 200);
    const euLbJson = await euLbRes.json();
    assert.equal(euLbJson.entries.length, 1);
    assert.equal(euLbJson.entries[0].highScore, 1850);

    // 6. Real WebSocket 2-player Private Room sync + Anti-Cheat + Admin Ban
    const wsAlice = new WebSocket(`${ctx.baseWs}?action=create&mode=PRIVATE_FRIENDS_MATCH&arena=cyber_city`, {
      headers: { Authorization: 'Bearer token-alice' }
    });
    const aliceWelcome = await nextWsMessage(wsAlice, (m) => m.type === 'welcome');
    assert.ok(aliceWelcome.roomCode);
    assert.ok(aliceWelcome.reconnectToken);

    const wsBob = new WebSocket(`${ctx.baseWs}?action=join&room=${aliceWelcome.roomCode}`, {
      headers: { Authorization: 'Bearer token-bob' }
    });
    const bobWelcome = await nextWsMessage(wsBob, (m) => m.type === 'welcome');
    assert.equal(bobWelcome.roomCode, aliceWelcome.roomCode);
    assert.equal(bobWelcome.players, 2);

    // Send input and wait for authoritative world_snapshot containing both players
    wsAlice.send(JSON.stringify({ type: 'input', yaw: 0.75, boost: true, seq: 1 }));
    const snap = await nextWsMessage(
      wsBob,
      (m) => m.type === 'world_snapshot' && Array.isArray(m.snakes) && m.snakes.length === 2
    );
    assert.equal(snap.snakes.length, 2);

    // Admin API requires credentials
    const adminFail = await fetch(`${ctx.baseHttp}/api/v1/admin/overview`);
    assert.equal(adminFail.status, 403);

    // Admin bans Bob -> immediately closes Bob's WebSocket and blocks future auth
    const adminBan = await fetch(`${ctx.baseHttp}/api/v1/admin/players/uid_bob/moderate`, {
      method: 'POST',
      headers: {
        'X-Admin-Key': 'test-super-secret-admin-key',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ action: 'BAN', reason: 'Cheating test' })
    });
    assert.equal(adminBan.status, 200);

    const bobBlocked = await fetch(`${ctx.baseHttp}/api/v1/players/wallet`, {
      headers: { Authorization: 'Bearer token-bob' }
    });
    assert.equal(bobBlocked.status, 403);

    wsAlice.close();
    wsBob.close();
  } finally {
    await ctx.close();
  }
});
