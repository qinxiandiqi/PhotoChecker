# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PhotoChecker is a cross-platform EXIF viewer application that displays metadata from photos. The project supports Android (native Kotlin/Compose), HarmonyOS (ArkTS), and web platforms.

## Architecture

### Android Platform (Primary)

- **Framework**: Android with Jetpack Compose UI toolkit
- **Architecture**: MVVM with ViewModel pattern
- **Key Components**:
  - `MainActivity`: Main entry point with navigation between Home and About screens
  - `HomeViewModel`: Manages photo selection and EXIF parsing state
  - `PhotoInfo`: Data class for photo metadata extraction
  - `ExifInterface`: Custom EXIF parsing library (in `lib/exif/`)
  - Feature modules: `home` and `about` screens

### Multi-Platform Structure

The project supports three platforms:

- **Android**: `/android/` - Native Kotlin/Compose implementation
- **HarmonyOS**: `/harmony/` - ArkTS implementation
- **Web**: `/web/` - Static privacy page with Docker deployment

### Data Flow

1. User selects photo via launcher
2. `HomeViewModel` processes Uri and calls `PhotoInfo.parseExif()`
3. `PhotoInfo` uses custom `ExifInterface` to extract metadata
4. Results displayed through `HomeUIState` sealed class (Empty, Loading, Success, Error)

## Build and Development Commands

### Android

```bash
# Navigate to Android directory
cd android

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Install on device
./gradlew installDebug
```

### HarmonyOS

```bash
# Navigate to HarmonyOS directory
cd harmony

# Build HarmonyOS application
hvigorw assembleHap

# Run HarmonyOS tests
hvigorw build
```

### Web

```bash
# Navigate to web directory
cd web

# Build and run with Docker
docker-compose up --build

# Access privacy page at localhost:80
```

## Development Setup

### Android Requirements

- Android Studio installed
- Android SDK (API 24-34 configured)
- Gradle wrapper included in project
- Debug signing key: Extract `debugkey.zip` in `android/app/`

### Key Dependencies

- **Android**: Jetpack Compose, Material3, EXIF Interface, Coil (image loading)
- **HarmonyOS**: ArkTS, Hypium testing framework
- **Web**: Nginx for static serving

### Custom EXIF Library

The project includes a custom EXIF parser in `android/app/src/main/java/cn/qinxiandiqi/lib/exif/` that extends Android's native EXIFInterface functionality. This is the core component for photo metadata extraction.

## Testing

### Android Tests

- Unit tests: `android/app/src/test/`
- Instrumented tests: `android/app/src/androidTest/`
- Use standard JUnit and AndroidX testing frameworks

### HarmonyOS Tests

- Located in `harmony/entry/src/test/` and `harmony/entry/src/ohosTest/`
- Uses Hypium framework

## Project Configuration

### Version Information

- Android: Version 1.0.0 (versionCode: 3), targeting API 34
- Supports Android 7.0+ (minSdk 24)

### Code Style

- Kotlin: Uses Compose Material3 design system
- Follows Android Jetpack best practices
- Uses ViewModels for state management
- Coroutines for asynchronous operations
