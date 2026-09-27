/**
 * One JSON object per line: readable by Cloud Logging, journalctl and
 * `docker logs` alike. Keys that could carry personal data are masked, so a
 * careless log call cannot leak an email, phone number or token.
 */
const SENSITIVE = /^(email|phone|phone_number|token|idtoken|authorization|emailenc|phoneenc|password)$/i;

export function createLogger({ level = "info", write = (line) => process.stdout.write(`${line}\n`) } = {}) {
  const order = { debug: 10, info: 20, warn: 30, error: 40 };
  const threshold = order[level] ?? 20;

  const emit = (severity, message, fields = {}) => {
    if (order[severity] < threshold) return;
    const safe = {};
    for (const [key, value] of Object.entries(fields)) safe[key] = SENSITIVE.test(key) ? "[redacted]" : value;
    write(JSON.stringify({ severity: severity.toUpperCase(), time: new Date().toISOString(), message, ...safe }));
  };

  return {
    debug: (m, f) => emit("debug", m, f),
    info: (m, f) => emit("info", m, f),
    warn: (m, f) => emit("warn", m, f),
    error: (m, f) => emit("error", m, f),
  };
}
