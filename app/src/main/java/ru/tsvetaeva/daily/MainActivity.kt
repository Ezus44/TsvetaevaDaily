package ru.tsvetaeva.daily

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.tsvetaeva.daily.core.Dates
import ru.tsvetaeva.daily.core.Poem
import ru.tsvetaeva.daily.core.WikiParser
import ru.tsvetaeva.daily.data.PoemRepository
import ru.tsvetaeva.daily.notify.DailyNotifications
import ru.tsvetaeva.daily.ui.TsvetaevaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DailyNotifications.ensureChannel(this)
        DailyNotifications.schedule(this)
        DailyNotifications.scheduleWeeklyRefresh(this)

        setContent {
            TsvetaevaTheme {
                val state by vm.state.collectAsState()
                val context = LocalContext.current
                val prefs = remember { PoemRepository.get(context).prefs }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                fun askPermissionIfNeeded(force: Boolean) {
                    if (Build.VERSION.SDK_INT >= 33 && !DailyNotifications.canPost(context) &&
                        (force || !prefs.askedNotificationPermission)
                    ) {
                        prefs.askedNotificationPermission = true
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                // Один раз после первой загрузки предложим включить уведомления.
                LaunchedEffect(state.today != null) {
                    if (state.today != null && state.notifyEnabled) askPermissionIfNeeded(force = false)
                }

                var showSettings by remember { mutableStateOf(false) }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        state.today != null -> PoemScreen(
                            state = state,
                            onAnother = vm::another,
                            onShare = { share(state.today!!.poem) },
                            onOpenWiki = { openWiki(state.today!!.poem) },
                            onSettings = { showSettings = true },
                        )
                        state.error != null -> ErrorScreen(state.error!!, onRetry = vm::retry)
                        else -> LoadingScreen(state.progress)
                    }
                }

                if (showSettings) SettingsDialog(
                    state = state,
                    onDismiss = { showSettings = false; vm.clearMessage() },
                    onNearDate = vm::setNearDate,
                    onNotify = { on ->
                        vm.setNotify(on)
                        if (on) askPermissionIfNeeded(force = true)
                    },
                    onPickTime = {
                        TimePickerDialog(context, { _, h, m -> vm.setNotifyTime(h, m) },
                            state.notifyHour, state.notifyMinute, true).show()
                    },
                    onRefresh = { vm.refresh(silent = false) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun share(p: Poem) {
        val text = buildString {
            append(p.title).append("\n\n").append(p.text)
            p.dateText?.let { append("\n\n").append(it) }
            append("\n\nМарина Цветаева")
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Поделиться стихотворением"))
    }

    private fun openWiki(p: Poem) {
        val url = "https://ru.wikisource.org/wiki/" + Uri.encode(p.pageTitle.replace(' ', '_'), "/")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

private val RU = Locale("ru")

@Composable
private fun PoemScreen(
    state: UiState,
    onAnother: () -> Unit,
    onShare: () -> Unit,
    onOpenWiki: () -> Unit,
    onSettings: () -> Unit,
) {
    val today = state.today ?: return
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))

        AnimatedContent(
            targetState = today,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "poem",
        ) { t ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                ) {
                    DateBadge(t.poem, t.distance)
                    Text(
                        t.poem.title,
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    sourceLabel(t.poem)?.let {
                        Text(
                            it, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    SelectionContainer {
                        Text(
                            t.poem.text,
                            fontFamily = FontFamily.Serif,
                            fontSize = 19.sp,
                            lineHeight = 29.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    t.poem.dateText?.let {
                        Text(
                            it, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                    Text(
                        "Марина Цветаева",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 28.dp, bottom = 24.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAnother) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Другое")
            }
            TextButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text("Поделиться")
            }
            TextButton(onClick = onOpenWiki) { Text("Викитека") }
        }
    }
}

@Composable
private fun DateBadge(p: Poem, distance: Int?) {
    if (distance == null || distance > ru.tsvetaeva.daily.core.PoemPicker.MAX_NEAR_DAYS || !p.hasDayMonth) {
        Spacer(Modifier.height(8.dp)); return
    }
    val written = "${p.day} ${Dates.monthGenitive(p.month!!)}" + (p.year?.let { " $it" } ?: "")
    val label = if (distance == 0) {
        "Написано в этот же день" + (p.year?.let { ", в $it году" } ?: "")
    } else {
        "Написано $written — ${daysWord(distance)} от сегодняшнего числа"
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 16.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun daysWord(n: Int): String {
    val m10 = n % 10; val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> "день"
        m10 in 2..4 && m100 !in 12..14 -> "дня"
        else -> "дней"
    }
    return "$n $w"
}

/** «из „Вечерний альбом“» — если стих взят из сборника или цикла. */
private fun sourceLabel(p: Poem): String? {
    val parts = p.pageTitle.split('/')
    val parent = when {
        parts.size > 1 -> WikiParser.displayTitle(parts[0])
        p.id != p.pageTitle -> WikiParser.displayTitle(p.pageTitle)
        else -> return null
    }
    return if (p.title.startsWith(parent)) null else "из «$parent»"
}

@Composable
private fun LoadingScreen(progress: String?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            "Первый запуск: собираю все стихи Цветаевой с Викитеки. Это займёт минуту-другую, дальше всё работает без интернета.",
            textAlign = TextAlign.Center, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 24.sp,
        )
        progress?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Не получилось загрузить стихи", fontFamily = FontFamily.Serif, fontSize = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRetry) { Text("Попробовать снова") }
    }
}

@Composable
private fun SettingsDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onNearDate: (Boolean) -> Unit,
    onNotify: (Boolean) -> Unit,
    onPickTime: () -> Unit,
    onRefresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        title = { Text("Настройки") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(
                    "Ближе к сегодняшнему числу",
                    "Стих, написанный в этот же день или рядом с ним; если такого нет — случайный",
                    state.nearDate, onNearDate,
                )
                SwitchRow("Уведомление каждый день", null, state.notifyEnabled, onNotify)
                if (state.notifyEnabled) {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onPickTime).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Время уведомления", Modifier.weight(1f))
                        Text(
                            "%02d:%02d".format(state.notifyHour, state.notifyMinute),
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val total = state.today?.total ?: 0
                val last = if (state.lastRefreshMillis > 0)
                    Instant.ofEpochMilli(state.lastRefreshMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", RU))
                else "—"
                Text("В базе: $total стихотворений\nОбновлено: $last", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRefresh, enabled = !state.refreshing) {
                    Text(if (state.refreshing) "Обновляю…" else "Обновить из Викитеки")
                }
                state.refreshMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Тексты: ru.wikisource.org (Викитека), общественное достояние. База обновляется раз в неделю.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
