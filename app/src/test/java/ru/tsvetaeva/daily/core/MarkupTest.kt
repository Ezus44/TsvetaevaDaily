package ru.tsvetaeva.daily.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разметка стихов на Викитеке у разных авторов (фрагменты настоящих страниц). */
class MarkupTest {

    @Test
    fun poemxWithNumberAndDate() {
        val wiki = """
{{Отексте
| АВТОР                 =[[София Яковлевна Парнок]] (1885—1933)
| НАЗВАНИЕ              =«Снова знак к отплытию нам дан!..»
| ДАТАСОЗДАНИЯ          =1915
}}

{{poemx|60.|
Снова знак к отплытию нам дан!
Дикой полночью из пристани мы выбыли.
Снова сердце — сумасшедший капитан —
правит парус к неотвратимой гибели.
|7 февраля 1915}}

[[Категория:Поэзия Софии Яковлевны Парнок]]
"""
        val p = WikiParser.parse("Снова знак к отплытию нам дан! (София Парнок)", wiki, author = Author.PARNOK).poems.single()
        assertEquals("Снова знак к отплытию нам дан!", p.title)
        assertTrue(p.text, p.text.startsWith("Снова знак к отплытию нам дан!\nДикой полночью"))
        assertEquals("7 февраля 1915", p.dateText)
        assertTrue(p.toString(), p.day == 7 && p.month == 2 && p.year == 1915)
    }

    @Test
    fun f1WithNestedRefAndComment() {
        val wiki = """
{{Отексте
| АВТОР = [[Александр Сергеевич Пушкин]] (1799—1837)
| НАЗВАНИЕ = Пророк
| ДАТАСОЗДАНИЯ = 1826
}}
{{аудиостатья|Ru-Пророк (Пушкин).ogg}}

{{f1|Пророк|
Духовной жаждою томим,
В пустыне мрачной я влачился, —
И дольней лозы прозябанье<ref>То есть: прорастание.</ref>.
И он мне грудь рассек<!--рассЕк--> мечом,
|}}
"""
        val p = WikiParser.parse("Пророк (Пушкин)", wiki, author = Author.PUSHKIN).poems.single()
        assertEquals("Пророк", p.title)
        assertEquals("Духовной жаждою томим,\nВ пустыне мрачной я влачился, —\nИ дольней лозы прозябанье.\nИ он мне грудь рассек мечом,", p.text)
        assertEquals(1826, p.year)
    }

    @Test
    fun mayakovskyLadderAndLineNumbers() {
        val wiki = """
{{v|А ВЫ МОГЛИ БЫ?|
Я сразу смазал карту будня,
плеснувши краску из стакана;
{{№|10}}на флейте водосточных труб?
{{лесенка2|Петров|Капла́ном}}
|<1913>}}
"""
        val p = WikiParser.parse("А вы могли бы? (Маяковский)", wiki, author = Author.MAYAKOVSKY).poems.single()
        assertEquals("А вы могли бы?", p.title)
        assertEquals(
            "Я сразу смазал карту будня,\nплеснувши краску из стакана;\nна флейте водосточных труб?\nПетров\n       Капла́ном",
            p.text,
        )
        assertEquals("<1913>", p.dateText)
        assertEquals(1913, p.year)
    }

    @Test
    fun azLibTables() {
        val wiki = """
= Лоза =

{|
|width="100%"| «[[#0001|Там родина моя, где восходил мой дух…]]»<br>
[[#0002|Орган]]<br>
[[#0003|Тоска]]<br>
[[#0004|В толпе]]<br>
|}

{|
|width="100%"|

==== БЕЛОЙ НОЧЬЮ ====
Не небо — купол безвоздушный<br>
Над голой белизной домов,<br>
Как будто кто-то равнодушный<br>
С вещей и лиц совлек покров.<br>
<br>
И тьма — как будто тень от света,<br>
<br>
<span id="0002"></span>
|}

{|
|width="100%"|

Словно дан мои первоначальные<br>Воскресила ты, весна.<br>
Грезы грезятся мне беспечальные,<br>
Даль младенчески ясна.<br>
|}
"""
        val page = WikiParser.parse("Лоза (Парнок)", wiki, author = Author.PARNOK)
        assertEquals(page.poems.map { it.title }.toString(), 2, page.poems.size)
        assertEquals("БЕЛОЙ НОЧЬЮ", page.poems[0].title)
        assertTrue(page.poems[0].text, page.poems[0].text.startsWith("Не небо — купол безвоздушный\nНад голой"))
        assertTrue(page.poems[0].text, page.poems[0].text.lines().contains(""))
        assertEquals("«Словно дан мои первоначальные…»", page.poems[1].title)
    }

    @Test
    fun twoOrthographyLinksAndSeeds() {
        val wiki = """
==== Стихотворения 1814 г.====
* {{2О|К сестре (Пушкин)|К сестре («Ты хочешь, друг бесценный…»)}}{{Илл}}
* [[Осгар (Пушкин)|Осгар]]
"""
        val page = WikiParser.parse("Стихотворения Пушкина 1809—1825", wiki, author = Author.PUSHKIN)
        assertTrue(page.links.toString(), "К сестре (Пушкин)" in page.links && "Осгар (Пушкин)" in page.links)
        assertEquals(DateInfo(null, null, 1814), page.dateHints["К сестре (Пушкин)"])
        assertEquals("Евгений Онегин", WikiParser.displayTitle("Евгений Онегин (Пушкин)/ПСС 1977 (СО)"))
        assertEquals("Глава 1", WikiParser.displayTitle("Евгений Онегин (Пушкин)/ПСС 1977 (СО)/Глава 1"))
        assertEquals("Будрыс и его сыновья", WikiParser.displayTitle("Будрыс и его сыновья (Мицкевич; Пушкин)"))
    }
}
