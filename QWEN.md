# PhotoChecker Project Context

## Project Overview

PhotoChecker is a cross-platform EXIF viewer application that displays metadata from photos. The project supports four platforms:
- **Android**: Native Kotlin/Compose implementation (primary platform)
- **Tauri**: Desktop application using React + Material UI + Tauri 2.0
- **HarmonyOS**: ArkTS implementation
- **Web**: Static privacy policy page with Docker deployment

The app allows users to select photos from their device and view detailed EXIF metadata including camera settings, capture date/time, location, and more.

## Architecture

### Android Platform (Primary)
- **Framework**: Android with Jetpack Compose UI toolkit
- **Architecture**: MVVM with ViewModel pattern
- **Language**: Kotlin
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 36

#### Key Components:
- `MainActivity`: Main entry point with navigation between Home and About screens
- `HomeViewModel`: Manages photo selection and EXIF parsing state
- `PhotoInfo`: Data class for photo metadata extraction
- `ExifInterface`: Custom EXIF parsing library (extends Android's native EXIFInterface functionality)
- Feature modules: `home` and `about` screens

#### Data Flow:
1. User selects photo via launcher
2. `HomeViewModel` processes Uri and calls `PhotoInfo.parseExif()`
3. `PhotoInfo` uses custom `ExifInterface` to extract metadata
4. Results displayed through `HomeUIState` sealed class (Empty, Loading, Success, Error)

### Tauri Desktop Platform
- **Framework**: Tauri 2.0 with React 19 + TypeScript + Vite
- **UI**: Material UI 5 (MUI) with Material Design 3
- **Architecture**: Single Page Application with React Router
- **Language**: TypeScript
- **EXIF Parsing**: Uses `exifr` library
- **Platforms**: Windows, macOS, Linux desktop

#### Key Components:
- `App.tsx`: Main application component with routing
- `Home.tsx`: Main photo selection and display UI
- `About.tsx`: About information screen
- `exifParser.ts`: EXIF parsing utilities using `exifr` library
- Tauri backend: Rust-based desktop integration

#### Data Flow:
1. User selects photo via file dialog or drag & drop
2. `parseExif` function uses `exifr` library to extract metadata
3. Results displayed through `HomeUIState` union type (Empty, Loading, Success, Error)

### Multi-Platform Structure
```
PhotoChecker/
├── android/          # Native Android implementation
├── tauri/            # Tauri desktop implementation
├── harmony/          # HarmonyOS implementation
├── web/              # Static privacy page with Docker
└── doc/              # Documentation and assets
```

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

### Tauri Desktop
```bash
# Navigate to Tauri directory
cd tauri

# Install dependencies
pnpm install

# Start development server
pnpm tauri:dev

# Build desktop application
pnpm tauri:build

# Build for specific platforms
pnpm tauri build --target x86_64-apple-darwin     # macOS
pnpm tauri build --target x86_64-pc-windows-gnu   # Windows
pnpm tauri build --target x86_64-unknown-linux-gnu # Linux
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
- Android SDK (API 24-36 configured)
- Gradle wrapper included in project
- Debug signing key: Extract `debugkey.zip` in `android/app/`

### Tauri Desktop Requirements
- Node.js >= 18
- pnpm >= 8
- Rust >= 1.70
- Platform-specific dependencies:
  - Windows: WebView2
  - macOS: Xcode command line tools
  - Linux: WebKit2GTK development headers

### Key Dependencies
- **Android**: 
  - Jetpack Compose (Material3)
  - AndroidX Lifecycle (ViewModel, Runtime)
  - Coil (image loading)
  - AboutLibraries (for OSS attributions)
  - Custom EXIF Interface library

- **Tauri Desktop**:
  - React 19 + TypeScript + Vite
  - Material UI 5 (MUI)
  - React Router DOM
  - Tauri 2.0 API plugins (dialog, fs, opener, os)
  - exifr library for EXIF parsing

- **HarmonyOS**: ArkTS, Hypium testing framework
- **Web**: Nginx for static serving

## Code Structure

### Android Source Tree
```
android/app/src/main/java/cn/qinxiandiqi/
├── photochecker/
│   ├── App.kt                 # Main application composable
│   ├── MainActivity.kt        # Entry point activity
│   ├── feature/
│   │   ├── home/              # Home screen components
│   │   │   ├── Home.kt        # Home screen UI
│   │   │   ├── HomeUIState.kt # UI state management
│   │   │   ├── HomeViewModel.kt # ViewModel for home screen
│   │   │   └── PhotoInfo.kt   # Photo metadata handler
│   │   └── about/             # About screen components
│   │       ├── About.kt       # About screen UI
│   │       └── LinkContract.kt # Link handling
│   └── ui/
│       └── theme/             # Material theme definitions
└── lib/
    └── exif/                  # Custom EXIF parsing library
```

### Tauri Source Tree
```
tauri/
├── src/                       # React source code
│   ├── components/            # Reusable UI components
│   ├── pages/                 # Page components (Home, About)
│   │   ├── Home.tsx           # Main photo selection UI
│   │   └── About.tsx          # About information screen
│   ├── hooks/                 # Custom React hooks
│   │   └── usePhotoSelector.ts # Photo selection logic
│   ├── utils/                 # Utility functions
│   │   └── exifParser.ts      # EXIF parsing utilities
│   ├── types/                 # TypeScript type definitions
│   │   └── photo.ts           # Photo info and state types
│   ├── theme.tsx              # Material UI theme configuration
│   ├── App.tsx                # Main application component
│   └── main.tsx               # Application entry point
├── src-tauri/                 # Tauri backend (Rust)
│   ├── src/                   # Rust source code
│   ├── tauri.conf.json        # Tauri configuration
│   └── Cargo.toml             # Rust dependencies
├── public/                    # Static assets
├── package.json               # Project dependencies and scripts
├── vite.config.ts             # Vite build configuration
└── tsconfig.json              # TypeScript configuration
```

### Custom EXIF Library
The project includes a custom EXIF parser in `android/app/src/main/java/cn/qinxiandiqi/lib/exif/` that extends Android's native EXIFInterface functionality. This is the core component for photo metadata extraction.

## Testing

### Android Tests
- Unit tests: `android/app/src/test/`
- Instrumented tests: `android/app/src/androidTest/`
- Uses standard JUnit and AndroidX testing frameworks

### Tauri Tests
- Frontend tests: Standard React testing with Jest/Vitest
- Tauri backend tests: Rust tests in `src-tauri/tests/`

### HarmonyOS Tests  
- Located in `harmony/entry/src/test/` and `harmony/entry/src/ohosTest/`
- Uses Hypium framework

## Project Configuration

### Version Information
- Android: Version 1.0.1 (versionCode: 4), targeting API 36
- Tauri Desktop: Version 1.0.0
- Supports Android 7.0+ (minSdk 24)

### Code Style
- **Android**: 
  - Kotlin: Uses Compose Material3 design system
  - Follows Android Jetpack best practices
  - Uses ViewModels for state management
  - Coroutines for asynchronous operations
  
- **Tauri Desktop**:
  - TypeScript: Uses React Hooks and Functional Components
  - Material UI 5 with Material Design 3
  - React Router for navigation
  - Responsive design principles

## Additional Information

### Privacy Policy
The web directory contains a static privacy policy page that explains:
- No data is collected by the app
- Disclaimer for data loss or bugs
- Contact information

### Documentation
The `doc/` directory contains screenshots and other documentation assets.

### Tauri Desktop Features
- **Cross-platform**: Builds for Windows, macOS, and Linux
- **Lightweight**: Uses system WebView for UI rendering
- **Secure**: Sandboxed execution with controlled system access
- **Native Performance**: Rust backend for optimal performance
- **File System Access**: Direct access to local files via Tauri APIs
- **Window Management**: Configurable window size and properties