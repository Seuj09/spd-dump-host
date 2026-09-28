# SPD Dump Host (Android)

Thin Android **host** APK that replicates Jeus’s proven BootROM+FDL1 path:

`UsbManager.requestPermission` → `openDevice` → usbfs **FD** → TomKing `spd_dump --usb-fd` (wrap via `libusb_wrap_sys_device`).

## USB transport (read this)

| Mechanism | Used for USB? |
| --- | --- |
| **Android UsbManager** (`USB_DEVICE_ATTACHED` + permission dialog) | **YES — only path** |
| Shizuku / Shizuku-API | **NO** — optional future helper for non-USB privileges only; **never opens USB** |
| Termux `termux-usb` | Not required (this APK replaces it) |
| Root `/dev/bus/usb` | Not required |

If someone says “Shizuku opened the OTG device,” that is incorrect for this project.

## TomKing pin

**`f2fc779210d9e4b5ca1904c79a49cc5e114b58f3`** (`put FindPort in loop`).

Tip `d24c21a` is 12 commits ahead — do **not** use for v1 unless needed.  
Details: [`third_party/TOMKING_PIN.md`](third_party/TOMKING_PIN.md).

Confirmed in `spd_dump.c` under `__ANDROID__`:

- `LIBUSB_OPTION_NO_DEVICE_DISCOVERY`
- `--usb-fd` → `libusb_wrap_sys_device`
- FD is the **last** argv (termux-usb style), not the token after `--usb-fd`

## v1 success gate

**Required:** BootROM `CHECK_BAUD` → `SPRD3` → FDL1 connect / `KEEP_CHARGE`.

**Out of scope:** FDL2 `SEND` timeout, `reboot-recovery` succeeding.

## ums9230 / Infinix command

Addrs used by the in-app **Run** button:

```text
spd_dump --usb-fd \
  exec_addr 0x65015f08 \
  fdl <filesDir>/fdl/ums9230/infinix/fdl1-dl.bin 0x65000800 \
  fdl <filesDir>/fdl/ums9230/infinix/fdl2-dl.bin 0x9efffe00 \
  exec reboot-recovery \
  <UsbManager FD>
```

Packaged FDL assets (from Seuj09/Spd_dump_termux `no-root/fdl/ums9230/infinix/`):

- `fdl1-dl.bin`
- `fdl2-dl.bin`

You can also replace them under the app’s files dir after first run.

## How to build the APK

### Prerequisites

- JDK 17+ (21 works)
- Android SDK platform 34, build-tools 34, CMake 3.22.1, NDK 26.1.10909125

```bash
export ANDROID_HOME=/path/to/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
```

### 1) Cross-build TomKing `spd_dump` (required for a working APK)

```bash
./scripts/build-spd_dump-android.sh
# → app/src/main/assets/bin/arm64-v8a/spd_dump
# → app/src/main/assets/bin/armeabi-v7a/spd_dump
```

Prebuilt binaries for both ABIs are already committed (see `third_party/BINARY_SHA256.md`). Re-run the script to rebuild.

### 2) Assemble

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

CI: `.github/workflows/build-apk.yml` (builds native + APK on `workflow_dispatch` / push).

## How to run on device

1. Install the debug APK on a **host** phone with USB OTG.
2. Put the target phone in Unisoc BootROM (VID `1782`, PID `4d00`).
3. Connect OTG → grant the USB permission dialog (or tap **Find Unisoc USB**).
4. Tap **Run BootROM→FDL1 (ums9230)**.
5. Watch the console for `CHECK_BAUD` / `SPRD3` / FDL1. Ignore FDL2 SEND timeouts for v1.

## Architecture

```
[APK]
  UsbBackend (UsbManager) ── keeps UsbDeviceConnection alive
       │ int fd
       ▼
  JNI fork+exec (clears CLOEXEC; Android ProcessBuilder cannot inherit FD)
       │
       ▼
  assets/bin/<abi>/spd_dump --usb-fd … <fd>
       │
       ▼
  libusb_wrap_sys_device + NO_DEVICE_DISCOVERY
```

Optional: same FD can drive Seuj09 **spdhost** (`--usb-fd` / `TERMUX_USB_FD` / `SPD_USB_FD`) once you place that binary; v1 ships TomKing for the proven `exec_addr` path.

## Why a new repo (not `android-host/` under Spd_dump_termux)?

A full Gradle + NDK + wrapper tree would balloon the Termux-focused `Spd_dump_termux` repo. This companion stays public under Seuj09 and shares FDL assets / docs with that project.

## License

App code: MIT (same spirit as Spd_dump_termux no-root).  
TomKing `spd_dump` and FDL blobs retain their upstream licenses; Android binaries in `assets/bin/` are built from the pinned SHA via `scripts/build-spd_dump-android.sh`.
