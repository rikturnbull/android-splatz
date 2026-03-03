package uk.co.controlz.splatz.worldlabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme

/**
 * Header component for the WorldLabs panel with navigation icons.
 * When enabled=false, only the Settings button is active (for initial API key setup).
 */
@Composable
fun PanelHeader(
    currentView: ViewState,
    onViewChange: (ViewState) -> Unit,
    onRefresh: () -> Unit,
    enabled: Boolean = true
) {
    val disabledTint = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.3f)
    val normalTint = LocalColorScheme.current.primaryAlphaBackground
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "WorldLabs Generator",
            style = SpatialTheme.typography.headline1Strong,
            color = LocalColorScheme.current.primaryAlphaBackground
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // My Worlds button
            IconButton(
                onClick = { onViewChange(ViewState.WORLDS_LIST) },
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (currentView == ViewState.WORLDS_LIST) 
                        WorldLabsColors.Primary.copy(alpha = 0.3f) else Color.Transparent
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = "My Worlds",
                    tint = if (enabled) normalTint else disabledTint
                )
            }
            
            // Create World button
            IconButton(
                onClick = { onViewChange(ViewState.CREATE_WORLD) },
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (currentView == ViewState.CREATE_WORLD) 
                        WorldLabsColors.Primary.copy(alpha = 0.3f) else Color.Transparent
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Create World",
                    tint = if (enabled) normalTint else disabledTint
                )
            }
            
            // Refresh button - only shown on worlds list
            if (currentView == ViewState.WORLDS_LIST) {
                IconButton(
                    onClick = onRefresh,
                    enabled = enabled
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (enabled) normalTint else disabledTint
                    )
                }
            }
            
            // Help button
            IconButton(
                onClick = { onViewChange(ViewState.HELP) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (currentView == ViewState.HELP) 
                        WorldLabsColors.Primary.copy(alpha = 0.3f) else Color.Transparent
                )
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Help",
                    tint = normalTint
                )
            }
            
            // Settings button - always enabled
            IconButton(
                onClick = { onViewChange(ViewState.SETTINGS) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (currentView == ViewState.SETTINGS) 
                        WorldLabsColors.Primary.copy(alpha = 0.3f) else Color.Transparent
                )
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = normalTint
                )
            }
        }
    }
}

/**
 * Card component showing a pending generation operation with progress.
 */
@Composable
fun PendingOperationCard(operation: PendingOperation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = operation.displayName ?: "Generating...",
                style = SpatialTheme.typography.headline2Strong,
                color = LocalColorScheme.current.primaryAlphaBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (operation.hasError) {
                Text(
                    text = operation.errorMessage ?: "An error occurred",
                    color = Color.Red
                )
            } else {
                LinearProgressIndicator(
                    progress = { operation.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = WorldLabsColors.Primary,
                    trackColor = Color.Gray.copy(alpha = 0.3f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = operation.status,
                    style = SpatialTheme.typography.body2,
                    color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Card component displaying a world thumbnail and basic info.
 */
@Composable
fun WorldCard(
    worldItem: WorldItem,
    modifier: Modifier = Modifier,
    onViewDetails: () -> Unit
) {
    val world = worldItem.world
    val context = LocalContext.current
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                        text = "No Preview",
                        color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Title
            Text(
                text = world.displayName ?: "Untitled",
                style = SpatialTheme.typography.body1,
                fontWeight = FontWeight.Bold,
                color = LocalColorScheme.current.primaryAlphaBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Model
            world.model?.let {
                Text(
                    text = it,
                    style = SpatialTheme.typography.body2,
                    color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // View Details button
            Button(
                onClick = onViewDetails,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WorldLabsColors.Primary)
            ) {
                Text("View Details")
            }
        }
    }
}

/**
 * Row component for displaying label-value pairs in info cards.
 */
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = SpatialTheme.typography.body2,
            color = LocalColorScheme.current.primaryAlphaBackground.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = SpatialTheme.typography.body2,
            color = LocalColorScheme.current.primaryAlphaBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
        )
    }
}
