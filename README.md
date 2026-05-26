# Snapdragon Accelerated llama.cpp Android GUI Wrapper

This project is a Jetpack Compose Android GUI for running a prebuilt `llama-server` executable from llama.cpp on Android devices. It does not compile llama.cpp source code inside this Gradle project and it does not call llama.cpp through JNI. Instead, the app packages prebuilt native artifacts, restores them into app-private storage at runtime, and launches `llama-server` as a child process.

The wrapper is designed around Snapdragon-oriented builds of llama.cpp, including CPU, OpenCL, and Hexagon/HTP acceleration libraries.

## What this app does

- Provides a Compose UI for selecting a model and configuring server options.
- Persists server configuration to app-private JSON storage.
- Packages prebuilt llama.cpp native libraries and the `llama-server` executable into the APK.
- Extracts or symlinks those artifacts into app-private runtime directories.
- Starts and stops `llama-server` with `ProcessBuilder`.
- Monitors server output to report whether the HTTP server is running.

## Project layout

```text
android-gui-wrapper/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/llamaserver/
│       │   ├── MainActivity.kt
│       │   ├── LlamaServerApplication.kt
│       │   ├── data/
│       │   ├── di/
│       │   ├── model/
│       │   ├── repository/
│       │   ├── service/
│       │   ├── ui/
│       │   ├── util/
│       │   └── viewmodel/
│       ├── jniLibs/arm64-v8a/
│       └── res/
├── build.gradle.kts
├── copy-native-libs.sh
├── Dockerfile
├── gradle.properties
├── gradle/
└── settings.gradle.kts
```

Important files:

- `copy-native-libs.sh` copies prebuilt llama.cpp artifacts into `app/src/main/jniLibs/arm64-v8a`.
- `app/src/main/java/com/example/llamaserver/util/BinaryExtractor.kt` prepares the packaged executable and libraries for runtime use.
- `app/src/main/java/com/example/llamaserver/service/ServerProcessManager.kt` builds the `llama-server` command and starts the process.
- `app/src/main/java/com/example/llamaserver/ui/launcher/AllConfigScreen.kt` contains the main configuration UI.
- `app/src/main/java/com/example/llamaserver/ui/runtime/RuntimeScreen.kt` contains the runtime status, start/stop controls, and logs.
- `app/src/main/java/com/example/llamaserver/model/ServerConfig.kt` defines the app's configuration contract with `llama-server`.

## Build prerequisites

Before building this Android app, the llama.cpp native artifacts must already exist under:

```text
../pkg-snapdragon/llama.cpp/
├── bin/llama-server
└── lib/*.so
```

The wrapper expects these artifacts to have been produced separately from the main llama.cpp build system. This Android project only packages and launches them.

After building or installing the native artifacts, run the copy script from this folder or from the llama.cpp root:

```bash
./android-gui-wrapper/copy-native-libs.sh
```

The script copies libraries into:

```text
app/src/main/jniLibs/arm64-v8a/
```

It also copies `bin/llama-server` as:

```text
app/src/main/jniLibs/arm64-v8a/libllama-server.so
```

This rename is intentional: Android automatically packages and extracts `.so` files from `jniLibs`, so the executable is stored with a library-style filename during packaging and restored to `llama-server` at runtime.

## Building the APK

The APK can be built inside the Android builder container with:

```bash
docker run --rm \
  -v /d/llama.cpp/android-gui-wrapper:/source \
  -w /source \
  llama-android-builder:latest \
  bash -c "rm -rf .gradle app/.gradle app/build && export ANDROID_HOME=/opt/android-sdk && gradle assembleDebug --no-daemon --parallel"
```

The debug APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The Gradle project is a single Android application module:

- root project: `Snapdragon Accelerated lcpp`
- module: `:app`
- namespace/application ID: `tridefender.llama.snapdragon`
- ABI: `arm64-v8a`
- compile SDK: 34
- min SDK: 24

## Release signing

Release builds require a signing keystore. The keystore and credentials are **not** included in the repository — you must generate your own.

### Setup

1. **Generate a keystore** (one-time):

   Linux/macOS:
   ```bash
   ./generate-keystore.sh
   ```

   Windows:
   ```cmd
   generate-keystore.bat
   ```

2. **Create your signing config**:

   ```bash
   cp keystore.properties.example keystore.properties
   ```

   Edit `keystore.properties` and fill in the password you chose during keystore generation.

