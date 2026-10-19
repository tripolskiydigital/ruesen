package digital.tripolskiy.ruesen

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.atomic.AtomicBoolean

class WordWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        Scheduler.sync(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, manager, id)
    }

    override fun onDisabled(context: Context) {
        Scheduler.sync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT -> {
                Engine.advance(context)
                updateAll(context)
                // A widget tap may start a foreground service, so revive the watcher if it was killed.
                Scheduler.sync(context)
            }
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return
                val lang = intent.getStringExtra(EXTRA_LANG) ?: return
                // Keep the receiver alive until the utterance finishes (or a safety timeout).
                val pending = goAsync()
                val finished = AtomicBoolean(false)
                val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
                Handler(Looper.getMainLooper()).postDelayed(finish, 9_000)
                Speaker.speak(context, text, lang, finish)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_NEXT = "digital.tripolskiy.ruesen.NEXT"
        private const val ACTION_SPEAK = "digital.tripolskiy.ruesen.SPEAK"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_LANG = "lang"

        // Gap between the two copies of a scrolling line.
        private const val MARQUEE_GAP = "   "

        // Horizontal space taken by everything but the text, in dp (see layout/widget.xml).
        private const val CHROME_DP = 12f + 8f + (36f + 8f)
        private const val EMOJI_DP = 40f + 8f
        private const val FLAG_DP = 22f + 6f
        private const val SPEAK_DP = 28f + 6f

        fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            ids(context).forEach { update(context, manager, it) }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WordWidget::class.java))

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            // Portrait width; launchers that never report sizes get "screen width minus margins".
            val reported = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val width = if (reported > 0) reported.toFloat() else screenWidthDp(context) - 24f
            manager.updateAppWidget(id, views(context, width))
        }

        fun screenWidthDp(context: Context): Float = context.resources.configuration.screenWidthDp.toFloat()

        fun flag(lang: String): String = when (lang) {
            "en-GB" -> "🇬🇧"
            "en-US" -> "🇺🇸"
            "es-US" -> "🇲🇽"
            else -> "🇪🇸"
        }

        /** Widget views for a widget [widthDp] wide. */
        fun views(context: Context, widthDp: Float): RemoteViews {
            val card = Engine.current(context)
            val e = card.entry
            val enVoice = Settings.enVoice(context)
            val esVoice = Settings.esVoice(context)
            val layout = if (Engine.layoutFlip(context)) R.layout.widget_alt else R.layout.widget

            val textDp = widthDp - CHROME_DP - (if (e.emoji.isEmpty()) 0f else EMOJI_DP)
            val ru = if (card.repeat) "🔁 ${e.ru}" else e.ru

            return RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.emoji, e.emoji)
                setViewVisibility(R.id.emoji, if (e.emoji.isEmpty()) View.GONE else View.VISIBLE)
                line(context, R.id.ru, R.id.ru_track, R.id.ru_scroll, ru, bold = true, textDp)
                line(context, R.id.en, R.id.en_track, R.id.en_scroll, e.en, bold = false, textDp - FLAG_DP - SPEAK_DP)
                line(context, R.id.es, R.id.es_track, R.id.es_scroll, e.es, bold = false, textDp - FLAG_DP - SPEAK_DP)
                setTextViewText(R.id.en_flag, flag(enVoice))
                setTextViewText(R.id.es_flag, flag(esVoice))

                setOnClickPendingIntent(R.id.next, broadcast(context, 1, Intent(ACTION_NEXT)))
                setOnClickPendingIntent(R.id.speak_en, broadcast(context, 2, speakIntent(e.en, enVoice)))
                setOnClickPendingIntent(R.id.speak_es, broadcast(context, 3, speakIntent(e.es, esVoice)))
                setOnClickPendingIntent(
                    android.R.id.background,
                    PendingIntent.getActivity(
                        context, 4, Intent(context, SettingsActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }

        /** Shows [text] statically if it fits in [availableDp], otherwise as a scrolling line. */
        private fun RemoteViews.line(
            context: Context, staticId: Int, trackId: Int, scrollId: Int,
            text: String, bold: Boolean, availableDp: Float,
        ) {
            val scroll = !fits(context, text, bold, availableDp)
            setTextViewText(staticId, if (scroll) "" else text)
            setViewVisibility(staticId, if (scroll) View.GONE else View.VISIBLE)
            setTextViewText(scrollId, if (scroll) "$text$MARQUEE_GAP$text$MARQUEE_GAP" else "")
            setViewVisibility(trackId, if (scroll) View.VISIBLE else View.GONE)
        }

        private fun fits(context: Context, text: String, bold: Boolean, availableDp: Float): Boolean {
            val res = context.resources
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = res.getDimension(R.dimen.widget_text)
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            // 4dp of slack for rounding and launcher-specific insets.
            return paint.measureText(text) <= (availableDp - 4f) * res.displayMetrics.density
        }

        private fun speakIntent(text: String, lang: String) =
            Intent(ACTION_SPEAK).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_LANG, lang)

        private fun broadcast(context: Context, code: Int, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(
                context, code, intent.setClass(context, WordWidget::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
