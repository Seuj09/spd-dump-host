package com.seuj09.spddumphost

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream

object AssetHelper {

    fun ensureNativeBinary(context: Context, log: (String) -> Unit): File {
        val abi = preferredAbi()
        val dest = File(context.filesDir, "bin/$abi/spd_dump")
        if (!dest.exists() || dest.length() == 0L) {
            dest.parentFile?.mkdirs()
            val assetPath = "bin/$abi/spd_dump"
            try {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                dest.setExecutable(true, false)
                log("Extracted $assetPath → ${dest.absolutePath} (${dest.length()} bytes)")
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Missing packaged spd_dump for ABI $abi. " +
                        "Build with scripts/build-spd_dump-android.sh and place under " +
                        "app/src/main/assets/bin/$abi/spd_dump. (${e.message})",
                    e
                )
            }
        } else {
            dest.setExecutable(true, false)
        }
        return dest
    }

    fun ensureFdlAssets(context: Context, log: (String) -> Unit): Pair<File, File> {
        val dir = File(context.filesDir, "fdl/ums9230/infinix")
        dir.mkdirs()
        val f1 = copyAsset(context, "fdl/ums9230/infinix/fdl1-dl.bin", File(dir, "fdl1-dl.bin"), log)
        val f2 = copyAsset(context, "fdl/ums9230/infinix/fdl2-dl.bin", File(dir, "fdl2-dl.bin"), log)
        return f1 to f2
    }

    private fun copyAsset(context: Context, asset: String, dest: File, log: (String) -> Unit): File {
        if (dest.exists() && dest.length() > 0) return dest
        context.assets.open(asset).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        log("Extracted $asset (${dest.length()} bytes)")
        return dest
    }

    private fun preferredAbi(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.contains("arm64-v8a") -> "arm64-v8a"
            abis.contains("armeabi-v7a") -> "armeabi-v7a"
            else -> abis.firstOrNull() ?: "arm64-v8a"
        }
    }
}
