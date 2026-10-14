package tss.t.tsiptv.feature.clip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import tss.t.tsiptv.player.network.playerHeaders
import tss.t.tsiptv.player.network.playerOkHttpClient
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [ClipHttp] over the player's own OkHttp client: the same certificate policy, edge failover and
 * connection pool as playback, and the channel's headers sent the same way.
 */
internal class AndroidClipHttp : ClipHttp {

    override suspend fun fetch(url: String, headers: Map<String, String>, range: HlsByteRange?): ClipHttpBody {
        val response = execute(request(url, headers, range))
        return withContext(Dispatchers.IO) {
            response.use { ClipHttpBody(it.body?.bytes() ?: ByteArray(0), it.request.url.toString()) }
        }
    }

    override suspend fun stream(url: String, headers: Map<String, String>, onChunk: suspend (ByteArray, Int) -> Boolean) {
        val response = execute(request(url, headers, range = null))
        withContext(Dispatchers.IO) {
            response.use {
                val input = it.body?.byteStream() ?: return@use
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0 || !onChunk(buffer, read)) break
                }
            }
        }
    }

    private fun request(url: String, headers: Map<String, String>, range: HlsByteRange?): Request {
        val (userAgent, others) = playerHeaders(headers)
        return Request.Builder().url(url).apply {
            userAgent?.let { header("User-Agent", it) }
            others.forEach { (name, value) -> header(name, value) }
            range?.let { header("Range", "bytes=${it.offset}-${it.offset + it.length - 1}") }
        }.build()
    }

    /** Runs the call, cancelling it with the coroutine; an error status is a [ClipException]. */
    private suspend fun execute(request: Request): Response {
        val call = playerOkHttpClient.newCall(request)
        val response = suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
                override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, _, _ -> response.close() }
            })
        }
        if (!response.isSuccessful) {
            response.close()
            throw ClipException(ClipFailure.NETWORK, "HTTP ${response.code}")
        }
        return response
    }
}

internal val JvmAes128Cbc = Aes128Cbc { key, iv, data ->
    Cipher.getInstance("AES/CBC/PKCS5Padding")
        .apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        .doFinal(data)
}
