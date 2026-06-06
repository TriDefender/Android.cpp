# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Overview

This is a **Jetpack Compose Android GUI wrapper** for llama.cpp. The app does NOT compile llama.cpp source code - it only packages and launches prebuilt native binaries (specifically `llama-server` and supporting libraries) on Android devices. The wrapper is designed for Snapdragon-oriented builds with CPU, OpenCL, and Hexagon/HTP acceleration support.

**Architecture principle**: The Android app is a thin UI layer that manages native process lifecycle via `ProcessBuilder`. All actual inference happens in the prebuilt `llama-server` executable.

## Key Architecture Components

- **Native Integration**: Packages prebuilt binaries from `../pkg-snapdragon/llama.cpp/` into `app/src/main/jniLibs/arm64-v8a/`, then extracts/symlinks them to app-private storage at runtime
- **GUI Layer**: Jetpack Compose with Material 3 and Navigation Component
- **Dependency Injection**: Hilt (`LlamaServerApplication` with `@HiltAndroidApp`)
- **Persistence**: DataStore for configuration storage, JSON serialization via `ConfigRepositoryImpl`
- **Process Management**: `ServerProcessManager` launches `llama-server` as a child process and monitors stdout/stderr for status

## Critical Integration Points

### Binary Preparation (`util/BinaryExtractor.kt`)
- Packages `libllama-server.so` (renamed from `bin/llama-server`) and libraries in `jniLibs/arm64-v8a/`
- At runtime, symlinks (or copies if symlinking fails) binaries to `filesDir/bin/` and `filesDir/lib/`
- Marks executable with `chmod 755`

### Server Process Launch (`service/ServerProcessManager.kt`)
- Builds `llama-server` command line with flags from `ServerConfig`
- Configures environment variables for library loading:
  - `LD_LIBRARY_PATH`: includes `filesDir`, `filesDir/lib`, and system paths
  - `ADSP_LIBRARY_PATH`: `<filesDir>/lib`
  - `GGML_HEXAGON_EXPERIMENTAL=1` (for HTP devices)
- Monitors stdout/stderr for `HTTP server is listening` or `llama server listening` to mark server as running
- **Real-time logging**: Uses `realtimeOutput` StateFlow to emit each line as read, parsed by `RuntimeViewModel` for display

### Configuration Model (`model/ServerConfig.kt`)
Comprehensive data class that maps to `llama-server` CLI flags:
- Model selection, embedding mode, context size, batch size
- Device types: `cpu`, `opencl`, `htp0`-`htp4`
- KV cache types, KV offload, flash attention, auto-fit settings
- HTTP server config (port, bind, API key), timeout, threads, continuous batching
- `extraParams`: String for passing additional command-line arguments

## Build Commands

### Prerequisites
Native artifacts must exist at:
```text
../pkg-snapdragon/llama.cpp/
├── bin/llama-server
└── lib/*.so
```

### Development Workflow

1. **Copy native libraries**:
   ```bash
   ./copy-native-libs.sh
   ```
   Copies artifacts to `app/src/main/jniLibs/arm64-v8a/` and renames `llama-server` to `libllama-server.so`

2. **Build with Docker** (recommended):
   ```bash
   docker run --rm \
     -v /d/llama.cpp/android-gui-wrapper:/source \
     -w /source \
     llama-android-builder:latest \
     bash -c "rm -rf app/build && export ANDROID_HOME=/opt/android-sdk && gradle assembleDebug --no-daemon --parallel"
   ```

3. **Build release APK** (signed):
   ```bash
   docker run --rm \
     -v /d/llama.cpp/android-gui-wrapper:/source \
     -w /source \
     llama-android-builder:latest \
     bash -c "rm -rf app/build && export ANDROID_HOME=/opt/android-sdk && gradle assembleRelease --no-daemon"
   ```

4. **Install on device**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   # or for release
   adb install app/build/outputs/apk/release/app-release.apk
   ```

### Output
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### Signing
Release builds require a keystore. See `keystore.properties.example` and `README.md` (Release signing section) for setup instructions. Signing config is optional — debug builds work without it.

## Runtime Flow

```
MainActivity
└── MainScreen
    ├── Config tab  -> AllConfigScreen (ui/launcher/)
    └── Runtime tab -> RuntimeScreen (ui/runtime/)
```

Server startup sequence:
```
RuntimeScreen
└── RuntimeViewModel.startServer()
    └── ServerProcessManager.startServer(config)
        ├── BinaryExtractor.ensureAllAvailable(context)
        ├── build llama-server command line
        ├── configure LD_LIBRARY_PATH and other env vars
        ├── ProcessBuilder(...).start()
        └── read stdout/stderr for server status (real-time via realtimeOutput)
```

## Common Modification Points

- **Add UI settings**: Edit `AllConfigScreen.kt`, `ModelConfigViewModel.kt`, and `ServerConfig.kt`
- **Change llama-server flags**: Modify `ServerProcessManager.kt`
- **Modify binary/library preparation**: Update `BinaryExtractor.kt` and `copy-native-libs.sh`
- **Change runtime controls or logs**: Edit `RuntimeScreen.kt` and `RuntimeViewModel.kt`
- **Adjust Gradle configuration**: Modify `app/build.gradle.kts`

## Important Constraints

- **Only supports `arm64-v8a` ABI** - other architectures require native builds and Gradle changes
- **Model files** use Android's document picker URI system (`UriUtils` resolves `content://` URIs)
- **Binary compatibility**: The Android app can only use features supported by the prebuilt `llama-server` binary
- **Package name mismatch**: Code package is `tridefender.llama.snapdragon`, but namespace/applicationId is `tridefender.llama.snapdragon`. Always use full class names in `AndroidManifest.xml` (e.g., `android:name="tridefender.llama.snapdragon.LlamaServerApplication"` instead of `android:name=".LlamaServerApplication"`)
- **Resource imports**: Kotlin files importing R must use `import tridefender.llama.snapdragon.R`

## Multi-User/Clone App Considerations

On some devices (e.g., OnePlus with MultiApp feature), the app may be installed in multiple user profiles. To fully uninstall:
```bash
adb uninstall --user 0 tridefender.llama.snapdragon
adb uninstall --user 999 tridefender.llama.snapdragon  # MultiApp profile
# or
adb shell pm uninstall --user all tridefender.llama.snapdragon
```

## Reference

- Parent repo: [llama.cpp](../)
- Server docs: [tools/server/README.md](../tools/server/README.md)
- Build docs: [docs/build.md](../docs/build.md)