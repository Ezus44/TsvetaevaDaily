package ru.tsvetaeva.daily.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.tsvetaeva.daily.core.DateInfo
import ru.tsvetaeva.daily.core.Dates
import ru.tsvetaeva.daily.core.HtmlToWiki
import ru.tsvetaeva.daily.core.Poem
import ru.tsvetaeva.daily.core.PoemPicker
import ru.tsvetaeva.daily.core.WikiParser
import java.time.LocalDate
import kotlin.random.Random

/**
 * Собирает все стихотворения Цветаевой с ru.wikisource.org, хранит их офлайн
 * и выбирает «стих дня».
 */
class PoemRepository private constructor(context: Context) {

    private val store = PoemStore(context)
    val prefs = Prefs(context)
    private val api = WikiApi()
    private val mutex = Mutex()

    @Volatile private var cache: List<Poem>? = null

    data class Today(val poem: Poem, val distance: Int?, val total: Int)

    suspend fun poems(): List<Poem> = cache ?: withContext(Dispatchers.IO) {
        store.load().also { cache = it }
    }

    fun needsRefresh(): Boolean =
        System.currentTimeMillis() - prefs.lastRefreshMillis > REFRESH_INTERVAL_MS

    /**
     * Обходит Викитеку: оглавления → страницы стихов и циклов → подстраницы.
     * Берутся только страницы, в названии которых указан автор «(Цветаева)».
     */
    suspend fun refresh(onProgress: (pages: Int, poems: Int) -> Unit = { _, _ -> }): Int =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val queue = ArrayDeque<String>()
                val seen = HashSet<String>()
                val hints = HashMap<String, DateInfo>()
                val found = LinkedHashMap<String, Poem>()

                fun enqueue(t: String) { if (seen.add(t)) queue.addLast(t) }
                SEED_PAGES.forEach(::enqueue)
                for (cat in SEED_CATEGORIES) {
                    runCatching { api.categoryMembers(cat) }.getOrDefault(emptyList())
                        .filter(WikiParser::isRelevantTitle).forEach(::enqueue)
                }

                var pagesDone = 0
                while (queue.isNotEmpty() && pagesDone < MAX_PAGES) {
                    val batch = ArrayList<String>()
                    while (queue.isNotEmpty() && batch.size < 50) batch += queue.removeFirst()
                    val pages = api.fetchContents(batch)
                    for (page in pages) {
                        seen += page.title // перенаправления: итоговое название тоже считаем посещённым
                        var parsed = WikiParser.parse(page.title, page.wikitext, hints[page.title])
                        if (parsed.poems.isEmpty() && !parsed.isProse && PAGES_TAG.containsMatchIn(page.wikitext)) {
                            // Текст включён из страниц скана — берём отрисованный HTML.
                            val html = runCatching { api.parseHtml(page.title) }.getOrNull()
                            if (html != null) {
                                val fromHtml = WikiParser.parse(page.title, HtmlToWiki.convert(html), hints[page.title])
                                parsed = fromHtml.copy(links = parsed.links + fromHtml.links)
                            }
                        }
                        hints.putAll(parsed.dateHints)
                        for (p in parsed.poems) found[p.id] = p
                        if (!parsed.isProse) parsed.links.filter(WikiParser::isRelevantTitle).forEach(::enqueue)
                    }
                    pagesDone += batch.size
                    onProgress(pagesDone, found.size)
                }

