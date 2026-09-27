import { DatabaseSync } from "node:sqlite";
import { conflict } from "../errors.js";

/**
 * The own-server store (VPS / physical machine): one SQLite file, no external
 * database to run. Documents are kept as JSON, with the fields the service
 * filters on copied into indexed columns — the same document shapes as
 * Firestore, which keeps the migration script trivial.
 *
 * node:sqlite is synchronous, so every method below runs to completion without
 * yielding; BEGIN IMMEDIATE additionally serialises writers if two processes
 * ever share the file.
 */
export function createSqliteStore(path) {
  const db = new DatabaseSync(path);
  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA busy_timeout = 5000;
    PRAGMA foreign_keys = ON;
    CREATE TABLE IF NOT EXISTS docs (
      col          TEXT NOT NULL,
      id           TEXT NOT NULL,
      data         TEXT NOT NULL,
      status       TEXT,
      owner        TEXT,
      content_hash TEXT,
      created_at   INTEGER,
      PRIMARY KEY (col, id)
    );
    CREATE INDEX IF NOT EXISTS docs_status ON docs (col, status);
    CREATE INDEX IF NOT EXISTS docs_owner ON docs (col, owner);
    CREATE INDEX IF NOT EXISTS docs_content ON docs (col, content_hash);
  `);

  const sql = {
    get: db.prepare("SELECT data FROM docs WHERE col = ? AND id = ?"),
    put: db.prepare(`INSERT INTO docs (col, id, data, status, owner, content_hash, created_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (col, id) DO UPDATE SET data = excluded.data, status = excluded.status,
                       owner = excluded.owner, content_hash = excluded.content_hash, created_at = excluded.created_at`),
    del: db.prepare("DELETE FROM docs WHERE col = ? AND id = ?"),
    all: db.prepare("SELECT data FROM docs WHERE col = ? ORDER BY created_at DESC"),
    ids: db.prepare("SELECT id FROM docs WHERE col = ?"),
    byStatus: db.prepare("SELECT data FROM docs WHERE col = ? AND status = ? ORDER BY created_at ASC LIMIT ?"),
    allAsc: db.prepare("SELECT data FROM docs WHERE col = ? ORDER BY created_at ASC LIMIT ?"),
    byStatusDesc: db.prepare("SELECT data FROM docs WHERE col = ? AND status = ? ORDER BY created_at DESC LIMIT ?"),
    allDesc: db.prepare("SELECT data FROM docs WHERE col = ? ORDER BY created_at DESC LIMIT ?"),
    byOwner: db.prepare("SELECT data FROM docs WHERE col = ? AND owner = ? ORDER BY created_at DESC"),
    byContent: db.prepare("SELECT data FROM docs WHERE col = ? AND content_hash = ?"),
    counts: db.prepare("SELECT status, COUNT(*) AS n FROM docs WHERE col = ? GROUP BY status"),
  };

  const parse = (row) => (row ? JSON.parse(row.data) : null);
  const get = (col, id) => parse(sql.get.get(col, id));
  const put = (col, id, doc) =>
    sql.put.run(col, id, JSON.stringify(doc), doc.status ?? null, doc.ownerUid ?? doc.uid ?? null, doc.contentHash ?? null, doc.createdAt ?? null);
  const del = (col, id) => sql.del.run(col, id);

  function tx(fn) {
    db.exec("BEGIN IMMEDIATE");
    try {
      const result = fn();
      db.exec("COMMIT");
      return result;
    } catch (error) {
      db.exec("ROLLBACK");
      throw error;
    }
  }

  function transition(col, id, fromStatuses, patch) {
    return tx(() => {
      const current = get(col, id);
      if (!current || !fromStatuses.includes(current.status)) return { ok: false, current };
      const next = { ...current, ...patch };
      put(col, id, next);
      return { ok: true, current: next };
    });
  }

  const patchDoc = (col, id, patch) =>
    tx(() => {
      const current = get(col, id);
      if (current) put(col, id, { ...current, ...patch });
    });

  return {
    kind: "sqlite",
    close: () => db.close(),

    // ---- requests ----
    async getRequest(uid) {
      return get("requests", uid);
    },
    async saveRequest(uid, build) {
      return tx(() => {
        const next = build(get("requests", uid));
        put("requests", uid, next);
        return next;
      });
    },
    async transitionRequest(uid, fromStatuses, patch) {
      return transition("requests", uid, fromStatuses, patch);
    },
    async patchRequest(uid, patch) {
      patchDoc("requests", uid, patch);
    },
    async deleteRequest(uid) {
      del("requests", uid);
    },
    async listRequests(status) {
      const rows = sql.all.all("requests").map(parse);
      return status ? rows.filter((r) => r.status === status) : rows;
    },
    async listRequestUids() {
      return sql.ids.all("requests").map((r) => r.id);
    },

    // ---- contributors ----
    async getContributor(uid) {
      return get("contributors", uid);
    },
    async putContributor(uid, data) {
      tx(() => put("contributors", uid, { ...(get("contributors", uid) ?? {}), ...data }));
    },
    async deleteContributor(uid) {
      del("contributors", uid);
    },
    async listContributors() {
      return sql.all.all("contributors").map(parse).sort((a, b) => (b.approvedAt ?? 0) - (a.approvedAt ?? 0));
    },
    async listContributorUids() {
      return sql.ids.all("contributors").map((r) => r.id);
    },

    // ---- submissions ----
    async createSubmission(submission, checkOwner = () => {}) {
      tx(() => {
        checkOwner(sql.byOwner.all("submissions", submission.ownerUid).map(parse));
        const lock = get("url_locks", submission.urlHash);
        if (lock) {
          throw conflict("link_already_submitted", "This link has already been submitted.", {
            sameOwner: lock.ownerUid === submission.ownerUid,
          });
        }
        put("url_locks", submission.urlHash, { submissionId: submission.id, ownerUid: submission.ownerUid });
        put("submissions", submission.id, submission);
      });
    },
    async getSubmission(id) {
      return get("submissions", id);
    },
    async transitionSubmission(id, fromStatuses, patch) {
      return transition("submissions", id, fromStatuses, patch);
    },
    async patchSubmission(id, patch) {
      patchDoc("submissions", id, patch);
    },
    async listSubmissionsByOwner(uid) {
      return sql.byOwner.all("submissions", uid).map(parse);
    },
    /** Oldest first (review queue); `newestFirst` for the admin console. */
    async listSubmissionsByStatus(status, limit = 50, { newestFirst = false } = {}) {
      const rows = newestFirst
        ? (status ? sql.byStatusDesc.all("submissions", status, limit) : sql.allDesc.all("submissions", limit))
        : (status ? sql.byStatus.all("submissions", status, limit) : sql.allAsc.all("submissions", limit));
      return rows.map(parse);
    },
    async findSubmissionsByContentHash(hash) {
      return sql.byContent.all("submissions", hash).map(parse);
    },
    async countByStatus() {
      return Object.fromEntries(sql.counts.all("submissions").map((r) => [r.status, r.n]));
    },
    async releaseUrl(urlHash, submissionId) {
      tx(() => {
        if (get("url_locks", urlHash)?.submissionId === submissionId) del("url_locks", urlHash);
      });
    },

    // ---- images, public directory, bot state ----
    async putImage(kind, id, image) {
      put(`${kind}_images`, id, image);
    },
    async getImage(kind, id) {
      return get(`${kind}_images`, id);
    },
    async deleteImage(kind, id) {
      del(`${kind}_images`, id);
    },
    async putPublicPlaylist(id, doc) {
      put("public_playlists", id, doc);
    },
    async deletePublicPlaylist(id) {
      del("public_playlists", id);
    },
    async getPublicPlaylist(id) {
      return get("public_playlists", id);
    },
    async listPublicPlaylists() {
      return sql.all.all("public_playlists").map(parse).sort((a, b) => (b.approvedAt ?? 0) - (a.approvedAt ?? 0)).slice(0, 300);
    },
    async getState(key) {
      return get("state", key)?.value ?? null;
    },
    async setState(key, value) {
      put("state", key, { value });
    },

    /** Migration hook: write a document verbatim. */
    async importDoc(col, id, doc) {
      put(col, id, doc);
    },
  };
}
