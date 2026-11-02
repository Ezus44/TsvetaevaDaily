package ru.tsvetaeva.daily.core

import kotlin.coroutines.cancellation.CancellationException

data class WikiPage(val title: String, val wikitext: String)

/** То, что обходу нужно от Викитеки (в приложении — MediaWiki API, в тестах — заглушка). */
interface WikiSource {
    /** Названия страниц основного пространства в категории. */
    suspend fun categoryMembers(category: String): List<String>

    /** Викитекст до 50 страниц за раз; перенаправления раскрыты, несуществующие пропущены. */
    suspend fun fetchContents(titles: List<String>): List<WikiPage>

    /** Готовый HTML страницы — для текстов, включённых из страниц скана. */
    suspend fun parseHtml(title: String): String?
}

/**
 * Обход Викитеки: оглавления и категории автора → страницы стихов и циклов → подстраницы.
 * Берутся только страницы, в названии которых указан автор: «(Цветаева)», «(Пушкин)»…
 * Общий для приложения и для сборщика встроенных стихов (tools/poems).
 */
object Crawler {

    class Result(
        /** Стихи без дублей, по дате написания. */
        val poems: List<Poem>,
        /** Викитека перестала отвечать на середине — собрано не всё. */
        val interrupted: Exception?,
        val pages: Int,
    )

    /** Текст включён из страниц скана: `<pages …/>` или `{{Страница|Книга.djvu/34|…}}` (так у Блока). */
    private val pagesTag = Regex("""<pages\s|\{\{\s*Страница\s*\|""", RegexOption.IGNORE_CASE)

    /**
     * @param previous что было собрано раньше: при неполном обходе не теряем это.
     */
    suspend fun crawl(
        source: WikiSource,
        author: Author,
        maxPages: Int = 4000,
        previous: () -> List<Poem> = { emptyList() },
        onProgress: (pages: Int, poems: Int) -> Unit = { _, _ -> },
    ): Result {
        val queue = ArrayDeque<String>()
        val seen = HashSet<String>()
        val hints = HashMap<String, DateInfo>()
        val found = LinkedHashMap<String, Poem>()

        fun enqueue(t: String) { if (seen.add(t)) queue.addLast(t) }
        author.seedPages.forEach(::enqueue)
        for (cat in author.seedCategories) {
            val members = try { source.categoryMembers(cat) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            members.filter(author::isRelevantTitle).forEach(::enqueue)
        }

        var pagesDone = 0
        var interrupted: Exception? = null
        try {
            while (queue.isNotEmpty() && pagesDone < maxPages) {
                val batch = ArrayList<String>()
                while (queue.isNotEmpty() && batch.size < 50) batch += queue.removeFirst()
                for (page in source.fetchContents(batch)) {
                    seen += page.title // перенаправления: итоговое название тоже считаем посещённым
                    var parsed = WikiParser.parse(page.title, page.wikitext, hints[page.title], author)
                    if (parsed.poems.isEmpty() && !parsed.isProse && pagesTag.containsMatchIn(page.wikitext)) {
                        // Текст включён из страниц скана — берём отрисованный HTML.
                        val html = try { source.parseHtml(page.title) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            interrupted = e
        }

        // Подсказки из оглавлений могли прийти позже самих страниц — дополняем даты.
        val withHints = found.values.map { p ->
            val h = hints[p.pageTitle]
            if (h == null || p.id != p.pageTitle) p else {
                val d = DateInfo(p.day, p.month, p.year).orElse(h)
                p.copy(day = d.day, month = d.month, year = d.year)
            }
        }
        val merged = if (interrupted == null) withHints else {
            val byId = LinkedHashMap<String, Poem>()
            previous().forEach { byId[it.id] = it }
            withHints.forEach { byId[it.id] = it }
            byId.values.toList()
        }
        val poems = Dedupe.dedupe(merged).sortedWith(compareBy({ it.year ?: 9999 }, { it.month ?: 13 }, { it.day ?: 32 }))
        return Result(poems, interrupted, pagesDone)
    }
}
