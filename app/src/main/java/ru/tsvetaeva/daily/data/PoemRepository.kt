package ru.tsvetaeva.daily.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.core.Crawler
import ru.tsvetaeva.daily.core.Dates
import ru.tsvetaeva.daily.core.Poem
import ru.tsvetaeva.daily.core.PoemPicker
import java.time.LocalDate
import kotlin.random.Random

/**
 * Собирает все стихотворения автора с ru.wikisource.org, хранит их офлайн
 * и выбирает «стих дня».
 */
class PoemRepository private constructor(context: Context, val author: Author) {

    private val store = PoemStore(context, author)
    /** Стихи, встроенные в APK (assets/poems): есть сразу, без загрузки. */
    private val bundle = PoemBundle(context, author)
    val prefs = Prefs(context)
    private val state = AuthorPrefs(context, author)
    private val api = WikiApi()

    @Volatile private var cache: List<Poem>? = null

    data class Today(val poem: Poem, val distance: Int?, val total: Int)

    /** Скачанная с Викитеки база важнее встроенной: она свежее. */
    suspend fun poems(): List<Poem> = cache ?: withContext(Dispatchers.IO) {
        (if (store.exists()) store.load() else bundle.load()).also { cache = it }
    }

    /** Стихи автора есть на телефоне: встроены в приложение или скачаны. */
    fun hasData(): Boolean = cache?.isNotEmpty() ?: (store.exists() || bundle.exists)

    /** Стихи встроены в приложение. */
    val isBundled: Boolean get() = bundle.exists

    /** Поверх встроенных лежит скачанная с Викитеки база. */
    fun hasDownloaded(): Boolean = store.exists()

    /** Удалить скачанные стихи: у встроенного автора останутся встроенные, остальных можно загрузить снова. */
    fun delete() {
        store.delete()
        cache = null
        state.clear()
    }

    val lastRefreshMillis: Long get() = if (store.exists()) state.lastRefreshMillis else bundle.generatedAt

    /** Встроенные стихи сами не обновляются — только вместе с приложением или по кнопке. */
    fun needsRefresh(): Boolean =
        store.exists() && System.currentTimeMillis() - state.lastRefreshMillis > REFRESH_INTERVAL_MS

    /**
     * Обходит Викитеку: оглавления → страницы стихов и циклов → подстраницы.
     * Берутся только страницы, в названии которых указан автор: «(Цветаева)», «(Пушкин)»…
     * Одновременно идёт только один обход — чтобы не перегружать Викитеку;
     * [onQueued] вызывается, если приходится ждать обхода другого автора.
     */
    suspend fun refresh(
        onProgress: (pages: Int, poems: Int) -> Unit = { _, _ -> },
        onWait: (seconds: Int) -> Unit = {},
        onQueued: () -> Unit = {},
    ): Int {
        val queued = crawlMutex.isLocked
        if (queued) onQueued()
        return crawlMutex.withLock {
            if (queued) onProgress(0, 0)
            withContext(Dispatchers.IO) {
                api.onWait = onWait
                val crawl = try {
                    Crawler.crawl(api, author, MAX_PAGES, previous = { cache ?: store.load() }, onProgress = onProgress)
                } finally {
                    api.onWait = null
                }
                val result = crawl.poems
                val interrupted = crawl.interrupted
                if (result.size < MIN_POEMS) {
                    throw interrupted
                        ?: IllegalStateException("С Викитеки получено слишком мало стихов (${result.size}). Попробуйте позже.")
                }
                store.save(result)
                cache = result
                state.lastRefreshMillis = if (interrupted == null) System.currentTimeMillis()
                // Неполная база: попробуем дособрать через несколько часов.
                else System.currentTimeMillis() - REFRESH_INTERVAL_MS + RETRY_PARTIAL_MS
                result.size
            }
        }
    }

    /** Стихотворение на сегодня (одно и то же весь день, пока не нажать «Другое»). */
    suspend fun today(date: LocalDate = LocalDate.now()): Today? {
        val all = poems()
        if (all.isEmpty()) return null
        val epoch = date.toEpochDay()
        if (state.todayEpochDay == epoch) {
            all.firstOrNull { it.id == state.todayId }?.let { return Today(it, distanceFor(it, date), all.size) }
        }
        val pick = PoemPicker.pick(
            all, date, state.recent.toSet(), prefs.nearDate, PoemPicker.randomForDay(date, salt = author.ordinal),
        ) ?: return null
        remember(pick.poem, epoch, all.size)
        return Today(pick.poem, distanceFor(pick.poem, date), all.size)
    }

    /** Другое стихотворение вместо текущего. */
    suspend fun another(date: LocalDate = LocalDate.now()): Today? {
        val all = poems()
        if (all.isEmpty()) return null
        val pick = PoemPicker.pick(
            all, date, state.recent.toSet(), prefs.nearDate, Random.Default, exclude = state.todayId,
        ) ?: return null
        remember(pick.poem, date.toEpochDay(), all.size)
        return Today(pick.poem, distanceFor(pick.poem, date), all.size)
    }

    /** Сбросить выбор на сегодня (например, после смены режима). */
    fun resetToday() {
        state.todayEpochDay = Long.MIN_VALUE
    }

    private fun remember(poem: Poem, epoch: Long, total: Int) {
        state.todayEpochDay = epoch
        state.todayId = poem.id
        state.addRecent(poem.id, max = (total / 2).coerceIn(1, 120))
    }

    private fun distanceFor(p: Poem, date: LocalDate): Int? =
        if (p.hasDayMonth) Dates.distance(p.month!!, p.day!!, date.monthValue, date.dayOfMonth) else null

    companion object {
        const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        private const val RETRY_PARTIAL_MS = 3L * 60 * 60 * 1000
        private const val MAX_PAGES = 4000
        private const val MIN_POEMS = 10

        /** Общий на всех авторов: обходы Викитеки идут по очереди. */
        private val crawlMutex = Mutex()

        private val instances = HashMap<Author, PoemRepository>()
        fun get(context: Context, author: Author): PoemRepository =
            synchronized(instances) {
                instances.getOrPut(author) { PoemRepository(context.applicationContext, author) }
            }
    }
}
