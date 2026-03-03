package uk.co.controlz.splatz.worldlabs

import uk.co.controlz.splatz.worldlabs.api.World

/**
 * View states for the generator panel.
 */
enum class ViewState {
    WORLDS_LIST,
    WORLD_DETAIL,
    CREATE_WORLD,
    SETTINGS,
    HELP
}

/**
 * Prompt types for world generation.
 */
enum class PromptType {
    TEXT,
    IMAGE_URL
}

/**
 * State holder for pending generation operations.
 */
data class PendingOperation(
    val operationId: String,
    val displayName: String?,
    val progress: Float = 0f,
    val status: String = "Processing...",
    val previewUrl: String? = null,
    val isComplete: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

/**
 * State holder for world items in the list.
 */
data class WorldItem(
    val world: World,
    val isImporting: Boolean = false
)
