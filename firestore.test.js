const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any BeatWave collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("playlists").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("playlists").doc("pl_1").set({
      userId: ALICE_UID,
      name: "Road Trip",
      createdAtMs: 1000,
      updatedAtMs: 1000,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("songStats").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("meta").get());
});

test("Owner can create, rename, list, and delete playlists and tracks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const playlistRef = aliceDb.collection("users").doc(ALICE_UID).collection("playlists").doc("pl_001");
  await assertSucceeds(
    playlistRef.set({
      userId: ALICE_UID,
      name: "Late Night Synth",
      createdAtMs: 1700000000000,
      updatedAtMs: 1700000000000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Rename playlist (partial update)
  await assertSucceeds(
    playlistRef.update({
      name: "Midnight Synth",
      updatedAtMs: 1700000005000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    playlistRef.update({
      name: "Shadow",
      isAdmin: true,
      updatedAtMs: 1700000006000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    playlistRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Add track to playlist
  const trackRef = playlistRef.collection("tracks").doc("tr_001");
  await assertSucceeds(
    trackRef.set({
      userId: ALICE_UID,
      playlistId: "pl_001",
      songKey: "song_abc123",
      mediaStoreId: 42,
      title: "Neon Horizon",
      artist: "Wave Runner",
      album: "Night Drive",
      durationMs: 214000,
      position: 0,
      addedAtMs: 1700000001000,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reorder track position
  await assertSucceeds(
    trackRef.update({
      position: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List playlists & tracks
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("playlists").get());
  await assertSucceeds(playlistRef.collection("tracks").get());

  // Delete track and playlist
  await assertSucceeds(trackRef.delete());
  await assertSucceeds(playlistRef.delete());
});

test("Owner can create and update songStats and listening_summary", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const statRef = aliceDb.collection("users").doc(ALICE_UID).collection("songStats").doc("song_abc123");
  await assertSucceeds(
    statRef.set({
      userId: ALICE_UID,
      songKey: "song_abc123",
      title: "Neon Horizon",
      artist: "Wave Runner",
      durationMs: 214000,
      playCount: 3,
      listenedMs: 540000,
      lastPlayedAtMs: 1700000000000,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    statRef.update({
      playCount: 4,
      listenedMs: 754000,
      lastPlayedAtMs: 1700000100000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const summaryRef = aliceDb.collection("users").doc(ALICE_UID).collection("meta").doc("listening_summary");
  await assertSucceeds(
    summaryRef.set({
      userId: ALICE_UID,
      totalListeningMs: 754000,
      totalPlays: 4,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user access and future timestamps are rejected", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("playlists").doc("pl_alice").set({
      userId: ALICE_UID,
      name: "Alice Private",
      createdAtMs: 1000,
      updatedAtMs: 1000,
      createdAt: created,
      updatedAt: created,
    });
  });

  // Bob cannot read, list, or delete Alice's playlists
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("playlists").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("playlists").doc("pl_alice").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("playlists").doc("pl_alice").delete());

  // Future timestamp rejected
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).collection("playlists").doc("pl_bob").set({
      userId: BOB_UID,
      name: "Future Playlist",
      createdAtMs: 1000,
      updatedAtMs: 1000,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});
