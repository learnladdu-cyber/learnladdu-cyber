package com.ashis.notesphare.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ashis.notesphare.MainActivity
import com.ashis.notesphare.R
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.util.noteSnippet

class NotificationHelper(private val context: Context) {

    fun showReminder(note: NoteEntity): Boolean {
        if (!canPostNotifications()) {
            Log.i(TAG, "Reminder fired but notifications are disabled for note ${note.id}")
            return false
        }
        val title = note.title.trim().ifEmpty { "Note reminder" }
        val snippet = noteSnippet(note.content)
            .ifEmpty { "Tap to open your note." }
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            note.id.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(snippet)
            .setStyle(NotificationCompat.BigTextStyle().bigText(snippet))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setWhen(note.reminderAt ?: System.currentTimeMillis())
            .build()

        return try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_BASE + (note.id % 100_000).toInt(), notification)
            true
        } catch (throwable: Throwable) {
            Log.e(TAG, "Could not post reminder notification", throwable)
            false
        }
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    companion object {
        const val CHANNEL_ID = "note_reminders"
        private const val TAG = "NotificationHelper"
        private const val NOTIFICATION_ID_BASE = 2_000

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Note reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders you schedule for your notes"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        private fun immutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
    }
}
