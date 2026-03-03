/**
 * WorldLabsGeneratorPanel.kt
 *
 * OVERVIEW: This file defines the main UI panel composable for generating Gaussian Splat worlds
 * using the WorldLabs API. The panel manages state and navigation between different views.
 *
 * KEY FEATURES:
 * - Text-based world generation prompts
 * - Image URL-based generation
 * - Model selection (Plus/Mini)
 * - Generation progress tracking
 * - Worlds list with thumbnails
 * - Direct loading of generated splats into the scene
 *
 * Related files:
 * - WorldLabsState.kt - State models (ViewState, PromptType, PendingOperation, WorldItem)
 * - WorldLabsViews.kt - View composables (WorldsListView, WorldDetailView, CreateWorldView, SettingsView)
 * - WorldLabsComponents.kt - Reusable UI components (PanelHeader, WorldCard, InfoRow, etc.)
 * - WorldLabsUtils.kt - Utility functions and constants
 */
package uk.co.controlz.splatz.worldlabs

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme
import kotlinx.coroutines.launch
import uk.co.controlz.splatz.worldlabs.api.ListWorldsRequest
import uk.co.controlz.splatz.worldlabs.api.MarbleModel
import uk.co.controlz.splatz.worldlabs.api.World
import uk.co.controlz.splatz.worldlabs.api.WorldLabsClient
import uk.co.controlz.splatz.worldlabs.api.WorldPrompt
import uk.co.controlz.splatz.worldlabs.api.WorldsGenerateRequest

/**
 * Main WorldLabs Generator Panel composable.
 * Provides UI for viewing, generating, and importing worlds.
 */
