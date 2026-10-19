package digital.tripolskiy.ruesen

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * Android delivers ACTION_SCREEN_ON only to receivers registered at runtime, so a small foreground
 * service keeps one registered. The app never asks for the notification permission, so on
 * Android 13+ its notification stays out of the shade (the service still shows in the task manager).
 */
class ScreenWatchService : Service() {

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) Engine.onScreenOn(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenOn)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Смена слова при включении экрана", NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle("Ruesen")
            .setContentText("Меняю слово в виджете при включении экрана")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "screen_watch"
        private const val NOTIFICATION_ID = 1

        /**
         * Starts the service. From the background (e.g. the widget-added broadcast) Android may refuse;
         * then an exact alarm retries a second later, since exact alarms may start foreground services.
         */
        fun start(context: Context, retry: Boolean = true) {
            try {
                context.startForegroundService(Intent(context, ScreenWatchService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w("Ruesen", "Cannot start screen watcher now, retry=$retry", e)
                if (retry) scheduleRetry(context)
            }
        }

        private fun scheduleRetry(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) return
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(context, StartReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1_000L, pi)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenWatchService::class.java))
        }
    }
}

/** Fired by the retry alarm of [ScreenWatchService.start]. */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (WordWidget.hasWidgets(context) && Settings.changeMode(context) != Settings.MODE_MANUAL) {
            ScreenWatchService.start(context, retry = false)
        }
    }
}
