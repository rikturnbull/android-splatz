# SplatZ

A Meta Quest VR application for viewing and interacting with 3D Gaussian Splats using the Meta Spatial SDK.

## Overview

This application rendere Gaussian Splat (.spz) files in a VR environment on Meta Quest devices. The app features integration with WorldLabs for AI-powered splat generation with voice input capabilities.

## Features

- **Gaussian Splat Rendering**: Load and display .spz splat files using the Meta Spatial SDK's SplatFeature
- **WorldLabs Integration**: Generate new splats via the WorldLabs API directly from the app
- **Voice Input**: Record audio for voice-based interactions (requires microphone permission)
- **Locomotion**: Explore splats using VR controllers

## Requirements

- Android Studio Hedgehog or later
- JDK 17
- Meta Quest 2, Quest 3, or Quest Pro
- Android SDK 34
- Meta Spatial SDK

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Connect your Meta Quest device via USB or enable wireless ADB
5. Build and run the app

### WorldLabs API Key

To use the WorldLabs splat generation feature, configure your API key in the app's Settings screen after launching.

## Project Structure

```
app/
├── src/main/
│   ├── java/uk/co/controlz/splatz/
│   │   ├── SplatzActivity.kt      # Main VR activity
│   │   ├── voice/                 # Voice input handling
│   │   └── worldlabs/             # WorldLabs API integration
│   └── res/                       # Android resources
├── scenes/                        # Scene configuration files
└── deleteme/                      # Sample assets (.spz files)
```

## Key Dependencies

- **Meta Spatial SDK**: Core VR rendering and interaction
  - `meta.spatial.sdk.base` - Core SDK functionality
  - `meta.spatial.sdk.splat` - Gaussian Splat rendering
  - `meta.spatial.sdk.vr` - VR features
  - `meta.spatial.sdk.toolkit` - UI and interaction utilities
  - `meta.spatial.sdk.compose` - Jetpack Compose integration
- **Jetpack Compose**: Declarative UI framework
- **AndroidX**: Core Android libraries

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install to connected device
./gradlew installDebug
```

## License

See LICENSE file for details.
