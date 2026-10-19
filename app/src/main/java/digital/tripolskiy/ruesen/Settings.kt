package digital.tripolskiy.ruesen

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object Settings {
    /** Change on every screen-on. */
    const val MODE_SCREEN_ON = 0

    /** Change only by the widget's "next" button. */
    const val MODE_MANUAL = -1

    /** Options shown in the app: mode value (minutes, or one of the MODE_ constants) to label. */
    val changeModes = listOf(
        MODE_SCREEN_ON to "При каждом включении экрана",
        1 to "Каждую минуту",
        5 to "Раз в 5 минут",
        15 to "Раз в 15 минут",
        30 to "Раз в 30 минут",
        60 to "Раз в час",
        180 to "Раз в 3 часа",
        1440 to "Раз в день",
        MODE_MANUAL to "Только кнопкой ↻",
    )

    /** Every N-th card is a repetition; 0 disables repetitions. */
    val repeatModes = listOf(
        0 to "Без повторений",
        6 to "Редко — каждая 6-я карточка",
        4 to "Иногда — каждая 4-я",
        3 to "Часто — каждая 3-я",
    )

    val enVoices = listOf("en-US" to "Американский", "en-GB" to "Британский")
    val esVoices = listOf("es-ES" to "Испания", "es-US" to "Латинская Америка")

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun changeMode(context: Context): Int = prefs(context).getInt("change_mode", MODE_SCREEN_ON)
    fun setChangeMode(context: Context, value: Int) = prefs(context).edit().putInt("change_mode", value).apply()

    fun repeatEvery(context: Context): Int = prefs(context).getInt("repeat_every", 4)
    fun setRepeatEvery(context: Context, value: Int) = prefs(context).edit().putInt("repeat_every", value).apply()

    /** Pick new cards in database (frequency) order instead of randomly. */
    fun frequencyOrder(context: Context): Boolean = prefs(context).getBoolean("frequency_order", false)
    fun setFrequencyOrder(context: Context, value: Boolean) = prefs(context).edit().putBoolean("frequency_order", value).apply()

    fun enVoice(context: Context): String = prefs(context).getString("en_voice", "en-US")!!
    fun setEnVoice(context: Context, value: String) = prefs(context).edit().putString("en_voice", value).apply()

    fun esVoice(context: Context): String = prefs(context).getString("es_voice", "es-ES")!!
    fun setEsVoice(context: Context, value: String) = prefs(context).edit().putString("es_voice", value).apply()

    fun locale(tag: String): Locale = Locale.forLanguageTag(tag)
}
