const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = "demo-no-project";
const DATABASE_ID = "ai-studio-android-jarvisai-16b63dbf-40ee-4f01-abae-eb77e00e2de0";

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085
    }
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

test("unauthenticated access is denied", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore({ databaseId: DATABASE_ID });
  await assertFails(unauthedDb.doc("users/user1").get());
  await assertFails(unauthedDb.doc("users/user1").set({
    userId: "user1",
    voiceProfile: "FEMALE",
    wakeWordEnabled: true,
    autoListen: true,
    createdAt: new Date()
  }));
});

test("authenticated user can create and read their own user profile", async () => {
  const authedDb = testEnv.authenticatedContext("user1").firestore({ databaseId: DATABASE_ID });
  await assertSucceeds(authedDb.doc("users/user1").set({
    userId: "user1",
    displayName: "Tony Stark",
    voiceProfile: "FEMALE",
    wakeWordEnabled: true,
    autoListen: true,
    createdAt: new Date()
  }));

  await assertSucceeds(authedDb.doc("users/user1").get());
});

test("user cannot access or modify another user's profile", async () => {
  const user1Db = testEnv.authenticatedContext("user1").firestore({ databaseId: DATABASE_ID });
  const user2Db = testEnv.authenticatedContext("user2").firestore({ databaseId: DATABASE_ID });

  await assertSucceeds(user1Db.doc("users/user1").set({
    userId: "user1",
    voiceProfile: "FEMALE",
    wakeWordEnabled: true,
    autoListen: true,
    createdAt: new Date()
  }));

  // User2 trying to read User1's profile
  await assertFails(user2Db.doc("users/user1").get());

  // User2 trying to overwrite User1's profile
  await assertFails(user2Db.doc("users/user1").set({
    userId: "user1",
    voiceProfile: "CINEMATIC",
    wakeWordEnabled: false,
    autoListen: false,
    createdAt: new Date()
  }));
});

test("authenticated user can log and view their commands and chat messages", async () => {
  const authedDb = testEnv.authenticatedContext("user1").firestore({ databaseId: DATABASE_ID });

  // Command record
  await assertSucceeds(authedDb.collection("users").doc("user1").collection("commands").doc("cmd123").set({
    userId: "user1",
    commandText: "Jarvis, turn on flashlight",
    actionCategory: "FLASHLIGHT_ON",
    status: "SUCCESS",
    resultMessage: "Flashlight activated, sir.",
    wasVoiceTriggered: true,
    createdAt: new Date()
  }));

  // Chat message
  await assertSucceeds(authedDb.collection("users").doc("user1").collection("chats").doc("chat123").set({
    userId: "user1",
    role: "USER",
    content: "Who created you?",
    createdAt: new Date()
  }));
});
