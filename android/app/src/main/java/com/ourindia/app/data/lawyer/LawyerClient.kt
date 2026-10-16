package com.ourindia.app.data.lawyer

import com.ourindia.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

@Serializable
private class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int = 2048
)

@Serializable
private class GroqMessage(val role: String, val content: String)

@Serializable
private class GroqResponse(val choices: List<GroqChoice> = emptyList())

@Serializable
private class GroqChoice(val message: GroqMessage)

class LawyerException(message: String) : Exception(message)

/**
 * Groq chat completions for the AI Lawyer.
 *
 * ponytail: the key ships inside the APK (BuildConfig), so anyone with the APK can extract
 * it and spend the quota. Move the call behind a small server (e.g. a Vercel function that
 * holds the key) before a public release.
 */
object LawyerClient {
    private const val URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "openai/gpt-oss-120b"
    private const val FALLBACK_MODEL = "openai/gpt-oss-20b"

    val configured: Boolean get() = BuildConfig.GROQ_API_KEY.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    // The app uses icons, not emoji, and renders only **bold**: the prompt asks for that shape.
    const val SYSTEM_PROMPT = """
You are "AI Lawyer" (न्याय साथी) inside the Our India app, a friendly legal guide for Indian citizens.
Explain Indian law in simple, everyday language so anyone can understand their rights and options.

How to answer:
1. Start with a two or three sentence plain explanation.
2. Then short sections, each starting with a bold heading on its own line, for example **What the law says**, **Your rights**, **What you can do now**, **Be careful about**.
3. Use numbered steps (1., 2., 3.) for actions. Be practical: where to go, what documents to carry, free legal aid (DLSA, NALSA 15100).
4. Do not use emoji, markdown headings (#), tables, or hyphen bullets. Bold is the only formatting.

Law:
- Cite the new criminal laws in force since 1 July 2024 alongside the old ones: Bharatiya Nyaya Sanhita (BNS) with the old IPC section, Bharatiya Nagarik Suraksha Sanhita (BNSS) with the old CrPC section, Bharatiya Sakshya Adhiniyam (BSA) with the old Evidence Act.
- Mention other statutes where relevant: Consumer Protection Act 2019, NI Act Section 138, Motor Vehicles Act, RTI Act 2005, IT Act, Domestic Violence Act 2005.
- If you are not sure of a section number, say so instead of guessing.
- End with one line: this is general legal information, and a local advocate should check anything before it is filed in court.
"""

    const val DRAFTER_PROMPT = "You are an experienced advocate in India who drafts legal notices, complaints and applications. " +
        "Write in formal, precise English following Indian drafting conventions. Do not use markdown symbols or emoji; use plain numbered paragraphs."

    suspend fun ask(query: String, history: List<ChatMessage> = emptyList(), system: String = SYSTEM_PROMPT): Result<String> =
        withContext(Dispatchers.IO) {
            if (!configured) return@withContext Result.failure(LawyerException("AI answers aren't set up in this build (no Groq API key)."))
            val messages = listOf(GroqMessage("system", system)) +
                history.takeLast(6).map { GroqMessage(it.role, it.content) } +
                GroqMessage("user", query)
            try {
                Result.success(complete(MODEL, messages))
            } catch (e: LawyerException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(LawyerException("Couldn't reach the AI service. Check your connection and try again."))
            } catch (e: Exception) {
                // The large model can be over capacity; the small one usually still answers.
                runCatching { complete(FALLBACK_MODEL, messages) }
                    .recoverCatching { throw LawyerException("The AI service is busy right now. Please try again in a minute.") }
            }
        }

    private fun complete(model: String, messages: List<GroqMessage>): String {
        val body = json.encodeToString(GroqRequest.serializer(), GroqRequest(model, messages))
        val request = Request.Builder()
            .url(URL)
            .header("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            when {
                r.code == 401 || r.code == 403 -> throw LawyerException("The AI service rejected this build's API key.")
                r.code == 429 -> throw LawyerException("Too many questions at once. Wait a minute and ask again.")
                !r.isSuccessful -> throw IllegalStateException("Groq HTTP ${r.code}")
            }
            return json.decodeFromString(GroqResponse.serializer(), text).choices.firstOrNull()?.message?.content?.trim()
                ?.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("Empty answer")
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
