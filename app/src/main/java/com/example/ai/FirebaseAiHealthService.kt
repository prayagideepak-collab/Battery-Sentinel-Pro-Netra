package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.BatteryRecord
import com.example.data.local.ChargingSession
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ConversationalLongevityInsight(
    val title: String,
    val summary: String,
    val detailedAdvice: String,
    val habitScore: Int?, // No validated habit score is available
    val personalizedActionPlan: List<String>,
    val electrochemicalExplanation: String,
    val aiModelUsed: String = "Firebase AI (Gemini Flash)"
)

object FirebaseAiHealthService {
    private const val TAG = "FirebaseAiHealthService"
    private const val REST_MODEL_NAME = "gemini-3.1-flash-lite-preview"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Analyzes Room database trends and generates personalized conversational battery longevity tips
     * using Firebase AI SDK with graceful REST fallback.
     */
    suspend fun analyzeLongevityTrends(
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>,
        degradationReport: DegradationReport
    ): ConversationalLongevityInsight = withContext(Dispatchers.IO) {
        if (degradationReport.estimatedCapacityHealthPercent == null) {
            return@withContext generateLocalLongevityFallback(degradationReport)
        }
        val prompt = buildLongevityPrompt(records, sessions, degradationReport)

        // Try Firebase AI SDK first
        try {
            val generativeModel = Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash"
            )
            val response = generativeModel.generateContent(prompt)
            val text = response.text
            if (!text.isNullOrBlank()) {
                val parsed = parseLongevityJson(text, "Firebase AI SDK (gemini-2.5-flash)")
                return@withContext parsed
            }
        } catch (e: Exception) {
            Log.d(TAG, "Firebase AI SDK call failed or not configured, attempting REST fallback: ${e.message}")
        }

