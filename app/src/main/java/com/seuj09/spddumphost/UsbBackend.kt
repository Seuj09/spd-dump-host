package com.seuj09.spddumphost

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build

/**
 * UsbManager → permission → openDevice → raw usbfs FD.
 *
 * This is the ONLY USB transport. Shizuku does not open USB and is not used here.
 * Keep [connection] alive for the lifetime of any child that wraps the FD.
 */
class UsbBackend(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onDeviceFound(device: UsbDevice)
        fun onPermissionGranted(connection: UsbDeviceConnection, fd: Int, device: UsbDevice)
        fun onPermissionDenied()
        fun onLog(line: String)
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionPermission = "${context.packageName}.USB_PERMISSION"

    /** Held so the usbfs FD stays valid for libusb_wrap_sys_device in the child. */
    var connection: UsbDeviceConnection? = null
        private set

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != actionPermission) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null) {
                openDevice(device)
            } else {
                listener.onLog("USB permission denied")
                listener.onPermissionDenied()
            }
        }
    }

    fun register() {
        val filter = IntentFilter(actionPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(permissionReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun findUnisocDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { it.vendorId == 0x1782 }
    }

    fun requestPermission(device: UsbDevice) {
        listener.onDeviceFound(device)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pi = PendingIntent.getBroadcast(context, 0, Intent(actionPermission), flags)
        if (usbManager.hasPermission(device)) {
            openDevice(device)
        } else {
            listener.onLog("Requesting USB permission for ${hexId(device)}…")
            usbManager.requestPermission(device, pi)
        }
    }

    fun handleAttachIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        if (device != null && device.vendorId == 0x1782) {
            listener.onLog("USB_DEVICE_ATTACHED ${hexId(device)}")
            requestPermission(device)
        }
    }

    private fun openDevice(device: UsbDevice) {
        // Drop previous connection before opening a new one.
        connection?.close()
        connection = null
        val conn = usbManager.openDevice(device)
        if (conn == null) {
            listener.onLog("openDevice() returned null")
            listener.onPermissionDenied()
            return
        }
        val fd = conn.fileDescriptor
        if (fd < 0) {
            listener.onLog("Invalid fileDescriptor from UsbDeviceConnection")
            conn.close()
            listener.onPermissionDenied()
            return
        }
        connection = conn
        listener.onLog("UsbManager FD=$fd for ${hexId(device)} (connection held)")
        listener.onPermissionGranted(conn, fd, device)
    }

    fun release() {
        connection?.close()
        connection = null
    }

    companion object {
        fun hexId(device: UsbDevice): String =
            "%04x:%04x".format(device.vendorId, device.productId)
    }
}
