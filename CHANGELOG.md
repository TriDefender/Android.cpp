# Changelog

## v1.1.c21b45 - 2026-05-28

- Added a dedicated Kernels screen for GitHub release discovery, compressed kernel downloads, local archive import, activation, and deletion.
- Added kernel version metadata, persistence, bundled fallback registration, package validation, and missing-library warnings.
- Updated downloaded kernel extraction to accept both `llama-server` and `libllama-server.so`, storing executables as `bin/libllama-server.so`.
- Fixed Android `error=13` launch failures by launching downloaded `.so` executables through `/system/bin/linker64` and launching bundled/reverted executables from Android's native library directory.
- Reworked kernel activation to clear stale active binaries/libraries before switching and to re-apply executable permissions whenever a kernel is activated or prepared.
- Fixed Hexagon startup by using semicolon-separated `ADSP_LIBRARY_PATH` entries and including app, native library, RFSA, and DSP search paths.
- Added a one-shot CPU fallback for Hexagon session-open failures so the server can recover when CDSP/HTP access fails.
- Updated runtime detection for newer llama-server logs that report `server is listening on ...`.
- Updated documentation for the new kernel packaging, activation, launch, and DSP library-path behavior.

### Verification

- Built signed release APK with `llama-android-builder:latest`.
- Installed and launched on device `3B15AV008YH00000`.
- Verified downloaded and bundled kernel launch paths no longer fail with `error=13`.
- Verified HTP0 startup after the ADSP path fix: model loaded and `llama-server` listened on `http://127.0.0.1:8080`.
