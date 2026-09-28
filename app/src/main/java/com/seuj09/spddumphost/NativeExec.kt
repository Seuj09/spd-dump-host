package com.seuj09.spddumphost

/**
 * JNI fork+exec helper that keeps a specific USB usbfs FD open across exec.
 * Android's ProcessBuilder closes FDs > 2, so plain ProcessBuilder cannot feed
 * TomKing spd_dump --usb-fd.
 */
object NativeExec {
    init {
        System.loadLibrary("spd_host_exec")
    }

    /**
     * Run [argv] (argv[0] = absolute binary path) with [usbFd] inherited.
     * TomKing @ f2fc779 reads the FD from the **last** argv (termux-usb style),
     * so callers append usbFd.toString() as the final argument.
     *
     * @return exit code; stdout/stderr delivered via [onLine] on a worker thread.
     */
    external fun runWithUsbFd(
        argv: Array<String>,
        usbFd: Int,
        onLine: LineCallback
    ): Int

    fun interface LineCallback {
        fun onLine(line: String)
    }
}
