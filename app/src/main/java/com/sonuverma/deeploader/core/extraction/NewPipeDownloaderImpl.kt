package com.sonuverma.deeploader.core.extraction

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Custom Downloader implementation for NewPipe Extractor.
 *
 * NewPipe Extractor requires a Downloader to make HTTP requests.
 * This uses OkHttp for reliable, performant network calls with:
 * - Proper User-Agent spoofing (Android YouTube app)
 * - Connection pooling
 * - Timeout configuration
 * - Redirect following
 *
 * Developer: Sonu Verma
 */
class NewPipeDownloaderImpl private constructor() : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val url = request.url()
        val httpMethod = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            // Mimic Android YouTube app to avoid blocks
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")

        // Add all custom headers from the request
        headers?.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        // Set HTTP method
        when (httpMethod) {
            "GET" -> requestBuilder.get()
            "HEAD" -> requestBuilder.head()
            "POST" -> requestBuilder.post(
                (dataToSend ?: ByteArray(0)).toRequestBody(null, 0, dataToSend?.size ?: 0)
            )
            else -> requestBuilder.method(
                httpMethod,
                dataToSend?.toRequestBody(null, 0, dataToSend.size)
            )
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseCode = response.code

        // NewPipe expects ReCaptchaException for 429 (rate limit) responses
        if (responseCode == 429) {
            response.close()
            throw ReCaptchaException("Rate limited by server", url)
        }

        val responseBody = response.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()
        response.headers.forEach { (name, value) ->
            responseHeaders[name] = responseHeaders.getOrDefault(name, emptyList()) + value
        }

        return Response(
            responseCode,
            response.message,
            responseHeaders,
            responseBody,
            response.request.url.toString()
        )
    }

    companion object {
        @Volatile
        private var instance: NewPipeDownloaderImpl? = null

        fun getInstance(): NewPipeDownloaderImpl {
            return instance ?: synchronized(this) {
                instance ?: NewPipeDownloaderImpl().also { instance = it }
            }
        }
    }
}
