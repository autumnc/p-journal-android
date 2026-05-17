package com.pjournal.app.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class FlomoLoginResult(
    val code: Int = -1,
    val data: FlomoLoginData? = null,
    val access_token: String? = null
)

data class FlomoLoginData(
    val access_token: String? = null
)

data class FlomoMemoResult(
    val code: Int = -1
)

class FlomoApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val apiBase = "https://flomoapp.com/api/v1"
    private val signSecret = "dbbc3dd73364b4084c3a69346e0ce2b2"

    private fun sign(params: Map<String, String?>): String {
        val parts = mutableListOf<String>()
        for (key in params.keys.sorted()) {
            val value = params[key] ?: continue
            if (value.isEmpty()) continue
            parts.add("$key=$value")
        }
        val raw = parts.joinToString("&") + signSecret
        return MessageDigest.getInstance("MD5").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun login(email: String, password: String): String? = withContext(Dispatchers.IO) {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val params = mapOf(
            "email" to email,
            "password" to password,
            "wechat_union_id" to "",
            "wechat_oa_open_id" to "",
            "timestamp" to timestamp,
            "api_key" to "flomo_web",
            "app_version" to "4.0",
            "platform" to "web",
            "webp" to "1"
        )
        val signedParams = params + ("sign" to sign(params))

        val json = gson.toJson(signedParams)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$apiBase/user/login_by_email")
            .post(body)
            .header("User-Agent", "pjournal/1.0")
            .build()

        try {
            val response = client.newCall(request).execute()
            val result = gson.fromJson(response.body?.string(), FlomoLoginResult::class.java)
            if (result.code == 0) {
                result.data?.access_token ?: result.access_token
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createMemo(token: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val params = mapOf(
            "timestamp" to timestamp,
            "api_key" to "flomo_web",
            "app_version" to "4.0",
            "platform" to "web",
            "webp" to "1",
            "content" to "<p>$content\n\n#日记</p>",
            "source" to "web",
            "tz" to "8:0"
        )
        val signedParams = params + ("sign" to sign(params))

        val json = gson.toJson(signedParams)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$apiBase/memo")
            .put(body)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "pjournal/1.0")
            .build()

        try {
            val response = client.newCall(request).execute()
            val result = gson.fromJson(response.body?.string(), FlomoMemoResult::class.java)
            result.code == 0
        } catch (e: Exception) {
            false
        }
    }
}