        // Fallback: Direct Gemini REST API with BuildConfig.GEMINI_API_KEY
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val requestJson = JSONObject().apply {
                    val contents = JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    }
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.4)
                        put("responseMimeType", "application/json")
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are the Gemini Health Insights engine in Netra Sentinel. Analyze the user's real Room database battery telemetry logs and return structured JSON with keys: 'title', 'summary', 'detailedAdvice', 'habitScore' (integer 0-100), 'personalizedActionPlan' (array of 3-4 bullet strings), and 'electrochemicalExplanation'.")
                            })
                        })
                    })
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$REST_MODEL_NAME:generateContent?key=$apiKey"
                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(requestBody).build()
                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val root = JSONObject(bodyString)
                    val candidateText = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "") ?: ""

                    if (candidateText.isNotBlank()) {
                        return@withContext parseLongevityJson(candidateText, "Gemini REST API ($REST_MODEL_NAME)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "REST Fallback error", e)
            }
        }

        // Local Heuristic Fallback
        generateLocalLongevityFallback(degradationReport)
    }

    /**
     * Answers conversational user queries regarding their battery health and Room DB trends.
     */
    suspend fun answerConversationalQuery(
        query: String,
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>,
        report: DegradationReport
    ): String = withContext(Dispatchers.IO) {
        if (report.estimatedCapacityHealthPercent == null) {
            return@withContext generateConversationalFallbackAnswer(query, report)
        }
        val prompt = """
            User Question: "$query"

            Device Real Battery Telemetry Context:
            - Health Failure Risk: ${report.riskPercent?.let { "$it%" } ?: "Unavailable"} (${report.riskLevel ?: "Unavailable"})
            - Primary Risk Factor: ${report.primaryRiskFactor}
            - Estimated Remaining Capacity: ${report.estimatedCapacityHealthPercent?.let { "$it%" } ?: "Unavailable"}
            - High Voltage (>80%) Dwell Time: Unavailable (sample intervals not enough)
            - Thermal Stress Hours (>38°C): Unavailable (sample intervals not enough)
            - Deep Discharges: ${report.deepDischargeCount?.toString() ?: "Unavailable"}
            - Observed charge throughput (partial history): ${report.totalEquivalentCycles} full-charge equivalents
            - Recent Charging Sessions: ${sessions.take(3).joinToString { "${it.startLevel}%->${it.endLevel}% (${it.durationMinutes}m, Peak: ${it.peakTemperature}°C)" }}

            Provide a concise, friendly, conversational and scientifically accurate answer tailored to the user's real hardware telemetry. Keep under 4-5 sentences.
        """.trimIndent()

        // Try Firebase AI
        try {
            val generativeModel = Firebase.ai.generativeModel("gemini-2.5-flash")
            val response = generativeModel.generateContent(prompt)
            val text = response.text
            if (!text.isNullOrBlank()) return@withContext text.trim()
        } catch (_: Exception) {}

        // Try REST
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val requestJson = JSONObject().apply {
                    val contents = JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    }
                    put("contents", contents)
                }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$REST_MODEL_NAME:generateContent?key=$apiKey"
                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(requestBody).build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val root = JSONObject(bodyString)
                    val candidateText = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "") ?: ""
                    if (candidateText.isNotBlank()) return@withContext candidateText.trim()
                }
            } catch (_: Exception) {}
        }

        // Local Fallback Answer
        generateConversationalFallbackAnswer(query, report)
    }

    private fun buildLongevityPrompt(
        records: List<BatteryRecord>,
        sessions: List<ChargingSession>,
        report: DegradationReport
    ): String {
        return """
            Analyze these real Room SQLite database telemetry trends for an Android device:
            - Failure Risk Index: ${report.riskPercent?.let { "$it%" } ?: "Unavailable"} (Level: ${report.riskLevel ?: "Unavailable"})
            - Primary Degradation Driver: ${report.primaryRiskFactor}
            - Estimated Cell Capacity Retention: ${report.estimatedCapacityHealthPercent?.let { "$it%" } ?: "Unavailable"}
            - High Voltage (>80% SoC) Dwell Time: Unavailable (sample intervals not enough)
            - Thermal Stress Time (>38°C): Unavailable (sample intervals not enough)
            - Deep Discharges (<15% SoC): ${report.deepDischargeCount?.toString() ?: "Unavailable"}
            - Total Recorded Historical Samples: ${records.size}
            - Recent Completed Sessions: ${sessions.take(5).joinToString { "${it.startLevel}%->${it.endLevel}% (${it.chargerType}, Peak ${it.peakTemperature}°C)" }}

            Provide personalized, conversational battery longevity tips in structured JSON with keys:
            - 'title': Catchy headline
            - 'summary': 1-2 sentence conversational summary
            - 'detailedAdvice': In-depth actionable advice
            - 'habitScore': 0-100 score of the user's charging habits
            - 'personalizedActionPlan': Array of 3-4 specific bullet recommendations
            - 'electrochemicalExplanation': Short explanation of how their habits affect the lithium-ion cathode/anode SEI layer
        """.trimIndent()
    }

    private fun parseLongevityJson(jsonStr: String, source: String): ConversationalLongevityInsight {
        return try {
            val clean = jsonStr.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(clean)
            val actionPlan = mutableListOf<String>()
            val array = obj.optJSONArray("personalizedActionPlan")
            if (array != null) {
                for (i in 0 until array.length()) {
                    actionPlan.add(array.getString(i))
                }
            }

            ConversationalLongevityInsight(
                title = obj.optString("title", "Personalized Battery Longevity Blueprint"),
                summary = obj.optString("summary", "Capacity health and failure risk are unavailable."),
                detailedAdvice = obj.optString("detailedAdvice", "Use device-supported charging controls and avoid excessive heat."),
                habitScore = null,
                personalizedActionPlan = actionPlan.ifEmpty { listOf("Unplug when reaching 80%", "Avoid charging under direct sunlight", "Recharge before dropping below 15%") },
                electrochemicalExplanation = obj.optString("electrochemicalExplanation", "Lithium-ion cells experience mechanical strain during phase transition at 4.3V+. Limiting peak state-of-charge preserves cathode lattice stability."),
                aiModelUsed = source
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON", e)
            generateLocalLongevityFallback(
                BatteryDegradationPredictor.analyzeDegradationAndFailureRisk(emptyList(), emptyList())
            )
        }
    }

    private fun generateLocalLongevityFallback(report: DegradationReport): ConversationalLongevityInsight {
        val habitScore: Int? = null
        val title = "Battery history insight unavailable"
        val summary = "Battery capacity and failure risk cannot be determined from the available data."
        val advice = "Collect battery records over time. Android charge readings alone do not measure remaining cell capacity."
        val actions = listOf("Watch for excessive heat during charging.")
        val explanation = "No calibrated battery capacity or validated failure-risk estimate is available."

        return ConversationalLongevityInsight(
            title = title,
            summary = summary,
            detailedAdvice = advice,
            habitScore = habitScore,
            personalizedActionPlan = actions,
            electrochemicalExplanation = explanation,
            aiModelUsed = "Local observation summary (no AI diagnosis)"
        )
    }

    private fun generateConversationalFallbackAnswer(query: String, report: DegradationReport): String =
        "Capacity health and failure risk are unavailable. The available battery records cannot establish either measure."
}
