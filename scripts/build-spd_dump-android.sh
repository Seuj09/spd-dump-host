#!/usr/bin/env bash
# Cross-build TomKing spd_dump @ f2fc779 for Android (arm64-v8a + armeabi-v7a).
# Requires: Android NDK (r26+), cmake, git.
set -euo pipefail

PIN=f2fc779210d9e4b5ca1904c79a49cc5e114b58f3
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKDIR="${WORKDIR:-$ROOT/third_party}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:+$ANDROID_HOME/ndk/26.1.10909125}}"
API=26

if [[ -z "${NDK}" || ! -d "${NDK}" ]]; then
  echo "Set ANDROID_NDK_HOME or ANDROID_HOME with ndk/26.1.10909125" >&2
  exit 1
fi

mkdir -p "$WORKDIR"
TOMKING="$WORKDIR/spreadtrum_flash"
LIBUSB="$WORKDIR/libusb"

if [[ ! -d "$TOMKING/.git" ]]; then
  git clone https://github.com/TomKing062/spreadtrum_flash.git "$TOMKING"
fi
git -C "$TOMKING" fetch --all --tags
git -C "$TOMKING" checkout "$PIN"

if [[ ! -d "$LIBUSB/.git" ]]; then
  git clone --depth 1 --branch v1.0.27 https://github.com/libusb/libusb.git "$LIBUSB"
fi

HOST_TAG="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
if [[ ! -d "$TOOLCHAIN" ]]; then
  # macOS arm64 prebuilt folder name
  HOST_TAG="$(uname -s | tr '[:upper:]' '[:lower:]')-arm64"
  TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
fi

build_abi() {
  local ABI="$1"
  local TRIPLE="$2"
  local OUTDIR="$ROOT/app/src/main/assets/bin/$ABI"
  mkdir -p "$OUTDIR"

  local PREFIX="$WORKDIR/prefix-$ABI"
  rm -rf "$PREFIX"
  mkdir -p "$PREFIX"

  echo "==> libusb $ABI"
  pushd "$LIBUSB" >/dev/null
  ./bootstrap.sh >/dev/null 2>&1 || true
  make distclean >/dev/null 2>&1 || true
  export PATH="$TOOLCHAIN/bin:$PATH"
  export CC="$TOOLCHAIN/bin/${TRIPLE}${API}-clang"
  export AR="$TOOLCHAIN/bin/llvm-ar"
  export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
  export STRIP="$TOOLCHAIN/bin/llvm-strip"
  ./configure --host="$TRIPLE" --prefix="$PREFIX" \
    --enable-static --disable-shared \
    --disable-udev \
    CFLAGS="-O2 -fPIC" \
    || { echo "libusb configure failed for $ABI"; exit 1; }
  make -j"$(nproc)"
  make install
  popd >/dev/null

  echo "==> spd_dump $ABI @ $PIN"
  pushd "$TOMKING" >/dev/null
  echo "#define GIT_VER \"android-host\"" > GITVER.h
  echo "#define GIT_SHA1 \"$PIN\"" >> GITVER.h
  "$CC" -O2 -Wall -std=c99 -fPIC \
    -D__ANDROID__ -DUSE_LIBUSB=1 \
    -I"$PREFIX/include" -I"$PREFIX/include/libusb-1.0" \
    -o "$OUTDIR/spd_dump" \
    spd_dump.c common.c \
    "$PREFIX/lib/libusb-1.0.a" -llog \
    -Wl,-z,max-page-size=16384
  "$STRIP" "$OUTDIR/spd_dump" || true
  popd >/dev/null

  echo "Built $OUTDIR/spd_dump ($(wc -c < "$OUTDIR/spd_dump") bytes)"
  sha256sum "$OUTDIR/spd_dump"
}

build_abi arm64-v8a aarch64-linux-android
build_abi armeabi-v7a armv7a-linux-androideabi

echo "Done. Binaries in app/src/main/assets/bin/{arm64-v8a,armeabi-v7a}/spd_dump"
