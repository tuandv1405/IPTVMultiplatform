// Moves the contributor programme from Firestore (today) to the own server.
//
//   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json \
//   node scripts/migrate-firestore-to-sqlite.js ./data/contributors.sqlite
//
// Copies every Firestore-phase collection into the SQLite file the server uses
// (STORE_DRIVER=sqlite). Documents keep their shape; Firestore Timestamps become
// epoch milliseconds. Re-running overwrites documents with the same ID, so run it
// once as a dry run and again right before the switch (with writes paused).
//
// Afterwards: start the server on that file, set BACKEND = "http" and API_BASE in
// web/public/assets/contributor-config.js, and deploy hosting.
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { migrate } from "../src/migrate.js";
import { createSqliteStore } from "../src/store/sqliteStore.js";

const target = resolve(process.argv[2] ?? "./data/contributors.sqlite");
mkdirSync(dirname(target), { recursive: true });
const store = createSqliteStore(target);
const firestore = getFirestore(initializeApp({ credential: applicationDefault(), projectId: process.env.FIREBASE_PROJECT_ID || "tsiptv-8bdd6" }));
await migrate(firestore, store);
store.close();
console.log(`\nDone: ${target}`);
