#!/usr/bin/env bash
# Builds libncnn_jni.so (ncnn core static + this project's JNI shim) for the
# Android ABIs we ship (arm64-v8a for devices, x86_64 for the emulator).
#
# Prerequisites:
#   - ncnn source cloned at $NCNN_SRC (provides src/c_api.h + the JNI bridge).
#   - ncnn core built as a STATIC lib (libncnn.a) for each ABI under $BUILD/<abi>.
#     Configure example (NDK 27):
#       cmake -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
#             -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
#             -DNCNN_BUILD_TOOLS=OFF -DNCNN_BUILD_EXAMPLES=OFF -DNCNN_VULKAN=OFF \
#             -DNCNN_STRING=ON -DNCNN_STDIO=ON -DNCNN_OPENMP=ON \
#             -S $NCNN_SRC -B $BUILD/arm64-v8a && cmake --build $BUILD/arm64-v8a -j
#
# Output (picked up by AGP from androidMain/jniLibs):
#   shared/src/androidMain/jniLibs/<abi>/libncnn_jni.so
set -euo pipefail

NCNN_SRC=/tmp/ncnn_work/ncnn
NDK=$HOME/Library/Android/sdk/ndk/27.0.12077973
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/darwin-x86_64
BRIDGE=/Users/dmitrijisaev/AndroidStudioProjects/KMPMLBench/shared/src/androidMain/jni/ncnn_jni_bridge.cpp
BUILD=/tmp/ncnn_work
OUT=/Users/dmitrijisaev/AndroidStudioProjects/KMPMLBench/shared/src/androidMain/jniLibs

build_abi() {
  local abi=$1 min=$2
  local triple
  case "$abi" in
    arm64-v8a)   triple=aarch64-linux-android ;;
    x86_64)      triple=x86_64-linux-android ;;
    armeabi-v7a) triple=armv7a-linux-androideabi ;;
    *) echo "unknown abi $abi" >&2; exit 1 ;;
  esac
  local dir
  case "$abi" in
    arm64-v8a) dir=arm64 ;;
    *) dir="$abi" ;;
  esac
  local libncnn="$BUILD/android_build_$dir/src/libncnn.a"
  [ -f "$libncnn" ] || { echo "missing $libncnn" >&2; exit 1; }
  mkdir -p "$OUT/$abi"
  "$TOOLCHAIN/bin/clang++" \
    --target="$triple$min" \
    -fPIC -shared -O2 \
    -fexceptions -frtti \
    -I"$NCNN_SRC/src" \
    -I"$BUILD/android_build_$dir/src" \
    -I"$TOOLCHAIN/sysroot/usr/include" \
    "$BRIDGE" \
    "$libncnn" \
    -o "$OUT/$abi/libncnn_jni.so"
  echo "built $OUT/$abi/libncnn_jni.so"
  "$TOOLCHAIN/bin/llvm-strip" --strip-unneeded "$OUT/$abi/libncnn_jni.so" || true
}

build_abi arm64-v8a 24
build_abi x86_64 24
echo "done"
