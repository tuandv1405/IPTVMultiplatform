/* Firebase setup shared by the account-deletion pages.
 *
 * Web API keys are not secrets — they identify the project, and access is
 * controlled by Firebase Auth rules and API key restrictions in the Google Cloud
 * console. This is the same key that ships inside the Android app.
 */
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  sendSignInLinkToEmail,
  isSignInWithEmailLink,
  signInWithEmailLink,
  deleteUser,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";

export const firebaseConfig = {
  apiKey: "AIzaSyAink_cGRkOZe6PcxJ7y5DCL7JIwrebCH8",
  authDomain: "tsiptv-8bdd6.firebaseapp.com",
  projectId: "tsiptv-8bdd6",
  storageBucket: "tsiptv-8bdd6.firebasestorage.app",
  messagingSenderId: "234600934735",
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
auth.useDeviceLanguage();

export { sendSignInLinkToEmail, isSignInWithEmailLink, signInWithEmailLink, deleteUser };

/** Where the emailed link lands. Must be an authorised domain in Firebase Auth. */
export const CONFIRM_URL = new URL("/delete-account/confirm/", location.origin).href;

/** Remembers the address between sending the link and opening it. */
export const EMAIL_KEY = "tsiptv-delete-email";

export function rememberEmail(email) {
  try {
    localStorage.setItem(EMAIL_KEY, email);
  } catch (e) {
    /* private window — the confirm page will ask for the address instead */
  }
}

export function recallEmail() {
  try {
    return localStorage.getItem(EMAIL_KEY);
  } catch (e) {
    return null;
  }
}

export function forgetEmail() {
  try {
    localStorage.removeItem(EMAIL_KEY);
  } catch (e) {
    /* nothing to clean up */
  }
}

/**
 * Turns a Firebase error into something a person can act on.
 * The raw SDK messages name internal API endpoints and help nobody.
 */
export function describeError(error) {
  switch (error && error.code) {
    case "auth/invalid-email":
      return "That does not look like a valid email address.";
    case "auth/invalid-action-code":
    case "auth/expired-action-code":
      return "This link has expired or has already been used. Request a new one.";
    case "auth/user-disabled":
      return "This account has been disabled. Contact support.";
    case "auth/too-many-requests":
      return "Too many attempts. Wait a few minutes and try again.";
    case "auth/network-request-failed":
      return "Network problem. Check your connection and try again.";
    case "auth/unauthorized-continue-uri":
    case "auth/operation-not-allowed":
      return "Email-link sign-in is not enabled for this project yet. Contact support.";
    default:
      return (error && error.message) || "Something went wrong. Please try again.";
  }
}
