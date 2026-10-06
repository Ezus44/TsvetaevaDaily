package ru.tsvetaeva.daily

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.tsvetaeva.daily.data.PoemRepository
import ru.tsvetaeva.daily.notify.DailyNotifications
import java.time.LocalDate

data class UiState(
    val date: LocalDate = LocalDate.now(),
    val today: PoemRepository.Today? = null,
    /** Первая загрузка базы — показываем прогресс вместо стиха. */
    val loading: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    /** Фоновое обновление базы (стих уже на экране). */
    val refreshing: Boolean = false,
    val refreshMessage: String? = null,
    val nearDate: Boolean = true,
    val notifyEnabled: Boolean = true,
    val notifyHour: Int = 9,
    val notifyMinute: Int = 0,
    val lastRefreshMillis: Long = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PoemRepository.get(app)
    private val prefs = repo.prefs
    private val _state = MutableStateFlow(settingsState(UiState()))
    val state: StateFlow<UiState> = _state

    init {
        load()
    }

    private fun settingsState(s: UiState) = s.copy(
        nearDate = prefs.nearDate,
        notifyEnabled = prefs.notifyEnabled,
        notifyHour = prefs.notifyHour,
        notifyMinute = prefs.notifyMinute,
        lastRefreshMillis = prefs.lastRefreshMillis,
    )

    private fun load() = viewModelScope.launch {
        val date = LocalDate.now()
        val t = repo.today(date)
        if (t != null) {
            _state.update { it.copy(date = date, today = t, loading = false, error = null) }
            if (repo.needsRefresh()) refresh(silent = true)
        } else {
            refresh(silent = false)
        }
    }

    /** Вызывается при возвращении в приложение: если наступил новый день — новый стих. */
    fun onResume() {
        if (_state.value.date != LocalDate.now() && !_state.value.loading) load()
    }

    fun retry() = load()

    fun another() = viewModelScope.launch {
        repo.another()?.let { t -> _state.update { it.copy(today = t, date = LocalDate.now()) } }
    }

    fun refresh(silent: Boolean) = viewModelScope.launch {
        if (_state.value.refreshing || _state.value.loading) return@launch
        _state.update {
            if (silent) it.copy(refreshing = true, refreshMessage = null)
            else it.copy(loading = it.today == null, refreshing = it.today != null, error = null,
                progress = "Собираю стихи с Викитеки…", refreshMessage = null)
        }
        try {
            var counts = ""
            val count = repo.refresh(
                onProgress = { pages, poems ->
                    counts = "Просмотрено страниц: $pages\nНайдено стихотворений: $poems"
                    _state.update { it.copy(progress = counts) }
                },
                onWait = { sec ->
                    _state.update {
                        it.copy(progress = listOf(counts, "Викитека просит не торопиться — жду $sec с…")
                            .filter { l -> l.isNotEmpty() }.joinToString("\n"))
                    }
                },
            )
            val t = _state.value.today ?: repo.today()
            _state.update {
                settingsState(it).copy(
                    today = t, loading = false, refreshing = false, progress = null, error = null,
                    refreshMessage = if (silent) null else "База обновлена: $count стихотворений",
                )
            }
        } catch (e: Exception) {
            _state.update {
                if (it.today == null) it.copy(loading = false, refreshing = false, error = errorText(e))
                else it.copy(loading = false, refreshing = false,
                    refreshMessage = if (silent) null else "Не удалось обновить: ${errorText(e)}")
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(refreshMessage = null) }

    fun setNearDate(v: Boolean) = viewModelScope.launch {
        prefs.nearDate = v
        repo.resetToday()
        val t = repo.today()
        _state.update { settingsState(it).copy(today = t ?: it.today) }
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
