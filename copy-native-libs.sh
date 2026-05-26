#!/bin/bash
# Copy native libraries and llama-server binary from pkg-snapdragon to jniLibs
# Run from llama.cpp root directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LLAMA_CPP_ROOT="$(dirname "$SCRIPT_DIR")"
PKG_DIR="$LLAMA_CPP_ROOT/pkg-snapdragon/llama.cpp"
JNI_LIBS_DIR="$SCRIPT_DIR/app/src/main/jniLibs/arm64-v8a"

# Check pkg-snapdragon exists
if [ ! -d "$PKG_DIR" ]; then
    echo "Error: pkg-snapdragon not found at $PKG_DIR"
    echo "Please build llama.cpp for Snapdragon first:"
    echo "  cmake -B build-snapdragon -DCMAKE_TOOLCHAIN_FILE=cmake/arm64-android-clang.cmake ..."
    echo "  cmake --build build-snapdragon --config Release"
    echo "  cmake --install build-snapdragon --prefix pkg-snapdragon"
    exit 1
fi

# Create jniLibs directory
mkdir -p "$JNI_LIBS_DIR"

echo "Copying native libraries from:"
echo "  $PKG_DIR/lib"
echo "To:"
echo "  $JNI_LIBS_DIR"
echo ""

# Copy .so files
SO_COUNT=0
for lib in "$PKG_DIR/lib"/*.so; do
    if [ -f "$lib" ]; then
        libname=$(basename "$lib")
        cp -f "$lib" "$JNI_LIBS_DIR/"
        echo "  [SO] $libname"
        ((SO_COUNT++))
    fi
done

# Copy llama-server as libllama-server.so (Android only extracts .so files)
if [ -f "$PKG_DIR/bin/llama-server" ]; then
    cp -f "$PKG_DIR/bin/llama-server" "$JNI_LIBS_DIR/libllama-server.so"
    echo "  [BIN] llama-server -> libllama-server.so"
else
    echo "Warning: llama-server not found at $PKG_DIR/bin/llama-server"
fi

echo ""
echo "Done! Copied $SO_COUNT .so files + llama-server"
echo ""
echo "Files in jniLibs/arm64-v8a:"
ls -lh "$JNI_LIBS_DIR" 2>/dev/null || echo "  (directory is empty)"
