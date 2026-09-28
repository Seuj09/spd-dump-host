# TomKing spd_dump pin

| Field | Value |
| --- | --- |
| Repo | https://github.com/TomKing062/spreadtrum_flash |
| **Pinned SHA** | `f2fc779210d9e4b5ca1904c79a49cc5e114b58f3` |
| Subject | `put FindPort in loop` |
| Date | 2025-06-08 +0800 |
| Tip (do **not** use for v1) | `d24c21a` — **12 commits ahead** (async transfer PRs, etc.) |

## Why this pin

- Matches Jeus’s proven BootROM+FDL1 log (`exec_addr`, FDL addrs).
- Android path already present under `__ANDROID__`:
  - `libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);`
  - `--usb-fd` → `libusb_wrap_sys_device`
- Tip async work is unrelated to the v1 success gate.

## `--usb-fd` argv contract (stock pin)

TomKing’s parser (termux-usb style) does **not** take the FD as the next argv after `--usb-fd`.
It reads the FD from the **last** argument:

```text
spd_dump --usb-fd exec_addr 0x65015f08 fdl fdl1-dl.bin 0x65000800 \
  fdl fdl2-dl.bin 0x9efffe00 exec reboot-recovery <FD>
```

This host app’s `SpdDumpRunner` appends the UsbManager FD as the final argv.
Env aliases (`SPD_USB_FD` / `TERMUX_USB_FD`) are **not** required for the APK path;
spdhost in Seuj09/Spd_dump_termux may still grow an `SPD_USB_FD` alias separately.

## Confirm on checkout

```bash
git clone https://github.com/TomKing062/spreadtrum_flash.git
cd spreadtrum_flash
git checkout f2fc779210d9e4b5ca1904c79a49cc5e114b58f3
rg -n 'usb-fd|NO_DEVICE_DISCOVERY|wrap_sys_device' spd_dump.c
```
