package ru.tsvetaeva.daily.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.core.DateInfo
import ru.tsvetaeva.daily.core.Dates
import ru.tsvetaeva.daily.core.Dedupe
import ru.tsvetaeva.daily.core.HtmlToWiki
import ru.tsvetaeva.daily.core.Poem
import ru.tsvetaeva.daily.core.PoemPicker
import ru.tsvetaeva.daily.core.WikiParser
import java.time.LocalDate
import kotlin.random.Random

/**
 * Собирает все стихотворения автора с ru.wikisource.org, хранит их офлайн
 * и выбирает «стих дня».
 */
class PoemRepository private constructor(context: Context, val author: Author) {

    private val store = PoemStore(context, author)
    val prefs = Prefs(context)
    private val state = AuthorPrefs(context, author)
    private val api = WikiApi()

    @Volatile private var cache: List<Poem>? = null

    data class Today(val poem: Poem, val distance: Int?, val total: Int)

    suspend fun poems(): List<Poem> = cache ?: withContext(Dispatchers.IO) {
        store.load().also { cache = it }
    }

    /** Стихи автора уже скачаны. */
    fun hasData(): Boolean = cache?.isNotEmpty() ?: store.exists()

    /** Удалить скачанные стихи (загрузить снова можно в любой момент). */
    fun delete() {
        store.delete()
        cache = emptyList()
        state.clear()
    }

    val lastRefreshMillis: Long get() = state.lastRefreshMillis

    fun needsRefresh(): Boolean =
        System.currentTimeMillis() - state.lastRefreshMillis > REFRESH_INTERVAL_MS

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
                val queue = ArrayDeque<String>()
                val seen = HashSet<String>()
                val hints = HashMap<String, DateInfo>()
                val found = LinkedHashMap<String, Poem>()

                fun enqueue(t: String) { if (seen.add(t)) queue.addLast(t) }
                author.seedPages.forEach(::enqueue)
                for (cat in author.seedCategories) {
                    runCatching { api.categoryMembers(cat) }.getOrDefault(emptyList())
                        .filter(author::isRelevantTitle).forEach(::enqueue)
                }

                var pagesDone = 0
                var interrupted: Exception? = null
                try {
                while (queue.isNotEmpty() && pagesDone < MAX_PAGES) {
                    val batch = ArrayList<String>()
                    while (queue.isNotEmpty() && batch.size < 50) batch += queue.removeFirst()
                    val pages = api.fetchContents(batch)
                    for (page in pages) {
                        seen += page.title // перенаправления: итоговое название тоже считаем посещённым
                        var parsed = WikiParser.parse(page.title, page.wikitext, hints[page.title], author)
                        if (parsed.poems.isEmpty() && !parsed.isProse && PAGES_TAG.containsMatchIn(page.wikitext)) {
                            // Текст включён из страниц скана — берём отрисованный HTML.
                            val html = runCatching { api.parseHtml(page.title) }.getOrNull()
                            if (html != null) {
                                val fromHtml = WikiParser.parse(page.title, HtmlToWiki.convert(html), hints[page.title], author)
                                parsed = fromHtml.copy(links = parsed.links + fromHtml.links)
                            }
                        }
                        hints.putAll(parsed.dateHints)
                        for (p in parsed.poems) found[p.id] = p
                        if (!parsed.isProse) parsed.links.filter(author::isRelevantTitle).forEach(::enqueue)
                    }
                    pagesDone += batch.size
                    onProgress(pagesDone, found.size)
                }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Викитека перестала отвечать на середине — сохраним то, что успели собрать.
                    interrupted = e
                } finally {
                    api.onWait = null
                }

                // Подсказки из оглавлений могли прийти позже самих страниц — дополняем даты.
                val withHints = found.values.map { p ->
                    val h = hints[p.pageTitle]
                    if (h == null || p.id != p.pageTitle) p else {
                        val d = DateInfo(p.day, p.month, p.year).orElse(h)
                        p.copy(day = d.day, month = d.month, year = d.year)
                    }
                }
                // При неполном обходе не теряем то, что было собрано раньше.
                val merged = if (interrupted == null) withHints else {
                    val byId = LinkedHashMap<String, Poem>()
                    (cache ?: store.load()).forEach { byId[it.id] = it }
                    withHints.forEach { byId[it.id] = it }
                    byId.values.toList()
                }
                val result = Dedupe.dedupe(merged).sortedWith(compareBy({ it.year ?: 9999 }, { it.month ?: 13 }, { it.day ?: 32 }))
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
        private val PAGES_TAG = Regex("""<pages\s""", RegexOption.IGNORE_CASE)

        /** Общий на всех авторов: обходы Викитеки идут по очереди. */
        private val crawlMutex = Mutex()

        private val instances = HashMap<Author, PoemRepository>()
        fun get(context: Context, author: Author): PoemRepository =
            synchronized(instances) {
                instances.getOrPut(author) { PoemRepository(context.applicationContext, author) }
            }
    }
}
