package com.local.wahelper.notif

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.local.wahelper.data.AppDatabase
import com.local.wahelper.data.CapturedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Reads notifications as Android delivers them to every listener app.
 * It never launches WhatsApp and never interacts with a chat, so WhatsApp
 * has no reason to mark anything as read or send a receipt because of this.
 *
 * Limitation: WhatsApp's notification only ever carries what WhatsApp put in it
 * - sender name, message text, and sometimes a thumbnail image. A voice note's
 * actual audio or a file's actual bytes are NOT in the notification; they only
 * exist once you open the chat in WhatsApp itself. So audio/file entries are
 * logged as "new audio" / "new file" placeholders, not as playable/openable content.
 */
class WaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val targetPackages = setOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in targetPackages) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // Group summary notifications repeat individual ones; skip them.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val isGroup = title.contains(":") || extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val kind = classify(text)
        val bigPicture = extras.get(Notification.EXTRA_PICTURE) as? Bitmap
        val savedImagePath = bigPicture?.let { saveBitmap(it) }

        val message = CapturedMessage(
            sender = title,
            text = text,
            kind = kind,
            imagePath = savedImagePath,
            packageName = sbn.packageName,
            timestamp = sbn.postTime,
            isGroup = isGroup
        )

        scope.launch {
            AppDatabase.get(applicationContext).messageDao().insert(message)
        }
    }

    private fun classify(text: String): String = when {
        text.contains("photo", ignoreCase = true) || text.contains("image", ignoreCase = true) -> "IMAGE"
        text.contains("audio", ignoreCase = true) || text.contains("voice message", ignoreCase = true) -> "AUDIO"
        text.contains("document", ignoreCase = true) || text.contains("file", ignoreCase = true) -> "FILE"
        else -> "TEXT"
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        return try {
            val dir = File(filesDir, "notif_images").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
