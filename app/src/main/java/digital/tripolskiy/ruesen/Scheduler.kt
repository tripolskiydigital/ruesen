package digital.tripolskiy.ruesen

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Keeps the background parts in line with the settings:
 *  - [ScreenWatchService] runs while a widget exists and the mode is not "manual" (it catches screen-on);
 *  - a non-wakeup alarm changes the card on interval modes while the screen stays on.
 *    Non-wakeup alarms never wake the phone: if it sleeps, the alarm fires at the next screen-on.
 */
object Scheduler {
    fun sync(context: Context) {
        val active = WordWidget.hasWidgets(context) && Settings.changeMode(context) != Settings.MODE_MANUAL
        if (active) ScreenWatchService.start(context) else ScreenWatchService.stop(context)
        schedule(context)
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, TickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mode = Settings.changeMode(context)
        if (mode <= 0 || !WordWidget.hasWidgets(context)) {
            am.cancel(pi)
            return
        }
        val now = System.currentTimeMillis()
        val at = maxOf(Engine.lastChange(context) + mode * 60_000L, now + 5_000L)
        am.set(AlarmManager.RTC, at, pi)
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Engine.onTick(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Scheduler.sync(context)
        } catch (e: Exception) {
            Log.w("Ruesen", "sync after ${intent.action} failed", e)
        }
    }
}
