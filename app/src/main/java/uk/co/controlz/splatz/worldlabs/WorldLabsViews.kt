package uk.co.controlz.splatz.worldlabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme
import uk.co.controlz.splatz.worldlabs.api.MarbleModel
import uk.co.controlz.splatz.worldlabs.api.World
import uk.co.controlz.splatz.voice.VoiceMicButton

/**
 * View showing the list of worlds with pending operations.
 */
@Composable
fun WorldsListView(
    worlds: List<WorldItem>,
    isLoading: Boolean,
    error: String?,
    pendingOperations: Map<String, PendingOperation>,
    onRetry: () -> Unit,
    onSelectWorld: (World) -> Unit,
    onCreateNew: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pending operations
        if (pendingOperations.isNotEmpty()) {
            Text(
                text = "In Progress",
                style = SpatialTheme.typography.headline2Strong,
                color = LocalColorScheme.current.primaryAlphaBackground
            )
            
            pendingOperations.values.forEach { operation ->
                PendingOperationCard(operation)
            }
            
            HorizontalDivider(color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.3f))
        }
        
        // Loading state
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WorldLabsColors.Primary)
            }
            return@Column
        }
        
        // Error state
        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
            return@Column
        }
        
        // Worlds header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Worlds",
                style = SpatialTheme.typography.headline2Strong,
                color = LocalColorScheme.current.primaryAlphaBackground
            )
            
            Button(
                onClick = onCreateNew,
                colors = ButtonDefaults.buttonColors(containerColor = WorldLabsColors.Primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create New")
            }
        }
        
        // Worlds grid
        if (worlds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No worlds yet",
                        style = SpatialTheme.typography.body1,
                        color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onCreateNew,
                        colors = ButtonDefaults.buttonColors(containerColor = WorldLabsColors.Primary)
                    ) {
                        Text("Create your first world")
                    }
                }
            }
        } else {
            // Grid layout with 3 columns
            val chunkedWorlds = worlds.chunked(3)
            chunkedWorlds.forEach { rowWorlds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowWorlds.forEach { worldItem ->
                        WorldCard(
                            worldItem = worldItem,
                            modifier = Modifier.weight(1f),
                            onViewDetails = { onSelectWorld(worldItem.world) }
                        )
                    }
                    // Add spacers for incomplete rows
                    repeat(3 - rowWorlds.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Detail view for a single world showing all information and resolution options.
 */
@Composable
fun WorldDetailView(
    world: World,
    splatLoadComplete: Int = 0,
    onLoadSplat: (String) -> Unit,
    onBack: () -> Unit,
    onCreateNew: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var loadingResolution by remember { mutableStateOf<String?>(null) }
    
    // Reset loading state when splat finishes loading
    LaunchedEffect(splatLoadComplete) {
        loadingResolution = null
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back button and title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = WorldLabsColors.Primary)
            }
            Text(
                text = world.displayName ?: "Untitled World",
                style = SpatialTheme.typography.headline2Strong,
                color = LocalColorScheme.current.primaryAlphaBackground,
                modifier = Modifier.weight(1f)
            )
            // New World button
            Button(
                onClick = onCreateNew,
                colors = ButtonDefaults.buttonColors(containerColor = WorldLabsColors.Primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New")
            }
        }
        
        // Large thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (world.assets?.thumbnailUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(world.assets.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = world.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "No Preview Available",
                    color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.5f)
                )
            }
        }
        
        // Load Splat buttons (right under the banner)
        val availableResolutions = world.assets?.splats?.getAvailableResolutions() ?: emptyList()
        if (availableResolutions.isNotEmpty()) {
            Text(
                text = "Load Splat",
                style = SpatialTheme.typography.headline2Strong,
                color = LocalColorScheme.current.primaryAlphaBackground
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableResolutions.forEach { resolution ->
                    val url = world.assets?.splats?.spzUrls?.get(resolution)
                    val isLoading = loadingResolution == resolution
                    val isAnyLoading = loadingResolution != null
                    if (url != null) {
                        OutlinedButton(
                            onClick = { 
                                loadingResolution = resolution
                                onLoadSplat(url) 
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isAnyLoading,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = getResolutionButtonColor(resolution)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = getResolutionButtonColor(resolution),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = formatResolutionLabel(resolution),
                                    style = SpatialTheme.typography.body2,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Caption (full, not truncated)
        world.assets?.caption?.let { caption ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.1f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Caption",
                        style = SpatialTheme.typography.body2,
                        fontWeight = FontWeight.Bold,
                        color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = caption,
                        style = SpatialTheme.typography.body1,
                        color = LocalColorScheme.current.primaryAlphaBackground
                    )
                }
            }
        }
        
        // World Info section
        Card(
            colors = CardDefaults.cardColors(
                containerColor = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "World Information",
                    style = SpatialTheme.typography.body1,
                    fontWeight = FontWeight.Bold,
                    color = LocalColorScheme.current.primaryAlphaBackground
                )
                
                world.model?.let {
                    InfoRow(label = "Model", value = it)
                }
                world.worldId.let {
                    InfoRow(label = "World ID", value = it)
                }
                world.createdAt?.let {
                    InfoRow(label = "Created", value = formatDate(it))
                }
                world.updatedAt?.let {
                    InfoRow(label = "Updated", value = formatDate(it))
                }
                world.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    InfoRow(label = "Tags", value = tags.joinToString(", "))
                }
            }
        }
        
        // Assets section
        Card(
            colors = CardDefaults.cardColors(
                containerColor = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Assets",
                    style = SpatialTheme.typography.body1,
                    fontWeight = FontWeight.Bold,
                    color = LocalColorScheme.current.primaryAlphaBackground
                )
                
                world.assets?.imagery?.panoUrl?.let {
                    InfoRow(label = "Panorama", value = "Available")
                }
                world.assets?.mesh?.colliderMeshUrl?.let {
                    InfoRow(label = "Collider Mesh", value = "Available")
                }
                world.worldMarbleUrl?.let {
                    InfoRow(label = "World Marble", value = "Available")
                }
                
                val resolutions = world.assets?.splats?.getAvailableResolutions() ?: emptyList()
                if (resolutions.isNotEmpty()) {
                    InfoRow(label = "Splat Resolutions", value = resolutions.joinToString(", ") { formatResolutionLabel(it) })
                }
            }
        }
    }
}

