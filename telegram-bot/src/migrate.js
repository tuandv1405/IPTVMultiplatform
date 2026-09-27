/** Firestore-phase collection → collection in src/store/sqliteStore.js. */
export const COLLECTIONS = {
  contributor_requests: "requests",
  contributors: "contributors",
  playlist_submissions: "submissions",
  playlist_urls: "url_locks",
  playlist_submission_images: "submission_images",
  public_playlists: "public_playlists",
  public_playlist_images: "public_images",
};

/** Firestore Timestamps → epoch milliseconds, recursively. */
export const toPlain = (value) => {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (Array.isArray(value)) return value.map(toPlain);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toPlain(v)]));
  return value;
};

/** Adds the bookkeeping fields the server keeps and the browser backend never writes. */
export function normalise(collection, id, doc) {
  if (collection === "submissions") return { telegramMessages: [], flags: [], ...doc, id };
  if (collection === "requests") return { telegramMessages: [], ...doc, uid: doc.uid ?? id };
  return doc;
}

/** Copies every collection from `firestore` (Admin SDK) into `store` (sqliteStore). */
export async function migrate(firestore, store, log = console.log) {
  const counts = {};
  for (const [source, destination] of Object.entries(COLLECTIONS)) {
    const snap = await firestore.collection(source).get();
    for (const doc of snap.docs) await store.importDoc(destination, doc.id, normalise(destination, doc.id, toPlain(doc.data())));
    counts[source] = snap.size;
    log(`${source.padEnd(28)} → ${destination.padEnd(18)} ${snap.size} document(s)`);
  }
  return counts;
}
