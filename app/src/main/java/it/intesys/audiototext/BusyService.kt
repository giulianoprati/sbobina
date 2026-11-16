package it.intesys.audiototext

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Process-wide "work in progress" flag, owned by the ViewModel that runs the work. */
object BusyState {
    val busy = MutableStateFlow(false)
}

/**
 * Keeps the process at foreground priority while a download or transcription runs.
 * It always calls startForeground first, then stops itself when [BusyState.busy] turns false,
 * so there is no start/stop race and nobody has to remember to stop it.
 */
class BusyService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Elaborazione", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Sbobina")
            .setContentText("Elaborazione in corso…")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        if (watcher?.isActive != true) {
            watcher = scope.launch {
                BusyState.busy.first { !it }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // Android 15 limits dataSync services to 6 hours; stop cleanly instead of being treated as a crash.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "busy"
    }
}
