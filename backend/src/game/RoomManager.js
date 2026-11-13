import crypto from 'node:crypto';
import { SimulationRoom } from './SimulationRoom.js';
import { ApiError } from '../middleware/errorHandler.js';

export class RoomManager {
  constructor({ config, playerService = null, antiCheat = null }) {
    this.config = config;
    this.playerService = playerService;
    this.antiCheat = antiCheat;
    this.rooms = new Map(); // roomCode -> SimulationRoom
    this.cleanupInterval = setInterval(() => this.pruneIdleRooms(), 30_000);
    if (typeof this.cleanupInterval.unref === 'function') {
      this.cleanupInterval.unref();
    }
  }

  #generateRoomCode() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    for (let attempt = 0; attempt < 16; attempt++) {
      let code = '';
      const bytes = crypto.randomBytes(6);
      for (let i = 0; i < 6; i++) {
        code += chars[bytes[i] % chars.length];
      }
      if (!this.rooms.has(code)) return code;
    }
    return crypto.randomBytes(3).toString('hex').toUpperCase();
  }

  resolveCapacity(requestedMaxPlayers) {
    const raw = Number.parseInt(requestedMaxPlayers, 10);
    if (!Number.isFinite(raw)) return this.config.defaultMaxPlayers;
    return Math.min(this.config.maxPlayersPerMatch, Math.max(2, raw));
  }

  createRoom({
    modeId = 'PRIVATE_FRIENDS_MATCH',
    arenaId = 'jungle_arena',
    isPrivate = false,
    hostUid = null,
    maxPlayers = undefined
  }) {
    const roomCode = this.#generateRoomCode();
    const room = new SimulationRoom({
      roomCode,
      modeId,
      arenaId,
      isPrivate,
      hostUid,
      maxPlayers: this.resolveCapacity(maxPlayers),
      tickRateHz: this.config.tickRateHz,
      snapshotRateHz: this.config.snapshotRateHz,
      reconnectGraceMs: this.config.reconnectGraceMs,
      playerService: this.playerService,
      antiCheat: this.antiCheat,
      onRoomEmpty: (code) => this.closeRoom(code)
    });
    this.rooms.set(roomCode, room);
    room.start();
    return room;
  }

  findOrCreateMatchmakingRoom({ modeId = 'RANKED_MULTIPLAYER', arenaId = 'jungle_arena', maxPlayers = undefined }) {
    for (const room of this.rooms.values()) {
      if (!room.isPrivate && room.mode.id === modeId && room.arena.id === arenaId && room.hasCapacity()) {
        return room;
      }
    }
    return this.createRoom({
      modeId,
      arenaId,
      isPrivate: false,
      maxPlayers
    });
  }

  getRoom(roomCode) {
    const clean = String(roomCode || '').trim().toUpperCase();
    const room = this.rooms.get(clean);
    if (!room) {
      throw new ApiError(404, 'ROOM_NOT_FOUND', `Room '${clean}' does not exist or has ended`);
    }
    return room;
  }

  listPublicRooms() {
    const list = [];
    for (const room of this.rooms.values()) {
      if (!room.isPrivate && !room.matchFinished) {
        list.push(room.getSummary());
      }
    }
    return list;
  }

  listAllRooms() {
    return Array.from(this.rooms.values()).map((r) => r.getSummary());
  }

  closeRoom(roomCode) {
    const clean = String(roomCode || '').trim().toUpperCase();
    const room = this.rooms.get(clean);
    if (!room) return false;
    room.stop();
    this.rooms.delete(clean);
    return true;
  }

  pruneIdleRooms() {
    const now = Date.now();
    for (const [code, room] of this.rooms.entries()) {
      const idleMs = now - room.lastActivityMs;
      if (idleMs > this.config.idleRoomTtlMs || (room.matchFinished && idleMs > 20_000)) {
        this.closeRoom(code);
      }
    }
  }

  getStats() {
    let totalConnectedPlayers = 0;
    for (const room of this.rooms.values()) {
      for (const p of room.players.values()) {
        if (p.connected) totalConnectedPlayers++;
      }
    }
    return {
      activeRooms: this.rooms.size,
      totalConnectedPlayers,
      maxPlayersPerMatch: this.config.maxPlayersPerMatch
    };
  }

  shutdown() {
    clearInterval(this.cleanupInterval);
    for (const code of Array.from(this.rooms.keys())) {
      this.closeRoom(code);
    }
  }
}
