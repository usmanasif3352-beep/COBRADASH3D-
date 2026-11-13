import test from 'node:test';
import assert from 'node:assert/strict';
import { loadConfig } from '../src/config/env.js';
import { SimulationRoom } from '../src/game/SimulationRoom.js';
import { RoomManager } from '../src/game/RoomManager.js';
import { AntiCheatMonitor } from '../src/services/antiCheat.js';

test('SimulationRoom enforces yaw turn limits, boost drain, orb collection, and server score', () => {
  const room = new SimulationRoom({
    roomCode: 'TST101',
    modeId: 'RANKED_MULTIPLAYER',
    arenaId: 'jungle_arena',
    maxPlayers: 8
  });

  const { player } = room.addOrReconnectPlayer({ uid: 'u1', nickname: 'Alice' });
  room.orbs.push({ id: 9999, x: player.headX + 0.4, z: player.headZ, value: 120, isDropped: false, pulsePhase: 0 });
  room.applyPlayerInput('u1', { yaw: 0, boost: true, seq: 1 });

  const initialBoost = player.boostMeter;
  for (let i = 0; i < 15; i++) {
    room.step(0.033);
  }

  assert.ok(player.score >= 120, 'Server computed score should increase after eating orb');
  assert.ok(player.targetSegments > 14, 'Snake length should grow with score');
  assert.ok(player.boostMeter < initialBoost, 'Boost meter should drain when boosting');
  room.stop();
});

test('SimulationRoom detects head-to-body collision and eliminates victim with dropped orbs', () => {
  const room = new SimulationRoom({
    roomCode: 'TST102',
    modeId: 'RANKED_MULTIPLAYER',
    arenaId: 'jungle_arena',
    maxPlayers: 8
  });

  const { player: p1 } = room.addOrReconnectPlayer({ uid: 'u1', nickname: 'Alice' });
  const { player: p2 } = room.addOrReconnectPlayer({ uid: 'u2', nickname: 'Bob' });

  // Strip initial temp protection to test immediate collision detection
  p1.activePowerUps = [];
  p2.activePowerUps = [];
  p1.headX = p2.segments[2].x;
  p1.headZ = p2.segments[2].z;

  const orbCountBefore = room.orbs.length;
  room.step(0.033);

  assert.equal(p1.isAlive, false, 'Victim colliding into opponent body must be eliminated');
  assert.equal(p2.kills, 1, 'Killer must receive server-side kill credit');
  assert.ok(p2.score >= 150, 'Killer must receive +150 elimination bonus');
  assert.ok(room.orbs.length > orbCountBefore, 'Eliminated snake must drop energy orbs');
  room.stop();
});

test('SimulationRoom validates reconnection token and preserves player within grace window', () => {
  const room = new SimulationRoom({
    roomCode: 'TST103',
    modeId: 'PRIVATE_FRIENDS_MATCH',
    arenaId: 'cyber_city',
    reconnectGraceMs: 5000
  });

  const { player } = room.addOrReconnectPlayer({ uid: 'u1', nickname: 'Alice' });
  player.score = 777;
  const token = player.reconnectToken;

  room.markDisconnected('u1');
  assert.equal(player.connected, false);

  const badRetry = room.addOrReconnectPlayer({ uid: 'u1', reconnectToken: 'wrong-token' });
  assert.equal(badRetry.error, 'INVALID_RECONNECT_TOKEN');

  const goodRetry = room.addOrReconnectPlayer({ uid: 'u1', reconnectToken: token });
  assert.equal(goodRetry.reconnected, true);
  assert.equal(goodRetry.player.score, 777);
  room.stop();
});

test('AntiCheatMonitor rejects client-forged score/currency fields and invalid yaw', () => {
  const ac = new AntiCheatMonitor({ maxStrikes: 4 });
  const tamper = ac.validatePacket('u1', { type: 'input', yaw: 0.5, score: 999999 });
  assert.equal(tamper.ok, false);
  assert.equal(tamper.reason, 'CLIENT_AUTHORITY_TAMPER');

  const nanYaw = ac.validatePacket('u1', { type: 'input', yaw: Number.NaN });
  assert.equal(nanYaw.ok, false);
  assert.equal(nanYaw.kick, true, 'Cumulative strikes should trigger kick');
});

test('RoomManager enforces configurable MAX_PLAYERS_PER_MATCH limit', () => {
  const config = loadConfig({ maxPlayersPerMatch: 4, defaultMaxPlayers: 3 });
  const rm = new RoomManager({ config });
  const room = rm.createRoom({ modeId: 'RANKED_MULTIPLAYER', arenaId: 'jungle_arena', maxPlayers: 99 });
  assert.equal(room.maxPlayers, 4, 'Requested maxPlayers must be capped at MAX_PLAYERS_PER_MATCH');
  rm.shutdown();
});
