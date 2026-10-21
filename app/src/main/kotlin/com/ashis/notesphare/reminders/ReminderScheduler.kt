package com.ashis.notesphare.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

enum class ScheduleAccuracy { EXACT, INEXACT, FAILED }

interface ReminderScheduler {
    fun schedule(noteId: Long, triggerAtMillis: Long): ScheduleAccuracy
    fun cancel(noteId: Long)
    fun canScheduleExactAlarms(): Boolean
}

class AlarmReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun schedule(noteId: Long, triggerAtMillis: Long): ScheduleAccuracy {
        val manager = alarmManager ?: return ScheduleAccuracy.FAILED
        val pendingIntent = reminderPendingIntent(noteId)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                ScheduleAccuracy.EXACT
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                ScheduleAccuracy.INEXACT
            }
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied, falling back to inexact", securityException)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                ScheduleAccuracy.INEXACT
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not schedule reminder", throwable)
                ScheduleAccuracy.FAILED
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "Could not schedule reminder", throwable)
            ScheduleAccuracy.FAILED
        }
    }

    override fun cancel(noteId: Long) {
        val manager = alarmManager ?: return
        try {
            manager.cancel(reminderPendingIntent(noteId))
        } catch (throwable: Throwable) {
            Log.e(TAG, "Could not cancel reminder", throwable)
        }
    }

    override fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    private fun reminderPendingIntent(noteId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            data = Uri.parse("notesphare://reminder/$noteId")
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            })
        return PendingIntent.getBroadcast(context, noteId.toInt(), intent, flags)
    }

    companion object {
        private const val TAG = "ReminderScheduler"
    }
}
