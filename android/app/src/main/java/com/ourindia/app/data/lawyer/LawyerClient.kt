package com.ourindia.app.data.lawyer

import com.ourindia.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ChatMessage(val role: String, val content: String)

enum class LawyerMode(val wire: String) { ASK("ask"), DRAFT("draft") }

@Serializable
private class ProxyRequest(val mode: String, val query: String, val history: List<ChatMessage>)

@Serializable
private class ProxyResponse(val answer: String? = null, val error: String? = null)

class LawyerException(message: String) : Exception(message)

/**
 * The AI Lawyer talks to our own endpoint (api/lawyer.js on Vercel), which holds the Groq
 * key and the system prompts. Nothing secret ships in the APK.
 */
object LawyerClient {
    val configured: Boolean get() = BuildConfig.LAWYER_API_URL.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // A cold function plus the model's answer can take most of a minute.
        .readTimeout(75, TimeUnit.SECONDS)
        .build()

    suspend fun ask(query: String, history: List<ChatMessage> = emptyList(), mode: LawyerMode = LawyerMode.ASK): Result<String> =
        withContext(Dispatchers.IO) {
            if (!configured) return@withContext Result.failure(LawyerException("AI answers aren't set up in this build."))
            val body = json.encodeToString(ProxyRequest.serializer(), ProxyRequest(mode.wire, query, history.takeLast(6)))
            val request = Request.Builder()
                .url(BuildConfig.LAWYER_API_URL)
                .apply { if (BuildConfig.LAWYER_APP_TOKEN.isNotBlank()) header("X-App-Token", BuildConfig.LAWYER_APP_TOKEN) }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(request).execute().use { r ->
                    val parsed = runCatching { json.decodeFromString(ProxyResponse.serializer(), r.body?.string().orEmpty()) }.getOrNull()
                    val answer = parsed?.answer?.takeIf { r.isSuccessful && it.isNotBlank() }
                    if (answer != null) Result.success(answer)
                    // The server words its errors for people; anything else gets a generic line.
                    else Result.failure(LawyerException(parsed?.error ?: "The AI service didn't answer (${r.code}). Please try again."))
                }
            } catch (e: IOException) {
                Result.failure(LawyerException("Couldn't reach the AI service. Check your connection and try again."))
            }
        }
}

/**
 * Models sometimes ignore "no markdown". Headings lose their #, list markers become "•",
 * and only **bold** survives (as markers the UI turns into bold spans).
 */
fun tidyLegalText(raw: String): String = raw.lines().joinToString("\n") { line ->
    val t = line.trimStart()
    when {
        t.startsWith("#") -> "**" + t.trimStart('#').trim().removePrefix("**").removeSuffix("**") + "**"
        t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> "• " + t.substring(2).trim()
        else -> line
    }
}.replace(Regex("(?<!\\*)\\*(?!\\*)"), "").replace(Regex("\n{3,}"), "\n\n").trim()
