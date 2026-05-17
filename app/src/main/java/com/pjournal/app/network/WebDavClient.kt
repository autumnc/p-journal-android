package com.pjournal.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

data class WebDavFile(
    val filename: String,
    val lastModified: Long?
)

class WebDavClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun authHeader(username: String, password: String): String {
        val credentials = "$username:$password"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
    }

    private fun buildUrl(baseUrl: String, filename: String? = null): String {
        val url = baseUrl.trimEnd('/') + "/journal/"
        return if (filename != null) url + filename else url
    }

    suspend fun mkdir(baseUrl: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = buildUrl(baseUrl)
            val request = Request.Builder()
                .url(url)
                .method("MKCOL", null)
                .header("Authorization", authHeader(username, password))
                .header("User-Agent", "pjournal/1.0")
                .build()
            try {
                val resp = client.newCall(request).execute()
                resp.code in listOf(200, 201, 405, 301, 302)
            } catch (e: Exception) {
                false
            }
        }

    suspend fun upload(
        baseUrl: String, username: String, password: String,
        filename: String, content: String
    ): Boolean = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, filename)
        val body = content.toRequestBody("text/plain; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", authHeader(username, password))
            .header("User-Agent", "pjournal/1.0")
            .header("Content-Type", "text/plain; charset=utf-8")
            .build()
        try {
            val resp = client.newCall(request).execute()
            resp.code in listOf(200, 201, 204)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun download(
        baseUrl: String, username: String, password: String, filename: String
    ): String? = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, filename)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", authHeader(username, password))
            .header("User-Agent", "pjournal/1.0")
            .build()
        try {
            val resp = client.newCall(request).execute()
            if (resp.code in listOf(200, 203)) {
                resp.body?.string()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun delete(
        baseUrl: String, username: String, password: String, filename: String
    ): Boolean = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, filename)
        val request = Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", authHeader(username, password))
            .header("User-Agent", "pjournal/1.0")
            .build()
        try {
            val resp = client.newCall(request).execute()
            resp.code in listOf(200, 204, 404)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listFiles(
        baseUrl: String, username: String, password: String
    ): List<WebDavFile> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl)
        val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:getlastmodified/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>""".trimIndent()

        val body = propfindBody.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .header("Authorization", authHeader(username, password))
            .header("User-Agent", "pjournal/1.0")
            .header("Content-Type", "application/xml; charset=utf-8")
            .header("Depth", "1")
            .build()

        try {
            val resp = client.newCall(request).execute()
            if (resp.code !in listOf(207, 200)) return@withContext emptyList()
            val xml = resp.body?.string() ?: return@withContext emptyList()
            parsePropfindResponse(xml)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parsePropfindResponse(xml: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        val responseRegex = Regex("<d:response>(.*?)</d:response>", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex = Regex("<d:href>(.*?)</d:href>")
        val collectionRegex = Regex("<d:collection/>")
        val mtimeRegex = Regex("<d:getlastmodified>(.*?)</d:getlastmodified>")

        for (respMatch in responseRegex.findAll(xml)) {
            val respXml = respMatch.groupValues[1]
            val href = hrefRegex.find(respXml)?.groupValues?.get(1) ?: continue

            if (collectionRegex.containsMatchIn(respXml)) continue

            val filename = java.net.URLDecoder.decode(
                href.trimEnd('/').split('/').lastOrNull() ?: continue,
                "UTF-8"
            )
            if (!filename.endsWith(".txt") && !filename.endsWith(".json")) continue

            val mtimeStr = mtimeRegex.find(respXml)?.groupValues?.get(1)
            val mtime = mtimeStr?.let { parseDate(it) }

            files.add(WebDavFile(filename, mtime))
        }
        return files
    }

    private fun parseDate(dateStr: String): Long? {
        val formats = listOf(
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US),
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
        )
        for (fmt in formats) {
            try {
                return fmt.parse(dateStr.trim())?.time
            } catch (_: Exception) {}
        }
        return null
    }
}
