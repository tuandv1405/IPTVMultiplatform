// Google Play Developer API (Android Publisher v3), the two calls the billing server needs.
// https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2/get
// https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions/acknowledge

const BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";

export class PlayApiError extends Error {
  /** @param {number} status HTTP status from Google; `notFound` for 404/410 (unknown or expired token). */
  constructor(message, status) {
    super(message);
    this.name = "PlayApiError";
    this.status = status;
    this.notFound = status === 404 || status === 410;
    // 400/401/403/404/410 will not fix themselves on retry; 429 and 5xx may.
    this.transient = status === 429 || status >= 500 || status === 0;
  }
}

const enc = encodeURIComponent;

/**
 * @param {{ auth: { getAccessToken(): Promise<string> }, fetch?: typeof fetch }} options
 */
export function createPlayApi({ auth, fetch = globalThis.fetch }) {
  async function call(method, url, body) {
    const token = await auth.getAccessToken();
    let response;
    try {
      response = await fetch(url, {
        method,
        headers: {
          authorization: `Bearer ${token}`,
          ...(body !== undefined ? { "content-type": "application/json" } : {}),
        },
        ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      });
    } catch (error) {
      throw new PlayApiError(`play api network error: ${error.message}`, 0);
    }
    if (!response.ok) {
      // The error body never contains the purchase token, but keep only the status in the message.
      throw new PlayApiError(`play api ${method} failed: HTTP ${response.status}`, response.status);
    }
    const text = await response.text();
    return text ? JSON.parse(text) : {};
  }

  return {
    /** SubscriptionPurchaseV2 for a purchase token. */
    getSubscriptionV2(packageName, purchaseToken) {
      return call("GET", `${BASE}/${enc(packageName)}/purchases/subscriptionsv2/tokens/${enc(purchaseToken)}`);
    },

    /** Acknowledges a subscription purchase (Play refunds unacknowledged purchases after 3 days). */
    acknowledge(packageName, productId, purchaseToken) {
      return call(
        "POST",
        `${BASE}/${enc(packageName)}/purchases/subscriptions/${enc(productId)}/tokens/${enc(purchaseToken)}:acknowledge`,
        {},
      );
    },
  };
}
