/* Data access for the contributor pages, behind one interface with two
 * implementations:
 *
 *   FirestoreBackend — today. The browser reads/writes Firestore directly and
 *                      firestore.rules is the only authority.
 *   HttpBackend      — the own server (telegram-bot/ on a VPS or physical box,
 *                      SQLite). Same operations, same document shapes.
 *
 * Pick with BACKEND in contributor-config.js. Pages never import Firestore.
 * All timestamps come back as epoch milliseconds, whichever backend is used.
 */
import { app, auth } from "/assets/firebase.js";
import {
  GoogleAuthProvider, signInWithPopup, signInWithRedirect, signOut as fbSignOut, onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import { API_BASE, ADMIN_EMAILS, BACKEND } from "/assets/contributor-config.js";

// ------------------------------------------------------------ shared ----

export class BackendError extends Error {
  constructor(code, message, details) {
    super(message || code);
    this.code = code;
    this.details = details;
  }
}

const millis = (value) => (value && typeof value.toMillis === "function" ? value.toMillis() : value ?? null);

/** Timestamps → millis, recursively (Firestore Timestamps live in nested maps too). */
function plain(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (Array.isArray(value)) return value.map(plain);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, plain(v)]));
  return value;
}

/** Same normalisation as the server's normaliseUrl, so both compute the same link hash. */
export function normaliseUrl(raw) {
  let url;
  try {
    url = new URL(String(raw ?? "").trim());
  } catch {
    throw new BackendError("invalid_url");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") throw new BackendError("unsupported_scheme");
  if (url.username || url.password) throw new BackendError("credentials_in_url");
  url.hash = "";
  if ((url.protocol === "http:" && url.port === "80") || (url.protocol === "https:" && url.port === "443")) url.port = "";
  // "host." is the same host; a bare "?" is no query. Both would give a second lock.
  url.hostname = url.hostname.toLowerCase().replace(/\.+$/, "");
  if (!url.search) url.search = "";
  return url.href;
}

export async function urlHash(raw) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(normaliseUrl(raw)));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Google is the only sign-in method for contributors and admins.
const provider = new GoogleAuthProvider();
provider.setCustomParameters({ prompt: "select_account" });

const authApi = {
  onUser: (callback) => onAuthStateChanged(auth, callback),
  currentUser: () => auth.currentUser,
  async signInWithGoogle() {
    try {
      await signInWithPopup(auth, provider);
    } catch (error) {
      // Popups are blocked in some in-app browsers; a redirect always works.
      if (error?.code === "auth/popup-blocked" || error?.code === "auth/operation-not-supported-in-this-environment") {
        await signInWithRedirect(auth, provider);
        return;
      }
      if (error?.code === "auth/popup-closed-by-user" || error?.code === "auth/cancelled-popup-request") return;
      throw new BackendError(error?.code ?? "auth_failed", error?.message);
    }
  },
  signOut: () => fbSignOut(auth),
  /** UI hint only; the rules / server decide. */
  async isAdmin() {
    const user = auth.currentUser;
    if (!user) return false;
    const token = await user.getIdTokenResult();
    return token.claims.admin === true || (user.emailVerified && ADMIN_EMAILS.includes(user.email));
  },
};

// Browser-side link check. Many playlist hosts do not allow cross-origin reads,
// so "unreachable from the browser" is reported as unchecked, not as broken.
async function browserLinkCheck(rawUrl) {
  const url = normaliseUrl(rawUrl);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15_000);
  try {
    const response = await fetch(url, { signal: controller.signal, redirect: "follow", credentials: "omit" });
    if (!response.ok) return { ok: false, checked: true, method: "browser", code: "http_error", status: response.status };
    const text = (await response.text()).slice(0, 5 * 1024 * 1024);
    return { method: "browser", checked: true, ...summarise(text) };
  } catch {
    return { ok: true, checked: false, method: "browser", code: "not_checkable" };
  } finally {
    clearTimeout(timer);
  }
}

