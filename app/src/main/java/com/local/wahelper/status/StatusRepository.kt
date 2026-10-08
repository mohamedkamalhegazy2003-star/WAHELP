package com.local.wahelper.status

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean
)

/**
 * Reads WhatsApp's own local status cache folder
 * ( .../Android/media/com.whatsapp/WhatsApp/Media/.Statuses )
 * through the Storage Access Framework folder the user grants once.
 *
 * This only reads files WhatsApp has already downloaded to your device so it
 * can show the status tray in its own UI. Browsing that folder here never
 * opens WhatsApp's status viewer, so it never registers a "seen by" for you
 * on your contacts' statuses. WhatsApp also clears each file from this folder
 * roughly 24-48h after it expires, so capture things you want to keep soon.
 */
class StatusRepository(private val context: Context) {

    fun listStatuses(treeUri: Uri): List<StatusItem> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val statusesDir = findStatusesDir(root) ?: root
        return statusesDir.listFiles()
            .filter { it.isFile && (it.type?.startsWith("image") == true || it.type?.startsWith("video") == true) }
            .filter { !it.name.orEmpty().endsWith(".nomedia") }
            .map { StatusItem(it.uri, it.name ?: "status", it.type?.startsWith("video") == true) }
            .sortedByDescending { it.name }
    }

    /** If the user picked a parent folder, walk down to find .Statuses automatically. */
    private fun findStatusesDir(root: DocumentFile): DocumentFile? {
        if (root.name == ".Statuses") return root
        root.listFiles().forEach { child ->
            if (child.isDirectory) {
                if (child.name == ".Statuses") return child
                findStatusesDir(child)?.let { return it }
            }
        }
        return null
    }

    fun saveToDownloads(item: StatusItem): Boolean {
        return try {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/WaHelperStatuses")
            }
            val collection = if (item.isVideo)
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val dest = resolver.insert(collection, values) ?: return false
            resolver.openInputStream(item.uri)?.use { input ->
                resolver.openOutputStream(dest)?.use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
