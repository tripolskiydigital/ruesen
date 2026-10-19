package digital.tripolskiy.ruesen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.util.UUID

/** Shared TextToSpeech instance; initialised lazily and released after a few idle minutes. */
object Speaker {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = ArrayList<() -> Unit>()
    private val callbacks = HashMap<String, () -> Unit>()
    private val release = Runnable {
        tts?.shutdown()
        tts = null
        ready = false
    }

    fun speak(context: Context, text: String, lang: String, onDone: () -> Unit) {
        val app = context.applicationContext
        main.post {
            main.removeCallbacks(release)
            val action = { say(app, text, lang, onDone) }
            if (ready) {
                action()
            } else {
                queue += action
                if (tts == null) init(app)
            }
        }
    }

    private fun init(context: Context) {
        tts = TextToSpeech(context) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setOnUtteranceProgressListener(listener)
                    val pending = queue.toList()
                    queue.clear()
                    pending.forEach { it() }
                } else {
                    tts = null
                    queue.clear()
                    Toast.makeText(context, "Синтез речи недоступен", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun say(context: Context, text: String, lang: String, onDone: () -> Unit) {
        val engine = tts ?: return onDone()
        val result = engine.setLanguage(Settings.locale(lang))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(
                context,
                "Нет голоса для $lang. Установите его: Настройки → Специальные возможности → Синтез речи",
                Toast.LENGTH_LONG,
            ).show()
        }
        engine.setSpeechRate(0.9f)
        val id = UUID.randomUUID().toString()
        callbacks[id] = onDone
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            callbacks.remove(id)?.invoke()
        }
        main.postDelayed(release, 5 * 60_000L)
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {}
        override fun onDone(utteranceId: String) = complete(utteranceId)
        override fun onStop(utteranceId: String, interrupted: Boolean) = complete(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = complete(utteranceId)

        private fun complete(id: String) {
            main.post { callbacks.remove(id)?.invoke() }
        }
    }
}
