package com.ashis.notesphare.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ashis.notesphare.NoteSphereApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return

        val app = context.applicationContext as? NoteSphereApp
        val pendingResult = goAsync()
        if (app == null) {
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val note = app.container.noteRepository.getNote(noteId)
                if (note != null) {
                    val dueAt = note.reminderAt
                    val due = dueAt != null && dueAt <= System.currentTimeMillis() + 60_000L
                    if (!note.isTrashed && due) {
                        app.container.notificationHelper.showReminder(note)
                    }
                    if (dueAt != null) {
                        app.container.noteRepository.markReminderFired(noteId)
                    }
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not deliver reminder", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.ashis.notesphare.action.REMINDER"
        const val EXTRA_NOTE_ID = "com.ashis.notesphare.extra.REMINDER_NOTE_ID"
        private const val TAG = "ReminderReceiver"
    }
}