                // Подсказки из оглавлений могли прийти позже самих страниц — дополняем даты.
                val withHints = found.values.map { p ->
                    val h = hints[p.pageTitle]
                    if (h == null || p.id != p.pageTitle) p else {
                        val d = DateInfo(p.day, p.month, p.year).orElse(h)
                        p.copy(day = d.day, month = d.month, year = d.year)
                    }
                }
                val result = dedupe(withHints).sortedWith(compareBy({ it.year ?: 9999 }, { it.month ?: 13 }, { it.day ?: 32 }))
                if (result.size < MIN_POEMS) {
                    throw IllegalStateException("С Викитеки получено слишком мало стихов (${result.size}). Попробуйте позже.")
                }
                store.save(result)
                cache = result
                prefs.lastRefreshMillis = System.currentTimeMillis()
                result.size
            }
        }

    /** Стихотворение на сегодня (одно и то же весь день, пока не нажать «Другое»). */
    suspend fun today(date: LocalDate = LocalDate.now()): Today? {
        val all = poems()
        if (all.isEmpty()) return null
        val epoch = date.toEpochDay()
        if (prefs.todayEpochDay == epoch) {
            all.firstOrNull { it.id == prefs.todayId }?.let { return Today(it, distanceFor(it, date), all.size) }
        }
        val pick = PoemPicker.pick(
            all, date, prefs.recent.toSet(), prefs.nearDate, PoemPicker.randomForDay(date),
        ) ?: return null
        remember(pick.poem, epoch, all.size)
        return Today(pick.poem, distanceFor(pick.poem, date), all.size)
    }

    /** Другое стихотворение вместо текущего. */
    suspend fun another(date: LocalDate = LocalDate.now()): Today? {
        val all = poems()
        if (all.isEmpty()) return null
        val pick = PoemPicker.pick(
            all, date, prefs.recent.toSet(), prefs.nearDate, Random.Default, exclude = prefs.todayId,
        ) ?: return null
        remember(pick.poem, date.toEpochDay(), all.size)
        return Today(pick.poem, distanceFor(pick.poem, date), all.size)
    }

    /** Сбросить выбор на сегодня (например, после смены режима). */
    fun resetToday() {
        prefs.todayEpochDay = Long.MIN_VALUE
    }

    private fun remember(poem: Poem, epoch: Long, total: Int) {
        prefs.todayEpochDay = epoch
        prefs.todayId = poem.id
        prefs.addRecent(poem.id, max = (total / 2).coerceIn(1, 120))
    }

    private fun distanceFor(p: Poem, date: LocalDate): Int? =
        if (p.hasDayMonth) Dates.distance(p.month!!, p.day!!, date.monthValue, date.dayOfMonth) else null

    /** Одно и то же стихотворение может лежать и на отдельной странице, и внутри сборника. */
    private fun dedupe(poems: List<Poem>): List<Poem> {
        val byKey = LinkedHashMap<String, Poem>()
        for (p in poems) {
            val key = p.text.lowercase().filter { it.isLetter() }.take(120)
            val prev = byKey[key]
            byKey[key] = when {
                prev == null -> p
                score(p) > score(prev) -> p
                else -> prev
            }
        }
        return byKey.values.toList()
    }

    private fun score(p: Poem): Int =
        (if (p.hasDayMonth) 4 else 0) + (if (p.year != null) 2 else 0) + (if (p.id == p.pageTitle) 1 else 0)

    companion object {
        const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_PAGES = 4000
        private const val MIN_POEMS = 20
        private val PAGES_TAG = Regex("""<pages\s""", RegexOption.IGNORE_CASE)

        val SEED_PAGES = listOf(
            "Стихотворения 1906—1920 (Цветаева)",
            "Стихотворения 1921—1941 (Цветаева)",
            "Марина Ивановна Цветаева",
            "Автор:Марина Ивановна Цветаева",
        )
        val SEED_CATEGORIES = listOf(
            "Категория:Марина Ивановна Цветаева",
            "Категория:Поэзия Марины Ивановны Цветаевой",
            "Категория:Стихотворения Марины Ивановны Цветаевой",
        )

        @Volatile private var instance: PoemRepository? = null
        fun get(context: Context): PoemRepository =
            instance ?: synchronized(this) {
                instance ?: PoemRepository(context.applicationContext).also { instance = it }
            }
    }
}
