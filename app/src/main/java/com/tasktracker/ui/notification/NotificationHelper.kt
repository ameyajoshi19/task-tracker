package com.tasktracker.ui.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tasktracker.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_RESCHEDULE = "reschedule_proposals"
        const val CHANNEL_DEADLINE = "deadline_alerts"
        const val NOTIFICATION_ID_RESCHEDULE = 1001
        const val NOTIFICATION_ID_DEADLINE = 1002
    }

    fun createChannels() {
        val rescheduleChannel = NotificationChannel(
            CHANNEL_RESCHEDULE,
            "Reschedule Proposals",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications when tasks need to be rescheduled"
        }

        val deadlineChannel = NotificationChannel(
            CHANNEL_DEADLINE,
            "Deadline Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications when a task can't be scheduled before its deadline"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(rescheduleChannel)
        manager.createNotificationChannel(deadlineChannel)
    }

    fun showRescheduleProposal(taskCount: Int) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reschedule")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RESCHEDULE)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Schedule Conflict Detected")
            .setContentText("$taskCount task(s) need to be rescheduled. Tap to review.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_RESCHEDULE, notification)
    }

    fun showDeadlineAtRisk(taskTitle: String, taskId: Long = 0) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DEADLINE)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Deadline at Risk")
            .setContentText("\"$taskTitle\" can't be scheduled before its deadline.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_DEADLINE + taskId.toInt(), notification)
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
