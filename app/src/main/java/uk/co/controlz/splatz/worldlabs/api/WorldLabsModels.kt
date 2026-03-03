package uk.co.controlz.splatz.worldlabs.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Model selection for world generation.
 */
enum class MarbleModel(val apiValue: String) {
    PLUS("Marble 0.1-plus"),
    MINI("Marble 0.1-mini");
    
    companion object {
        fun fromApiValue(value: String): MarbleModel {
            return entries.find { it.apiValue == value } ?: PLUS
        }
    }
}

/**
 * Types of prompts for world generation.
 */
sealed class WorldPrompt {
    abstract fun toJson(): JSONObject
    
    /**
     * Text-based world generation prompt.
     */
    data class TextPrompt(
        val text: String
    ) : WorldPrompt() {
        override fun toJson(): JSONObject {
            return JSONObject().apply {
                put("type", "text")
                put("text_prompt", text)
            }
        }
    }
    
    /**
     * Image URL-based world generation prompt.
     */
    data class ImageUrlPrompt(
        val imageUrl: String,
        val textGuidance: String? = null
    ) : WorldPrompt() {
        override fun toJson(): JSONObject {
            return JSONObject().apply {
                put("type", "image")
                put("content", JSONObject().apply {
                    put("type", "uri")
                    put("uri", imageUrl)
                })
                textGuidance?.let { put("text_guidance", it) }
            }
        }
    }
}

/**
 * Permission settings for generated worlds.
 */
data class Permission(
    val isPublic: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("is_public", isPublic)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject?): Permission {
            if (json == null) return Permission()
            return Permission(
                isPublic = json.optBoolean("is_public", false)
            )
        }
    }
}

/**
 * Request to generate a world.
 */
data class WorldsGenerateRequest(
    val worldPrompt: WorldPrompt,
    val displayName: String? = null,
    val model: MarbleModel = MarbleModel.PLUS,
    val tags: List<String>? = null,
    val seed: Int? = null,
    val permission: Permission = Permission()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("world_prompt", worldPrompt.toJson())
            displayName?.let { put("display_name", it) }
            put("model", model.apiValue)
            tags?.let { 
                put("tags", JSONArray().apply {
                    it.forEach { tag -> put(tag) }
                })
            }
            seed?.let { put("seed", it) }
            put("permission", permission.toJson())
        }.toString()
    }
}

/**
 * Response from generate world endpoint.
 */
data class GenerateWorldResponse(
    val operationId: String
) {
    companion object {
        fun fromJson(json: String): GenerateWorldResponse {
            val obj = JSONObject(json)
            return GenerateWorldResponse(
                operationId = obj.getString("operation_id")
            )
        }
    }
}

/**
 * Splat assets URLs.
 */
data class SplatAssets(
    val spzUrls: Map<String, String>
) {
    companion object {
        fun fromJson(json: JSONObject?): SplatAssets? {
            if (json == null) return null
            val spzUrlsJson = json.optJSONObject("spz_urls") ?: return null
            val urls = mutableMapOf<String, String>()
            spzUrlsJson.keys().forEach { key ->
                urls[key] = spzUrlsJson.getString(key)
            }
            return SplatAssets(spzUrls = urls)
        }
    }
    
    /**
     * Get available resolutions.
     */
    fun getAvailableResolutions(): List<String> {
        return spzUrls.keys.toList()
    }
    
    /**
     * Get URL for a specific resolution, or the best available.
     */
    fun getSpzUrl(resolution: String? = null): String? {
        if (resolution != null && spzUrls.containsKey(resolution)) {
            return spzUrls[resolution]
        }
        // Prefer 100k for mobile, then 500k, then full_res
        return spzUrls["full_res"] ?: spzUrls["500k"] ?: spzUrls["full_res"] ?: spzUrls.values.firstOrNull()
    }
}

/**
 * Imagery assets.
 */
data class ImageryAssets(
    val panoUrl: String?
) {
    companion object {
        fun fromJson(json: JSONObject?): ImageryAssets? {
            if (json == null) return null
            return ImageryAssets(
                panoUrl = json.optString("pano_url").takeIf { it.isNotEmpty() }
            )
        }
    }
}

/**
 * Mesh assets.
 */
data class MeshAssets(
    val colliderMeshUrl: String?
) {
    companion object {
        fun fromJson(json: JSONObject?): MeshAssets? {
            if (json == null) return null
            return MeshAssets(
                colliderMeshUrl = json.optString("collider_mesh_url").takeIf { it.isNotEmpty() }
            )
        }
    }
}

/**
 * World assets including imagery, mesh, and splats.
 */