/** A light version of the server's parser: enough to show the user what the link holds. */
function summarise(text) {
  const body = text.replace(/^﻿/, "");
  const head = body.trimStart().slice(0, 256).toLowerCase();
  let format = null;
  let channelCount = 0;
  if (head.startsWith("#extm3u") || /#extinf/i.test(body.slice(0, 65536))) {
    format = "m3u";
    const lines = body.split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
      if (/^#EXTINF/i.test(lines[i].trim())) {
        const next = lines.slice(i + 1).find((l) => l.trim() && !l.trim().startsWith("#"));
        if (next && /^(https?|rtmps?|rtsp|rtp|udp|mms|srt):\/\//i.test(next.trim())) channelCount++;
      }
    }
  } else if (/<tracklist[\s>]/i.test(body)) {
    format = "xspf";
    channelCount = (body.match(/<location>/gi) ?? []).length;
  } else if (head.startsWith("[") || head.startsWith("{")) {
    format = "json";
    channelCount = (body.match(/"(url|stream_url|streamUrl|link)"\s*:\s*"(https?|rtmp|rtsp):/gi) ?? []).length;
  } else if (head.startsWith("<!doctype html") || head.startsWith("<html")) {
    return { ok: false, code: "not_a_playlist" };
  }
  if (!format) return { ok: false, code: "not_a_playlist" };
  if (channelCount === 0) return { ok: false, code: "no_channels", format };
  return { ok: true, format, channelCount };
}

// --------------------------------------------------------- Firestore ----

async function createFirestoreBackend() {
  const fs = await import("https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js");
  const {
    getFirestore, doc, getDoc, setDoc, deleteDoc, collection, query, where, getDocs, writeBatch, serverTimestamp,
  } = fs;
  const db = getFirestore(app);
  const uid = () => {
    const user = auth.currentUser;
    if (!user) throw new BackendError("unauthorized");
    return user.uid;
  };
  const read = async (ref) => {
    const snap = await getDoc(ref).catch(rethrow);
    return snap.exists() ? plain({ id: snap.id, ...snap.data() }) : null;
  };
  const list = async (q) => (await getDocs(q).catch(rethrow)).docs.map((d) => plain({ id: d.id, ...d.data() }));
  const newest = (a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0);
  // Requesters and contributors can read decisions on their own records, so the
  // admin is recorded by uid with a neutral label — never by personal email.
  const ADMIN_LABEL = "Admin";
  const reviewer = () => ({ by: auth.currentUser.uid, name: ADMIN_LABEL, at: serverTimestamp() });

  function rethrow(error) {
    if (error?.code === "permission-denied") throw new BackendError("forbidden", error.message);
    if (error?.code === "unavailable") throw new BackendError("network", error.message);
    throw new BackendError(error?.code ?? "unknown", error?.message);
  }

  return {
    kind: "firestore",
    ...authApi,

    // ---- public directory (no sign-in) --------------------------------
    async listPublicPlaylists() {
      const { orderBy, limit } = fs;
      return list(query(collection(db, "public_playlists"), orderBy("approvedAt", "desc"), limit(300)));
    },
    getPublicImage: (id) => read(doc(db, "public_playlist_images", id)),

    // ---- contributor request -------------------------------------------
    getMyRequest: () => read(doc(db, "contributor_requests", uid())),

    /** `request` = { publicName, about, links, contactEnc, consents: {policy, terms, privacy} } */
    async saveRequest(request) {
      const me = uid();
      const existing = await read(doc(db, "contributor_requests", me));
      if (existing && existing.status !== "REJECTED") {
        throw new BackendError(existing.status === "PENDING" ? "request_pending" : "already_contributor");
      }
      await setDoc(doc(db, "contributor_requests", me), {
        uid: me,
        publicName: request.publicName,
        about: request.about,
        links: request.links,
        contactEnc: request.contactEnc,
        consents: { ...request.consents, acceptedAt: serverTimestamp() },
        status: "PENDING",
        attempt: (existing?.attempt ?? 0) + 1,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      }).catch(rethrow);
    },

    cancelRequest: () => deleteDoc(doc(db, "contributor_requests", uid())).catch(rethrow),

    getMyContributor: () => read(doc(db, "contributors", uid())),

    // ---- playlists ----------------------------------------------------------
    checkLink: browserLinkCheck,

    async submitPlaylist(form) {
      const me = uid();
      const contributor = await read(doc(db, "contributors", me));
      if (!contributor) throw new BackendError("not_contributor");
      if (contributor.status !== "ACTIVE") throw new BackendError("suspended");
      const url = normaliseUrl(form.url);
      const hash = await urlHash(url);
      if (await read(doc(db, "playlist_urls", hash))) throw new BackendError("link_already_submitted");

      const ref = doc(collection(db, "playlist_submissions"));
      const batch = writeBatch(db);
      batch.set(ref, {
        id: ref.id,
        ownerUid: me,
        publicName: contributor.publicName,
        name: form.name,
        // Normalised, because firestore.rules derives the lock key from this exact string.
        url,
        urlHash: hash,
        description: form.description,
        category: form.category,
        language: form.language,
        declarations: { ...form.declarations, policyVersion: form.policyVersion },
        verification: form.verification ?? null,
        status: "IN_REVIEW",
        statusReason: null,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      batch.set(doc(db, "playlist_submission_images", ref.id), { mime: form.image.mime, data: form.image.data });
      batch.set(doc(db, "playlist_urls", hash), { submissionId: ref.id, ownerUid: me });
      await batch.commit().catch(rethrow);
      return ref.id;
    },

    async listMySubmissions() {
      return (await list(query(collection(db, "playlist_submissions"), where("ownerUid", "==", uid())))).sort(newest);
    },

    async withdrawSubmission(submission) {
      const batch = writeBatch(db);
      batch.update(doc(db, "playlist_submissions", submission.id), {
        status: "WITHDRAWN",
        statusReason: { code: "withdrawn" },
        updatedAt: serverTimestamp(),
      });
      if (submission.status === "APPROVED") {
        batch.delete(doc(db, "public_playlists", submission.id));
        batch.delete(doc(db, "public_playlist_images", submission.id));
      }
      batch.delete(doc(db, "playlist_urls", submission.urlHash));
      await batch.commit().catch(rethrow);
    },

    /** Account deletion: withdraw live playlists (off the public directory), then
     *  delete the request, which holds the (encrypted) personal data. */
    async purgeMyData() {
      const mine = await this.listMySubmissions().catch(() => []);
      for (const submission of mine.filter((s) => s.status === "IN_REVIEW" || s.status === "APPROVED")) {
        await this.withdrawSubmission(submission).catch(() => {});
      }
      await deleteDoc(doc(db, "contributor_requests", uid())).catch(() => {});
    },

    // ---- admin --------------------------------------------------------------
    async listRequests(status) {
      const base = collection(db, "contributor_requests");
      return (await list(status ? query(base, where("status", "==", status)) : base)).sort(newest);
    },

    async decideRequest(request, decision, reason = "") {
      const batch = writeBatch(db);
      batch.update(doc(db, "contributor_requests", request.uid), {
        status: decision,
        decision: { by: auth.currentUser.uid, name: ADMIN_LABEL, at: serverTimestamp(), reason },
        updatedAt: serverTimestamp(),
      });
      if (decision === "APPROVED") {
        batch.set(doc(db, "contributors", request.uid), {
          uid: request.uid,
          publicName: request.publicName,
          status: "ACTIVE",
          approvedAt: serverTimestamp(),
          approvedBy: auth.currentUser.uid,
          updatedAt: serverTimestamp(),
        });
      }
      await batch.commit().catch(rethrow);
    },

    async listContributors() {
      return (await list(collection(db, "contributors"))).sort((a, b) => (b.approvedAt ?? 0) - (a.approvedAt ?? 0));
    },

    /** Suspension also rejects the contributor's playlists in review and takes down
     *  the published ones — the same as the own server does. */
    async setContributorStatus(contributor, status, note = "") {
      const batch = writeBatch(db);
      batch.update(doc(db, "contributors", contributor.uid), { status, statusNote: note, updatedAt: serverTimestamp() });
      if (status === "SUSPENDED") {
        const theirs = await list(query(collection(db, "playlist_submissions"), where("ownerUid", "==", contributor.uid)));
        for (const s of theirs) {
          const ref = doc(db, "playlist_submissions", s.id);
          if (s.status === "IN_REVIEW") {
            batch.update(ref, { status: "REJECTED", statusReason: { code: "banned", note }, review: reviewer(), updatedAt: serverTimestamp() });
          } else if (s.status === "APPROVED") {
            batch.update(ref, { status: "REMOVED", statusReason: { code: "banned", note }, review: reviewer(), updatedAt: serverTimestamp() });
            batch.delete(doc(db, "public_playlists", s.id));
            batch.delete(doc(db, "public_playlist_images", s.id));
          }
        }
      }
      await batch.commit().catch(rethrow);
    },

    async listSubmissions(status) {
      const base = collection(db, "playlist_submissions");
      return (await list(status ? query(base, where("status", "==", status)) : base)).sort(newest);
    },

    getSubmissionImage: (id) => read(doc(db, "playlist_submission_images", id)),

    /** action: "approve" | "reject" | "takedown" */
    async decideSubmission(submission, action, { code = "", note = "" } = {}) {
      const batch = writeBatch(db);
      const ref = doc(db, "playlist_submissions", submission.id);
      if (action === "approve") {
        const image = await read(doc(db, "playlist_submission_images", submission.id));
        batch.update(ref, { status: "APPROVED", statusReason: null, review: reviewer(), updatedAt: serverTimestamp() });
        batch.set(doc(db, "public_playlists", submission.id), {
          id: submission.id,
          name: submission.name,
          url: submission.url,
          description: submission.description ?? "",
          category: submission.category,
          language: submission.language,
          publicName: submission.publicName,
          channelCount: submission.verification?.channelCount ?? null,
          groupCount: submission.verification?.groupCount ?? null,
          format: submission.verification?.format ?? null,
          approvedAt: serverTimestamp(),
        });
        if (image) batch.set(doc(db, "public_playlist_images", submission.id), { mime: image.mime, data: image.data });
      } else if (action === "reject") {
        batch.update(ref, { status: "REJECTED", statusReason: { code, note }, review: reviewer(), updatedAt: serverTimestamp() });
        // A link rejected as copied stays locked so it cannot simply be resubmitted.
        if (code !== "copyright") batch.delete(doc(db, "playlist_urls", submission.urlHash));
      } else if (action === "takedown") {
        batch.update(ref, {
          status: "REMOVED",
          statusReason: { code: "removed", note },
          review: reviewer(),
          updatedAt: serverTimestamp(),
        });
        batch.delete(doc(db, "public_playlists", submission.id));
        batch.delete(doc(db, "public_playlist_images", submission.id));
      } else {
        throw new BackendError("invalid_action");
      }
      await batch.commit().catch(rethrow);
    },
  };
}

// -------------------------------------------------------------- HTTP ----

function createHttpBackend() {
  async function call(method, path, body) {
    const user = auth.currentUser;
    const headers = body ? { "Content-Type": "application/json" } : {};
    if (user) headers.Authorization = `Bearer ${await user.getIdToken()}`;
    let response;
    try {
      response = await fetch(API_BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
    } catch {
      throw new BackendError("network");
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new BackendError(data.error ?? "unknown", data.message, data.details);
    return data;
  }
  const enc = encodeURIComponent;

  return {
    kind: "http",
    ...authApi,
    listPublicPlaylists: async () => (await call("GET", "/api/public/playlists")).playlists,
    getPublicImage: (id) => call("GET", `/api/public/playlists/${enc(id)}/image`).catch(() => null),
    getMyRequest: async () => (await call("GET", "/api/requests/me")).request,
    saveRequest: (request) => call("POST", "/api/requests", request),
    cancelRequest: () => call("POST", "/api/requests/me/cancel"),
    getMyContributor: async () => (await call("GET", "/api/me")).contributor,
    async checkLink(url) {
      const result = await call("POST", "/api/playlists/verify", { url });
      return { ...result, checked: true, method: "server" };
    },
    submitPlaylist: async (form) => (await call("POST", "/api/submissions", {
      name: form.name,
      url: form.url,
      description: form.description,
      category: form.category,
      language: form.language,
      image: `data:${form.image.mime};base64,${form.image.data}`,
      declarations: form.declarations,
    })).id,
    listMySubmissions: async () => (await call("GET", "/api/submissions")).submissions,
    withdrawSubmission: (submission) => call("POST", `/api/submissions/${enc(submission.id)}/withdraw`),
    purgeMyData: () => call("POST", "/api/me/delete").catch(() => {}),

    listRequests: async (status) => (await call("GET", `/api/admin/requests${status ? `?status=${enc(status)}` : ""}`)).requests,
    decideRequest: (request, decision, reason = "") =>
      call("POST", `/api/admin/requests/${enc(request.uid)}/decide`, { decision, reason }),
    listContributors: async () => (await call("GET", "/api/admin/contributors")).contributors,
    setContributorStatus: (contributor, status, note = "") =>
      call("POST", `/api/admin/contributors/${enc(contributor.uid)}/status`, { status, note }),
    listSubmissions: async (status) =>
      (await call("GET", `/api/admin/submissions${status ? `?status=${enc(status)}` : ""}`)).submissions,
    getSubmissionImage: (id) => call("GET", `/api/admin/submissions/${enc(id)}/image`),
    decideSubmission: (submission, action, { code = "", note = "" } = {}) =>
      call("POST", `/api/admin/submissions/${enc(submission.id)}/decide`, { action, code, note }),
  };
}

let backendPromise = null;

/** The configured backend (created once per page). */
export function getBackend() {
  backendPromise ??= BACKEND === "http" ? Promise.resolve(createHttpBackend()) : createFirestoreBackend();
  return backendPromise;
}

export { millis };
