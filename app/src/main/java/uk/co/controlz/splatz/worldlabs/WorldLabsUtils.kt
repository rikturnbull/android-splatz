package uk.co.controlz.splatz.worldlabs

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.meta.spatial.uiset.theme.SpatialColorScheme
import com.meta.spatial.uiset.theme.darkSpatialColorScheme
import com.meta.spatial.uiset.theme.lightSpatialColorScheme

/**
 * Physical dimensions of the generator panel in 3D space (in meters).
 */
const val WORLDLABS_PANEL_WIDTH = 1.8f
const val WORLDLABS_PANEL_HEIGHT = 1.4f

/**
 * Common colors used throughout the WorldLabs panel.
 */
object WorldLabsColors {
    val Primary = Color(0xFF1877F2)
    val FullRes = Color(0xFF1877F2)
    val Medium = Color(0xFF2E8B57)
    val Fast = Color(0xFF6B8E23)
}

/**
 * Formats an ISO date string to a simple date format.
 */
fun formatDate(isoDate: String): String {
    return try {
        isoDate.substringBefore("T")
    } catch (e: Exception) {
        isoDate
    }
}

/**
 * Formats a resolution key into a user-friendly label.
 */
fun formatResolutionLabel(resolution: String): String {
    return when (resolution.lowercase()) {
        "full_res" -> "Full Res"
        "500k" -> "500K"
        "100k" -> "100K"
        else -> resolution.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Returns the appropriate button color for a resolution.
 */
@Composable
fun getResolutionButtonColor(resolution: String): Color {
    return when (resolution.lowercase()) {
        "full_res" -> WorldLabsColors.FullRes
        "500k" -> WorldLabsColors.Medium
        "100k" -> WorldLabsColors.Fast
        else -> WorldLabsColors.Primary
    }
}

/**
 * Validates input based on prompt type.
 */
fun isValidInput(promptType: PromptType, textPrompt: String, imageUrl: String): Boolean {
    return when (promptType) {
        PromptType.TEXT -> textPrompt.isNotBlank()
        PromptType.IMAGE_URL -> imageUrl.isNotBlank()
    }
}

/**
 * Returns the appropriate theme based on system dark mode setting.
 */
@Composable
fun getPanelTheme(): SpatialColorScheme =
    if (isSystemInDarkTheme()) darkSpatialColorScheme() else lightSpatialColorScheme()
