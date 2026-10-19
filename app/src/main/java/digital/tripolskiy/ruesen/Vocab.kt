package digital.tripolskiy.ruesen

import android.content.Context

/** One RU-EN-ES triple. [key] is stable across database edits, so progress survives reordering. */
data class Entry(
    val key: String,
    val phrase: Boolean,
    val emoji: String,
    val ru: String,
    val en: String,
    val es: String,
)

object Vocab {
    @Volatile
    private var cache: Pair<List<Entry>, List<Entry>>? = null

    fun words(context: Context): List<Entry> = load(context).first
    fun phrases(context: Context): List<Entry> = load(context).second

    fun byKey(context: Context, key: String?): Entry? {
        if (key == null) return null
        val (w, p) = load(context)
        return w.firstOrNull { it.key == key } ?: p.firstOrNull { it.key == key }
    }

    private fun load(context: Context): Pair<List<Entry>, List<Entry>> = cache ?: synchronized(this) {
        cache ?: Pair(read(context, "words.tsv", false), read(context, "phrases.tsv", true)).also { cache = it }
    }

    // Asset format: header line, then "emoji \t ru \t en \t es" (emoji may be empty).
    private fun read(context: Context, file: String, phrase: Boolean): List<Entry> =
        context.assets.open(file).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1)
                .map { it.split('\t') }
                .filter { it.size >= 4 && it[2].isNotBlank() }
                .map { c ->
                    val prefix = if (phrase) "p:" else "w:"
                    Entry(prefix + c[2].trim().lowercase(), phrase, c[0].trim(), c[1].trim(), c[2].trim(), c[3].trim())
                }
                .toList()
        }
}
