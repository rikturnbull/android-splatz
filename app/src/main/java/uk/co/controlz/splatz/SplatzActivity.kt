package uk.co.controlz.splatz

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import uk.co.controlz.splatz.voice.LocalPermissionRequester
import uk.co.controlz.splatz.voice.LocalHasAudioPermission
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.SpatialSDKExperimentalAPI
import com.meta.spatial.core.SpatialSDKInternalTestingAPI
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.NetworkedAssetLoader
import uk.co.controlz.splatz.R
import uk.co.controlz.splatz.worldlabs.WORLDLABS_PANEL_HEIGHT
import uk.co.controlz.splatz.worldlabs.WORLDLABS_PANEL_WIDTH
import uk.co.controlz.splatz.worldlabs.WorldLabsGeneratorPanel
import com.meta.spatial.splat.SpatialSDKExperimentalSplatAPI
import com.meta.spatial.splat.Splat
import com.meta.spatial.splat.SplatFeature
import com.meta.spatial.splat.SplatLoadEventArgs
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.AvatarAttachment
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.GrabbableType
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.SupportsLocomotion
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.VRFeature
import java.io.File

@OptIn(SpatialSDKExperimentalSplatAPI::class)
class SplatzActivity : ActivityCompat.OnRequestPermissionsResultCallback, AppSystemActivity() {

  companion object {
    private const val TAG = "SplatzActivity"
    private const val PERMISSIONS_REQUEST_CODE = 1000
    private val PERMISSIONS_REQUIRED = arrayOf(
      "android.permission.RECORD_AUDIO"
    )
  }

  // Permission handling
  private var permissionsResultCallback: ((Boolean) -> Unit)? = null
  private var hasAudioPermission = mutableStateOf(false)

  private lateinit var worldLabsPanelEntity: Entity
  // Entity that holds the Splat component for rendering Gaussian Splats
  private lateinit var splatEntity: Entity
  // Tracks whether we're in splat viewing mode (a splat has been loaded)
  private var isSplatMode = false

  // Initial panel position - used to reset panel when exiting splat mode
  private val initialPanelPosition = Vector3(-0.5f, 1.5f, 1f)
  private val initialPanelRotation = Quaternion(0f, 0f, 0f)

  // WorldLabs API key - stored in SharedPreferences
  private var worldLabsApiKey = mutableStateOf("")
  // Tracks when a splat has finished loading (for UI spinner reset)
  private var splatLoadComplete = mutableStateOf(0)
  private val PREFS_NAME = "worldlabs_prefs"
  private val API_KEY_PREF = "api_key"

  private val headQuery =
      Query.where { has(AvatarAttachment.id) }
          .filter { isLocal() and by(AvatarAttachment.typeData).isEqualTo("head") }

