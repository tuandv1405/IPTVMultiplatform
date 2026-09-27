/** An error the HTTP layer turns into a JSON response. `code` is stable and read by the web app. */
export class ApiError extends Error {
  constructor(status, code, message = code, details = undefined) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export const badRequest = (code, message, details) => new ApiError(400, code, message, details);
export const unauthorized = (code = "unauthorized", message = "Sign in again.") =>
  new ApiError(401, code, message);
export const forbidden = (code, message) => new ApiError(403, code, message);
export const notFound = (code = "not_found", message = "Not found.") => new ApiError(404, code, message);
export const conflict = (code, message, details) => new ApiError(409, code, message, details);
export const tooMany = (code = "rate_limited", message = "Too many requests. Try again in a minute.") =>
  new ApiError(429, code, message);
