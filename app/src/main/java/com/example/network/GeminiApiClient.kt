package com.example.network

import android.util.Log
import com.example.data.ApiKeyConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    sealed interface CallStepState {
        object Idle : CallStepState
        data class Sending(val keyDesc: String, val model: String) : CallStepState
        data class RetryingRateLimit(val attempt: Int, val delaySecondsLeft: Int, val keyDesc: String) : CallStepState
        data class VpnBlockPrompt(val onContinuePressed: () -> Unit) : CallStepState
        data class ServerErrorOptionPrompt(
            val message: String,
            val onSkipToNext: () -> Unit,
            val onRetryCurrentTenTimes: () -> Unit
        ) : CallStepState
        data class Success(val responseText: String) : CallStepState
        data class OutOfOptions(val error: String) : CallStepState
    }

    // Call state callback to update UI in real-time
    interface StatusListener {
        fun onStateChanged(state: CallStepState)
    }

    /**
     * Sends subtitle content to Gemini API for refining and transforming to beautiful SRT lines,
     * traversing the API key priority list as necessary, coping with rate-limits, VPN geo-blocks, and servers-busy errors.
     */
    suspend fun refineSubtitles(
        rawSrv3Xml: String,
        preferredLanguageCode: String,
        apiConfigs: List<ApiKeyConfig>,
        listener: StatusListener
    ): String? {
        if (apiConfigs.isEmpty()) {
            listener.onStateChanged(CallStepState.OutOfOptions("No Gemini API keys are configured. Please add one in Settings."))
            return null
        }

        val prompt = """
            You are an expert subtitle editor. I will give you a YouTube SRV3 transcription in XML format.
            Please clean this RAW transcription, group the words/phrases into beautiful, natural-sounding, grammatically correct sentences, and format the output as a standard SRT file matching the timings of the inputs.
            The target language for any polishing / grammar correction is preferred to be: $preferredLanguageCode.
            
            RULES:
            1. Clean up transcription stuttering, word repetitions, or raw ASR noises.
            2. Match timings accurately based on where sentences begin and end within the input timings.
            3. Use the standard SRT format (Index, Time in HH:MM:SS,mmm --> HH:MM:SS,mmm, Text).
            4. Output ONLY the raw contents of the completed SRT file. Do NOT include any intro, outro, conversational notes, or markdown fences (such as ```srt or ```). Start directly with subtitle index 1.
            
            INPUT SRV3 XML:
            $rawSrv3Xml
        """.trimIndent()

        var currentConfigIndex = 0

        while (currentConfigIndex < apiConfigs.size) {
            val config = apiConfigs[currentConfigIndex]
            val keyDesc = if (config.description.isNotBlank()) config.description else "Key #${config.id}"
            
            Log.d(TAG, "Trying API Key Config: $keyDesc (${config.modelName})")
            listener.onStateChanged(CallStepState.Sending(keyDesc, config.modelName))

            var attempt429 = 1
            var attempt503 = 1
            var shouldRetryCurrentConfig = true

            while (shouldRetryCurrentConfig) {
                val result = makeSingleApiCall(config.apiKey, config.modelName, prompt)
                
                when {
                    result.isSuccess -> {
                        val text = result.getOrNull()
                        if (!text.isNullOrBlank()) {
                            listener.onStateChanged(CallStepState.Success(text))
                            return text
                        } else {
                            // Empty response, try next config
                            Log.w(TAG, "Success response code but text content is empty or null.")
                            currentConfigIndex++
                            shouldRetryCurrentConfig = false
                        }
                    }
                    else -> {
                        val exception = result.exceptionOrNull()
                        val code = (exception as? GeminiException)?.code ?: -1
                        val errMsg = exception?.message ?: "Unknown error"

                        Log.e(TAG, "API Call Failed: Code = $code, Msg = $errMsg")

                        when (code) {
                            429 -> {
                                if (attempt429 <= 4) {
                                    Log.d(TAG, "Rate limiting error 429 detected. Preparing retry #$attempt429 of 4")
                                    // Delay for 30 seconds, updating status listener second-by-second
                                    for (sec in 30 downTo 1) {
                                        listener.onStateChanged(CallStepState.RetryingRateLimit(attempt429, sec, keyDesc))
                                        delay(1000)
                                    }
                                    attempt429++
                                } else {
                                    Log.w(TAG, "429 Rate limiting retries exhausted for this key. Rolling over to next.")
                                    currentConfigIndex++
                                    shouldRetryCurrentConfig = false
                                }
                            }
                            400, 403 -> {
                                // Geo-block or Auth error (possible VPN required)
                                Log.d(TAG, "Geo-blocking or forbidden error $code detected. Prompting VPN change.")
                                val vpnDeferred = CompletableDeferred<Unit>()
                                listener.onStateChanged(CallStepState.VpnBlockPrompt {
                                    vpnDeferred.complete(Unit)
                                })
                                // Wait for user to enable/switch VPN and press Continue
                                vpnDeferred.await()
                                Log.d(TAG, "VPN Continue pressed. Retrying current config...")
                                // Retry immediately with same config
                            }
                            else -> {
                                // 503 or other network timeout errors
                                if (attempt503 <= 10) {
                                    Log.d(TAG, "Network / Service error detected. Attempting automatic retry $attempt503 of 10")
                                    // Countdown 30 seconds
                                    for (sec in 30 downTo 1) {
                                        listener.onStateChanged(CallStepState.RetryingRateLimit(attempt503, sec, "$keyDesc (Net Error)"))
                                        delay(1000)
                                    }
                                    attempt503++
                                } else {
                                    // Prompt User with options
                                    Log.d(TAG, "Network retries exhausted. Prompting user with options.")
                                    val userChoiceDeferred = CompletableDeferred<Boolean>() // true = skip, false = retry 10 times
                                    
                                    listener.onStateChanged(CallStepState.ServerErrorOptionPrompt(
                                        message = errMsg,
                                        onSkipToNext = {
                                            userChoiceDeferred.complete(true)
                                        },
                                        onRetryCurrentTenTimes = {
                                            userChoiceDeferred.complete(false)
                                        }
                                    ))

                                    val shouldSkip = userChoiceDeferred.await()
                                    if (shouldSkip) {
                                        Log.d(TAG, "User chose to skip to next API config.")
                                        currentConfigIndex++
                                        shouldRetryCurrentConfig = false
                                    } else {
                                        Log.d(TAG, "User chose to retry current API config 10 more times.")
                                        attempt503 = 1 // Reset count for 10 more retries
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        listener.onStateChanged(CallStepState.OutOfOptions("All configured API Keys failed or were exhausted."))
        return null
    }

    private fun makeSingleApiCall(apiKey: String, model: String, prompt: String): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val code = response.code
            val bodyStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(bodyStr)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    if (contentObj != null) {
                        val parts = contentObj.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val textValue = parts.getJSONObject(0).optString("text")
                            return Result.success(textValue)
                        }
                    }
                }
                Result.failure(GeminiException(code, "Empty content or unrecognized JSON response schema."))
            } else {
                var message = "HTTP error $code"
                try {
                    val errJson = JSONObject(bodyStr)
                    val errObj = errJson.optJSONObject("error")
                    if (errObj != null) {
                        message = errObj.optString("message", message)
                    }
                } catch (e: Exception) {
                    // ignore
                }
                Result.failure(GeminiException(code, message))
            }
        } catch (e: IOException) {
            Result.failure(GeminiException(503, e.message ?: "Network call failed due to IO exception."))
        } catch (e: Exception) {
            Result.failure(GeminiException(500, e.message ?: "Unknown local exception during call."))
        }
    }

    class GeminiException(val code: Int, message: String) : Exception(message)
}
