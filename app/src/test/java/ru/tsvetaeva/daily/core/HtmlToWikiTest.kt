package ru.tsvetaeva.daily.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToWikiTest {
    @Test
    fun scannedPageBecomesPoem() {
        val html = """
<div class="mw-parser-output"><h2><span class="mw-headline" id="x">Тестовое название</span></h2>
<div class="poem">
<p>Первая строка теста,<br />
<span class="pagenum" id="12">[12]</span>вторая&#160;строка теста,<br />
третья строка теста.<sup class="reference"><a href="#n1">[1]</a></sup>
</p>
</div>
<p><i>Москва, 6 октября 1915</i>
</p>
<ol class="references"><li>Примечание</li></ol>
</div>"""
        val wiki = HtmlToWiki.convert(html)
        val page = WikiParser.parse("Тестовое название (Цветаева)", wiki)
        assertEquals(page.toString(), 1, page.poems.size)
        val p = page.poems.single()
        assertEquals("Первая строка теста,\nвторая строка теста,\nтретья строка теста.", p.text)
        assertEquals("Москва, 6 октября 1915", p.dateText)
        assertTrue(p.day == 6 && p.month == 10 && p.year == 1915)
    }
}
