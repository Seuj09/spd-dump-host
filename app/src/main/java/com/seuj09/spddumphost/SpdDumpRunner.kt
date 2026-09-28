package com.seuj09.spddumphost

import android.content.Context
import java.util.concurrent.Executors

/**
 * Builds the Jeus ums9230/Infinix BootROM+FDL1 command and execs packaged TomKing spd_dump.
 *
 * TomKing pin f2fc779: `--usb-fd` takes the FD as the **last** argv (termux-usb contract),
 * not as the next argument after the flag.
 *
 * v1 success gate: CHECK_BAUD → SPRD3 → FDL1 connect/KEEP_CHARGE.
 * FDL2 SEND timeout is OUT OF SCOPE.
 */
class SpdDumpRunner(private val context: Context) {

    private val pool = Executors.newSingleThreadExecutor()

    fun runUms9230Infinix(
        usbFd: Int,
        onLine: (String) -> Unit,
        onDone: (Int) -> Unit
    ) {
        pool.execute {
            try {
                val bin = AssetHelper.ensureNativeBinary(context, onLine)
                val (fdl1, fdl2) = AssetHelper.ensureFdlAssets(context, onLine)

                // TomKing @ f2fc779: FD is last argv after `--usb-fd` marker.
                val argv = arrayOf(
                    bin.absolutePath,
                    "--usb-fd",
                    "exec_addr", "0x65015f08",
                    "fdl", fdl1.absolutePath, "0x65000800",
                    "fdl", fdl2.absolutePath, "0x9efffe00",
                    "exec", "reboot-recovery",
                    usbFd.toString()
                )
                onLine("exec: ${argv.joinToString(" ")}")
                onLine("--- spd_dump stdout/stderr ---")
                val code = NativeExec.runWithUsbFd(argv, usbFd) { line -> onLine(line) }
                onLine("--- exit $code ---")
                onLine(
                    "v1 gate: look for CHECK_BAUD / SPRD3 / FDL1. " +
                        "FDL2 SEND timeout is out of scope."
                )
                onDone(code)
            } catch (t: Throwable) {
                onLine("ERROR: ${t.message}")
                onDone(-1)
            }
        }
    }
}
