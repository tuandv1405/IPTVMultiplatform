package tss.t.tsiptv.feature.auth.domain.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * Data class representing an authentication token.
 *
 * @property accessToken The access token used for API requests
 * @property refreshToken The refresh token used to get a new access token when it expires
 * @property expiresIn The number of seconds until the access token expires
 * @property createdAt The timestamp when the token was created (in milliseconds since epoch)
 */
@OptIn(ExperimentalTime::class)
data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
) {
    /**
     * Checks if the access token is expired.
     *
     * @return True if the token is expired, false otherwise
     */
    @OptIn(ExperimentalTime::class)
    fun isExpired(): Boolean {
        val expirationTime = createdAt + (expiresIn * 1000)
        return Clock.System.now().toEpochMilliseconds() >= expirationTime
    }

    /**
     * Checks if the token should be refreshed.
     * We consider a token should be refreshed if it's going to expire in the next 5 minutes.
     *
     * @return True if the token should be refreshed, false otherwise
     */
    fun shouldRefresh(): Boolean {
        val expirationTime = createdAt + (expiresIn * 1000)
        val fiveMinutesInMillis = 5 * 60 * 1000
        return Clock.System.now().toEpochMilliseconds() >= (expirationTime - fiveMinutesInMillis)
    }
}
