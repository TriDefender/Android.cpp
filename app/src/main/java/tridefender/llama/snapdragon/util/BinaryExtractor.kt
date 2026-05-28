package tridefender.llama.snapdragon.util

import android.content.Context
import android.util.Log
import java.io.File

object BinaryExtractor {
    private const val TAG = "BinaryExtractor"
    private const val EXECUTABLE_SO = "libllama-server.so"
    private const val LEGACY_EXECUTABLE = "llama-server"
    private const val LINKER64 = "/system/bin/linker64"

    val EXECUTABLES = listOf(LEGACY_EXECUTABLE)

    val LIBRARIES = listOf(
        "libllama.so",
        "libllama-common.so",
        "libllama-server-impl.so",
        "libmtmd.so",
        "libggml.so",
        "libggml-base.so",
        "libggml-cpu.so",
        "libggml-opencl.so",
        "libggml-hexagon.so",
        "libggml-htp-v68.so",
        "libggml-htp-v69.so",
        "libggml-htp-v73.so",
        "libggml-htp-v75.so",
        "libggml-htp-v79.so",
        "libggml-htp-v81.so"
    )

    fun getBinaryPath(context: Context): String {
        return getActiveExecutableFile(context).takeIf { it.exists() }?.absolutePath
            ?: getBundledExecutableFile(context).absolutePath
    }

    fun getLaunchCommand(context: Context): List<String> {
        val activeExecutable = getActiveExecutableFile(context)
        if (activeExecutable.exists()) {
            chmod755(activeExecutable)
            val linker = File(LINKER64)
            if (linker.exists()) {
                return listOf(linker.absolutePath, activeExecutable.absolutePath)
            }

            Log.w(TAG, "Android linker not found at $LINKER64, falling back to direct executable path")
            return listOf(activeExecutable.absolutePath)
        }

        return listOf(getBundledExecutableFile(context).absolutePath)
    }

    fun getLibraryDir(context: Context): String {
        return File(context.filesDir, "lib").absolutePath
    }

    fun getRuntimeLibraryDirs(context: Context): List<String> {
        return listOf(
            getActiveBinDir(context).absolutePath,
            File(context.filesDir, "lib").absolutePath,
            context.applicationInfo.nativeLibraryDir
        )
    }

    fun ensureAllAvailable(context: Context): Boolean {
        val binDir = getActiveBinDir(context)
        val libDir = File(context.filesDir, "lib")

        if (!binDir.exists()) binDir.mkdirs()
        if (!libDir.exists()) libDir.mkdirs()

        removeLegacyExecutable(binDir)

        val bundledExecutable = getBundledExecutableFile(context)
        if (!bundledExecutable.exists()) {
            Log.e(TAG, "Bundled executable not found: ${bundledExecutable.absolutePath}")
            return false
        }

        val activeExecutable = getActiveExecutableFile(context)
        if (activeExecutable.exists()) {
            chmod755(activeExecutable)
            activeExecutable.setReadable(true, false)
        }

        val hasLibs = libDir.listFiles()?.any { it.name.endsWith(".so") } == true

        if (hasLibs) {
            Log.d(TAG, "Active libraries already present")
            return true
        }

        Log.i(TAG, "Active libraries missing, falling back to native lib dir extraction")
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val nativeLibFile = File(nativeLibDir)
        if (!nativeLibFile.exists()) {
            Log.e(TAG, "Native lib dir does not exist: $nativeLibDir")
            return false
        }

        var allSuccess = true

        for (lib in LIBRARIES) {
            val sourceFile = File(nativeLibDir, lib)

            if (!sourceFile.exists()) {
                Log.w(TAG, "Library not found in native dir: $lib")
                continue
            }

            val destFile = File(libDir, lib)
            if (!setupFile(sourceFile, destFile, false)) {
                allSuccess = false
            }
        }

        Log.i(TAG, "Library setup completed, success: $allSuccess")
        return allSuccess
    }