data class WorldAssets(
    val caption: String?,
    val thumbnailUrl: String?,
    val imagery: ImageryAssets?,
    val mesh: MeshAssets?,
    val splats: SplatAssets?
) {
    companion object {
        fun fromJson(json: JSONObject?): WorldAssets? {
            if (json == null) return null
            return WorldAssets(
                caption = json.optString("caption").takeIf { it.isNotEmpty() },
                thumbnailUrl = json.optString("thumbnail_url").takeIf { it.isNotEmpty() },
                imagery = ImageryAssets.fromJson(json.optJSONObject("imagery")),
                mesh = MeshAssets.fromJson(json.optJSONObject("mesh")),
                splats = SplatAssets.fromJson(json.optJSONObject("splats"))
            )
        }
    }
}

/**
 * A generated world.
 */
data class World(
    val worldId: String,
    val displayName: String?,
    val worldMarbleUrl: String?,
    val assets: WorldAssets?,
    val createdAt: String?,
    val updatedAt: String?,
    val model: String?,
    val permission: Permission?,
    val tags: List<String>?
) {
    companion object {
        fun fromJson(json: JSONObject): World {
            val tagsJson = json.optJSONArray("tags")
            val tags = if (tagsJson != null) {
                (0 until tagsJson.length()).map { tagsJson.getString(it) }
            } else null
            
            return World(
                worldId = json.getString("world_id"),
                displayName = json.optString("display_name").takeIf { it.isNotEmpty() },
                worldMarbleUrl = json.optString("world_marble_url").takeIf { it.isNotEmpty() },
                assets = WorldAssets.fromJson(json.optJSONObject("assets")),
                createdAt = json.optString("created_at").takeIf { it.isNotEmpty() },
                updatedAt = json.optString("updated_at").takeIf { it.isNotEmpty() },
                model = json.optString("model").takeIf { it.isNotEmpty() },
                permission = Permission.fromJson(json.optJSONObject("permission")),
                tags = tags
            )
        }
    }
}

/**
 * Operation error information.
 */
data class OperationError(
    val code: String?,
    val message: String?
) {
    companion object {
        fun fromJson(json: JSONObject?): OperationError? {
            if (json == null) return null
            return OperationError(
                code = json.optString("code").takeIf { it.isNotEmpty() },
                message = json.optString("message").takeIf { it.isNotEmpty() }
            )
        }
    }
}

/**
 * Response from get operation endpoint.
 */
data class GetOperationResponse(
    val operationId: String,
    val done: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
    val expiresAt: String?,
    val error: OperationError?,
    val metadata: Map<String, Any>?,
    val response: World?
) {
    val progressPercentage: Float?
        get() = (metadata?.get("progress_percentage") as? Number)?.toFloat()
    
    val previewUrl: String?
        get() = metadata?.get("preview_url") as? String
    
    companion object {
        fun fromJson(json: String): GetOperationResponse {
            val obj = JSONObject(json)
            
            val metadataJson = obj.optJSONObject("metadata")
            val metadata = if (metadataJson != null) {
                val map = mutableMapOf<String, Any>()
                metadataJson.keys().forEach { key ->
                    map[key] = metadataJson.get(key)
                }
                map
            } else null
            
            val responseJson = obj.optJSONObject("response")
            
            return GetOperationResponse(
                operationId = obj.getString("operation_id"),
                done = obj.optBoolean("done", false),
                createdAt = obj.optString("created_at").takeIf { it.isNotEmpty() },
                updatedAt = obj.optString("updated_at").takeIf { it.isNotEmpty() },
                expiresAt = obj.optString("expires_at").takeIf { it.isNotEmpty() },
                error = OperationError.fromJson(obj.optJSONObject("error")),
                metadata = metadata,
                response = if (responseJson != null) World.fromJson(responseJson) else null
            )
        }
    }
}

/**
 * Sort order for listing worlds.
 */
enum class SortBy(val apiValue: String) {
    CREATED_AT("created_at"),
    UPDATED_AT("updated_at");
}

/**
 * Request to list worlds.
 */
data class ListWorldsRequest(
    val pageSize: Int = 10,
    val pageToken: String? = null,
    val sortBy: SortBy = SortBy.CREATED_AT,
    val sortDescending: Boolean = true,
    val model: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("page_size", pageSize)
            pageToken?.let { put("page_token", it) }
            put("sort_by", sortBy.apiValue)
            put("sort_descending", sortDescending)
            model?.let { put("model", it) }
        }.toString()
    }
}

/**
 * Response from list worlds endpoint.
 */
data class ListWorldsResponse(
    val worlds: List<World>,
    val nextPageToken: String?
) {
    companion object {
        fun fromJson(json: String): ListWorldsResponse {
            val obj = JSONObject(json)
            val worldsJson = obj.optJSONArray("worlds") ?: JSONArray()
            val worlds = (0 until worldsJson.length()).map { 
                World.fromJson(worldsJson.getJSONObject(it))
            }
            
            return ListWorldsResponse(
                worlds = worlds,
                nextPageToken = obj.optString("next_page_token").takeIf { it.isNotEmpty() }
            )
        }
    }
}
