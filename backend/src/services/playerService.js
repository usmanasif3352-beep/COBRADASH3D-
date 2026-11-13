import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import { ApiError } from '../middleware/errorHandler.js';
import { normalizeRegion } from '../game/catalog.js';

export class PlayerService {
  constructor(db) {
    this.db = db;
  }

  async getOrCreateWallet(uid, defaultNickname = 'CobraPilot', defaultRegion = 'GLOBAL') {
    const walletRef = this.db.collection('wallets').document(uid);
    const snap = await walletRef.get();
    if (snap.exists) {
      return { uid, ...snap.data() };
    }
    const cleanRegion = normalizeRegion(defaultRegion);
    const initial = {
      uid,
      nickname: String(defaultNickname || 'CobraPilot').slice(0, 24),
      region: cleanRegion,
      coins: 250,
      gems: 10,
      eloRating: 1000,
      vipActive: false,
      battlePassPremium: false,
      adsRemoved: false,
      onlineMatchesPlayed: 0,
      onlineWins: 0,
      onlineKills: 0,
      onlineHighScore: 0,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    };
    await walletRef.set(initial);
    return {
      ...initial,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
  }

  async getProfileOverview(uid) {
    const [userSnap, wallet] = await Promise.all([
      this.db.collection('users').document(uid).get(),
      this.getOrCreateWallet(uid)
    ]);
    return {
      uid,
      profile: userSnap.exists ? userSnap.data() : null,
      wallet
    };
  }

  async updateRegion(uid, region) {
    const cleanRegion = normalizeRegion(region);
    if (cleanRegion === 'GLOBAL') {
      throw new ApiError(400, 'INVALID_REGION', 'Select a specific region: NA, EU, AS, SA, OC, or AF');
    }
    await this.getOrCreateWallet(uid);
    await this.db.collection('wallets').document(uid).update({
      region: cleanRegion,
      updatedAt: FieldValue.serverTimestamp()
    });
    return { uid, region: cleanRegion };
  }

  /**
   * Atomic server-side currency transaction (coins/gems).
   * Clients cannot pass raw balances; only validated item/reward operations call this.
   */
  async executeCurrencyTransaction({
    uid,
    coinsDelta = 0,
    gemsDelta = 0,
    reason,
    referenceId = null,
    metadata = {}
  }) {
    if (!Number.isInteger(coinsDelta) || !Number.isInteger(gemsDelta)) {
      throw new ApiError(400, 'INVALID_DELTA', 'Currency deltas must be integers');
    }
    if (!reason || typeof reason !== 'string') {
      throw new ApiError(400, 'MISSING_REASON', 'Transaction reason is required');
    }

    const walletRef = this.db.collection('wallets').document(uid);
    const txLogRef = this.db.collection('currency_transactions').document();

    return this.db.runTransaction(async (tx) => {
      const snap = await tx.get(walletRef);
      const current = snap.exists
        ? snap.data()
        : {
            uid,
            coins: 250,
            gems: 10,
            region: 'GLOBAL',
            eloRating: 1000,
            vipActive: false,
            battlePassPremium: false,
            adsRemoved: false
          };

      const nextCoins = (current.coins || 0) + coinsDelta;
      const nextGems = (current.gems || 0) + gemsDelta;

      if (nextCoins < 0) {
        throw new ApiError(409, 'INSUFFICIENT_COINS', 'Not enough coins for this transaction');
      }
      if (nextGems < 0) {
        throw new ApiError(409, 'INSUFFICIENT_GEMS', 'Not enough gems for this transaction');
      }

      tx.set(
        walletRef,
        {
          ...current,
          coins: nextCoins,
          gems: nextGems,
          updatedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );

      tx.set(txLogRef, {
        txId: txLogRef.id,
        uid,
        coinsDelta,
        gemsDelta,
        balanceAfterCoins: nextCoins,
        balanceAfterGems: nextGems,
        reason,
        referenceId,
        metadata,
        createdAt: FieldValue.serverTimestamp()
      });

      return {
        txId: txLogRef.id,
        uid,
        coins: nextCoins,
        gems: nextGems
      };
    });
  }

  async getTransactionHistory(uid, limit = 30) {
    const snap = await this.db
      .collection('currency_transactions')
      .where('uid', '==', uid)
      .orderBy('createdAt', 'desc')
      .limit(Math.min(100, Math.max(1, limit)))
      .get();

    return snap.docs.map((d) => d.data());
  }

  /**
   * Authoritative match finalization: writes match record, updates user stats,
   * grants server-computed coins/XP, and updates global + regional leaderboards.
   */
  async recordAuthoritativeMatchResult({
    uid,
    nickname,
    skinId,
    mode,
    arenaId,
    score,
    kills,
    maxLength,
    durationSeconds,
    victory,
    roomCode
  }) {
    const safeScore = Math.max(0, Math.floor(score));
    const safeKills = Math.max(0, Math.floor(kills));
    const safeMaxLen = Math.max(1, Math.floor(maxLength));
    const safeDur = Math.max(0, Math.floor(durationSeconds));
    const earnedXp = Math.floor(safeScore / 10) + safeKills * 25 + (victory ? 150 : 40);
    const earnedCoins = Math.min(500, Math.floor(safeScore / 50) + safeKills * 15 + (victory ? 100 : 15));

    const wallet = await this.getOrCreateWallet(uid, nickname);
    const region = normalizeRegion(wallet.region || 'GLOBAL');
    const eloDelta = victory ? 24 : safeScore >= 1000 ? 6 : -10;
    const nextElo = Math.max(100, (wallet.eloRating || 1000) + eloDelta);

    const userRef = this.db.collection('users').document(uid);
    const matchRef = userRef.collection('matches').document();
    const lbRef = this.db.collection('leaderboard_entries').document(uid);
    const regLbRef = this.db.collection('regional_leaderboard').document(`${region}_${uid}`);
    const walletRef = this.db.collection('wallets').document(uid);
    const txRef = this.db.collection('currency_transactions').document();

    await this.db.runTransaction(async (tx) => {
      const [userSnap, lbSnap, wSnap] = await Promise.all([
        tx.get(userRef),
        tx.get(lbRef),
        tx.get(walletRef)
      ]);

      const uData = userSnap.exists
        ? userSnap.data()
        : {
            userId: uid,
            nickname: nickname || 'CobraPilot',
            xp: 0,
            level: 1,
            highScore: 0,
            totalKills: 0,
            matchesPlayed: 0,
            wins: 0,
            selectedSkinId: skinId || 'royal_cobra',
            selectedArenaId: arenaId || 'jungle_arena',
            unlockedSkinIds: ['royal_cobra', 'emerald_striker', 'cyber_fang'],
            unlockedAchievementIds: [],
            battlePassXp: 0,
            dailyRewardStreak: 0,
            lastDailyClaimDay: 0,
            createdAt: FieldValue.serverTimestamp()
          };

      const nextXp = (uData.xp || 0) + earnedXp;
      const nextLevel = Math.min(500, 1 + Math.floor(nextXp / 500));
      const nextHigh = Math.max(uData.highScore || 0, safeScore);
      const nextWins = (uData.wins || 0) + (victory ? 1 : 0);
      const nextKills = (uData.totalKills || 0) + safeKills;
      const nextMatches = (uData.matchesPlayed || 0) + 1;

      tx.set(
        userRef,
        {
          ...uData,
          userId: uid,
          nickname: (nickname || uData.nickname || 'CobraPilot').slice(0, 24),
          xp: nextXp,
          level: nextLevel,
          highScore: nextHigh,
          totalKills: nextKills,
          matchesPlayed: nextMatches,
          wins: nextWins,
          battlePassXp: (uData.battlePassXp || 0) + earnedXp,
          updatedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );

      tx.set(matchRef, {
        userId: uid,
        mode,
        arenaId,
        skinId: skinId || 'royal_cobra',
        roomCode: roomCode || null,
        authoritative: true,
        score: safeScore,
        kills: safeKills,
        maxLength: safeMaxLen,
        durationSeconds: safeDur,
        victory: Boolean(victory),
        createdAt: FieldValue.serverTimestamp()
      });

      const lbPayload = {
        userId: uid,
        nickname: (nickname || uData.nickname || 'CobraPilot').slice(0, 24),
        highScore: nextHigh,
        level: nextLevel,
        wins: nextWins,
        skinId: skinId || uData.selectedSkinId || 'royal_cobra',
        isPublic: true,
        updatedAt: FieldValue.serverTimestamp(),
        createdAt: lbSnap.exists ? lbSnap.data().createdAt : FieldValue.serverTimestamp()
      };
      tx.set(lbRef, lbPayload, { merge: true });

      tx.set(
        regLbRef,
        {
          ...lbPayload,
          region,
          eloRating: nextElo
        },
        { merge: true }
      );

      const curWallet = wSnap.exists ? wSnap.data() : wallet;
      const nextCoins = (curWallet.coins || 0) + earnedCoins;

      tx.set(
        walletRef,
        {
          ...curWallet,
          coins: nextCoins,
          eloRating: nextElo,
          onlineMatchesPlayed: (curWallet.onlineMatchesPlayed || 0) + 1,
          onlineWins: (curWallet.onlineWins || 0) + (victory ? 1 : 0),
          onlineKills: (curWallet.onlineKills || 0) + safeKills,
          onlineHighScore: Math.max(curWallet.onlineHighScore || 0, safeScore),
          updatedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );

      tx.set(txRef, {
        txId: txRef.id,
        uid,
        coinsDelta: earnedCoins,
        gemsDelta: 0,
        balanceAfterCoins: nextCoins,
        balanceAfterGems: curWallet.gems || 0,
        reason: 'ONLINE_MATCH_REWARD',
        referenceId: roomCode || matchRef.id,
        metadata: { mode, score: safeScore, kills: safeKills, victory: Boolean(victory) },
        createdAt: FieldValue.serverTimestamp()
      });
    });

    return {
      earnedXp,
      earnedCoins,
      eloDelta,
      nextElo
    };
  }

  async getMatchHistory(uid, limit = 25) {
    const snap = await this.db
      .collection('users')
      .document(uid)
      .collection('matches')
      .orderBy('createdAt', 'desc')
      .limit(Math.min(50, Math.max(1, limit)))
      .get();
    return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  }

  async getGlobalLeaderboard(limit = 50) {
    const snap = await this.db
      .collection('leaderboard_entries')
      .where('isPublic', '==', true)
      .orderBy('highScore', 'desc')
      .limit(Math.min(100, Math.max(1, limit)))
      .get();
    return snap.docs.map((d, idx) => ({ rank: idx + 1, ...d.data() }));
  }

  async getRegionalLeaderboard(region, limit = 50) {
    const cleanRegion = normalizeRegion(region);
    if (cleanRegion === 'GLOBAL') {
      return this.getGlobalLeaderboard(limit);
    }
    const snap = await this.db
      .collection('regional_leaderboard')
      .where('region', '==', cleanRegion)
      .orderBy('highScore', 'desc')
      .limit(Math.min(100, Math.max(1, limit)))
      .get();
    return snap.docs.map((d, idx) => ({ rank: idx + 1, ...d.data() }));
  }

  async getModerationStatus(uid) {
    const snap = await this.db.collection('player_moderation').document(uid).get();
    if (!snap.exists) return null;
    const data = snap.data();
    if (data.banned && data.banExpiresAt) {
      const expMs = data.banExpiresAt instanceof Timestamp ? data.banExpiresAt.toMillis() : Date.parse(data.banExpiresAt);
      if (Number.isFinite(expMs) && expMs <= Date.now()) {
        await this.db.collection('player_moderation').document(uid).set(
          {
            banned: false,
            updatedAt: FieldValue.serverTimestamp()
          },
          { merge: true }
        );
        return { ...data, banned: false };
      }
    }
    return data;
  }

  async moderatePlayer({ uid, action, reason = 'Policy enforcement', durationHours = null, actor = 'admin' }) {
    const ref = this.db.collection('player_moderation').document(uid);
    const update = {
      uid,
      reason,
      updatedBy: actor,
      updatedAt: FieldValue.serverTimestamp()
    };

    if (action === 'BAN') {
      update.banned = true;
      update.banExpiresAt =
        durationHours && durationHours > 0
          ? Timestamp.fromMillis(Date.now() + durationHours * 3_600_000)
          : null;
    } else if (action === 'UNBAN') {
      update.banned = false;
      update.banExpiresAt = null;
    } else if (action === 'MUTE') {
      update.muted = true;
    } else if (action === 'UNMUTE') {
      update.muted = false;
    } else {
      throw new ApiError(400, 'INVALID_MODERATION_ACTION', 'Action must be BAN, UNBAN, MUTE, or UNMUTE');
    }

    await ref.set(update, { merge: true });
    return { uid, action, reason };
  }

  async recordModerationFlag({ uid, code, detail, strikes }) {
    const flagRef = this.db.collection('moderation_flags').document();
    await flagRef.set({
      flagId: flagRef.id,
      uid,
      code,
      detail,
      strikes,
      createdAt: FieldValue.serverTimestamp()
    });
  }

  async listRecentFlags(limit = 50) {
    const snap = await this.db
      .collection('moderation_flags')
      .orderBy('createdAt', 'desc')
      .limit(Math.min(100, Math.max(1, limit)))
      .get();
    return snap.docs.map((d) => d.data());
  }
}
