package ru.tsvetaeva.daily.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Обход на подставной «Викитеке»: оглавление, категория, издания, страница из скана. */
class CrawlerTest {

    private class FakeWiki(private val pages: Map<String, String>, private val html: Map<String, String> = emptyMap()) : WikiSource {
        val requested = ArrayList<String>()
        override suspend fun categoryMembers(category: String) =
            if (category == "Категория:Поэзия Александра Александровича Блока") listOf("Скифы (Блок)", "Чужое (Брюсов)") else emptyList()
        override suspend fun fetchContents(titles: List<String>): List<WikiPage> {
            requested += titles
            return titles.mapNotNull { t -> pages[t]?.let { WikiPage(t, it) } }
        }
        override suspend fun parseHtml(title: String) = html[title]
    }

    @Test
    fun crawlsIndexCategoryEditionsAndScans() = runBlocking {
        val wiki = FakeWiki(
            mapOf(
                "Стихотворения Блока 1904—1916" to "=== 1912 ===\n* [[Ночь, улица, фонарь, аптека (Блок)]] (10 октября)\n",
                "Ночь, улица, фонарь, аптека (Блок)" to "* [[/1923 (СО)|Текст]]\n* [[/1960 (СО)|Текст]]\n",
                "Ночь, улица, фонарь, аптека (Блок)/1923 (СО)" to "{{Страница|Собрание. Т. 3 (1923).djvu/34|section=x}}",
                "Ночь, улица, фонарь, аптека (Блок)/1960 (СО)" to
                    "{{poemx|* * *|\nНочь, улица, фонарь, аптека,\nБессмысленный и тусклый свет.\nЖиви ещё хоть четверть века —\nВсё будет так. Исхода нет.\n|10 октября 1912}}",
                "Скифы (Блок)" to "{{poemx|Скифы|\nМильоны — вас. Нас — тьмы, и тьмы, и тьмы.\nПопробуйте, сразитесь с нами!\n|30 января 1918}}",
            ),
            html = mapOf(
                "Ночь, улица, фонарь, аптека (Блок)/1923 (СО)" to
                    "<div class=\"poem\"><p>Ночь, улица, фонарь, аптека,<br>Бессмысленный и тусклый свет.<br>Живи еще хоть четверть века —<br>Все будет так. Исхода нет.</p></div>",
            ),
        )
        val r = Crawler.crawl(wiki, Author.BLOK)
        assertNull(r.interrupted)
        assertEquals(r.poems.map { it.title }.toString(), listOf("Ночь, улица, фонарь, аптека", "Скифы"), r.poems.map { it.title })
        val night = r.poems[0]
        assertTrue(night.toString(), night.day == 10 && night.month == 10 && night.year == 1912)
        assertTrue("чужие страницы не запрашиваются", "Чужое (Брюсов)" !in wiki.requested)
    }

    @Test
    fun interruptedCrawlKeepsPrevious() = runBlocking {
        val broken = object : WikiSource {
            override suspend fun categoryMembers(category: String) = emptyList<String>()
            override suspend fun fetchContents(titles: List<String>): List<WikiPage> = throw java.io.IOException("нет сети")
            override suspend fun parseHtml(title: String): String? = null
        }
        val old = Poem("Скифы (Блок)", "Скифы (Блок)", "Скифы", "Мильоны — вас.\nНас — тьмы.", year = 1918)
        val r = Crawler.crawl(broken, Author.BLOK, previous = { listOf(old) })
        assertTrue(r.interrupted is java.io.IOException)
        assertEquals(listOf(old), r.poems)
    }
}
