export class AntiCheatMonitor {
  constructor({ playerService, maxStrikes = 6 } = {}) {
    this.playerService = playerService;
    this.maxStrikes = maxStrikes;
    this.strikesByUid = new Map();
  }

  /**
   * Validates incoming gameplay packets and flags any client-forged authority fields.
   */
  validatePacket(uid, packet, lastInputSeq = -1) {
    if (!packet || typeof packet !== 'object') {
      return this.#recordStrike(uid, 'MALFORMED_PACKET', 'Non-object packet payload');
    }

    // Never trust client-supplied scores, currency, kills, speed, or position teleportation
    const forbiddenFields = ['score', 'kills', 'coins', 'gems', 'xp', 'speed', 'segments', 'headX', 'headZ', 'isVictory'];
    for (const field of forbiddenFields) {
      if (field in packet) {
        return this.#recordStrike(uid, 'CLIENT_AUTHORITY_TAMPER', `Forbidden client field '${field}' supplied`, 3);
      }
    }

    if (packet.type === 'input') {
      if (typeof packet.yaw !== 'number' || !Number.isFinite(packet.yaw) || Math.abs(packet.yaw) > 100) {
        return this.#recordStrike(uid, 'INVALID_YAW', `Invalid steering yaw: ${packet.yaw}`, 2);
      }
      if (packet.boost !== undefined && typeof packet.boost !== 'boolean') {
        return this.#recordStrike(uid, 'INVALID_BOOST_TYPE', 'Boost flag must be boolean', 1);
      }
      if (packet.seq !== undefined) {
        if (!Number.isInteger(packet.seq) || packet.seq < 0 || packet.seq <= lastInputSeq) {
          return { ok: false, kick: false, reason: 'STALE_OR_OUT_OF_ORDER_SEQ' };
        }
      }
    }

    return { ok: true, kick: false };
  }

  /**
   * Validates per-tick physics bounds for an entity in case of external tampering.
   */
  validateEntityPhysics(snake, prevX, prevZ, dt, maxTheoreticalSpeed) {
    const dx = snake.headX - prevX;
    const dz = snake.headZ - prevZ;
    const dist = Math.hypot(dx, dz);
    const allowedDist = maxTheoreticalSpeed * dt * 1.25 + 0.5;
    if (dist > allowedDist) {
      this.#recordStrike(snake.uid, 'SPEED_ANOMALY', `Moved ${dist.toFixed(2)} in ${dt.toFixed(3)}s`, 2);
      return false;
    }
    return true;
  }

  #recordStrike(uid, code, detail, weight = 1) {
    const prev = this.strikesByUid.get(uid) || 0;
    const next = prev + weight;
    this.strikesByUid.set(uid, next);

    if (this.playerService) {
      this.playerService
        .recordModerationFlag({
          uid,
          code,
          detail,
          strikes: next
        })
        .catch(() => {});
    }

    return {
      ok: false,
      kick: next >= this.maxStrikes,
      reason: code,
      detail,
      strikes: next
    };
  }

  clearPlayer(uid) {
    this.strikesByUid.delete(uid);
  }
}
