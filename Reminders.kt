package com.glucoguide.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.Calendar

/** Schedules daily repeating reminder notifications and persists them so they
 *  survive reboots (BootReceiver re-registers everything). */
object Reminders {

    private const val PREFS = "glucoguide_reminders"
    const val CHANNEL_ID = "glucoguide_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Daily reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Glucose checks, medications, and plan reminders" }
            )
        }
    }

    private fun pendingIntent(context: Context, id: Int, title: String, message: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("id", id)
            .putExtra("title", title)
            .putExtra("message", message)
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, id: Int, hour: Int, minute: Int, title: String, message: String) {
        ensureChannel(context)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context, id, title, message)
        )
        // persist for boot re-registration
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = JSONObject()
            .put("h", hour).put("m", minute)
            .put("title", title).put("msg", message)
        prefs.edit().putString(id.toString(), json.toString()).apply()
    }

    fun cancel(context: Context, id: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, id, "", ""))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(id.toString()).apply()
    }

    fun rescheduleAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for ((key, value) in prefs.all) {
            try {
                val id = key.toInt()
                val j = JSONObject(value as String)
                schedule(context, id, j.getInt("h"), j.getInt("m"), j.getString("title"), j.getString("msg"))
            } catch (_: Exception) { /* skip malformed entries */ }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.ensureChannel(context)
        val id = intent.getIntExtra("id", 0)
        val title = intent.getStringExtra("title") ?: "GlucoGuide"
        val message = intent.getStringExtra("message") ?: "Time for your health check-in"

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setColor(0xFF0E7C66.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(id, notification)
        } catch (_: SecurityException) { /* notifications not permitted */ }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.rescheduleAll(context)
        }
    }
}
