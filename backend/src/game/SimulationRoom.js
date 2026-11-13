import crypto from 'node:crypto';
import { resolveArena, resolveMode, POWER_UPS } from './catalog.js';

function normalizeAngle(a) {
  const twoPi = Math.PI * 2;
  let r = a;
  while (r > Math.PI) r -= twoPi;
  while (r < -Math.PI) r += twoPi;
  return r;
}

export class SimulationRoom {
  constructor({
    roomCode,
    modeId,
    arenaId,
    isPrivate = false,
    hostUid = null,
    maxPlayers = 12,
    tickRateHz = 30,
    snapshotRateHz = 20,
    reconnectGraceMs = 15000,
    playerService = null,
    antiCheat = null,
    onRoomEmpty = null
  }) {
    this.roomCode = roomCode;
    this.mode = resolveMode(modeId);
    this.arena = resolveArena(arenaId);
    this.isPrivate = isPrivate;
    this.hostUid = hostUid;
    this.maxPlayers = maxPlayers;
    this.tickRateHz = tickRateHz;
    this.snapshotRateHz = snapshotRateHz;
    this.reconnectGraceMs = reconnectGraceMs;
    this.playerService = playerService;
    this.antiCheat = antiCheat;
    this.onRoomEmpty = onRoomEmpty;

    this.arenaRadius = this.arena.radius;
    this.currentRingRadius = this.arena.radius;
    this.elapsedSec = 0;
    this.timeRemainingSec = this.mode.durationSec;
    this.matchFinished = false;
    this.resultsPersisted = false;
    this.createdAtMs = Date.now();
    this.lastActivityMs = Date.now();

    this.players = new Map(); // uid -> player state
    this.sockets = new Map(); // uid -> WebSocket
    this.orbs = [];
    this.powerUps = [];
    this.obstacles = [];
    this.killFeed = [];
    this.nextOrbId = 1;
    this.nextPowerUpId = 1;
    this.powerUpSpawnTimer = 3.0;

    this.#generateObstacles();
    this.#seedInitialOrbs();
    for (let i = 0; i < 3; i++) this.#spawnPowerUp();

    this.tickTimer = null;
    this.snapshotAccum = 0;
  }

  start() {
    if (this.tickTimer) return;
    const intervalMs = Math.max(15, Math.round(1000 / this.tickRateHz));
    let lastTime = Date.now();
    this.tickTimer = setInterval(() => {
      const now = Date.now();
      const dt = Math.min(0.05, Math.max(0.005, (now - lastTime) / 1000));
      lastTime = now;
      this.step(dt, now);
    }, intervalMs);
  }

  stop() {
    if (this.tickTimer) {
      clearInterval(this.tickTimer);
      this.tickTimer = null;
    }
    for (const ws of this.sockets.values()) {
      try {
        ws.close(1000, 'Room closed');
      } catch {
        // ignore close errors
      }
    }
    this.sockets.clear();
  }

  hasCapacity() {
    return this.players.size < this.maxPlayers && !this.matchFinished;
  }

  addOrReconnectPlayer({ uid, nickname = 'CobraPilot', skinId = 'royal_cobra', reconnectToken = null, ws = null }) {
    this.lastActivityMs = Date.now();
    const existing = this.players.get(uid);
    if (existing) {
      if (reconnectToken && existing.reconnectToken !== reconnectToken) {
        return { error: 'INVALID_RECONNECT_TOKEN' };
      }
      existing.connected = true;
      existing.disconnectedAtMs = null;
      if (ws) this.sockets.set(uid, ws);
      return { player: existing, reconnected: true };
    }

    if (!this.hasCapacity()) {
      return { error: 'ROOM_FULL_OR_FINISHED' };
    }

    const index = this.players.size;
    const angle = (index / Math.max(1, this.maxPlayers)) * Math.PI * 2;
    const dist = this.currentRingRadius * 0.48;
    const spawnX = Math.cos(angle) * dist;
    const spawnZ = Math.sin(angle) * dist;
    const yaw = normalizeAngle(angle + Math.PI);
    const teamId = this.mode.isTeamMode ? index % 2 : index;

    const player = {
      uid,
      nickname: String(nickname || 'CobraPilot').slice(0, 24),
      skinId: String(skinId || 'royal_cobra').slice(0, 64),
      teamId,
      reconnectToken: crypto.randomBytes(16).toString('hex'),
      connected: true,
      disconnectedAtMs: null,
      headX: spawnX,
      headZ: spawnZ,
      yawRad: yaw,
      targetYawRad: yaw,
      baseSpeed: 10.5,
      boostMeter: 100,
      isBoosting: false,
      wantBoost: false,
      score: 0,
      kills: 0,
      targetSegments: 14,
      maxLengthReached: 14,
      isAlive: true,
      abilityCooldownSec: 0,
      slowTimerSec: 0,
      dropTrailAccum: 0,
      lastInputSeq: -1,
      activePowerUps: [{ type: 'TEMP_PROTECTION', remainingSec: 3.5 }],
      pathHistory: [],
      segments: []
    };

    this.#initSnakeGeometry(player, spawnX, spawnZ, yaw);
    this.players.set(uid, player);
    if (ws) this.sockets.set(uid, ws);

    return { player, reconnected: false };
  }

