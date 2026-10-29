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

    @Test
    fun poemSplitAcrossScanPagesIsJoined() {
        val html = """
<div class="mw-parser-output"><div class="text"><div class="poem"><p>Пустая улица. Один огонь в окне.<br />
Еврей-аптекарь охает во сне.<br /></p></div></div>
<span class="pagenum" id="36">[36]</span><div class="text"><div class="poem"><p>А перед шкапом с надписью venena,<br />
Хозяйственно согнув скрипучие колена,<br /></p></div></div>
<h3>Другое</h3>
<div class="poem"><p>Совсем другой стих, строка первая,<br />и строка вторая.</p></div></div>"""
        val page = WikiParser.parse("Пустая улица. Один огонь в окне (Блок)/1923 (СО)", HtmlToWiki.convert(html), author = Author.BLOK)
        assertEquals(page.poems.map { it.text }.toString(), 2, page.poems.size)
        assertTrue(page.poems[0].text, page.poems[0].text.contains("во сне.\nА перед шкапом"))
    }

    @Test
    fun editionsOfOnePoemMerge() {
        fun p(page: String, text: String, year: Int? = null) = Poem(page, page, "Ночь", text, year = year)
        val old = p("Ночь, улица, фонарь, аптека (Блок)/1914 (ДО)",
            "Ночь, улица, фонарь, аптека,\nБезсмысленный и тусклый свѣтъ.\nЖиви еще хоть четверть вѣка —\nВсе будетъ такъ. Исхода нѣтъ.", 1912)
        val a = p("Ночь, улица, фонарь, аптека (Блок)/1923 (СО)",
            "Ночь, улица, фонарь, аптека,\nБессмысленный и тусклый свет.\nЖиви еще хоть четверть века —\nВсё будет так. Исхода нет.")
        val b = p("Ночь, улица, фонарь, аптека (Блок)/1960 (СО)",
            "Ночь, улица, фонарь, аптека,\nБессмысленный и тусклый свет.\nЖиви ещё хоть четверть века —\nВсё будет так. Исхода нет.\n\nУмрёшь — начнёшь опять сначала")
        val other = p("Пустая улица. Один огонь в окне (Блок)/1960 (СО)", "Пустая улица. Один огонь в окне.\nЕврей-аптекарь охает во сне.")
        val r = Dedupe.dedupe(listOf(old, a, b, other))
        assertEquals(r.map { it.id }.toString(), 2, r.size)
        assertEquals(b.id, r[0].id)
        assertEquals(1912, r[0].year)
    }
}
