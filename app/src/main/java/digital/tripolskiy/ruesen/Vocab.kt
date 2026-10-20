package digital.tripolskiy.ruesen

import android.content.Context

/** One RU-EN-ES card. [key] is stable across database edits, so progress survives reordering. */
data class Entry(
    val key: String,
    val deck: String,
    val phrase: Boolean,
    val emoji: String,
    val ru: String,
    val en: String,
    val es: String,
    /** Argentine (Rioplatense) Spanish; empty when it matches [es]. */
    val esAr: String,
) {
    fun spanish(argentine: Boolean): String = if (argentine && esAr.isNotEmpty()) esAr else es
}

/** A themed set of cards, stored as assets/decks/<id>.tsv. */
data class Deck(val id: String, val emoji: String, val title: String)

object Vocab {
    val decks = listOf(
        Deck("basic", "📚", "Базовые слова и фразы"),
        Deck("business", "💼", "Бизнес: продажа сайтов и приложений"),
        Deck("sport", "⚽", "Спорт и хобби"),
        Deck("medical", "🩺", "У врача"),
        Deck("kids", "🧸", "Детский сад"),
    )

    @Volatile
    private var cache: List<Entry>? = null

    /** Every card of every deck, duplicates (same kind and English text) kept once. */
    fun all(context: Context): List<Entry> = cache ?: synchronized(this) {
        cache ?: decks.flatMap { read(context, it.id) }.distinctBy { it.key }.also { cache = it }
    }

    fun words(context: Context): List<Entry> = enabled(context).filter { !it.phrase }
    fun phrases(context: Context): List<Entry> = enabled(context).filter { it.phrase }

    fun byKey(context: Context, key: String?): Entry? = key?.let { k -> all(context).firstOrNull { it.key == k } }

    private fun enabled(context: Context): List<Entry> {
        val ids = Settings.enabledDecks(context)
        return all(context).filter { it.deck in ids }
    }

    // Asset format: header line, then "type \t emoji \t ru \t en \t es \t es_ar"; type is w or p,
    // emoji and es_ar may be empty.
    private fun read(context: Context, deck: String): List<Entry> =
        context.assets.open("decks/$deck.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1)
                .map { it.split('\t') }
                .filter { it.size >= 5 && it[3].isNotBlank() }
                .map { c ->
                    val phrase = c[0].trim() == "p"
                    val en = c[3].trim()
                    val es = c[4].trim()
                    val esAr = c.getOrNull(5)?.trim().orEmpty().takeIf { it != es }.orEmpty()
                    val key = (if (phrase) "p:" else "w:") + en.lowercase()
                    Entry(key, deck, phrase, c[1].trim(), c[2].trim(), en, es, esAr)
                }
                .toList()
        }
}