  markDisconnected(uid) {
    const p = this.players.get(uid);
    this.sockets.delete(uid);
    if (!p) return;
    p.connected = false;
    p.disconnectedAtMs = Date.now();
    p.wantBoost = false;
  }

  removePlayer(uid) {
    this.players.delete(uid);
    this.sockets.delete(uid);
    this.antiCheat?.clearPlayer(uid);
    if (this.players.size === 0 && this.onRoomEmpty) {
      this.onRoomEmpty(this.roomCode);
    }
  }

  applyPlayerInput(uid, { yaw, boost = false, seq = undefined }) {
    const p = this.players.get(uid);
    if (!p || !p.isAlive || this.matchFinished) return;
    p.targetYawRad = normalizeAngle(yaw);
    p.wantBoost = Boolean(boost);
    if (Number.isInteger(seq)) {
      p.lastInputSeq = seq;
    }
    this.lastActivityMs = Date.now();
  }

  triggerPlayerAbility(uid) {
    const p = this.players.get(uid);
    if (!p || !p.isAlive || this.matchFinished) return { ok: false, reason: 'NOT_ALIVE' };
    if (p.abilityCooldownSec > 0) return { ok: false, reason: 'ON_COOLDOWN' };

    p.abilityCooldownSec = 8.0;
    this.#grantPowerUp(p, 'SPEED_BOOST', 2.5);
    this.#grantPowerUp(p, 'SHIELD', 3.0);

    for (const other of this.players.values()) {
      if (other.uid === uid || !other.isAlive) continue;
      if (this.mode.isTeamMode && other.teamId === p.teamId) continue;
      const dist = Math.hypot(other.headX - p.headX, other.headZ - p.headZ);
      if (dist < 13) {
        other.slowTimerSec = 2.6;
      }
    }
    return { ok: true };
  }

  triggerPlayerRespawn(uid) {
    const p = this.players.get(uid);
    if (!p || this.matchFinished || !this.mode.allowRespawn || p.isAlive) {
      return { ok: false, reason: 'RESPAWN_NOT_ALLOWED' };
    }
    const angle = Math.random() * Math.PI * 2;
    const r = Math.max(6, this.currentRingRadius * 0.35);
    const x = Math.cos(angle) * r;
    const z = Math.sin(angle) * r;
    p.isAlive = true;
    p.score = Math.floor(p.score * 0.75);
    p.targetSegments = Math.max(14, 14 + Math.floor(p.score / 45));
    p.boostMeter = 100;
    p.isBoosting = false;
    p.wantBoost = false;
    p.slowTimerSec = 0;
    p.activePowerUps = [{ type: 'TEMP_PROTECTION', remainingSec: 4.0 }];
    this.#initSnakeGeometry(p, x, z, normalizeAngle(angle + Math.PI));
    return { ok: true };
  }

