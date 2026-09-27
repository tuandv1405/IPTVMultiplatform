import { conflict } from "../errors.js";

/**
 * Firestore-backed store (Admin SDK, so security rules do not apply here —
 * firestore.rules denies every one of these collections to browsers).
 *
 * Queries use single-field filters only, so no composite index is needed;
 * sorting happens in memory on result sets that are small by construction.
 */
export function createFirestoreStore(db) {
  const contributors = db.collection("contributors");
  const submissions = db.collection("submissions");
  const urlLocks = db.collection("submission_urls");
  const imageCollections = {
    submission: db.collection("submission_images"),
    public: db.collection("public_playlist_images"),
  };
  const publicPlaylists = db.collection("public_playlists");
  const botState = db.collection("bot_state");
  const data = (snap) => (snap.exists ? snap.data() : null);

  return {
    kind: "firestore",

    async getContributor(uid) {
      return data(await contributors.doc(uid).get());
    },
    async putContributor(uid, value) {
      await contributors.doc(uid).set(value, { merge: true });
    },
    async deleteContributor(uid) {
      await contributors.doc(uid).delete();
    },
    async findContributorsByHash(field, hash) {
      const snap = await contributors.where(field, "==", hash).limit(20).get();
      return snap.docs.map((d) => d.data());
    },
    async listContributorUids() {
      const refs = await contributors.listDocuments();
      return refs.map((ref) => ref.id);
    },

    async createSubmission(submission, checkOwner = () => {}) {
      const lockRef = urlLocks.doc(submission.urlHash);
      const subRef = submissions.doc(submission.id);
      const ownQuery = submissions.where("ownerUid", "==", submission.ownerUid);
      await db.runTransaction(async (tx) => {
        // Read inside the transaction: parallel submits from one contributor
        // conflict and retry, so the per-contributor limits hold.
        const own = await tx.get(ownQuery);
        checkOwner(own.docs.map((d) => d.data()));
        const lock = await tx.get(lockRef);
        if (lock.exists) {
          throw conflict("link_already_submitted", "This link has already been submitted.", {
            sameOwner: lock.data().ownerUid === submission.ownerUid,
          });
        }
        tx.create(lockRef, { submissionId: submission.id, ownerUid: submission.ownerUid });
        tx.create(subRef, submission);
      });
    },
    async getSubmission(id) {
      return data(await submissions.doc(id).get());
    },
    async transitionSubmission(id, fromStatuses, patch) {
      const ref = submissions.doc(id);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists) return { ok: false, current: null };
        const current = snap.data();
        if (!fromStatuses.includes(current.status)) return { ok: false, current };
        tx.update(ref, patch);
        return { ok: true, current: { ...current, ...patch } };
      });
    },
    async patchSubmission(id, patch) {
      await submissions.doc(id).set(patch, { merge: true });
    },
    async listSubmissionsByOwner(uid) {
      const snap = await submissions.where("ownerUid", "==", uid).get();
      return snap.docs.map((d) => d.data()).sort((a, b) => b.createdAt - a.createdAt);
    },
    async listSubmissionsByStatus(status, limit = 50) {
      const snap = await submissions.where("status", "==", status).limit(500).get();
      return snap.docs
        .map((d) => d.data())
        .sort((a, b) => a.createdAt - b.createdAt)
        .slice(0, limit);
    },
    async findSubmissionsByContentHash(hash) {
      const snap = await submissions.where("contentHash", "==", hash).limit(50).get();
      return snap.docs.map((d) => d.data());
    },
    async countByStatus() {
      const counts = {};
      for (const status of ["IN_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN", "REMOVED"]) {
        const agg = await submissions.where("status", "==", status).count().get();
        counts[status] = agg.data().count;
      }
      return counts;
    },
    async releaseUrl(urlHash, submissionId) {
      const ref = urlLocks.doc(urlHash);
      await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (snap.exists && snap.data().submissionId === submissionId) tx.delete(ref);
      });
    },

    async putImage(kind, id, image) {
      await imageCollections[kind].doc(id).set(image);
    },
    async getImage(kind, id) {
      return data(await imageCollections[kind].doc(id).get());
    },
    async deleteImage(kind, id) {
      await imageCollections[kind].doc(id).delete();
    },

    async putPublicPlaylist(id, doc) {
      await publicPlaylists.doc(id).set(doc);
    },
    async deletePublicPlaylist(id) {
      await publicPlaylists.doc(id).delete();
    },
    async getPublicPlaylist(id) {
      return data(await publicPlaylists.doc(id).get());
    },

    async getState(key) {
      const value = data(await botState.doc(key).get());
      return value ? value.value : null;
    },
    async setState(key, value) {
      await botState.doc(key).set({ value });
    },
  };
}
