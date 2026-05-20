package com.pjournal.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ApiResult {
    data class Success(val content: String) : ApiResult()
    data class Failure(val error: String) : ApiResult()
}

class DeepseekApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generatePrompt(
        apiKey: String,
        experience: String,
        hobbies: String,
        recentStatus: String = ""
    ): ApiResult = withContext(Dispatchers.IO) {
        val systemPrompt = "你是一个日记写作助手。根据用户提供的个人信息（尤其是最近状态），" +
            "随机生成一个日记提示词，以问题的形式呈现。" +
            "提示词应优先围绕用户的最近状态展开，结合经历和爱好，帮助用户深入思考和记录。" +
            "只生成一个问题，不要其他内容，不要加引号。"

        val userParts = mutableListOf<String>()
        if (recentStatus.isNotBlank()) userParts.add("最近状态（主要参考）：$recentStatus")
        if (experience.isNotBlank()) userParts.add("个人经历：$experience")
        if (hobbies.isNotBlank()) userParts.add("个人爱好：$hobbies")
        userParts.add("请生成一个日记提示词：")
        val userMessage = userParts.joinToString("\n")

        val json = JSONObject().apply {
            put("model", "deepseek-v4-flash")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            })
            put("max_tokens", 200)
            put("temperature", 0.8)
            put("thinking", JSONObject().apply {
                put("type", "disabled")
            })
        }.toString()

        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val msg = try {
                    val errJson = JSONObject(responseBody)
                    val errorObj = errJson.optJSONObject("error")
                    if (errorObj != null) {
                        errorObj.optString("message", "HTTP ${response.code}")
                    } else {
                        errJson.optString("message", "HTTP ${response.code}: ${responseBody.take(200)}")
                    }
                } catch (_: Exception) {
                    "HTTP ${response.code}: ${responseBody.take(200)}"
                }
                return@withContext ApiResult.Failure(msg)
            }
            val respJson = JSONObject(responseBody)
            val content = respJson
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
            if (!content.isNullOrBlank()) {
                ApiResult.Success(content.trim('"', '\'', '"', '"', '「', '」'))
            } else {
                ApiResult.Failure("API返回为空")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "未知错误")
        }
    }
}