  step(dt, nowMs = Date.now()) {
    if (this.matchFinished) return;

    // 1. Prune expired disconnected players beyond reconnect grace window
    for (const [uid, p] of this.players.entries()) {
      if (!p.connected && p.disconnectedAtMs && nowMs - p.disconnectedAtMs > this.reconnectGraceMs) {
        if (p.isAlive) {
          this.#eliminateSnake(p, null, `${p.nickname} disconnected`);
        }
        this.removePlayer(uid);
      }
    }

    if (this.players.size === 0) return;

    this.elapsedSec += dt;

    if (this.timeRemainingSec !== null) {
      this.timeRemainingSec = Math.max(0, this.timeRemainingSec - dt);
      if (this.timeRemainingSec <= 0) {
        this.#finishMatch();
        return;
      }
    }

    if (this.mode.hasShrinkingRing) {
      this.currentRingRadius = Math.max(16, this.arenaRadius - this.elapsedSec * 0.32);
    }

    this.powerUpSpawnTimer -= dt;
    if (this.powerUpSpawnTimer <= 0) {
      this.powerUpSpawnTimer = 6.5;
      if (this.powerUps.length < 6) {
        this.#spawnPowerUp();
      }
    }

    for (const orb of this.orbs) {
      orb.pulsePhase += dt * 3.5;
    }

    // 2. Step alive snakes authoritatively
    for (const p of this.players.values()) {
      if (!p.isAlive) continue;
      if (p.abilityCooldownSec > 0) p.abilityCooldownSec = Math.max(0, p.abilityCooldownSec - dt);
      if (p.slowTimerSec > 0) p.slowTimerSec = Math.max(0, p.slowTimerSec - dt);

      this.#updatePowerUps(p, dt);
      this.#moveSnake(p, dt);
      this.#collectOrbs(p, dt);
      this.#collectPowerUps(p);
    }

    // 3. Resolve authoritative collisions & eliminations
    this.#resolveCollisions();
    this.#maintainOrbPopulation();
    this.#checkMatchEndConditions();

    // 4. Broadcast world snapshot at SNAPSHOT_RATE_HZ
    this.snapshotAccum += dt;
    const snapInterval = 1 / this.snapshotRateHz;
    if (this.snapshotAccum >= snapInterval) {
      this.snapshotAccum = 0;
      this.broadcastSnapshot();
    }
  }

  getSummary() {
    return {
      roomCode: this.roomCode,
      modeId: this.mode.id,
      arenaId: this.arena.id,
      isPrivate: this.isPrivate,
      playerCount: this.players.size,
      maxPlayers: this.maxPlayers,
      elapsedSec: Math.floor(this.elapsedSec),
      timeRemainingSec: this.timeRemainingSec !== null ? Math.ceil(this.timeRemainingSec) : null,
      matchFinished: this.matchFinished
    };
  }

  buildSnapshotFor(recipientUid = null) {
    const snakes = [];
    for (const p of this.players.values()) {
      snakes.push({
        uid: p.uid,
        nickname: p.nickname,
        skinId: p.skinId,
        teamId: p.teamId,
        isAlive: p.isAlive,
        connected: p.connected,
        headX: Number(p.headX.toFixed(3)),
        headZ: Number(p.headZ.toFixed(3)),
        yawRad: Number(p.yawRad.toFixed(4)),
        isBoosting: p.isBoosting,
        score: p.score,
        kills: p.kills,
        length: p.targetSegments,
        activePowerUps: p.activePowerUps.map((pu) => ({
          type: pu.type,
          remainingSec: Number(pu.remainingSec.toFixed(2))
        })),
        segments: p.segments.map((s) => [Number(s.x.toFixed(2)), Number(s.z.toFixed(2))]),
        ...(recipientUid === p.uid
          ? {
              boostMeter: Number(p.boostMeter.toFixed(1)),
              abilityCooldownSec: Number(p.abilityCooldownSec.toFixed(2))
            }
          : {})
      });
    }

    return {
      type: 'world_snapshot',
      roomCode: this.roomCode,
      modeId: this.mode.id,
      arenaId: this.arena.id,
      players: this.players.size,
      maxPlayers: this.maxPlayers,
      elapsedSec: Number(this.elapsedSec.toFixed(2)),
      timeRemainingSec: this.timeRemainingSec !== null ? Math.ceil(this.timeRemainingSec) : null,
      currentRingRadius: Number(this.currentRingRadius.toFixed(2)),
      arenaRadius: this.arenaRadius,
      matchFinished: this.matchFinished,
      killFeed: this.killFeed.slice(-3),
      snakes,
      orbs: this.orbs.map((o) => [
        o.id,
        Number(o.x.toFixed(2)),
        Number(o.z.toFixed(2)),
        o.value,
        o.isDropped ? 1 : 0
      ]),
      powerUps: this.powerUps.map((pu) => ({
        id: pu.id,
        type: pu.type,
        x: Number(pu.x.toFixed(2)),
        z: Number(pu.z.toFixed(2))
      }))
    };
  }

