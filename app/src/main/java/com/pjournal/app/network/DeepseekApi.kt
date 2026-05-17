package com.pjournal.app.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int = 100,
    val temperature: Double = 0.9
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>? = null
)

data class Choice(
    val message: Message? = null
)

class DeepseekApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val apiUrl = "https://api.deepseek.com/v1/chat/completions"

    suspend fun generatePrompt(
        apiKey: String,
        experience: String,
        hobbies: String
    ): String? = withContext(Dispatchers.IO) {
        val systemPrompt = "你是一个日记写作助手。根据用户的个人信息，随机生成一个日记提示词，" +
            "以问题的形式呈现。提示词应该与个人的经历和爱好相关，帮助用户深入思考。" +
            "只生成一个问题，不要其他内容，不要加引号。"

        val userParts = mutableListOf<String>()
        if (experience.isNotBlank()) userParts.add("个人经历：$experience")
        if (hobbies.isNotBlank()) userParts.add("个人爱好：$hobbies")
        userParts.add("请生成一个日记提示词：")
        val userMessage = userParts.joinToString("\n")

        val requestBody = ChatRequest(
            messages = listOf(
                Message("system", systemPrompt),
                Message("user", userMessage)
            )
        )

        val json = gson.toJson(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .build()

        try {
            val response = client.newCall(request).execute()
            val result = gson.fromJson(response.body?.string(), ChatResponse::class.java)
            val content = result.choices?.firstOrNull()?.message?.content?.trim()
            content?.trim('"', '\'', '“', '”', '「', '」')
        } catch (e: Exception) {
            null
        }
    }
}
