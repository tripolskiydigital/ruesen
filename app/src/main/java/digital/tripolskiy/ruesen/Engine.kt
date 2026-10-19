package digital.tripolskiy.ruesen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What the widget shows right now. */
data class Card(val entry: Entry, val repeat: Boolean, val timesShown: Int)

data class Stats(val seenWords: Int, val totalWords: Int, val seenPhrases: Int, val totalPhrases: Int, val due: Int)

/**
 * Picks the next card: words and phrases alternate, and every N-th card (see [Settings.repeatModes])
 * is a repetition of an already shown one whose spaced-repetition interval has passed.
 */
object Engine {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    // Delay before an item is due again, indexed by how many times it has been shown (capped at the last).
    private val intervals = longArrayOf(30 * MINUTE, 4 * HOUR, DAY, 3 * DAY, 7 * DAY, 21 * DAY, 60 * DAY)

    private class Progress(var box: Int, var due: Long, var count: Int)

    /** Notified on the main thread after every card change (used by the settings screen). */
    @Volatile
    var listener: (() -> Unit)? = null

    private fun prefs(context: Context) = context.getSharedPreferences("state", Context.MODE_PRIVATE)

    @Synchronized
    fun current(context: Context): Card {
        val p = prefs(context)
        val entry = Vocab.byKey(context, p.getString("current", null)) ?: return advance(context)
        val count = loadHistory(context)[entry.key]?.count ?: 1
        return Card(entry, p.getBoolean("current_repeat", false), count)
    }

    @Synchronized
    fun advance(context: Context): Card {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val history = loadHistory(context)
        val currentKey = p.getString("current", null)
        val currentEntry = Vocab.byKey(context, currentKey)

        val phrase = currentEntry?.phrase == false
        val pool = if (phrase) Vocab.phrases(context) else Vocab.words(context)

        val repeatEvery = Settings.repeatEvery(context)
        val sinceRepeat = p.getInt("since_repeat", 0)
        val wantRepeat = repeatEvery > 0 && sinceRepeat >= repeatEvery - 1

        val due = pool.filter { e -> e.key != currentKey && (history[e.key]?.due ?: Long.MAX_VALUE) <= now }
        val unseen = pool.filter { it.key !in history }

        val pick: Entry
        val repeat: Boolean
        when {
            wantRepeat && due.isNotEmpty() -> {
                pick = due.minBy { history.getValue(it.key).due }
                repeat = true
            }
            unseen.isNotEmpty() -> {
                pick = if (Settings.frequencyOrder(context)) unseen.first() else unseen.random()
                repeat = false
            }
            else -> {
                // Everything of this kind has been shown: keep cycling through the earliest due items.
                pick = pool.filter { it.key != currentKey }.minBy { history[it.key]?.due ?: 0L }
                repeat = true
            }
        }

        val progress = history[pick.key]
        val updated = if (progress == null) {
            Progress(0, now + intervals[0], 1)
        } else {
            val box = (progress.box + 1).coerceAtMost(intervals.lastIndex)
            Progress(box, now + intervals[box], progress.count + 1)
        }
        history[pick.key] = updated
        saveHistory(context, history)

        p.edit()
            .putString("current", pick.key)
            .putBoolean("current_repeat", repeat)
            .putInt("since_repeat", if (repeat) 0 else sinceRepeat + 1)
            .putLong("last_change", now)
            .putBoolean("layout_flip", !p.getBoolean("layout_flip", false))
            .apply()
        listener?.let { l -> android.os.Handler(android.os.Looper.getMainLooper()).post { l() } }
        return Card(pick, repeat, updated.count)
    }

    /** Toggles on every card change; the widget alternates between two layout ids with it. */
    fun layoutFlip(context: Context): Boolean = prefs(context).getBoolean("layout_flip", false)

    fun lastChange(context: Context): Long = prefs(context).getLong("last_change", 0L)

    /** Called on every screen-on; changes the card if the chosen mode says it is time. */
    fun onScreenOn(context: Context) {
        val mode = Settings.changeMode(context)
        val elapsed = System.currentTimeMillis() - lastChange(context)
        val change = when {
            mode == Settings.MODE_SCREEN_ON -> true
            mode > 0 -> elapsed >= mode * MINUTE
            else -> false
        }
        android.util.Log.i("Ruesen", "screen on: mode=$mode change=$change")
        if (change) {
            advance(context)
            WordWidget.updateAll(context)
        }
        if (mode > 0) Scheduler.schedule(context)
    }

    /** Called by the interval alarm while the screen is on. */
    fun onTick(context: Context) {
        val mode = Settings.changeMode(context)
        if (mode <= 0) return
        // Inexact alarms may fire a little early; allow a 10% margin.
        if (System.currentTimeMillis() - lastChange(context) >= mode * MINUTE * 9 / 10) {
            advance(context)
            WordWidget.updateAll(context)
        }
        Scheduler.schedule(context)
    }

    @Synchronized
    fun stats(context: Context): Stats {
        val history = loadHistory(context)
        val now = System.currentTimeMillis()
        val words = Vocab.words(context)
        val phrases = Vocab.phrases(context)
        return Stats(
            seenWords = words.count { it.key in history },
            totalWords = words.size,
            seenPhrases = phrases.count { it.key in history },
            totalPhrases = phrases.size,
            due = history.values.count { it.due <= now },
        )
    }

    @Synchronized
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun loadHistory(context: Context): MutableMap<String, Progress> {
        val raw = prefs(context).getString("history", null) ?: return mutableMapOf()
        val json = JSONObject(raw)
        val map = HashMap<String, Progress>(json.length())
        for (key in json.keys()) {
            val a = json.getJSONArray(key)
            map[key] = Progress(a.getInt(0), a.getLong(1), a.getInt(2))
        }
        return map
    }

    private fun saveHistory(context: Context, history: Map<String, Progress>) {
        val json = JSONObject()
        for ((key, v) in history) json.put(key, JSONArray().put(v.box).put(v.due).put(v.count))
        prefs(context).edit().putString("history", json.toString()).apply()
    }
}
