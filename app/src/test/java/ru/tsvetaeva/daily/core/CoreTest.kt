package ru.tsvetaeva.daily.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** Проверки разбора викитекста и выбора стихотворения (на синтетических примерах). */
class CoreTest {
    private fun check(name: String, cond: Boolean, info: Any? = null) = assertTrue("$name :: $info", cond)

    @Test
    fun parsingAndPicking() {
    // ---- Даты ----
    check("word date", Dates.find("Москва, 6 октября 1915") == DateInfo(6, 10, 1915), Dates.find("Москва, 6 октября 1915"))
    check("1-го мая", Dates.find("1-го мая 1917") == DateInfo(1, 5, 1917), Dates.find("1-го мая 1917"))
    check("roman", Dates.find("11 XII 1918") == DateInfo(11, 12, 1918), Dates.find("11 XII 1918"))
    check("numeric", Dates.find("3.07.1920") == DateInfo(3, 7, 1920), Dates.find("3.07.1920"))
    check("year only", Dates.find("<1916>") == DateInfo(null, null, 1916))
    check("bad day", Dates.find("31 февраля 1916") == DateInfo(null, null, 1916), Dates.find("31 февраля 1916"))
    check("date line place", Dates.isDateLine("Коктебель, 11 мая 1911 г."))
    check("date line year", Dates.isDateLine("1916"))
    check("date line angle", Dates.isDateLine("<1922>"))
    check("not date line", !Dates.isDateLine("И ветер пел в саду до самого утра,"))
    check("not date line 2", !Dates.isDateLine("Я помню день, шестого октября,"), "verse line with month but no digit")
    check("distance wrap", Dates.distance(12, 31, 1, 1) == 1, Dates.distance(12, 31, 1, 1))
    check("distance", Dates.distance(10, 6, 10, 1) == 5)

    // ---- Отдельное стихотворение ----
    val single = """
{{Отексте
|АВТОР=Марина Ивановна Цветаева
|НАЗВАНИЕ=«Тестовая строка про осень…»
|ДАТАСОЗДАНИЯ=1915
|ИСТОЧНИК=[[Индекс:Что-то.djvu|Книга]]
}}
<poem>
Тестовая строка про осень, [[Москва|город]] спит,
И ''тихо'' дождь стучит в окно&nbsp;—
:Строка с отступом{{ref|1}}<ref>Примечание.</ref>
И {{razr|разрядка}} тоже.

''Москва, 6 октября 1915''
</poem>
[[Категория:Поэзия Марины Ивановны Цветаевой]]
"""
    val p1 = WikiParser.parse("«Тестовая строка про осень…» (Цветаева)", single)
    check("single: one poem", p1.poems.size == 1, p1.poems)
    val poem = p1.poems.first()
    check("single: title", poem.title == "«Тестовая строка про осень…»", poem.title)
    check("single: date", poem.day == 6 && poem.month == 10 && poem.year == 1915, poem)
    check("single: dateText", poem.dateText == "Москва, 6 октября 1915", poem.dateText)
    check("single: no date in text", !poem.text.contains("1915"), poem.text)
    check("single: link text", poem.text.contains("город спит"), poem.text)
    check("single: no ref", !poem.text.contains("Примечание"), poem.text)
    check("single: indent", poem.text.lines()[2].startsWith(" Строка"), poem.text.lines())
    check("single: razr", poem.text.contains("И разрядка тоже."), poem.text)
    check("single: no italics marks", !poem.text.contains("''"), poem.text)
    check("single: no category link", p1.links.none { it.startsWith("Категория") }, p1.links)

    // ---- Цикл на одной странице с римскими номерами и poem-off ----
    val cycle = """
== I ==
<poem>
Первая строка первой части,
Вторая строка первой части.
</poem>
{{poem-off|1 мая 1916}}
== II ==
<poem>
Первая строка второй части,
Вторая строка второй части.
2 мая 1916
</poem>
== * * * ==
<poem>
Без названия первая строка!
И ещё одна строка.
</poem>
"""
    val p2 = WikiParser.parse("Стихи для теста (Цветаева)", cycle)
    check("cycle: three poems", p2.poems.size == 3, p2.poems.map { it.title })
    check("cycle: title I", p2.poems[0].title == "Стихи для теста — I", p2.poems[0].title)
    check("cycle: poem-off date", p2.poems[0].day == 1 && p2.poems[0].month == 5, p2.poems[0])
    check("cycle: inline date", p2.poems[1].day == 2 && p2.poems[1].month == 5, p2.poems[1])
    check("cycle: untitled -> first line", p2.poems[2].title == "«Без названия первая строка!…»", p2.poems[2].title)
    check("cycle: ids unique", p2.poems.map { it.id }.toSet().size == 3)

    // ---- Оглавление ----
    val index = """
Марина Цветаева. Стихотворения.
=== 1915 ===
* [[«Первое тестовое…» (Цветаева)|«Первое тестовое…»]] (6 октября)
* [[Второе тестовое (Цветаева)]]
* [[/Подстраница]] — 3 ноября 1916
* [[Автор:Анна Ахматова|Ахматова]]
* [[Чужое стихотворение (Блок)]]
"""
    val p3 = WikiParser.parse("Стихотворения 1906—1920 (Цветаева)", index)
    check("index: no poems", p3.poems.isEmpty())
    check("index: links", p3.links.contains("«Первое тестовое…» (Цветаева)") && p3.links.contains("Стихотворения 1906—1920 (Цветаева)/Подстраница"), p3.links)
    check("index: author link skipped", p3.links.none { it.startsWith("Автор") }, p3.links)
    check("index: relevance", !WikiParser.isRelevantTitle("Чужое стихотворение (Блок)") && WikiParser.isRelevantTitle("Второе тестовое (Цветаева)"))
    check("index: hint with section year", p3.dateHints["«Первое тестовое…» (Цветаева)"] == DateInfo(6, 10, 1915), p3.dateHints)
    check("index: hint subpage", p3.dateHints["Стихотворения 1906—1920 (Цветаева)/Подстраница"] == DateInfo(3, 11, 1916), p3.dateHints)
    check("index: year-only hint", p3.dateHints["Второе тестовое (Цветаева)"] == DateInfo(null, null, 1915), p3.dateHints)

    // внешний hint дополняет страницу без даты
    val noDate = "<poem>\nСтрока без даты первая,\nСтрока без даты вторая.\n</poem>"
    val p4 = WikiParser.parse("«Первое тестовое…» (Цветаева)", noDate, DateInfo(6, 10, 1915))
    check("external hint", p4.poems.single().day == 6 && p4.poems.single().month == 10, p4.poems)

    // ---- Проза с эпиграфом ----
    val prose = "<poem>\nЧужой эпиграф строка,\nи вторая строка.\n</poem>\n" + "Длинный прозаический текст. ".repeat(300)
    val p5 = WikiParser.parse("Повесть (Цветаева)", prose)
    check("prose skipped", p5.poems.isEmpty() && p5.isProse)

    // ---- Заголовки ----
    check("display subpage num", WikiParser.displayTitle("Стихи к Блоку (Цветаева)/3") == "Стихи к Блоку — 3")
    check("display subpage name", WikiParser.displayTitle("Вечерний альбом (Цветаева)/Встреча") == "Встреча")
    check("display plain", WikiParser.displayTitle("Генералам двенадцатого года (Цветаева)") == "Генералам двенадцатого года")

    // ---- Другие авторы ----
    check("pushkin relevant", Author.PUSHKIN.isRelevantTitle("Пророк (Пушкин)") && Author.PUSHKIN.isRelevantTitle("Анчар (Пушкин)/ПСС 1959—1962 (ВТ)"))
    check("pushkin uncle", !Author.PUSHKIN.isRelevantTitle("Опасный сосед (В. Л. Пушкин)"))
    check("pushkin not tsvetaeva", !Author.PUSHKIN.isRelevantTitle("Мой Пушкин (Цветаева)") && Author.TSVETAEVA.isRelevantTitle("Мой Пушкин (Цветаева)"))
    check("parnok relevant", Author.PARNOK.isRelevantTitle("«Тебе одной» (Парнок)"))
    check("mayakovsky relevant", Author.MAYAKOVSKY.isRelevantTitle("Облако в штанах (Маяковский)"))
    check("display edition", WikiParser.displayTitle("Анчар (Пушкин)/ПСС 1959—1962 (ВТ)") == "Анчар", WikiParser.displayTitle("Анчар (Пушкин)/ПСС 1959—1962 (ВТ)"))
    check("display old orthography", WikiParser.displayTitle("Пророк (Пушкин)/ДО") == "Пророк", WikiParser.displayTitle("Пророк (Пушкин)/ДО"))
    check("display mayakovsky", WikiParser.displayTitle("Облако в штанах (Маяковский)/1") == "Облако в штанах — 1")
    val pIndex = WikiParser.parse(
        "Александр Сергеевич Пушкин",
        "=== 1826 ===\n* [[Пророк (Пушкин)|Пророк]] (8 сентября)\n* [[Мой Пушкин (Цветаева)]] (1937)\n",
        author = Author.PUSHKIN,
    )
    check("pushkin hints", pIndex.dateHints.keys == setOf("Пророк (Пушкин)") && pIndex.dateHints["Пророк (Пушкин)"] == DateInfo(8, 9, 1826), pIndex.dateHints)
    val edition = WikiParser.parse("Анчар (Пушкин)/ПСС 1959—1962 (ВТ)", "<poem>\nВ пустыне чахлой и скупой,\nНа почве, зноем раскаленной,\n</poem>", author = Author.PUSHKIN)
    check("edition title", edition.poems.single().title == "Анчар", edition.poems.map { it.title })
    val pushkinPoem = WikiParser.parse(
        "К морю (Пушкин)", "<poem>\nПрощай, свободная стихия!\nВ последний раз передо мной\n\nМихайловское, 1824\n</poem>", author = Author.PUSHKIN,
    )
    check("pushkin year", pushkinPoem.poems.single().year == 1824 && pushkinPoem.poems.single().dateText == "Михайловское, 1824", pushkinPoem.poems)
    check("pushkin word date", Dates.find("8 сентября 1826", years = Author.PUSHKIN.years) == DateInfo(8, 9, 1826))
    check("year outside life", Dates.find("1826").year == null && Dates.find("1915", years = Author.PUSHKIN.years).year == null)

    // ---- Дубликаты в разных изданиях ----
    val modern = Poem("Пророк (Пушкин)", "Пророк (Пушкин)", "Пророк", "Духовной жаждою томим,\nВ пустыне мрачной я влачился,\nИ шестикрылый серафим", year = 1826)
    val old = Poem("Пророк (Пушкин)/ДО", "Пророк (Пушкин)/ДО", "Пророк", "Духовной жаждою томимъ,\nВъ пустынѣ мрачной я влачился,\nИ шестикрылый серафимъ", day = 8, month = 9, year = 1826)
    check("old orthography key", Dedupe.key(modern.text) == Dedupe.key(old.text), Dedupe.key(old.text))
    val dd = Dedupe.dedupe(listOf(old, modern))
    check("dedupe keeps modern", dd.size == 1 && dd[0].id == modern.id, dd)
    check("dedupe merges date", dd[0].day == 8 && dd[0].month == 9, dd)
    check("old orthography detect", Dedupe.isOldOrthography(old.text) && !Dedupe.isOldOrthography(modern.text))

    // ---- Выбор ----
    val ps = listOf(
        Poem("a", "a", "A", "x\ny", day = 6, month = 10),
        Poem("b", "b", "B", "x\ny", day = 8, month = 10),
        Poem("c", "c", "C", "x\ny", day = 1, month = 3),
        Poem("d", "d", "D", "x\ny"),
    )
    val today = LocalDate.of(2026, 10, 6)
    val r = PoemPicker.pick(ps, today, emptySet(), true, Random(1))
    check("pick exact day", r?.poem?.id == "a" && r.distance == 0, r)
    val r2 = PoemPicker.pick(ps, today, setOf("a"), true, Random(1))
    check("pick nearest when exact recent", r2?.poem?.id == "b" && r2.distance == 2, r2)
    val r3 = PoemPicker.pick(ps, LocalDate.of(2026, 6, 15), emptySet(), true, Random(1))
    check("pick fallback random", r3 != null && r3.distance == null || (r3?.distance ?: 0) > PoemPicker.MAX_NEAR_DAYS, r3)
    val seen = (0 until 200).map { PoemPicker.pick(ps, today, emptySet(), false, Random(it))!!.poem.id }.toSet()
    check("random mode covers all", seen == setOf("a", "b", "c", "d"), seen)
    check("exclude works", PoemPicker.pick(ps, today, emptySet(), true, Random(1), exclude = "a")?.poem?.id == "b")
    check("deterministic per day", PoemPicker.randomForDay(today).nextInt() == PoemPicker.randomForDay(today).nextInt())

    }
}