          // Register all features your app needs. Features add capabilities to the Spatial SDK.
  override fun registerFeatures(): List<SpatialFeature> {
    return listOf(
        VRFeature(this), // Enable VR rendering
        // SplatFeature: REQUIRED for rendering Gaussian Splats
        // This feature handles loading, decoding, and rendering .spz Splat files
        // Must be registered before creating any entities with Splat components
        SplatFeature(this.spatialContext, systemManager),
        ComposeFeature(), // Enable Compose UI panels
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NetworkedAssetLoader.init(
        File(applicationContext.getCacheDir().canonicalPath),
        OkHttpAssetFetcher(),
    )
    
    // Check audio permission on startup
    hasAudioPermission.value = hasPermissions()

    // Load WorldLabs API key from preferences (no default - Settings screen required)
    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    worldLabsApiKey.value = prefs.getString(API_KEY_PREF, null) ?: ""
    initializeSplat()
  }

  // @OptIn(SpatialSDKInternalTestingAPI::class)
  override fun onSceneReady() {
    super.onSceneReady()
    // registerTestingIntentReceivers()
    scene.setLightingEnvironment(
        ambientColor = Vector3(0f),
        sunColor = Vector3(7.0f, 7.0f, 7.0f),
        sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
        environmentIntensity = 0.3f,
    )
    scene.setViewOrigin(0.0f, 0.0f, 0f)
    scene.enablePassthrough(true)

    // Create WorldLabs generator panel (positioned to the left of the control panel)
    worldLabsPanelEntity =
        Entity.Companion.createPanelEntity(
            R.id.worldlabs_generator_panel,
            Transform(Pose(initialPanelPosition, initialPanelRotation)),
            Grabbable(type = GrabbableType.PIVOT_Y, minHeight = 0.75f, maxHeight = 2.5f),
        )
    systemManager.registerSystem(ControllerListenerSystem())
  }

  /**
   * Creates an entity with a Splat component.
   *
   * Gaussian Splats are a 3D representation technique that uses millions of small 3D Gaussians to
   * represent real-world captured scenes with photorealistic quality.
   *
   * To create a Splat entity, you need:
   * 1. Splat component: Points to the .spz or .ply file containing the Gaussian Splat data
   * 2. Transform component: Positions and rotates the Splat in 3D space
   * 3. Scale component: Adjusts the size of the Splat
   *
   * Splat files (.ply or .spz) can be loaded from:
   * - Application assets: "apk://filename.spz"
   * - Network URLs: "https://example.com/splat.spz"
   * - Local files: "file:///path/to/splat.spz"
   */
  private fun initializeSplat() {
    splatEntity =
        Entity.Companion.create(
            listOf(
                Transform(
                    Pose(
                        Vector3(0.0f, 1.25f, 0.0f),
                        // rotate Quaternion 180 on y
                        Quaternion(180f, 180f, 0f)
                    )
                ),
                Scale(Vector3(1f)),
                SupportsLocomotion(),
            )
        )
    splatEntity.registerEventListener<SplatLoadEventArgs>(SplatLoadEventArgs.Companion.EVENT_NAME) { _, _ ->
      Log.d("SplatManager", "Splat loaded EVENT!")
      onSplatLoaded()
    }
  }

  private fun onSplatLoaded() {
    setSplatVisibility(true)
    // setPanelVisibility(false)
    isSplatMode = true
    // Increment to signal loading complete to UI
    splatLoadComplete.value++
  }

  // Permission handling methods
  private fun hasPermissions() = PERMISSIONS_REQUIRED.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
  }

  // Save API key to SharedPreferences and update state
  private fun saveApiKey(newKey: String) {
    val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    prefs.edit().putString(API_KEY_PREF, newKey).apply()
    worldLabsApiKey.value = newKey
  }

  private fun requestPermissions(callback: (Boolean) -> Unit) {
    permissionsResultCallback = callback
    ActivityCompat.requestPermissions(this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    
    when (requestCode) {
      PERMISSIONS_REQUEST_CODE -> {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.d(TAG, "Audio permission granted")
          hasAudioPermission.value = true
          permissionsResultCallback?.invoke(true)
        } else {
          Log.w(TAG, "Audio permission denied")
          hasAudioPermission.value = false
          permissionsResultCallback?.invoke(false)
        }
        permissionsResultCallback = null
      }
    }
  }

  /**
   * Controls the visibility of the WorldLabs panel.
   *
   * @param isVisible true to show the panel, false to hide it
   */
  private fun setPanelVisibility(isVisible: Boolean) {
    worldLabsPanelEntity.setComponent(Visible(isVisible))
  }

  /**
   * Resets the panel to its initial starting position.
   * Called when exiting splat mode to ensure consistent panel placement.
   */
  private fun resetPanelPosition() {
    worldLabsPanelEntity.setComponent(Transform(Pose(initialPanelPosition, initialPanelRotation)))
  }

  /**
   * Loads a new Splat asset into the scene.
   *
   * This function demonstrates how to dynamically change which Splat is displayed. You can call
   * this function to switch between different Splat assets at runtime.
   *
   * @param newSplatPath Path to the .spz Splat file (e.g., "apk://MySplat.spz" or a URL)
   */
  fun loadSplat(newSplatPath: String) {

    if (splatEntity.hasComponent<Splat>()) {
      // Entity exists with a Splat component
      val splatComponent = splatEntity.getComponent<Splat>()

      if (splatComponent.path.toString() == newSplatPath) {
        // Already have this Splat loaded, just show it without reloading
        // Directly trigger the loaded callback since no load event will fire
        onSplatLoaded()
      } else {
        // Replace the existing Splat component with a new one pointing to a different file
        // setComponent() automatically unloads the old Splat from memory and loads the new one
        splatEntity.setComponent(Splat(newSplatPath.toUri()))
        setSplatVisibility(false)
      }
    } else {
      // No Splat Component exists yet, create one
      splatEntity.setComponent(Splat(newSplatPath.toUri()))
    }
  }

