package digital.tripolskiy.ruesen

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opening the app is a foreground moment: (re)start the screen watcher if it is needed.
        Scheduler.sync(this)
        setContent {
            val context = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    // Bumped after every change so the screen re-reads settings and state.
    var version by remember { mutableIntStateOf(0) }
    fun changed(block: () -> Unit) {
        block()
        WordWidget.updateAll(context)
        Scheduler.sync(context)
        version++
    }
    // Widget taps (also in the preview) change the card outside Compose; refresh when they do.
    DisposableEffect(Unit) {
        Engine.listener = { version++ }
        onDispose { Engine.listener = null }
    }

    Column(
        Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ruesen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Русский · English · Español — виджет для рабочего стола",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        WidgetPreview(version)

        if (!read(version) { WordWidget.hasWidgets(context) }) {
            Button(onClick = { pinWidget(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить виджет на рабочий стол")
            }
        }

        Section("Когда менять слово") {
            Settings.changeModes.forEach { (value, label) ->
                Choice(label, read(version) { Settings.changeMode(context) } == value) {
                    changed { Settings.setChangeMode(context, value) }
                }
            }
            Hint(
                "«При включении экрана» — новое слово каждый раз, когда вы берёте телефон. " +
                    "Интервальные режимы меняют слово при включении экрана, если прошло нужное время, " +
                    "и по таймеру, пока экран включён.",
            )
        }

        Section("Повторения") {
            Settings.repeatModes.forEach { (value, label) ->
                Choice(label, read(version) { Settings.repeatEvery(context) } == value) {
                    changed { Settings.setRepeatEvery(context, value) }
                }
            }
            Hint("Уже показанные слова возвращаются через растущие интервалы: 30 мин → 4 ч → 1 день → 3 → 7 → 21 → 60 дней.")
        }

        Section("Порядок новых слов") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Сначала самые частотные", Modifier.weight(1f))
                Switch(
                    checked = read(version) { Settings.frequencyOrder(context) },
                    onCheckedChange = { on -> changed { Settings.setFrequencyOrder(context, on) } },
                )
            }
            Hint("Выключено — новые слова и фразы в случайном порядке. Слова и фразы всегда чередуются.")
        }

        Section("Озвучка") {
            Text("Английский", fontWeight = FontWeight.Medium)
            Settings.enVoices.forEach { (tag, label) ->
                Choice("${WordWidget.flag(tag)}  $label", read(version) { Settings.enVoice(context) } == tag) {
                    changed { Settings.setEnVoice(context, tag) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Испанский", fontWeight = FontWeight.Medium)
            Settings.esVoices.forEach { (tag, label) ->
                Choice("${WordWidget.flag(tag)}  $label", read(version) { Settings.esVoice(context) } == tag) {
                    changed { Settings.setEsVoice(context, tag) }
                }
            }
        }

        val stats = read(version) { Engine.stats(context) }
        Section("Прогресс") {
            Text("Слов показано: ${stats.seenWords} из ${stats.totalWords}")
            Text("Фраз показано: ${stats.seenPhrases} из ${stats.totalPhrases}")
            Text("Ждут повторения: ${stats.due}")
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { changed { Engine.reset(context) } }) { Text("Сбросить прогресс") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The real widget RemoteViews, so the preview and its buttons behave exactly like on the home screen. */
@Composable
private fun WidgetPreview(version: Int) {
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
            frame.tag = version
            frame.removeAllViews()
            frame.addView(
                WordWidget.views(frame.context, WordWidget.screenWidthDp(frame.context) - 32f).apply(frame.context, frame),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        },
        modifier = Modifier.fillMaxWidth().height(112.dp),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column { content() }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Evaluates [block] again whenever [version] changes. */
@Composable
private fun <T> read(version: Int, block: () -> T): T = remember(version) { block() }

private fun pinWidget(context: Context) {
    val manager = context.getSystemService(AppWidgetManager::class.java)
    val pinned = manager.isRequestPinAppWidgetSupported &&
        manager.requestPinAppWidget(ComponentName(context, WordWidget::class.java), null, null)
    if (!pinned) {
        Toast.makeText(
            context,
            "Лаунчер не умеет добавлять виджеты по запросу — добавьте «Ruesen» из списка виджетов лаунчера",
            Toast.LENGTH_LONG,
        ).show()
    }
}