    fun clearActiveBinLib(context: Context) {
        val binDir = File(context.filesDir, "bin")
        val libDir = File(context.filesDir, "lib")

        if (binDir.exists()) {
            binDir.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "Cleared bin directory")
        }
        if (libDir.exists()) {
            libDir.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "Cleared lib directory")
        }
    }

    fun activateFromVersion(context: Context, versionDir: File, useBundledExecutable: Boolean = false) {
        val binDir = getActiveBinDir(context)
        val libDir = File(context.filesDir, "lib")

        if (!binDir.exists()) binDir.mkdirs()
        if (!libDir.exists()) libDir.mkdirs()

        val versionBin = File(versionDir, "bin")
        val versionLib = File(versionDir, "lib")

        removeLegacyExecutable(binDir)

        if (useBundledExecutable) {
            val activeExecutable = getActiveExecutableFile(context)
            if (activeExecutable.exists() && !activeExecutable.delete()) {
                Log.w(TAG, "Failed to delete staged executable for bundled activation")
            }
            Log.i(TAG, "Bundled kernel will launch from native library dir")
        } else {
            val sourceExecutable = listOf(
                File(versionBin, EXECUTABLE_SO),
                File(versionBin, LEGACY_EXECUTABLE)
            ).firstOrNull { it.exists() }

            if (sourceExecutable == null) {
                Log.e(TAG, "No executable found in ${versionBin.absolutePath}")
            } else {
                val dest = getActiveExecutableFile(context)
                if (!setupFile(sourceExecutable, dest, true)) {
                    Log.e(TAG, "Failed to activate executable: ${sourceExecutable.name}")
                }
            }
        }

        if (versionLib.exists()) {
            versionLib.listFiles()?.forEach { source ->
                val dest = File(libDir, source.name)
                if (!setupFile(source, dest, false)) {
                    Log.e(TAG, "Failed to activate: ${source.name}")
                }
            }
        }

        val exeFile = getActiveExecutableFile(context)
        if (exeFile.exists()) {
            chmod755(exeFile)
        }

        Log.i(TAG, "Activated version from: ${versionDir.name}")
    }

    private fun setupFile(source: File, dest: File, needsExecute: Boolean): Boolean {
        dest.parentFile?.mkdirs()

        if (dest.exists()) {
            if (!needsExecute || dest.canExecute()) {
                Log.d(TAG, "File exists: ${dest.name}")
                return true
            }
            dest.delete()
        }

        return try {
            val cmd = arrayOf("ln", "-sf", source.absolutePath, dest.absolutePath)
            val process = Runtime.getRuntime().exec(cmd)
            val exitCode = process.waitFor()

            if (exitCode == 0 && dest.exists()) {
                Log.i(TAG, "Symlink created: ${dest.name} -> ${source.absolutePath}")
                if (needsExecute) {
                    chmod755(dest)
                }
                true
            } else {
                Log.w(TAG, "Symlink failed (exit=$exitCode), trying copy")
                copyFile(source, dest, needsExecute)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Symlink exception for ${dest.name}", e)
            copyFile(source, dest, needsExecute)
        }
    }

    private fun copyFile(source: File, dest: File, needsExecute: Boolean): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (needsExecute) {
                chmod755(dest)
            }

            dest.setReadable(true, false)

            val success = !needsExecute || dest.canExecute()
            Log.i(TAG, "Copied: ${dest.name}, success: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed for ${dest.name}", e)
            false
        }
    }

    private fun getActiveBinDir(context: Context): File = File(context.filesDir, "bin")

    private fun getActiveExecutableFile(context: Context): File {
        return File(getActiveBinDir(context), EXECUTABLE_SO)
    }

    private fun getBundledExecutableFile(context: Context): File {
        return File(context.applicationInfo.nativeLibraryDir, EXECUTABLE_SO)
    }

    private fun removeLegacyExecutable(binDir: File) {
        val legacy = File(binDir, LEGACY_EXECUTABLE)
        if (legacy.exists() && !legacy.delete()) {
            Log.w(TAG, "Failed to delete legacy executable path: ${legacy.absolutePath}")
        }
    }

    private fun chmod755(file: File) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath))
            process.waitFor()
            Log.d(TAG, "chmod 755 ${file.name}: exit=${process.exitValue()}")
        } catch (e: Exception) {
            Log.e(TAG, "chmod failed", e)
        }
    }
}