  broadcastSnapshot() {
    for (const [uid, ws] of this.sockets.entries()) {
      if (ws.readyState === 1) {
        ws.send(JSON.stringify(this.buildSnapshotFor(uid)));
      }
    }
  }

  broadcastEvent(payload) {
    const encoded = JSON.stringify(payload);
    for (const ws of this.sockets.values()) {
      if (ws.readyState === 1) {
        ws.send(encoded);
      }
    }
  }

  #headRadius(snake) {
    return 0.72 + Math.min(80, snake.targetSegments) * 0.0045;
  }

  #hasPowerUp(snake, type) {
    return snake.activePowerUps.some((p) => p.type === type);
  }

  #grantPowerUp(snake, type, durationSec) {
    snake.activePowerUps = snake.activePowerUps.filter((p) => p.type !== type);
    snake.activePowerUps.push({ type, remainingSec: durationSec });
  }

  #updatePowerUps(snake, dt) {
    snake.activePowerUps = snake.activePowerUps
      .map((pu) => ({ ...pu, remainingSec: pu.remainingSec - dt }))
      .filter((pu) => pu.remainingSec > 0);

    if (this.#hasPowerUp(snake, 'LIGHTNING') || this.#hasPowerUp(snake, 'FREEZE')) {
      const freeze = this.#hasPowerUp(snake, 'FREEZE');
      for (const other of this.players.values()) {
        if (other.uid === snake.uid || !other.isAlive) continue;
        if (this.mode.isTeamMode && other.teamId === snake.teamId) continue;
        const d = Math.hypot(other.headX - snake.headX, other.headZ - snake.headZ);
        if (d < 13 && other.slowTimerSec <= 0.1) {
          other.slowTimerSec = freeze ? 1.5 : 1.2;
        }
      }
    }
  }

  #moveSnake(snake, dt) {
    const prevX = snake.headX;
    const prevZ = snake.headZ;
    const turnSpeed = 4.4;
    const diff = normalizeAngle(snake.targetYawRad - snake.yawRad);
    const maxTurn = turnSpeed * dt;
    snake.yawRad =
      Math.abs(diff) <= maxTurn
        ? snake.targetYawRad
        : normalizeAngle(snake.yawRad + (diff > 0 ? maxTurn : -maxTurn));

    let speed = snake.baseSpeed;
    if (this.#hasPowerUp(snake, 'SPEED_BOOST')) speed *= 1.42;
    if (snake.slowTimerSec > 0) speed *= 0.58;

    const maxSpeed = snake.baseSpeed * 1.42 * 1.62;

    if (snake.wantBoost && snake.boostMeter > 1) {
      snake.isBoosting = true;
      speed *= 1.62;
      if (!this.#hasPowerUp(snake, 'SPEED_BOOST')) {
        snake.boostMeter = Math.max(0, snake.boostMeter - 26 * dt);
        snake.dropTrailAccum += dt;
        if (snake.dropTrailAccum >= 0.35 && snake.targetSegments > 10) {
          snake.dropTrailAccum = 0;
          const tail = snake.segments[snake.segments.length - 1];
          if (tail) {
            this.#spawnOrb(tail.x, tail.z, 8, true);
          }
        }
      }
    } else {
      snake.isBoosting = false;
      snake.boostMeter = Math.min(100, snake.boostMeter + 12 * dt);
    }

    snake.headX += Math.cos(snake.yawRad) * speed * dt;
    snake.headZ += Math.sin(snake.yawRad) * speed * dt;

    this.antiCheat?.validateEntityPhysics(snake, prevX, prevZ, dt, maxSpeed);

    const first = snake.pathHistory[0];
    if (!first || Math.hypot(snake.headX - first.x, snake.headZ - first.z) >= 0.22) {
      snake.pathHistory.unshift({ x: snake.headX, z: snake.headZ });
      const maxHistory = Math.max(80, snake.targetSegments * 5);
      if (snake.pathHistory.length > maxHistory) {
        snake.pathHistory.length = maxHistory;
      }
    }

    while (snake.segments.length < snake.targetSegments) {
      const last = snake.segments[snake.segments.length - 1] || { x: snake.headX, z: snake.headZ };
      snake.segments.push({ x: last.x, z: last.z });
    }
    while (snake.segments.length > snake.targetSegments && snake.segments.length > 6) {
      snake.segments.pop();
    }

    const spacing = this.#headRadius(snake) * 1.18;
    let histIdx = 0;
    let accumDist = 0;
    let prevPt = { x: snake.headX, z: snake.headZ };

    for (let segIdx = 0; segIdx < snake.segments.length; segIdx++) {
      const targetDist = (segIdx + 1) * spacing;
      while (histIdx < snake.pathHistory.length) {
        const pt = snake.pathHistory[histIdx];
        const stepD = Math.hypot(pt.x - prevPt.x, pt.z - prevPt.z);
        if (accumDist + stepD >= targetDist) {
          const t = stepD > 0.0001 ? (targetDist - accumDist) / stepD : 0;
          snake.segments[segIdx].x = prevPt.x + (pt.x - prevPt.x) * t;
          snake.segments[segIdx].z = prevPt.z + (pt.z - prevPt.z) * t;
          break;
        } else {
          accumDist += stepD;
          prevPt = pt;
          histIdx++;
        }
      }
    }
  }

  #collectOrbs(snake, dt) {
    const magnetRange = this.#hasPowerUp(snake, 'MAGNET') ? 11.5 : 2.2;
    const eatRange = this.#headRadius(snake) + 0.72;
    const mult = this.#hasPowerUp(snake, 'DOUBLE_ENERGY') ? 2 : 1;

    for (let i = this.orbs.length - 1; i >= 0; i--) {
      const orb = this.orbs[i];
      const dx = snake.headX - orb.x;
      const dz = snake.headZ - orb.z;
      const dist = Math.hypot(dx, dz);
      if (dist < magnetRange && dist > 0.01) {
        const pull = this.#hasPowerUp(snake, 'MAGNET') ? 22 : 9;
        orb.x += (dx / dist) * pull * dt;
        orb.z += (dz / dist) * pull * dt;
      }
      if (dist <= eatRange) {
        this.orbs.splice(i, 1);
        snake.score += orb.value * mult;
        snake.boostMeter = Math.min(100, snake.boostMeter + 3.5);
        const newTarget = Math.min(95, 14 + Math.floor(snake.score / 45));
        snake.targetSegments = newTarget;
        if (newTarget > snake.maxLengthReached) {
          snake.maxLengthReached = newTarget;
        }
      }
    }
  }

  #collectPowerUps(snake) {
    const reach = this.#headRadius(snake) + 1.35;
    for (let i = this.powerUps.length - 1; i >= 0; i--) {
      const pu = this.powerUps[i];
      const dist = Math.hypot(snake.headX - pu.x, snake.headZ - pu.z);
      if (dist <= reach) {
        this.powerUps.splice(i, 1);
        if (pu.type === 'TELEPORT') {
          const limit = this.currentRingRadius * 0.85;
          snake.headX = Math.max(-limit, Math.min(limit, snake.headX + Math.cos(snake.yawRad) * 12));
          snake.headZ = Math.max(-limit, Math.min(limit, snake.headZ + Math.sin(snake.yawRad) * 12));
          this.#grantPowerUp(snake, 'TEMP_PROTECTION', 2.5);
        } else {
          const def = POWER_UPS[pu.type] || { durationSec: 6.0 };
          this.#grantPowerUp(snake, pu.type, def.durationSec);
        }
      }
    }
  }

  #resolveCollisions() {
    for (const snake of this.players.values()) {
      if (!snake.isAlive) continue;
      const headR = this.#headRadius(snake);
      const isProtected =
        this.#hasPowerUp(snake, 'SHIELD') || this.#hasPowerUp(snake, 'TEMP_PROTECTION');

      // 1. Arena Ring Boundary
      const distCenter = Math.hypot(snake.headX, snake.headZ);
      if (distCenter + headR >= this.currentRingRadius) {
        if (isProtected) {
          const inward = Math.atan2(-snake.headZ, -snake.headX);
          snake.yawRad = inward;
          snake.targetYawRad = inward;
          const clampR = this.currentRingRadius - headR - 0.6;
          if (distCenter > 0.01) {
            snake.headX = (snake.headX / distCenter) * clampR;
            snake.headZ = (snake.headZ / distCenter) * clampR;
          }
        } else {
          this.#eliminateSnake(snake, null, 'Struck the Arena Energy Barrier');
          continue;
        }
      }

      // 2. Arena Pillars
      for (const obs of this.obstacles) {
        const dx = snake.headX - obs.x;
        const dz = snake.headZ - obs.z;
        const d = Math.hypot(dx, dz);
        if (d < obs.radius + headR) {
          if (isProtected) {
            const pushAngle = Math.atan2(dz, dx);
            snake.headX = obs.x + Math.cos(pushAngle) * (obs.radius + headR + 0.3);
            snake.headZ = obs.z + Math.sin(pushAngle) * (obs.radius + headR + 0.3);
            snake.yawRad = pushAngle;
          } else {
            this.#eliminateSnake(snake, null, 'Crashed into an Arena Pillar');
            break;
          }
        }
      }
      if (!snake.isAlive) continue;

      // 3. Snake-to-snake collisions
      for (const other of this.players.values()) {
        if (other.uid === snake.uid || !other.isAlive) continue;
        if (this.mode.isTeamMode && other.teamId === snake.teamId) continue;

        const otherR = this.#headRadius(other);
        const hhDist = Math.hypot(snake.headX - other.headX, snake.headZ - other.headZ);
        if (hhDist < headR + otherR * 0.9) {
          if (!isProtected && snake.targetSegments <= other.targetSegments) {
            this.#eliminateSnake(snake, other, `Head-on clash with ${other.nickname}`);
            break;
          }
        }

        let hitBody = false;
        const segR = otherR * 0.85;
        for (const seg of other.segments) {
          if (Math.hypot(snake.headX - seg.x, snake.headZ - seg.z) < headR + segR) {
            hitBody = true;
            break;
          }
        }
        if (hitBody) {
          if (this.#hasPowerUp(snake, 'SHIELD')) {
            snake.activePowerUps = snake.activePowerUps.filter((p) => p.type !== 'SHIELD');
            this.#grantPowerUp(snake, 'TEMP_PROTECTION', 1.5);
          } else if (!this.#hasPowerUp(snake, 'TEMP_PROTECTION')) {
            this.#eliminateSnake(snake, other, `Eliminated by ${other.nickname}`);
            break;
          }
        }
      }
    }
  }

  #eliminateSnake(victim, killer, reason) {
    victim.isAlive = false;
    victim.isBoosting = false;
    victim.wantBoost = false;

    const step = Math.max(1, Math.floor(victim.segments.length / 14));
    for (let i = 0; i < victim.segments.length; i += step) {
      const s = victim.segments[i];
      this.#spawnOrb(s.x, s.z, 35, true);
    }

    if (killer) {
      killer.kills += 1;
      killer.score += 150;
      this.#addKillFeed(`${killer.nickname} eliminated ${victim.nickname}`);
    } else {
      this.#addKillFeed(`${victim.nickname} hit an arena hazard`);
    }

    const ws = this.sockets.get(victim.uid);
    if (ws && ws.readyState === 1) {
      ws.send(
        JSON.stringify({
          type: 'eliminated',
          uid: victim.uid,
          reason,
          canRespawn: this.mode.allowRespawn && !this.matchFinished,
          score: victim.score,
          kills: victim.kills
        })
      );
    }

    if (!this.mode.allowRespawn && this.players.size >= 2) {
      this.#checkMatchEndConditions();
    }
  }

  #checkMatchEndConditions() {
    if (this.players.size < 2) return;
    if (!this.mode.allowRespawn) {
      const alive = Array.from(this.players.values()).filter((p) => p.isAlive);
      if (alive.length <= 1) {
        this.#finishMatch();
      }
    }
  }

  async #finishMatch() {
    if (this.matchFinished) return;
    this.matchFinished = true;
    this.broadcastSnapshot();

    const ranked = Array.from(this.players.values()).sort((a, b) => b.score - a.score);
    const winnerUid = ranked[0]?.uid || null;

    const results = [];
    for (let i = 0; i < ranked.length; i++) {
      const p = ranked[i];
      const victory = this.mode.isTeamMode
        ? this.#isWinningTeam(p.teamId)
        : p.uid === winnerUid && p.score > 0;

      let reward = { earnedXp: 0, earnedCoins: 0, eloDelta: 0 };
      if (this.playerService && !this.resultsPersisted) {
        try {
          reward = await this.playerService.recordAuthoritativeMatchResult({
            uid: p.uid,
            nickname: p.nickname,
            skinId: p.skinId,
            mode: this.mode.id,
            arenaId: this.arena.id,
            score: p.score,
            kills: p.kills,
            maxLength: p.maxLengthReached,
            durationSeconds: Math.floor(this.elapsedSec),
            victory,
            roomCode: this.roomCode
          });
        } catch (err) {
          console.error('[SimulationRoom] Failed to persist match result:', err?.message || err);
        }
      }

      const entry = {
        rank: i + 1,
        uid: p.uid,
        nickname: p.nickname,
        score: p.score,
        kills: p.kills,
        maxLength: p.maxLengthReached,
        victory,
        earnedXp: reward.earnedXp,
        earnedCoins: reward.earnedCoins
      };
      results.push(entry);
    }

    this.resultsPersisted = true;
    this.broadcastEvent({
      type: 'match_result',
      roomCode: this.roomCode,
      modeId: this.mode.id,
      arenaId: this.arena.id,
      durationSeconds: Math.floor(this.elapsedSec),
      standings: results
    });
  }

  #isWinningTeam(teamId) {
    let t0 = 0;
    let t1 = 0;
    for (const p of this.players.values()) {
      if (p.teamId === 0) t0 += p.score;
      else t1 += p.score;
    }
    return teamId === 0 ? t0 >= t1 : t1 > t0;
  }

  #initSnakeGeometry(snake, x, z, yaw) {
    snake.headX = x;
    snake.headZ = z;
    snake.yawRad = yaw;
    snake.targetYawRad = yaw;
    snake.pathHistory = [];
    snake.segments = [];
    const backX = -Math.cos(yaw);
    const backZ = -Math.sin(yaw);
    for (let i = 0; i < 60; i++) {
      snake.pathHistory.push({ x: x + backX * i * 0.25, z: z + backZ * i * 0.25 });
    }
    for (let i = 0; i < snake.targetSegments; i++) {
      snake.segments.push({ x: x + backX * i * 0.72, z: z + backZ * i * 0.72 });
    }
  }

  #generateObstacles() {
    const count = this.arena.obstacleCount;
    const ringDist = this.arenaRadius * 0.48;
    for (let i = 0; i < count; i++) {
      const angle = (i / count) * Math.PI * 2 + 0.35;
      this.obstacles.push({
        x: Math.cos(angle) * ringDist,
        z: Math.sin(angle) * ringDist,
        radius: 2.2 + (i % 2) * 0.8
      });
    }
  }

  #seedInitialOrbs() {
    for (let i = 0; i < 110; i++) {
      this.#spawnRandomOrb();
    }
  }

  #maintainOrbPopulation() {
    while (this.orbs.length < 95) {
      this.#spawnRandomOrb();
    }
  }

  #spawnRandomOrb() {
    const a = Math.random() * Math.PI * 2;
    const r = Math.random() * this.currentRingRadius * 0.88;
    const value = Math.random() < 0.12 ? 25 : 10;
    this.#spawnOrb(Math.cos(a) * r, Math.sin(a) * r, value, false);
  }

  #spawnOrb(x, z, value, isDropped) {
    if (this.orbs.length >= 220) return;
    this.orbs.push({
      id: this.nextOrbId++,
      x,
      z,
      value,
      isDropped,
      pulsePhase: Math.random() * Math.PI * 2
    });
  }

  #spawnPowerUp() {
    const keys = Object.keys(POWER_UPS).filter((k) => k !== 'TEMP_PROTECTION');
    const type = keys[Math.floor(Math.random() * keys.length)];
    const a = Math.random() * Math.PI * 2;
    const r = Math.random() * this.currentRingRadius * 0.72;
    this.powerUps.push({
      id: this.nextPowerUpId++,
      type,
      x: Math.cos(a) * r,
      z: Math.sin(a) * r
    });
  }

  #addKillFeed(msg) {
    this.killFeed.push(msg);
    if (this.killFeed.length > 4) this.killFeed.shift();
  }
}
