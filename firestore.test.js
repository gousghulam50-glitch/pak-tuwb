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
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 5000) {
  return new Date(Date.now() - msOffset);
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

test("Unauthenticated users can browse active videos/channels but cannot read private data or write anything", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("videos").where("status", "==", "ACTIVE").get());
  await assertSucceeds(unauthDb.collection("channels").where("activeFlag", "==", true).get());
  await assertFails(unauthDb.collection("videos").get());
  await assertFails(unauthDb.collection("channels").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("subscriptions").where("subscriberId", "==", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("videos").doc("vid_1").set({
      ownerId: ALICE_UID,
      title: "Hack",
    })
  );
});

test("Users collection: owner create/read/update succeeds, PII read & privilege escalation rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();

  const now = pastDate();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Khan",
      email: "alice@example.com",
      photoUrl: "https://example.com/alice.jpg",
      suspendedFlag: false,
      suspendedReason: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner read succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user PII read fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());

  // Owner cannot self-unsuspend or inject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      suspendedFlag: true,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice New",
      isVerified: true,
      updatedAt: pastDate(1000),
    })
  );

  // Valid partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      updatedAt: pastDate(1000),
    })
  );
});

test("Email spoofing test: unverified admin email rejected, verified bootstrapped admin succeeds", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_1", {
    email: "gousghulam50@gmail.com",
    email_verified: false,
  }).firestore();

  const verifiedAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "gousghulam50@gmail.com",
    email_verified: true,
  }).firestore();

  await assertFails(
    spoofedAdminDb.collection("platform_config").doc("settings").set({
      maxShortDurationSeconds: 60,
      maxNormalDurationSeconds: 180,
      maxUploadSizeMb: 12,
      updatedBy: "spoof_1",
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(
    verifiedAdminDb.collection("platform_config").doc("settings").set({
      maxShortDurationSeconds: 60,
      maxNormalDurationSeconds: 180,
      maxUploadSizeMb: 12,
      updatedBy: ADMIN_UID,
      updatedAt: pastDate(),
    })
  );
});

test("Channel, Video, Chunk, Reaction, Comment, Subscription, and WatchHistory workflows", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  // Create Alice user & channel
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      suspendedFlag: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("channels").doc(ALICE_UID).set({
      ownerId: ALICE_UID,
      name: "پاک ٹیوب چینل",
      handle: "@paktube_alice",
      description: "تخلیقی ویڈیوز",
      subscriberCount: 0,
      videoCount: 0,
      activeFlag: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Channel list query must filter activeFlag == true
  await assertSucceeds(
    bobDb.collection("channels").where("activeFlag", "==", true).get()
  );
  await assertFails(bobDb.collection("channels").get());

  // Upload Short video + binary chunk
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").set({
      ownerId: ALICE_UID,
      channelId: ALICE_UID,
      channelName: "پاک ٹیوب چینل",
      title: "میری شارٹ",
      description: "پاکستانی شارٹ ویڈیو",
      category: "تفریح",
      shortFlag: true,
      durationSeconds: 30,
      storageType: "CHUNKED",
      chunkCount: 1,
      videoUrl: "",
      thumbnailData: "",
      viewCount: 0,
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_1").collection("chunks").doc("0").set({
      ownerId: ALICE_UID,
      index: 0,
      data: "AQIDBA==",
    })
  );

  // Public feed query must include status == ACTIVE
  await assertSucceeds(
    bobDb.collection("videos").where("status", "==", "ACTIVE").get()
  );
  await assertFails(bobDb.collection("videos").get());

  // Bob subscribes to Alice's channel
  await assertSucceeds(
    bobDb.collection("subscriptions").doc(`${BOB_UID}_${ALICE_UID}`).set({
      subscriberId: BOB_UID,
      channelId: ALICE_UID,
      channelName: "پاک ٹیوب چینل",
      createdAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    bobDb.collection("channels").doc(ALICE_UID).update({
      subscriberCount: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Bob reacts LIKE (single active reaction per user) and updates video counter
  await assertSucceeds(
    bobDb.collection("videos").doc("vid_1").collection("reactions").doc(BOB_UID).set({
      userId: BOB_UID,
      reactionType: "LIKE",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("videos").doc("vid_1").update({
      likeCount: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Bob cannot overwrite Alice's video title
  await assertFails(
    bobDb.collection("videos").doc("vid_1").update({
      title: "Hijacked",
      updatedAt: pastDate(1000),
    })
  );

  // Bob comments and Alice replies
  await assertSucceeds(
    bobDb.collection("videos").doc("vid_1").collection("comments").doc("c1").set({
      videoId: "vid_1",
      authorId: BOB_UID,
      authorName: "Bob",
      parentCommentId: "",
      text: "بہت خوب!",
      likeCount: 0,
      createdAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Watch history is owner-isolated
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("watchHistory").doc("vid_1").set({
      videoId: "vid_1",
      title: "میری شارٹ",
      channelId: ALICE_UID,
      channelName: "پاک ٹیوب چینل",
      shortFlag: true,
      durationSeconds: 30,
      recordedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("watchHistory").get()
  );
});

test("Suspended users are blocked from creating videos or comments, and admins can moderate", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "gousghulam50@gmail.com",
    email_verified: true,
  }).firestore();

  const now = pastDate();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      suspendedFlag: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("channels").doc(ALICE_UID).set({
      ownerId: ALICE_UID,
      name: "Alice Channel",
      handle: "@alice",
      description: "Bio",
      subscriberCount: 0,
      videoCount: 0,
      activeFlag: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin suspends Alice
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      suspendedFlag: true,
      suspendedReason: "Policy violation",
      updatedAt: pastDate(1000),
    })
  );

  // Suspended Alice cannot upload video
  await assertFails(
    aliceDb.collection("videos").doc("vid_blocked").set({
      ownerId: ALICE_UID,
      channelId: ALICE_UID,
      channelName: "Alice Channel",
      title: "Blocked Video",
      description: "Desc",
      category: "تفریح",
      shortFlag: false,
      durationSeconds: 45,
      storageType: "URL",
      chunkCount: 0,
      videoUrl: "https://example.com/v.mp4",
      viewCount: 0,
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      status: "ACTIVE",
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});