/**
 * View for creating a new world with prompt input and settings.
 */
@Composable
fun CreateWorldView(
    textPrompt: String,
    onTextPromptChange: (String) -> Unit,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    selectedModel: MarbleModel,
    onModelChange: (MarbleModel) -> Unit,
    isGenerating: Boolean,
    error: String?,
    onGenerate: () -> Unit,
    onCancel: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Prompt Input
        Text(
            text = "Prompt",
            style = SpatialTheme.typography.headline2Strong,
            color = LocalColorScheme.current.primaryAlphaBackground
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = textPrompt,
                onValueChange = onTextPromptChange,
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                label = { Text("Describe the world you want to create") },
                enabled = !isGenerating,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = WorldLabsColors.Primary,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.LightGray,
                    unfocusedLabelColor = Color.Gray
                )
            )
            // Voice input button - TO REMOVE: Delete this VoiceMicButton
            if (!isGenerating) {
                VoiceMicButton(
                    currentText = textPrompt,
                    onTextReceived = { fullText ->
                        // fullText already includes base + all spoken segments
                        onTextPromptChange(fullText)
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        // Display Name (Optional)
        Text(
            text = "Display Name (Optional)",
            style = SpatialTheme.typography.headline2Strong,
            color = LocalColorScheme.current.primaryAlphaBackground
        )
        
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Give your world a name") },
            singleLine = true,
            enabled = !isGenerating,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = WorldLabsColors.Primary,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.LightGray,
                unfocusedLabelColor = Color.Gray
            )
        )
        
        HorizontalDivider(color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.3f))
        
        // Model selection
        Text(
            text = "Model",
            style = SpatialTheme.typography.headline2Strong,
            color = LocalColorScheme.current.primaryAlphaBackground
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedModel == MarbleModel.PLUS,
                onClick = { onModelChange(MarbleModel.PLUS) },
                label = { Text("Marble Plus") },
                enabled = !isGenerating
            )
            FilterChip(
                selected = selectedModel == MarbleModel.MINI,
                onClick = { onModelChange(MarbleModel.MINI) },
                label = { Text("Marble Mini") },
                enabled = !isGenerating
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error display
        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.2f))
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = Color.White
                )
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                enabled = !isGenerating
            ) {
                Text("Cancel")
            }
            
            Button(
                onClick = onGenerate,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = WorldLabsColors.Primary),
                enabled = !isGenerating && textPrompt.isNotBlank()
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isGenerating) "Generating..." else "Generate World")
            }
        }
    }
}

/**
 * Settings view for configuring API key and viewing help info.
 * When onBack is null, shows only the Save button (forces API key entry).
 */
@Composable
fun SettingsView(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val canSave = apiKey.isNotEmpty()
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (onBack == null) "Welcome - Enter API Key" else "Settings",
            style = SpatialTheme.typography.headline2Strong,
            color = LocalColorScheme.current.primaryAlphaBackground
        )
        
        if (onBack == null) {
            Text(
                text = "An API key is required to use WorldLabs. Enter your key below to get started.",
                style = SpatialTheme.typography.body1,
                color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.8f)
            )
        }
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("WorldLabs API Key") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = WorldLabsColors.Primary,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.LightGray,
                unfocusedLabelColor = Color.Gray
            )
        )
        
        Text(
            text = "Get your API key from platform.worldlabs.ai",
            style = SpatialTheme.typography.body2,
            color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
        )
        
        Text(
            text = buildAnnotatedString {
                append("Press ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("A") }
                append(" to snap the panel in front of you. \nPress ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("B") }
                append(" to recenter the view.")
            },
            style = SpatialTheme.typography.body1,
            color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Save button - always shown
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave
        ) {
            Text(if (onBack == null) "Save & Continue" else "Save API Key")
        }
        
        // Back button - only shown when API key is already configured
        if (onBack != null) {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back to Worlds")
            }
        }
    }
}

