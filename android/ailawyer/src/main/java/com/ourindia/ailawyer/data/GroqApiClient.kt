package com.ourindia.ailawyer.data

import com.ourindia.ailawyer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
private class GroqChatRequest(
    val model: String = "openai/gpt-oss-120b",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int = 2048
)

@Serializable
private class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
private class GroqChatResponse(
    val choices: List<GroqChoice> = emptyList()
)

@Serializable
private class GroqChoice(
    val message: GroqMessage
)

object GroqApiClient {
    private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val USER_AGENT = "Mozilla/5.0 (Android; Mobile; OurIndia-AILawyer/1.0)"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    const val SYSTEM_LEGAL_PROMPT = """
You are "AI Lawyer India" (न्याय साथी), a friendly, helpful legal companion for Indian citizens.
Your purpose is to explain Indian laws in simple, everyday language so any common person can easily understand their rights and options.

Tone and Language Guidelines:
1. Speak in a warm, simple, citizen-friendly tone. Avoid complicated legal words where possible. Always explain the core idea in plain, easy-to-understand words first.
2. Structure your answer with clear, organized sections such as:
   📌 Simple Explanation
   ⚖️ Relevant Law & Sections
   🔹 Your Legal Rights
   👉 Step-by-Step Action You Can Take
   ⚠️ Important Tips & Cautions
3. Be clear, practical, and empathetic. Give practical steps (where to go, what documents to carry, free legal aid options like DLSA / NALSA).

STRICT Formatting Rules (Crucial):
- NEVER use raw asterisks (* or **). Do not use asterisks for bold or bullets.
- NEVER use markdown heading hashtags (#, ##, ###).
- NEVER use hyphens (-) for bullet points or lists.
- For list items, always use clean emojis like 🔹 or standard numbers (1., 2., 3.).
- Keep the response clean and natural to read on a mobile phone screen without looking like AI-generated markdown.

Legal Reference Guidelines:
- Mention the new criminal laws enacted in 2024: Bharatiya Nyaya Sanhita (BNS 2023) alongside the legacy Indian Penal Code (IPC) section (e.g., "BNS Section 318 / old IPC Section 420 for cheating").
- For procedure and police matters, reference Bharatiya Nagarik Suraksha Sanhita (BNSS 2023) alongside CrPC.
- Mention relevant statutes like Consumer Protection Act, NI Act Section 138, Motor Vehicles Act, RTI Act, or IT Act where applicable.
- End with a short reminder that this advice is for awareness and consulting a local advocate is recommended for court filings.
"""

    suspend fun sendQuery(
        userQuery: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        customSystemPrompt: String = SYSTEM_LEGAL_PROMPT
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = mutableListOf<GroqMessage>()
            messages.add(GroqMessage(role = "system", content = customSystemPrompt))

            // Add previous recent context (up to last 6 messages)
            conversationHistory.takeLast(6).forEach { msg ->
                messages.add(GroqMessage(role = msg.role, content = msg.content))
            }
            messages.add(GroqMessage(role = "user", content = userQuery))

            val reqBody = json.encodeToString(
                GroqChatRequest(
                    model = "openai/gpt-oss-120b",
                    messages = messages
                )
            )

            val request = Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", USER_AGENT)
                .post(reqBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Try fallback fast model
                    return@withContext fallbackToFastModel(messages, body)
                }

                val parsed = json.decodeFromString<GroqChatResponse>(body)
                val reply = parsed.choices.firstOrNull()?.message?.content
                    ?: "No response generated by legal AI engine."
                Result.success(reply)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fallbackToFastModel(messages: List<GroqMessage>, previousError: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reqBody = json.encodeToString(
                GroqChatRequest(
                    model = "openai/gpt-oss-20b",
                    messages = messages
                )
            )

            val request = Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", USER_AGENT)
                .post(reqBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Cloud Legal API error: ${response.code} ($body)"))
                }
                val parsed = json.decodeFromString<GroqChatResponse>(body)
                val reply = parsed.choices.firstOrNull()?.message?.content
                    ?: "No response generated."
                Result.success(reply)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
