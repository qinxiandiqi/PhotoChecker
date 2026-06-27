# PhotoChecker

An EXIF viewer for Android — view the metadata hidden inside your photos, and strip the parts that put your privacy at risk.

![](./doc/playstore_head.png)

View the EXIF metadata hidden inside your photos — and strip the parts that put your privacy at risk.

Every photo you take carries hidden data: the camera and lens used, exposure settings, the exact date and time, and often a precise GPS location pinpointing where it was taken. PhotoChecker reveals all of it, and lets you remove it before you share.

🔍 **Read every detail**
Inspect the full EXIF metadata of any JPEG, PNG, or WebP image — camera make and model, lens, aperture, shutter speed, ISO, focal length, capture timestamp, software, and more.

⚠️ **Privacy risk at a glance**
Each photo is color-coded by privacy risk. A single glance tells you whether it contains high-risk GPS data, identifying device info, or is safe to share.

🗺️ **Locations, visualized**
If a photo has GPS coordinates, tap to open it in your map app and see exactly where it was captured.

🔎 **Spot tampering**
Consistency warnings flag suspicious signs — a capture time that disagrees with the file, mismatched dimensions, or an embedded thumbnail that may reveal the pre-edit original.

🧹 **Clean before you share**
Remove exactly what you want:
• Location data (GPS)
• Personal and device identifiers
• All metadata for a truly clean file
Then share the cleaned image to any app, or save it to your gallery.

**Private by design**
• 100% on-device — your photos never leave your phone
• No ads, no accounts, no network access
• Large-screen and foldable friendly
• Light and dark themes

Lightweight and built for one job: showing you what your photos know about you, and helping you take control.


## 🚀 Build & Run (Android)

### Prerequisites

- Android Studio (Koala or newer)
- Android SDK (compileSdk 37, minSdk 24)
- JDK 11+

### Build

```bash
cd android
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (requires signing config in key/)
./gradlew installDebug           # install on a connected device
```

The debug signing key is bundled in `android/app/debugkey.zip` — extract it before building.

## 📁 Project Structure

```
PhotoChecker/
├── android/            Android app (Kotlin + Jetpack Compose) — primary platform
│   └── app/src/main/java/cn/qinxiandiqi/photochecker/
│       ├── lib/exif/        custom EXIF parser (AOSP port, read-only)
│       ├── ui/theme/        design system (color scheme, spacing tokens)
│       └── feature/         feature modules
│           ├── home/        EXIF viewer & privacy cleaning (ui / services / model)
│           └── about/       about screen (open-source licenses)
├── web/                static privacy-policy page (Docker)
└── doc/                store assets & screenshots
```

The Android app follows **MVVM + single Activity + feature modules**: each feature splits into
`ui/` (Composables), a business layer (ViewModel + Services), and `model/` (data classes).

## 🌐 Web (privacy page)

A standalone static page served via Docker:

```bash
cd web
docker-compose up --build     # http://localhost:80
```

## 🔧 Development Workflow

1. **Make changes** in `android/`
2. **Build & install** with `./gradlew installDebug`
3. **Commit changes** using the conventional commit format

## 📝 Git Workflow

This project follows the **conventional commit** format: `type(scope): description`.

### Commit Types

- `feat`: 新功能 (New features)
- `fix`: 修复 bug (Bug fixes)
- `docs`: 文档更新 (Documentation updates)
- `style`: 代码格式调整 (Code formatting changes)
- `refactor`: 重构 (Code refactoring)
- `test`: 增加测试 (Adding tests)
- `build`: 构建相关变动 (Build related changes)
- `ci`: CI/CD 配置变动 (CI/CD configuration changes)
- `chore`: 其他修改 (Other changes)
- `revert`: 回滚 (Reverting changes)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Follow the commit message format
5. Submit a pull request

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