/**
 * View shown during world generation with animated loading text.
 */
@Composable
fun GeneratingWorldView(
    displayName: String,
    progress: Float = 0f,
    previewUrl: String? = null,
    errorMessage: String? = null,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    
    // Animated status messages that cycle
    val statusMessages = listOf(
        "Initializing world generation...",
        "Analyzing your prompt...",
        "Creating 3D environment...",
        "Building scene geometry...",
        "Adding atmospheric details...",
        "Rendering Gaussian splats...",
        "Optimizing for viewing...",
        "Almost there..."
    )
    
    var currentMessageIndex by remember { mutableStateOf(0) }
    var dotCount by remember { mutableStateOf(0) }
    
    // Cycle through status messages
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            currentMessageIndex = (currentMessageIndex + 1) % statusMessages.size
        }
    }
    
    // Animate dots
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            dotCount = (dotCount + 1) % 4
        }
    }
    
    val dots = ".".repeat(dotCount)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Display name
        Text(
            text = displayName.ifEmpty { "New World" },
            style = SpatialTheme.typography.headline2Strong,
            color = LocalColorScheme.current.primaryAlphaBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Preview image if available
        if (previewUrl != null) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(previewUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Loading indicator (spinning circle)
        if (errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = WorldLabsColors.Primary,
                strokeWidth = 4.dp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Animated status text
            Text(
                text = statusMessages[currentMessageIndex] + dots,
                style = SpatialTheme.typography.body1,
                color = LocalColorScheme.current.primaryAlphaBackground
            )
            
            // Progress indicator if available
            if (progress > 0f) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${(progress * 100).toInt()}% complete",
                    style = SpatialTheme.typography.body2,
                    color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
                )
            }
        } else {
            // Error state
            Text(
                text = "Generation Failed",
                style = SpatialTheme.typography.headline2Strong,
                color = Color.Red
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = SpatialTheme.typography.body1,
                color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Cancel button
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LocalColorScheme.current.primaryAlphaBackground
            )
        ) {
            Text(if (errorMessage != null) "Back" else "Cancel")
        }
    }
}

/**
 * Help view explaining how to use the app with a sample splat.
 */
@Composable
fun HelpView(
    onLoadSampleSplat: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "How to Use Splatz",
            style = SpatialTheme.typography.headline1Strong,
            color = LocalColorScheme.current.primaryAlphaBackground
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Instructions
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HelpStep(
                number = "1",
                title = "Generate a World",
                description = "Go to the Create screen, enter a text prompt describing your scene, and click Generate. WorldLabs AI will create a 3D Gaussian Splat environment."
            )
            
            HelpStep(
                number = "2", 
                title = "Load Your World",
                description = "Once generation is complete, go to My Worlds and select your world. Click 'Load Splat' to import it into the scene."
            )
            
            HelpStep(
                number = "3",
                title = "Exit the Splat",
                description = "Press the A button on your controller to exit the loaded splat and return to the panel."
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Divider
        HorizontalDivider(
            color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.2f),
            thickness = 1.dp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sample splat section
        Text(
            text = "Try a Sample",
            style = SpatialTheme.typography.headline2Strong,
            color = LocalColorScheme.current.primaryAlphaBackground
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Load the built-in Metaverse sample to see a Gaussian Splat in action:",
            style = SpatialTheme.typography.body1,
            color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sample image preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/metaverse.png")
                    .crossfade(true)
                    .build(),
                contentDescription = "Metaverse Sample",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Metaverse - Gaussian Splat Sample",
            style = SpatialTheme.typography.body2,
            color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Load sample button
        Button(
            onClick = onLoadSampleSplat,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = WorldLabsColors.Primary,
                contentColor = Color.White
            )
        ) {
            Text("Load Sample Splat")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Back button
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LocalColorScheme.current.primaryAlphaBackground
            )
        ) {
            Text("Back")
        }
    }
}

/**
 * Helper composable for a numbered help step.
 */
@Composable
private fun HelpStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Number badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(WorldLabsColors.Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = SpatialTheme.typography.body1Strong,
                color = Color.White
            )
        }
        
        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = SpatialTheme.typography.body1Strong,
                color = LocalColorScheme.current.primaryAlphaBackground
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = SpatialTheme.typography.body2,
                color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
            )
        }
    }
}
