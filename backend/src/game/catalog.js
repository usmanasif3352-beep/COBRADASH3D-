export const ARENAS = {
  jungle_arena: { id: 'jungle_arena', name: 'Jungle Arena', radius: 56, obstacleCount: 6 },
  desert_kingdom: { id: 'desert_kingdom', name: 'Desert Kingdom', radius: 60, obstacleCount: 7 },
  frozen_world: { id: 'frozen_world', name: 'Frozen World', radius: 58, obstacleCount: 8 },
  volcano_arena: { id: 'volcano_arena', name: 'Volcano Arena', radius: 54, obstacleCount: 6 },
  cyber_city: { id: 'cyber_city', name: 'Cyber City', radius: 64, obstacleCount: 10 },
  galaxy_arena: { id: 'galaxy_arena', name: 'Galaxy Arena', radius: 66, obstacleCount: 7 },
  crystal_kingdom: { id: 'crystal_kingdom', name: 'Crystal Kingdom', radius: 60, obstacleCount: 9 },
  underwater_world: { id: 'underwater_world', name: 'Underwater World', radius: 62, obstacleCount: 8 },
  dark_forest: { id: 'dark_forest', name: 'Dark Forest', radius: 58, obstacleCount: 11 },
  dragon_realm: { id: 'dragon_realm', name: 'Dragon Realm', radius: 68, obstacleCount: 9 }
};

export const GAME_MODES = {
  RANKED_MULTIPLAYER: {
    id: 'RANKED_MULTIPLAYER',
    durationSec: 180,
    allowRespawn: false,
    isTeamMode: false,
    hasShrinkingRing: false
  },
  PRIVATE_FRIENDS_MATCH: {
    id: 'PRIVATE_FRIENDS_MATCH',
    durationSec: 180,
    allowRespawn: true,
    isTeamMode: false,
    hasShrinkingRing: false
  },
  TOURNAMENT_MODE: {
    id: 'TOURNAMENT_MODE',
    durationSec: 180,
    allowRespawn: false,
    isTeamMode: false,
    hasShrinkingRing: true
  },
  BATTLE_ROYALE: {
    id: 'BATTLE_ROYALE',
    durationSec: null,
    allowRespawn: false,
    isTeamMode: false,
    hasShrinkingRing: true
  },
  TEAM_BATTLE: {
    id: 'TEAM_BATTLE',
    durationSec: 150,
    allowRespawn: true,
    isTeamMode: true,
    hasShrinkingRing: false
  },
  CLASSIC_SURVIVAL: {
    id: 'CLASSIC_SURVIVAL',
    durationSec: null,
    allowRespawn: false,
    isTeamMode: false,
    hasShrinkingRing: false
  },
  TIME_ATTACK: {
    id: 'TIME_ATTACK',
    durationSec: 120,
    allowRespawn: true,
    isTeamMode: false,
    hasShrinkingRing: false
  },
  ENDLESS_GROWTH: {
    id: 'ENDLESS_GROWTH',
    durationSec: null,
    allowRespawn: true,
    isTeamMode: false,
    hasShrinkingRing: false
  }
};

export const POWER_UPS = {
  SPEED_BOOST: { type: 'SPEED_BOOST', durationSec: 6.0 },
  SHIELD: { type: 'SHIELD', durationSec: 7.0 },
  MAGNET: { type: 'MAGNET', durationSec: 8.0 },
  DOUBLE_ENERGY: { type: 'DOUBLE_ENERGY', durationSec: 9.0 },
  LIGHTNING: { type: 'LIGHTNING', durationSec: 4.0 },
  FREEZE: { type: 'FREEZE', durationSec: 5.0 },
  INVISIBILITY: { type: 'INVISIBILITY', durationSec: 5.5 },
  TELEPORT: { type: 'TELEPORT', durationSec: 1.5 },
  TEMP_PROTECTION: { type: 'TEMP_PROTECTION', durationSec: 4.0 }
};

export const VALID_REGIONS = new Set(['NA', 'EU', 'AS', 'SA', 'OC', 'AF', 'GLOBAL']);

export const STORE_PRODUCTS = {
  cobradash_no_ads: { type: 'INAPP', adsRemoved: true, coins: 0, gems: 0 },
  cobradash_battle_pass_s1: { type: 'INAPP', battlePassPremium: true, coins: 0, gems: 0 },
  cobradash_coins_500: { type: 'INAPP', coins: 500, gems: 0 },
  cobradash_gems_120: { type: 'INAPP', coins: 0, gems: 120 },
  cobradash_bundle_dragon: {
    type: 'INAPP',
    coins: 1200,
    gems: 80,
    skinId: 'galaxy_dragon'
  },
  cobradash_vip_monthly: {
    type: 'SUBSCRIPTION',
    vipActive: true,
    adsRemoved: true,
    skinId: 'legendary_emperor_cobra'
  }
};

export function resolveArena(arenaId) {
  return ARENAS[arenaId] || ARENAS.jungle_arena;
}

export function resolveMode(modeId) {
  return GAME_MODES[modeId] || GAME_MODES.RANKED_MULTIPLAYER;
}

export function normalizeRegion(region) {
  const clean = String(region || 'GLOBAL').trim().toUpperCase();
  return VALID_REGIONS.has(clean) ? clean : 'GLOBAL';
}
