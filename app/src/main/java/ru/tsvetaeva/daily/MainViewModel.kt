package ru.tsvetaeva.daily

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.data.PoemRepository
import ru.tsvetaeva.daily.data.Prefs
import ru.tsvetaeva.daily.notify.DailyNotifications
import java.time.LocalDate

/** Состояние вкладки одного автора. */
data class AuthorUi(
    val date: LocalDate = LocalDate.now(),
    val today: PoemRepository.Today? = null,
    /** Первая загрузка базы — показываем прогресс вместо стиха. */
    val loading: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    /** Фоновое обновление базы (стих уже на экране). */
    val refreshing: Boolean = false,
    val refreshMessage: String? = null,
    val lastRefreshMillis: Long = 0,
)

data class UiState(
    /** Открытая вкладка. */
    val selected: Author = Author.TSVETAEVA,
    /** Основной автор: открывается при запуске и приходит в уведомлении. */
    val main: Author = Author.TSVETAEVA,
    val authors: Map<Author, AuthorUi> = emptyMap(),
    val nearDate: Boolean = true,
    val notifyEnabled: Boolean = true,
    val notifyHour: Int = 9,
    val notifyMinute: Int = 0,
) {
    fun of(author: Author): AuthorUi = authors[author] ?: AuthorUi()
    val current: AuthorUi get() = of(selected)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(settingsState(UiState(selected = prefs.mainAuthor)))
    val state: StateFlow<UiState> = _state

    /** Авторы, чьи вкладки уже открывались: их стих дня держим актуальным. */
    private val opened = LinkedHashSet<Author>()

    init {
        select(prefs.mainAuthor)
    }

    private fun repo(a: Author) = PoemRepository.get(getApplication(), a)

    private fun settingsState(s: UiState) = s.copy(
        main = prefs.mainAuthor,
        nearDate = prefs.nearDate,
        notifyEnabled = prefs.notifyEnabled,
        notifyHour = prefs.notifyHour,
        notifyMinute = prefs.notifyMinute,
    )

    private fun updateAuthor(a: Author, f: (AuthorUi) -> AuthorUi) =
        _state.update { it.copy(authors = it.authors + (a to f(it.of(a)))) }

    /** Переключение вкладки: база автора собирается при первом открытии. */
    fun select(a: Author) {
        _state.update { it.copy(selected = a) }
        if (opened.add(a)) load(a)
    }

    private fun load(a: Author) = viewModelScope.launch {
        val date = LocalDate.now()
        val repo = repo(a)
        val t = repo.today(date)
        if (t != null) {
            updateAuthor(a) {
                it.copy(date = date, today = t, loading = false, error = null, lastRefreshMillis = repo.lastRefreshMillis)
            }
            if (repo.needsRefresh()) refresh(a, silent = true)
        } else {
            refresh(a, silent = false)
        }
    }

    /** Вызывается при возвращении в приложение: если наступил новый день — новый стих. */
    fun onResume() {
        val now = LocalDate.now()
        for (a in opened) {
            val ui = _state.value.of(a)
            if (ui.date != now && !ui.loading) load(a)
        }
    }

    fun retry(a: Author) = load(a)

    fun another(a: Author) = viewModelScope.launch {
        repo(a).another()?.let { t -> updateAuthor(a) { it.copy(today = t, date = LocalDate.now()) } }
    }

    fun refreshCurrent() = refresh(_state.value.selected, silent = false)

    private fun refresh(a: Author, silent: Boolean) = viewModelScope.launch {
        val ui = _state.value.of(a)
        if (ui.refreshing || ui.loading) return@launch
        updateAuthor(a) {
            if (silent) it.copy(refreshing = true, refreshMessage = null)
            else it.copy(loading = it.today == null, refreshing = it.today != null, error = null,
                progress = "Собираю стихи с Викитеки…", refreshMessage = null)
        }
        val repo = repo(a)
        try {
            var counts = ""
            val count = repo.refresh(
                onProgress = { pages, poems ->
                    counts = "Просмотрено страниц: $pages\nНайдено стихотворений: $poems"
                    updateAuthor(a) { it.copy(progress = counts) }
                },
                onWait = { sec ->
                    updateAuthor(a) {
                        it.copy(progress = listOf(counts, "Викитека просит не торопиться — жду $sec с…")
                            .filter { l -> l.isNotEmpty() }.joinToString("\n"))
                    }
                },
                onQueued = {
                    updateAuthor(a) { it.copy(progress = "Жду, пока соберутся стихи другого автора…") }
                },
            )
            val t = _state.value.of(a).today ?: repo.today()
            updateAuthor(a) {
                it.copy(
                    date = LocalDate.now(), today = t, loading = false, refreshing = false, progress = null, error = null,
                    lastRefreshMillis = repo.lastRefreshMillis,
                    refreshMessage = if (silent) null else "База обновлена: $count стихотворений",
                )
            }
        } catch (e: Exception) {
            updateAuthor(a) {
                if (it.today == null) it.copy(loading = false, refreshing = false, error = errorText(e))
                else it.copy(loading = false, refreshing = false,
                    refreshMessage = if (silent) null else "Не удалось обновить: ${errorText(e)}")
            }
        }
    }

    fun clearMessage() = updateAuthor(_state.value.selected) { it.copy(refreshMessage = null) }

    fun setMain(a: Author) {
        prefs.mainAuthor = a
        _state.update { settingsState(it) }
    }

    fun setNearDate(v: Boolean) = viewModelScope.launch {
        prefs.nearDate = v
        _state.update { settingsState(it) }
        for (a in opened) {
            val repo = repo(a)
            repo.resetToday()
            val t = repo.today()
            updateAuthor(a) { it.copy(today = t ?: it.today) }
        }
    }

    fun setNotify(enabled: Boolean) {
        prefs.notifyEnabled = enabled
        DailyNotifications.schedule(getApplication(), replace = true)
        _state.update { settingsState(it) }
    }

    fun setNotifyTime(hour: Int, minute: Int) {
        prefs.notifyHour = hour
        prefs.notifyMinute = minute
        DailyNotifications.schedule(getApplication(), replace = true)
        _state.update { settingsState(it) }
    }

    private fun errorText(e: Exception): String = when (e) {
        is ru.tsvetaeva.daily.data.RateLimitedException ->
            "Викитека временно ограничила запросы (HTTP 429). Так бывает при включённом VPN: " +
                "отключите его или подождите 10–15 минут и попробуйте снова."
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            "Нет связи с Викитекой. Проверьте интернет."
        else -> e.message ?: e.javaClass.simpleName
    }
}