@Composable
fun WorldLabsGeneratorPanel(
    apiKey: String,
    splatLoadComplete: Int = 0,
    onLoadSplat: (String) -> Unit,
    onApiKeySaved: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    
    // Force settings view when no API key is configured
    val hasValidApiKey = apiKey.isNotEmpty()
    
    // API Client
    val client = remember(apiKey) { 
        if (hasValidApiKey) WorldLabsClient(apiKey) else null
    }
    
    // View state - start on settings if no API key
    var currentView by remember(hasValidApiKey) { 
        mutableStateOf(if (hasValidApiKey) ViewState.WORLDS_LIST else ViewState.SETTINGS) 
    }
    var selectedWorld by remember { mutableStateOf<World?>(null) }
    
    // Worlds list state
    var worlds by remember { mutableStateOf<List<WorldItem>>(emptyList()) }
    var isLoadingWorlds by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    
    // Create world state
    var textPrompt by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(MarbleModel.PLUS) }
    var isGenerating by remember { mutableStateOf(false) }
    var generateError by remember { mutableStateOf<String?>(null) }
    
    // Pending operations
    var pendingOperations by remember { mutableStateOf<Map<String, PendingOperation>>(emptyMap()) }
    
    // Settings state
    var tempApiKey by remember { mutableStateOf(apiKey) }
    
    // Function to refresh worlds list
    fun refreshWorlds() {
        if (client == null) return
        scope.launch {
            isLoadingWorlds = true
            loadError = null
            try {
                val response = client.listWorlds(ListWorldsRequest(pageSize = 12))
                worlds = response.worlds.map { WorldItem(it) }
            } catch (e: Exception) {
                Log.e("WorldLabsPanel", "Failed to load worlds", e)
                loadError = e.message ?: "Failed to load worlds"
            } finally {
                isLoadingWorlds = false
            }
        }
    }
    
    // Function to start generation
    fun startGeneration() {
        if (client == null) return
        
        val prompt = WorldPrompt.TextPrompt(textPrompt)
        
        scope.launch {
            isGenerating = true
            generateError = null
            
            try {
                val request = WorldsGenerateRequest(
                    worldPrompt = prompt,
                    displayName = displayName.ifEmpty { null },
                    model = selectedModel
                )
                val response = client.generateWorld(request)
                
                // Add to pending operations
                val savedDisplayName = displayName.ifEmpty { "New World" }
                pendingOperations = pendingOperations + (response.operationId to PendingOperation(
                    operationId = response.operationId,
                    displayName = savedDisplayName
                ))
                
                // Reset form and go back to list
                textPrompt = ""
                displayName = ""
                currentView = ViewState.WORLDS_LIST
                
                // Start polling in background (every 15 seconds)
                scope.launch {
                    try {
                        client.waitForOperation(
                            operationId = response.operationId,
                            pollingIntervalMs = 15000 // Poll every 15 seconds
                        ) { op ->
                            pendingOperations = pendingOperations + (op.operationId to PendingOperation(
                                operationId = op.operationId,
                                displayName = savedDisplayName,
                                progress = op.progressPercentage ?: 0f,
                                status = if (op.done) "Complete" else "Generating...",
                                previewUrl = op.previewUrl,
                                isComplete = op.done,
                                hasError = op.error != null,
                                errorMessage = op.error?.message
                            ))
                            
                            if (op.done && op.error == null) {
                                // Success! Remove from pending and refresh list
                                pendingOperations = pendingOperations - op.operationId
                                refreshWorlds()
                            } else if (op.done && op.error != null) {
                                // Keep in pending with error state
                                Log.e("WorldLabsPanel", "Generation failed: ${op.error.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("WorldLabsPanel", "Operation polling failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("WorldLabsPanel", "Failed to start generation", e)
                generateError = e.message ?: "Failed to start generation"
            } finally {
                isGenerating = false
            }
        }
    }
    
    // Initial load
    LaunchedEffect(client) {
        if (client != null) {
            refreshWorlds()
        }
    }
    
    SpatialTheme(colorScheme = getPanelTheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(SpatialTheme.shapes.large)
                .background(brush = LocalColorScheme.current.panel)
                .padding(24.dp)
        ) {
            // Header with navigation - disabled when no API key
            PanelHeader(
                currentView = currentView,
                onViewChange = { currentView = it },
                onRefresh = { refreshWorlds() },
                enabled = hasValidApiKey
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Content based on view state
            when (currentView) {
                ViewState.WORLDS_LIST -> WorldsListView(
                    worlds = worlds,
                    isLoading = isLoadingWorlds,
                    error = loadError,
                    pendingOperations = pendingOperations,
                    onRetry = { refreshWorlds() },
                    onSelectWorld = { world ->
                        selectedWorld = world
                        currentView = ViewState.WORLD_DETAIL
                    },
                    onCreateNew = { currentView = ViewState.CREATE_WORLD }
                )
                ViewState.WORLD_DETAIL -> {
                    selectedWorld?.let { world ->
                        WorldDetailView(
                            world = world,
                            splatLoadComplete = splatLoadComplete,
                            onLoadSplat = onLoadSplat,
                            onBack = { currentView = ViewState.WORLDS_LIST },
                            onCreateNew = { currentView = ViewState.CREATE_WORLD }
                        )
                    }
                }
                ViewState.CREATE_WORLD -> CreateWorldView(
                    textPrompt = textPrompt,
                    onTextPromptChange = { textPrompt = it },
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it },
                    selectedModel = selectedModel,
                    onModelChange = { selectedModel = it },
                    isGenerating = isGenerating,
                    error = generateError,
                    onGenerate = { startGeneration() },
                    onCancel = { currentView = ViewState.WORLDS_LIST }
                )
                ViewState.SETTINGS -> SettingsView(
                    apiKey = tempApiKey,
                    onApiKeyChange = { tempApiKey = it },
                    onSave = { 
                        onApiKeySaved(tempApiKey)
                        // After saving, navigate to worlds list
                        currentView = ViewState.WORLDS_LIST
                    },
                    onBack = if (hasValidApiKey) {{ currentView = ViewState.WORLDS_LIST }} else null
                )
                ViewState.HELP -> HelpView(
                    onLoadSampleSplat = { 
                        onLoadSplat("apk://metaverse.spz")
                    },
                    onBack = { currentView = ViewState.WORLDS_LIST }
                )
            }
        }
    }
}
