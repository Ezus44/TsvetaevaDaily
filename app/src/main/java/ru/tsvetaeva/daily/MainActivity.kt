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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.core.Dates
import ru.tsvetaeva.daily.core.Poem
import ru.tsvetaeva.daily.core.WikiParser
import ru.tsvetaeva.daily.data.Prefs
import ru.tsvetaeva.daily.notify.DailyNotifications
import ru.tsvetaeva.daily.ui.TsvetaevaTheme
import java.time.Instant
import java.time.LocalDate
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
                val prefs = remember { Prefs(context) }

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
                val anyLoaded = state.authors.values.any { it.today != null }
                LaunchedEffect(anyLoaded) {
                    if (anyLoaded && state.notifyEnabled) askPermissionIfNeeded(force = false)
                }

                var showSettings by remember { mutableStateOf(false) }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        state = state,
                        onSelect = vm::select,
                        onAnother = { vm.another(it) },
                        onRetry = { vm.retry(it) },
                        onShare = { a, p -> share(a, p) },
                        onOpenWiki = { openWiki(it) },
                        onSettings = { showSettings = true },
                    )
                }

                if (showSettings) SettingsDialog(
                    state = state,
                    onDismiss = { showSettings = false; vm.clearMessage() },
                    onMain = vm::setMain,
                    onNearDate = { vm.setNearDate(it) },
                    onNotify = { on ->
                        vm.setNotify(on)
                        if (on) askPermissionIfNeeded(force = true)
                    },
                    onPickTime = {
                        TimePickerDialog(context, { _, h, m -> vm.setNotifyTime(h, m) },
                            state.notifyHour, state.notifyMinute, true).show()
                    },
                    onRefresh = { vm.refreshCurrent() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun share(author: Author, p: Poem) {
        val text = buildString {
            append(p.title).append("\n\n").append(p.text)
            p.dateText?.let { append("\n\n").append(it) }
            append("\n\n").append(author.fullName)
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

private val AUTHORS = Author.entries

@Composable
private fun MainScreen(
    state: UiState,
    onSelect: (Author) -> Unit,
    onAnother: (Author) -> Unit,
    onRetry: (Author) -> Unit,
    onShare: (Author, Poem) -> Unit,
    onOpenWiki: (Poem) -> Unit,
    onSettings: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = AUTHORS.indexOf(state.selected)) { AUTHORS.size }
    val scope = rememberCoroutineScope()
    // Вкладка выбирается и касанием, и свайпом.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { onSelect(AUTHORS[it]) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.background,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
        ) {
            AUTHORS.forEachIndexed { i, a ->
                Tab(
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.tag)
                            if (a == state.main) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.Star, contentDescription = "основной", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                )
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { AUTHORS[it].key }) { page ->
            val author = AUTHORS[page]
            val ui = state.of(author)
            when {
                ui.today != null -> PoemScreen(
                    author = author,
                    ui = ui,
                    onAnother = { onAnother(author) },
                    onShare = { onShare(author, ui.today.poem) },
                    onOpenWiki = { onOpenWiki(ui.today.poem) },
                )
                ui.error != null -> ErrorScreen(ui.error, onRetry = { onRetry(author) })
                else -> LoadingScreen(author, ui.progress)
            }
        }
    }
}

@Composable
private fun PoemScreen(
    author: Author,
    ui: AuthorUi,
    onAnother: () -> Unit,
    onShare: () -> Unit,
    onOpenWiki: () -> Unit,
) {
    val today = ui.today ?: return
    Column(Modifier.fillMaxSize()) {
        if (ui.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))

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
                        author.fullName,
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
private fun LoadingScreen(author: Author, progress: String?) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            "Собираю все стихи ${author.genitive} с Викитеки. Это нужно сделать один раз и займёт " +
                "несколько минут, дальше всё работает без интернета.",
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
    onMain: (Author) -> Unit,
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
                Text("Основной автор", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Открывается при запуске, его стих приходит в уведомлении",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AUTHORS.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onMain(a) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.main == a, onClick = { onMain(a) })
                        Text(a.fullName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
                val ui = state.current
                val total = ui.today?.total ?: 0
                val last = if (ui.lastRefreshMillis > 0)
                    Instant.ofEpochMilli(ui.lastRefreshMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", RU))
                else "—"
                Text(
                    "${state.selected.fullName}\nВ базе: $total стихотворений\nОбновлено: $last",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRefresh, enabled = !ui.refreshing && !ui.loading) {
                    Text(if (ui.refreshing || ui.loading) "Обновляю…" else "Обновить из Викитеки")
                }
                ui.refreshMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Тексты: ru.wikisource.org (Викитека), общественное достояние. База каждого автора " +
                        "собирается при первом открытии вкладки и обновляется раз в неделю.",
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
