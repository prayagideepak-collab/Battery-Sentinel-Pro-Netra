package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.model.AiDiagnosticResult
import com.example.model.BatteryTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiAiService {
    private const val TAG = "GeminiAiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    // Required models according to instructions:
    // Low-Latency: gemini-3.1-flash-lite-preview
    // High-Thinking: gemini-3.1-pro-preview
    private const val MODEL_FLASH_LITE = "gemini-3.1-flash-lite-preview"
    private const val MODEL_PRO_THINKING = "gemini-3.1-pro-preview"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Fast, low-latency telemetry triage using gemini-3.1-flash-lite-preview
     */
    suspend fun runQuickTriage(telemetry: BatteryTelemetry): Result<AiDiagnosticResult> = withContext(Dispatchers.IO) {
        val prompt = buildTriagePrompt(telemetry)
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback diagnostic
            return@withContext Result.success(generateLocalFallback(telemetry, isThinking = false))
        }

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
                            put("text", "You are Netra AI, an ultra-low power Android Battery and Thermal Sentinel engine. Analyze the live battery hardware telemetry and respond with structured JSON containing: 'summary' (concise 1-2 sentence overview), 'thermalAnalysis' (thermal velocity evaluation), 'degradationRisk' (Low/Medium/High assessment), 'optimalChargingAdvice' (actionable tip), and 'recommendedActions' (array of 3 short practical bullet points).")
                        })
                    })
                })
            }

            val url = "$BASE_URL$MODEL_FLASH_LITE:generateContent?key=$apiKey"
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "API error: ${response.code} $errorBody")
                return@withContext Result.success(generateLocalFallback(telemetry, isThinking = false))
            }

            val responseString = response.body?.string() ?: ""
            val parsedResult = parseGeminiResponse(responseString, MODEL_FLASH_LITE, false)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Quick Triage", e)
            Result.success(generateLocalFallback(telemetry, isThinking = false))
        }
    }

    /**
     * High-Thinking Deep Battery & Thermal Telemetry Diagnostics using gemini-3.1-pro-preview with ThinkingLevel.HIGH
     */
    suspend fun runDeepThinkingAnalysis(
        telemetry: BatteryTelemetry,
        historySummary: String,
        recentSessionsSummary: String
    ): Result<AiDiagnosticResult> = withContext(Dispatchers.IO) {
        val prompt = buildDeepThinkingPrompt(telemetry, historySummary, recentSessionsSummary)
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.success(generateLocalFallback(telemetry, isThinking = true))
        }

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
                // Set thinkingLevel to HIGH and do not set maxOutputTokens
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", "HIGH")
                    })
                    put("responseMimeType", "application/json")
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are Netra Deep Sentinel AI, an expert electrochemical engineer and Android power architect. Perform in-depth thermal throttling prediction, lithium-ion stress analysis, impedance degradation forecasting, and autonomous power conservation strategy. Return structured JSON with keys: 'summary', 'thermalAnalysis', 'degradationRisk', 'optimalChargingAdvice', and 'recommendedActions' (array of strings).")
                        })
                    })
                })
            }

            val url = "$BASE_URL$MODEL_PRO_THINKING:generateContent?key=$apiKey"
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Thinking API error: ${response.code} $errorBody")
                return@withContext Result.success(generateLocalFallback(telemetry, isThinking = true))
            }

            val responseString = response.body?.string() ?: ""
            val parsedResult = parseGeminiResponse(responseString, MODEL_PRO_THINKING, true)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Deep Thinking Analysis", e)
            Result.success(generateLocalFallback(telemetry, isThinking = true))
        }
    }

    private fun parseGeminiResponse(jsonString: String, model: String, isThinking: Boolean): AiDiagnosticResult {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

            val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = JSONObject(cleanJson)

            val actions = mutableListOf<String>()
            val actionsArray = parsed.optJSONArray("recommendedActions")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    actions.add(actionsArray.getString(i))
                }
            }

            AiDiagnosticResult(
                summary = parsed.optString("summary", "Telemetry processed successfully."),
                thermalAnalysis = parsed.optString("thermalAnalysis", "Thermal zone operating within safe margins."),
                degradationRisk = parsed.optString("degradationRisk", "Nominal degradation trajectory."),
                optimalChargingAdvice = parsed.optString("optimalChargingAdvice", "Maintain charging between 20% and 80% to maximize cycle life."),
                recommendedActions = actions.ifEmpty { listOf("Keep 80% unplug alarm enabled", "Avoid fast charging in ambient temperatures above 35°C", "Monitor background app power drain") },
                modelUsed = model,
                isThinkingMode = isThinking
            )
        } catch (e: Exception) {
            Log.e(TAG, "JSON Parse failure, creating fallback", e)
            AiDiagnosticResult(
                summary = "Diagnostic response could not be read; telemetry unavailable for this response.",
                thermalAnalysis = "Unavailable",
                degradationRisk = "Unavailable",
                optimalChargingAdvice = "Unavailable",
                recommendedActions = emptyList(),
                modelUsed = "Local fallback",
                isThinkingMode = isThinking
            )
        }
    }

    private fun buildTriagePrompt(t: BatteryTelemetry): String {
        return """
            Live Android Battery Telemetry:
            - Battery Level: ${t.level}%
            - State: ${if (t.isCharging) "Charging (${t.pluggedType})" else "Discharging"}
            - Temperature: ${t.temperature}°C (Safety Threshold: 40°C, Critical: 45°C)
            - Voltage: ${t.voltageMv} mV
            - Instantaneous Current: ${t.currentMa} mA
            - Active Power: ${t.powerWatts} W
            - Thermal Velocity: ${t.thermalVelocity} °C/min
            - Capacity Health Score: ${t.healthScore?.let { "$it/100" } ?: "Unavailable"} (${t.healthGrade})
            Provide a quick diagnostic assessment in JSON format.
        """.trimIndent()
    }

    private fun buildDeepThinkingPrompt(t: BatteryTelemetry, historySummary: String, sessionsSummary: String): String {
        return """
            Perform high-level electrochemical and system power analysis on this Android device:
            Current Telemetry:
            - Battery Level: ${t.level}% (${if (t.isCharging) "Charging via " + t.pluggedType else "Discharging"})
            - Temperature: ${t.temperature}°C
            - Voltage: ${t.voltageMv} mV | Current: ${t.currentMa} mA | Power: ${t.powerWatts} W
            - Thermal Distance from 40°C: ${t.distanceTo40C}°C | Velocity: ${t.thermalVelocity}°C/min
            - Technology: ${t.technology} | OS Reported Health: ${t.healthString}

            Historical Telemetry Logs (Room Database):
            $historySummary

            Recent Charging Sessions:
            $sessionsSummary

            Analyze cyclic stress, thermal acceleration factors (Arrhenius equation approximation for SEI layer growth), voltage plateaus, and recommend optimal 24/7 battery preservation rules.
        """.trimIndent()
    }

    private fun generateLocalFallback(t: BatteryTelemetry, isThinking: Boolean): AiDiagnosticResult {
        val thermalStatus = when {
            t.temperature > 45f -> "CRITICAL OVERHEAT (${t.temperature}°C). Severe electrochemical stress. Disconnect charger immediately."
            t.temperature > 40f -> "Thermal Throttling Zone (${t.temperature}°C). High heat degrades anode SEI layer 3x faster."
            t.temperature > 35f -> "Elevated Temperature (${t.temperature}°C). Normal under fast charging, but monitor closely."
            else -> "Optimal Thermal Envelope (${t.temperature}°C). Excellent condition for lithium chemistry."
        }

        val degradation = when {
            t.healthScore == null -> "Capacity degradation cannot be determined from current Android telemetry."
            t.healthScore > 90 -> "Estimated lower degradation risk (Grade ${t.healthGrade})."
            t.healthScore > 75 -> "Estimated moderate degradation risk; confirm with longer-term measured history."
            else -> "Estimated elevated degradation risk; confirm with longer-term measured history."
        }

        val advice = if (t.isCharging) {
            if (t.level >= 80) "Battery is at ${t.level}%. Disconnecting now prevents high voltage dwell stress (4.35V+)."
            else "Charging actively at ${String.format("%.2f", t.powerWatts)}W. Target 80% SoC for optimal lifespan."
        } else {
            "Discharging at ~${String.format("%.2f", kotlin.math.abs(t.powerWatts))}W. Recharge before dipping below 20%."
        }

        val actions = mutableListOf(
            "Maintain 80% charging cutoff to double cycle life.",
            "Avoid high-CPU gaming or streaming while connected to fast charger.",
            if (t.temperature > 38f) "Remove protective phone case temporarily to dissipate heat." else "Sentinel 24/7 background guard active with zero wake-lock overhead."
        )

        return AiDiagnosticResult(
            summary = "Netra Sentinel Engine: Battery operating at ${t.level}% (${t.temperature}°C, ${t.voltageMv}mV). Capacity health score: ${t.healthScore?.let { "$it/100" } ?: "Unavailable"}.",
            thermalAnalysis = thermalStatus,
            degradationRisk = degradation,
            optimalChargingAdvice = advice,
            recommendedActions = actions,
            modelUsed = if (isThinkingMode(isThinking)) "Gemini Pro Reasoning (Local Fallback)" else "Gemini Flash Lite (Local Fallback)",
            isThinkingMode = isThinking
        )
    }

    private fun isThinkingMode(isThinking: Boolean): Boolean = isThinking
}
