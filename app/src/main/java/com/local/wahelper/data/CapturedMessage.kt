package com.local.wahelper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One captured WhatsApp notification.
 * We only ever read what Android's notification system hands us —
 * we never open WhatsApp and never touch its chat screens, so
 * nothing here marks a chat as read or sends a receipt.
 */
@Entity(tableName = "captured_messages")
data class CapturedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val text: String,
    val kind: String,       // TEXT, IMAGE, AUDIO, FILE, OTHER
    val imagePath: String?, // local copy of a notification's big picture, if any
    val packageName: String,
    val timestamp: Long,
    val isGroup: Boolean
)