3. **Build a signed release APK**:

   ```bash
   docker run --rm \
     -v /d/llama.cpp/android-gui-wrapper:/source \
     -w /source \
     llama-android-builder:latest \
     bash -c "rm -rf app/build && export ANDROID_HOME=/opt/android-sdk && gradle assembleRelease --no-daemon"
   ```

   The signed APK is produced at `app/build/outputs/apk/release/app-release.apk`.

> **Note:** `keystore.properties` and `*.keystore` files are gitignored. Debug builds work without signing config (they fall back to the default debug key).

## Runtime flow

At app startup, `MainActivity` opens the Compose UI:

```text
MainActivity
└── MainScreen
    ├── Config tab  -> AllConfigScreen
    └── Runtime tab -> RuntimeScreen
```

When the user starts the server:

```text
RuntimeScreen
└── RuntimeViewModel.startServer()
    └── ServerProcessManager.startServer(config)
        ├── BinaryExtractor.ensureAllAvailable(context)
        ├── build llama-server command line
        ├── configure process environment
        ├── ProcessBuilder(...).start()
        └── read stdout/stderr for server status
```

`BinaryExtractor` restores runtime files under app-private storage:

```text
filesDir/bin/llama-server
filesDir/lib/*.so
```

It first attempts to create symlinks from Android's extracted native library directory. If symlinking fails, it copies the files. The executable is marked with `chmod 755`.

`ServerProcessManager` configures library search paths before launching the process:

```text
LD_LIBRARY_PATH=<filesDir>:<filesDir>/lib:/system/lib64:/system/vendor/lib64:/vendor/lib64:/vendor/dsp/cdsp
ADSP_LIBRARY_PATH=<filesDir>/lib
GGML_HEXAGON_EXPERIMENTAL=1   # for HTP devices
```

## Configuration model

`ServerConfig` controls the generated `llama-server` command. It includes options for:

- model path
- context size
- batch size
- prediction tokens
- embedding mode and pooling type
- device type: CPU, OpenCL, or HTP0-HTP4
- KV cache types
- KV offload
- flash attention
- auto-fit settings
- server port and bind behavior
- API key
- timeout
- thread settings
- continuous batching

The active configuration is persisted as JSON at runtime by `ConfigRepositoryImpl`.

## Native process command

The generated command has this general shape:

```text
filesDir/bin/llama-server
  --model <model-path>
  --poll 1000
  --ctx-size <context-size>
  --batch-size <batch-size>
  --cache-type-k <type>
  --cache-type-v <type>
  [--device opencl|htp0|htp1|htp2|htp3|htp4]
  [--flash-attn on|off]
  [-ngl 99]
  [--kv-offload]
  [--cont-batching]
  [--fit on --fit-target <MiB> --fit-ctx <tokens>]
  [--port <port>]
  [--host 0.0.0.0]
  [--api-key <key>]
  [--timeout <seconds>]
  [--predict <tokens>]
```

The app treats stdout/stderr as the process status channel. It marks the server as running when output contains messages such as `HTTP server is listening` or `llama server listening`.

## Notes and known sharp edges

- `app/src/androidTest/java/tridefender/llama/snapdragon/util/BinaryExtractorTest.kt` appears to describe an older `BinaryExtractor` API. The current implementation is an `object` with `ensureAllAvailable`, `getBinaryPath`, and `getLibraryDir`.
- This wrapper only packages `arm64-v8a` artifacts. Other ABIs would need matching native builds and Gradle configuration changes.
- Model files are selected through Android's document picker. `UriUtils` attempts to resolve `content://` URIs to file paths and falls back to copying where possible.

## Development guide

Common places to edit:

- Add or change UI settings: `AllConfigScreen.kt`, `ModelConfigViewModel.kt`, and `ServerConfig.kt`
- Change generated llama-server flags: `ServerProcessManager.kt`
- Change binary/library preparation: `BinaryExtractor.kt` and `copy-native-libs.sh`
- Change runtime controls or logs: `RuntimeScreen.kt` and `RuntimeViewModel.kt`
- Change Gradle packaging or ABI behavior: `app/build.gradle.kts`

Keep in mind that the Android app is only a wrapper. Any llama.cpp feature used here must be supported by the prebuilt `llama-server` binary that is packaged into the APK.
