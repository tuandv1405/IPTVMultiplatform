/**
 * Fixed-window counter per key. In-process only: fine for one instance, and a
 * restart merely resets the windows. Behind several instances each gets its own
 * budget, which is still a cap, just a looser one.
 */
export function createRateLimiter({ limit, windowMs, clock = Date.now }) {
  const windows = new Map();
  let lastSweep = clock();

  return function take(key) {
    const now = clock();
    if (now - lastSweep > windowMs) {
      for (const [k, w] of windows) if (now - w.start >= windowMs) windows.delete(k);
      lastSweep = now;
    }
    const current = windows.get(key);
    if (!current || now - current.start >= windowMs) {
      windows.set(key, { start: now, count: 1 });
      return true;
    }
    if (current.count >= limit) return false;
    current.count += 1;
    return true;
  };
}
