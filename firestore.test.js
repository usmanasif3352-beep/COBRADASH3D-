const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const ts = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid) {
  return {
    userId: uid,
    nickname: "ViperOne",
    xp: 250,
    level: 3,
    highScore: 4200,
    totalKills: 12,
    matchesPlayed: 8,
    wins: 3,
    selectedSkinId: "royal_cobra",
    selectedArenaId: "jungle_arena",
    unlockedSkinIds: ["royal_cobra", "emerald_viper"],
    unlockedAchievementIds: ["first_blood"],
    battlePassXp: 150,
    dailyRewardStreak: 2,
    lastDailyClaimDay: 20400,
    createdAt: ts(),
    updatedAt: ts(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write profiles, matches, or leaderboard", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("matches").get());
  await assertFails(unauthDb.collection("leaderboard_entries").where("isPublic", "==", true).get());
});

test("Owner can create and update their own profile, but cannot add ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(profileRef.update({
    highScore: 5500,
    updatedAt: ts(),
  }));

  await assertFails(profileRef.update({
    isAdmin: true,
    updatedAt: ts(),
  }));
});

test("Cross-user access to private user profile and matches is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("matches").doc("m1").set({
      userId: ALICE_UID,
      mode: "AI_BATTLE",
      arenaId: "jungle_arena",
      skinId: "royal_cobra",
      score: 1200,
      kills: 4,
      maxLength: 38,
      durationSeconds: 180,
      victory: true,
      createdAt: ts(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("matches").get());
});

test("Match records are append-only (updates denied)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const matchRef = aliceDb.collection("users").doc(ALICE_UID).collection("matches").doc("m_immutable");
  await assertSucceeds(matchRef.set({
    userId: ALICE_UID,
    mode: "PRACTICE",
    arenaId: "cyber_city",
    skinId: "neon_snake",
    score: 900,
    kills: 2,
    maxLength: 25,
    durationSeconds: 95,
    victory: false,
    createdAt: ts(),
  }));

  await assertFails(matchRef.update({ score: 999999 }));
});

test("Leaderboard entries require isPublic == true query alignment and owner-only writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("leaderboard_entries").doc(ALICE_UID).set({
      userId: ALICE_UID,
      nickname: "ViperOne",
      highScore: 4200,
      level: 3,
      wins: 3,
      skinId: "royal_cobra",
      isPublic: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Aligned public query succeeds
  await assertSucceeds(
    bobDb.collection("leaderboard_entries").where("isPublic", "==", true).get()
  );
  // Unfiltered query fails (rules are not filters)
  await assertFails(bobDb.collection("leaderboard_entries").get());
  // Bob cannot overwrite Alice's entry
  await assertFails(
    bobDb.collection("leaderboard_entries").doc(ALICE_UID).update({
      highScore: 1,
      updatedAt: ts(),
    })
  );
});
