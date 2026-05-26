package tridefender.llama.snapdragon.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object BinaryExtractor {
    private const val TAG = "BinaryExtractor"
    
    private val EXECUTABLES = listOf("llama-server")
    
    private val LIBRARIES = listOf(
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
        return File(context.filesDir, "bin/llama-server").absolutePath
    }
    
    fun getLibraryDir(context: Context): String {
        return File(context.filesDir, "lib").absolutePath
    }
    
    fun ensureAllAvailable(context: Context): Boolean {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        Log.i(TAG, "Native lib dir: $nativeLibDir")
        
        val nativeLibFile = File(nativeLibDir)
        if (!nativeLibFile.exists()) {
            Log.e(TAG, "Native lib dir does not exist: $nativeLibDir")
            return false
        }
        
        val files = nativeLibFile.listFiles()
        Log.i(TAG, "Native lib files: ${files?.map { it.name }?.joinToString()}")
        
        val binDir = File(context.filesDir, "bin")
        val libDir = File(context.filesDir, "lib")
        
        if (!binDir.exists()) binDir.mkdirs()
        if (!libDir.exists()) libDir.mkdirs()
        
        var allSuccess = true
        
        for (exe in EXECUTABLES) {
            val sourceName = "lib$exe.so"
            val sourceFile = File(nativeLibDir, sourceName)
            
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source not found: ${sourceFile.absolutePath}")
                allSuccess = false
                continue
            }
            
            val destFile = File(binDir, exe)
            if (!setupFile(sourceFile, destFile, true)) {
                allSuccess = false
            }
        }
        
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
        
        Log.i(TAG, "Setup completed, success: $allSuccess")
        return allSuccess
    }
    
    private fun setupFile(source: File, dest: File, needsExecute: Boolean): Boolean {
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
