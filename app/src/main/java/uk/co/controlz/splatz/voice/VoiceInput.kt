/*
 * Voice Input using Vosk Speech-to-Text
 * 
 */

package uk.co.controlz.splatz.voice

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject

// Feature flag - set to false to disable voice input entirely
const val VOICE_INPUT_ENABLED = true

private const val TAG = "VoiceInput"

// CompositionLocals for Activity-based permission handling
// These allow Compose panels to request permissions from the Activity
val LocalHasAudioPermission = compositionLocalOf { mutableStateOf(false) }
val LocalPermissionRequester = compositionLocalOf<((Boolean) -> Unit) -> Unit> { {} }

/**
 * Voice input state for managing speech recognition lifecycle
 */
class VoiceInputState {
    var isModelLoaded by mutableStateOf(false)
    var isListening by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    
    private var model: Model? = null
    private var speechService: SpeechService? = null
    
    fun initializeModel(context: Context, onComplete: () -> Unit) {
        if (isModelLoaded || isLoading) return
        isLoading = true
        
        Thread {
            try {
                val modelPath = copyModelFromAssets(context)
                if (modelPath != null) {
                    model = Model(modelPath)
                    isModelLoaded = true
                    isLoading = false
                    onComplete()
                } else {
                    errorMessage = "Failed to copy voice model"
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing model", e)
                errorMessage = "Error initializing voice: ${e.message}"
                isLoading = false
            }
        }.start()
    }
    
    /**
     * Copy model from assets to internal storage (Vosk requires file path, not assets)
     */
    private fun copyModelFromAssets(context: Context): String? {
        val modelDir = java.io.File(context.filesDir, "vosk-model")
        
        // Check if already copied
        if (modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
            Log.d(TAG, "Model already exists at ${modelDir.absolutePath}")
            return modelDir.absolutePath
        }
        
        try {
            modelDir.mkdirs()
            copyAssetFolder(context.assets, "model-en-us", modelDir)
            Log.d(TAG, "Model copied to ${modelDir.absolutePath}")
            return modelDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model", e)
            return null
        }
    }
    
    private fun copyAssetFolder(assets: android.content.res.AssetManager, srcPath: String, destDir: java.io.File) {
        val files = assets.list(srcPath) ?: return
        
        if (files.isEmpty()) {
            // It's a file, copy it
            assets.open(srcPath).use { input ->
                java.io.File(destDir, srcPath.substringAfterLast("/")).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // It's a directory, recurse
            for (file in files) {
                val subPath = "$srcPath/$file"
                val subDestDir = if (srcPath == "model-en-us") destDir else java.io.File(destDir, srcPath.substringAfterLast("/"))
                subDestDir.mkdirs()
                copyAssetFolder(assets, subPath, subDestDir)
            }
        }
    }
    
    fun startListening(baseText: String, onResult: (String) -> Unit) {
        val currentModel = model ?: run {
            errorMessage = "Voice model not loaded"
            return
        }
        
        if (isListening) {
            stopListening()
            return
        }
        
        try {
            val recognizer = Recognizer(currentModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            
            // Accumulate all speech segments within this session
            val spokenSegments = mutableListOf<String>()
            
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    // Optional: show partial results as user speaks
                }
                
                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val text = json.optString("text", "")
                            Log.d(TAG, "onResult text: '$text'")
                            if (text.isNotBlank()) {
                                spokenSegments.add(text)
                                // Build full text: base + all spoken segments
                                val fullText = if (baseText.isBlank()) {
                                    spokenSegments.joinToString(" ")
                                } else {
                                    baseText + " " + spokenSegments.joinToString(" ")
                                }
                                onResult(fullText)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing result", e)
                        }
                    }
                }
                
                override fun onFinalResult(hypothesis: String?) {
                    // Final result when session ends
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val text = json.optString("text", "")
                            Log.d(TAG, "onFinalResult text: '$text'")
                            if (text.isNotBlank() && !spokenSegments.contains(text)) {
                                spokenSegments.add(text)
                                val fullText = if (baseText.isBlank()) {
                                    spokenSegments.joinToString(" ")
                                } else {
                                    baseText + " " + spokenSegments.joinToString(" ")
                                }
                                onResult(fullText)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing final result", e)
                        }
                    }
                    stopListening()
                }
                
                override fun onError(exception: Exception?) {
                    Log.e(TAG, "Recognition error", exception)
                    errorMessage = "Voice recognition error: ${exception?.message}"
                    stopListening()
                }
                
                override fun onTimeout() {
                    stopListening()
                }
            })
            
            isListening = true
            Log.d(TAG, "Voice recording started - isListening: $isListening")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech service", e)
            errorMessage = "Error starting voice: ${e.message}"
        }
    }
    
    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        isListening = false
        Log.d(TAG, "Voice recording stopped - isListening: $isListening")
    }
    
    fun cleanup() {
        stopListening()
        model?.close()
        model = null
        isModelLoaded = false
    }
}

/**
 * Composable microphone button for voice input
 * 
 * @param currentText The current text in the field (used as base when appending speech)
 * @param onTextReceived Callback with the FULL text (base + spoken) - use for replacement, not append
 * @param modifier Optional modifier
 */
@Composable
fun VoiceMicButton(
    currentText: String,
    onTextReceived: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Return nothing if voice input is disabled
    if (!VOICE_INPUT_ENABLED) return
    
    val context = LocalContext.current
    val voiceState = remember { VoiceInputState() }
    
    // Use Activity-based permission state from CompositionLocal
    val hasAudioPermissionState = LocalHasAudioPermission.current
    val hasPermission by hasAudioPermissionState
    val requestPermission = LocalPermissionRequester.current
    
    // Initialize model when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission && !voiceState.isModelLoaded && !voiceState.isLoading) {
            voiceState.initializeModel(context) {}
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            voiceState.cleanup()
        }
    }
    
    IconButton(
        onClick = {
            if (!hasPermission) {
                // Request permission through Activity
                requestPermission { granted ->
                    if (granted) {
                        voiceState.initializeModel(context) {
                            voiceState.startListening(currentText) { text ->
                                onTextReceived(text)
                            }
                        }
                    } else {
                        voiceState.errorMessage = "Microphone permission denied"
                        Log.w(TAG, "Microphone permission denied")
                    }
                }
            } else if (voiceState.isModelLoaded) {
                voiceState.startListening(currentText) { text ->
                    onTextReceived(text)
                }
            } else if (!voiceState.isLoading) {
                voiceState.initializeModel(context) {
                    voiceState.startListening(currentText) { text ->
                        onTextReceived(text)
                    }
                }
            }
        },
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = when {
                voiceState.isListening -> Color.Red
                voiceState.isModelLoaded -> Color.White
                else -> Color.Gray
            }
        )
    ) {
        when {
            voiceState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
            voiceState.isListening -> {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop recording",
                    tint = Color.Red
                )
            }
            !hasPermission -> {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = "Microphone permission required",
                    tint = Color.Gray
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Start voice input"
                )
            }
        }
    }
}
