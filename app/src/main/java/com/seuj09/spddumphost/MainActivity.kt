package com.seuj09.spddumphost

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.os.Bundle
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.seuj09.spddumphost.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), UsbBackend.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usb: UsbBackend
    private lateinit var runner: SpdDumpRunner

    private var usbFd: Int = -1
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usb = UsbBackend(this, this)
        runner = SpdDumpRunner(this)

        binding.btnFind.setOnClickListener {
            val dev = usb.findUnisocDevice()
            if (dev == null) {
                appendLog("No VID 1782 device attached. Plug OTG BootROM cable.")
                binding.statusText.text = "No USB device"
            } else {
                usb.requestPermission(dev)
            }
        }
        binding.btnRun.setOnClickListener { startRun() }
        binding.btnClear.setOnClickListener {
            binding.consoleText.text = ""
        }

        appendLog("SPD Dump Host v0.1 — UsbManager FD → TomKing spd_dump @ f2fc779")
        appendLog("Shizuku: not required / not wired for USB.")
        appendLog("v1 success: CHECK_BAUD → SPRD3 → FDL1. FDL2 OUT OF SCOPE.")

        usb.handleAttachIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        usb.handleAttachIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        usb.register()
    }

    override fun onStop() {
        usb.unregister()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) usb.release()
        super.onDestroy()
    }

    private fun startRun() {
        if (running) {
            appendLog("Already running")
            return
        }
        if (usbFd < 0 || usb.connection == null) {
            appendLog("No USB FD. Tap Find / grant permission first.")
            return
        }
        running = true
        binding.btnRun.isEnabled = false
        appendLog("Starting with FD=$usbFd (UsbDeviceConnection held)…")
        runner.runUms9230Infinix(
            usbFd = usbFd,
            onLine = { line -> runOnUiThread { appendLog(line) } },
            onDone = { code ->
                runOnUiThread {
                    running = false
                    binding.btnRun.isEnabled = usbFd >= 0
                    appendLog("Done (exit $code)")
                }
            }
        )
    }

    override fun onDeviceFound(device: UsbDevice) {
        binding.statusText.text = "Found ${UsbBackend.hexId(device)}"
    }

    override fun onPermissionGranted(connection: UsbDeviceConnection, fd: Int, device: UsbDevice) {
        usbFd = fd
        binding.statusText.text = "Ready ${UsbBackend.hexId(device)} fd=$fd"
        binding.btnRun.isEnabled = !running
        appendLog("Permission OK — ready to run")
    }

    override fun onPermissionDenied() {
        usbFd = -1
        binding.btnRun.isEnabled = false
        binding.statusText.text = "USB permission denied"
    }

    override fun onLog(line: String) {
        appendLog(line)
    }

    private fun appendLog(line: String) {
        binding.consoleText.append(line)
        binding.consoleText.append("\n")
        binding.consoleScroll.post {
            binding.consoleScroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
}
