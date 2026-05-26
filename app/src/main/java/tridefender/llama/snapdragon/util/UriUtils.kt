package tridefender.llama.snapdragon.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

object UriUtils {
    private const val TAG = "UriUtils"
    
    /**
     * Convert a content:// URI to a real file path that native code can read.
     * For ExternalStorageProvider URIs, extracts the real path directly.
     * For other URIs, copies the file to app storage and returns that path.
     */
    fun getRealPath(context: Context, uri: Uri): String? {
        Log.d(TAG, "Converting URI: $uri")
        
        // Try to get direct file path for external storage documents
        if (isExternalStorageDocument(uri)) {
            val path = getExternalStoragePath(uri)
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    Log.i(TAG, "Using direct path: $path")
                    return path
                }
                Log.w(TAG, "Direct path exists but not readable: $path")
            }
        }
        
        // Fallback: copy to app storage
        return copyToAppStorage(context, uri)
    }
    
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }
    
    private fun getExternalStoragePath(uri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(uri)
        Log.d(TAG, "Document ID: $docId")
        
        val split = docId.split(":")
        if (split.size != 2) {
            Log.w(TAG, "Unexpected document ID format: $docId")
            return null
        }
        
        val type = split[0]
        val path = split[1]
        
        // Decode URL-encoded path
        val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
        
        val fullPath = when (type) {
            "primary" -> {
                // Primary external storage
                "/storage/emulated/0/$decodedPath"
            }
            else -> {
                // Secondary storage (SD card, etc.)
                "/storage/$type/$decodedPath"
            }
        }
        
        Log.d(TAG, "Computed path: $fullPath")
        return fullPath
    }
    
    private fun copyToAppStorage(context: Context, uri: Uri): String? {
        return try {
            // Get filename
            val fileName = getFileName(context, uri) ?: "model_${System.currentTimeMillis()}.gguf"
            val destFile = File(context.filesDir, "models/$fileName")
            destFile.parentFile?.mkdirs()
            
            // Check if already copied
            if (destFile.exists()) {
                Log.i(TAG, "File already copied: ${destFile.absolutePath}")
                return destFile.absolutePath
            }
            
            // Copy file
            Log.i(TAG, "Copying file to: ${destFile.absolutePath}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "File copied successfully: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file", e)
            null
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        
        return name
    }
}