  /**
   * Controls the visibility of the Splat in the scene.
   *
   * Splats respect the Visible component like other rendered entities. Setting Visible(false) hides
   * the Splat without unloading it from memory, allowing for fast show/hide toggling.
   *
   * @param isSplatVisible true to show the Splat, false to hide it
   */
  fun setSplatVisibility(isSplatVisible: Boolean) {

    // Update the Visible Component on the Entity with a Splat Component
    splatEntity.setComponent(Visible(isSplatVisible))
  }

  /**
   * System that listens for controller button presses and performs actions.
   *
   * This demonstrates how to:
   * - Query for controller entities in the scene
   * - Filter for local and active controllers
   * - Detect button press events (ButtonA)
   * - Access head tracking data to reposition UI panels
   *
   * Button mappings:
   * - A Button: Exits splat mode and shows the panel
   *
   * This is a useful starting point for implementing controller-based interactions in your Spatial
   * SDK application.
   */
  
  inner class ControllerListenerSystem : SystemBase() {
    override fun execute() {
      // Query for all entities with Controller component and filter for local controllers
      val controllers = Query.Companion.where { has(Controller.Companion.id) }.eval().filter { it.isLocal() }

      for (controllerEntity in controllers) {
        val controller = controllerEntity.getComponent<Controller>()
        // Skip inactive controllers
        if (!controller.isActive) continue

        val attachment = controllerEntity.tryGetComponent<AvatarAttachment>()

        // Handle right controller
        if (attachment?.type == "right_controller") {
          // Check if Button A was just pressed (button state changed and is now pressed)
          // In splat mode, this toggles the panel visibility
          if (
              (controller.changedButtons and ButtonBits.ButtonA) != 0 &&
                  (controller.buttonState and ButtonBits.ButtonA) != 0
          ) {
            if (isSplatMode) {
              setSplatVisibility(false)
              
              // Position panel in front of user's current location
              positionPanelAtUser(1.5f)
              setPanelVisibility(true)
              
              isSplatMode = false
            }
          }
        }
      }
    }
  }
  

  /**
   * Positions the panel a specified distance in front of the user's current head position.
   *
   * This function:
   * 1. Queries for the user's head entity using AvatarAttachment
   * 2. Gets the head's transform to determine forward direction
   * 3. Projects the forward vector onto the horizontal plane (y = 0)
   * 4. Positions the panel a specified distance in front of the head
   * 5. Rotates the panel to face the user
   */
  private fun positionPanelAtUser(distance: Float) {
    // Find the user's head entity
    val head = headQuery.eval().firstOrNull()

    if (head != null) {
      // Get the head's current pose (position and rotation)
      val headPose = head.getComponent<Transform>().transform
      // Get the forward direction vector from the head pose
      val forward = headPose.forward()
      // Flatten to horizontal plane by zeroing out the y component
      forward.y = 0f
      val forwardNormalized = forward.normalize()
      // Calculate new position 2 meters in front of the head
      var newPosition = headPose.t + (forwardNormalized * distance)
      newPosition.y = 1.5f // Set y position to panel height
      // Create rotation to make panel face the user
      val lookRotation = Quaternion.lookRotation(forwardNormalized)
      // Update the panel's transform
      worldLabsPanelEntity.setComponent(Transform(Pose(newPosition, lookRotation)))
    }
  }

  @OptIn(SpatialSDKExperimentalAPI::class)
  override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        createSimpleComposePanel(
            R.id.worldlabs_generator_panel,
            WORLDLABS_PANEL_WIDTH,
            WORLDLABS_PANEL_HEIGHT,
        ) {
          // WorldLabs generator panel for creating new splats
            WorldLabsGeneratorPanel(
                apiKey = worldLabsApiKey.value,
                splatLoadComplete = splatLoadComplete.value,
                onLoadSplat = ::loadSplat,
                onApiKeySaved = ::saveApiKey
            )
        },
    )
  }

  private fun createSimpleComposePanel(
      panelId: Int,
      width: Float,
      height: Float,
      content: @Composable () -> Unit,
  ): ComposeViewPanelRegistration {
    return ComposeViewPanelRegistration(
        panelId,
        composeViewCreator = { _, ctx -> ComposeView(ctx).apply { 
          setContent { 
            // Provide permission state and request callback to all child composables
            CompositionLocalProvider(
              LocalHasAudioPermission provides hasAudioPermission,
              LocalPermissionRequester provides ::requestPermissions
            ) {
              content() 
            }
          } 
        } },
        settingsCreator = {
            UIPanelSettings(
                shape = QuadShapeOptions(width = width, height = height),
                style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
                display = DpPerMeterDisplayOptions(),
            )
        },
    )
  }
}