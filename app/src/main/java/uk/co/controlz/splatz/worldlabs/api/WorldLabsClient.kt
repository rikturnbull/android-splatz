package uk.co.controlz.splatz.worldlabs.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the World Labs Marble API.
 * Provides methods for generating worlds, managing media assets, and polling operations.
 */
class WorldLabsClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.worldlabs.ai"
) {
    companion object {
        private const val TAG = "WorldLabsClient"
    }

    /**
     * Start world generation.
     * Creates a new world generation job and returns a long-running operation.
     * Poll the getOperation endpoint to check generation status.
     */
    suspend fun generateWorld(request: WorldsGenerateRequest): GenerateWorldResponse {
        return withContext(Dispatchers.IO) {
            val json = request.toJson()
            val response = sendRequest("POST", "/marble/v1/worlds:generate", json)
            GenerateWorldResponse.Companion.fromJson(response)
        }
    }

    /**
     * Get an operation by ID.
     * Poll this endpoint to check the status of a long-running operation.
     */
    suspend fun getOperation(operationId: String): GetOperationResponse {
        return withContext(Dispatchers.IO) {
            val response = sendRequest("GET", "/marble/v1/operations/$operationId")
            GetOperationResponse.Companion.fromJson(response)
        }
    }

    /**
     * Get a world by ID.
     */
    suspend fun getWorld(worldId: String): World {
        return withContext(Dispatchers.IO) {
            val response = sendRequest("GET", "/marble/v1/worlds/$worldId")
            World.Companion.fromJson(JSONObject(response))
        }
    }

    /**
     * List worlds with optional filters.
     */
    suspend fun listWorlds(request: ListWorldsRequest = ListWorldsRequest()): ListWorldsResponse {
        return withContext(Dispatchers.IO) {
            val json = request.toJson()
            val response = sendRequest("POST", "/marble/v1/worlds:list", json)
            ListWorldsResponse.Companion.fromJson(response)
        }
    }

    /**
     * Polls an operation until it completes or times out.
     */
    suspend fun waitForOperation(
        operationId: String,
        pollingIntervalMs: Long = 5000,
        timeoutMs: Long = 600000, // 10 minutes default for world generation
        onProgress: ((GetOperationResponse) -> Unit)? = null
    ): GetOperationResponse {
        val startTime = System.currentTimeMillis()
        
        while (true) {
            val response = getOperation(operationId)
            onProgress?.invoke(response)
            
            if (response.done) {
                return response
            }
            
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                throw WorldLabsApiException("Operation $operationId did not complete within ${timeoutMs}ms")
            }
            
            delay(pollingIntervalMs)
        }
    }

    private fun sendRequest(method: String, endpoint: String, body: String? = null): String {
        val url = URL("$baseUrl$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = method
            connection.setRequestProperty("WLT-Api-Key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            
            if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
            
            if (responseCode !in 200..299) {
                Log.e(TAG, "API error $responseCode: $response")
                throw WorldLabsApiException("API request failed with status $responseCode: $response", responseCode)
            }
            
            return response
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Exception thrown when WorldLabs API requests fail.
 */
class WorldLabsApiException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)
