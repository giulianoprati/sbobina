package it.intesys.audiototext

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
import it.intesys.audiototext.history.HistoryEntry
import it.intesys.audiototext.ui.Formatters

/** True while the Activity is on screen; set from onStart/onStop. */
object AppVisibility {
    @Volatile var visible = false
}

/** "Trascrizione pronta" notification, shown only when the user is not looking at the app. */
class ResultNotifier(private val context: Context) {

    fun notifyIfInBackground(entry: HistoryEntry) {
        if (AppVisibility.visible) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Trascrizioni pronte", NotificationManager.IMPORTANCE_DEFAULT))

        val open = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_ENTRY)
            .putExtra(EXTRA_ENTRY_ID, entry.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, entry.id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Only the file name and length, never the text: notifications can show on the lock screen.
        val detail = entry.audioDurationMs?.let { "${entry.fileName} · ${Formatters.duration(it)}" } ?: entry.fileName
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Trascrizione pronta")
            .setContentText(detail)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(entry.id.hashCode(), notification)
    }

    companion object {
        const val CHANNEL = "results"
        const val ACTION_OPEN_ENTRY = "it.intesys.audiototext.OPEN_ENTRY"
        const val EXTRA_ENTRY_ID = "entry_id"
    }
}
