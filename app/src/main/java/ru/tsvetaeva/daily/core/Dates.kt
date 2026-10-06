package ru.tsvetaeva.daily.core

/** Распознавание дат в русских подписях к стихам: «6 октября 1915», «1-го мая», «11 XII 1918», «1916». */
object Dates {

    private val MONTHS_GEN = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val MONTHS_SHORT = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек",
    )
    private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")

    private val wordDate = Regex(
        """(?<![\d])(\d{1,2})(?:\s*-\s*(?:го|е|ое))?\s+(""" +
            MONTHS_GEN.joinToString("|") + "|" + MONTHS_SHORT.joinToString("|") { "$it\\." } +
            """)(?:\s*(\d{4}))?""",
        RegexOption.IGNORE_CASE,
    )
    private val romanDate = Regex(
        """(?<![\dA-Za-z])(\d{1,2})[\s.]+(XII|XI|X|IX|VIII|VII|VI|V|IV|III|II|I)(?![A-Za-z])\.?(?:[\s.]*(\d{4}))?""",
    )
    private val numericDate = Regex("""(?<![\d.])(\d{1,2})\.(\d{1,2})\.(\d{4})(?![\d])""")
    private val year = Regex("""(?<!\d)(1[6-9]\d\d)(?!\d)""")

    /** Годы, которые считаем годами написания (по умолчанию — Цветаевой). */
    val DEFAULT_YEARS = 1890..1941

    private fun findYear(s: String, years: IntRange): Int? =
        year.findAll(s).map { it.value.toInt() }.firstOrNull { it in years }

    /**
     * Ищет дату в произвольной строке. Римские месяцы понимает только при [allowRoman].
     * [years] — годы жизни автора: остальные четырёхзначные числа годом не считаются.
     */
    fun find(s: String, allowRoman: Boolean = true, years: IntRange = DEFAULT_YEARS): DateInfo {
        wordDate.find(s)?.let { m ->
            val day = m.groupValues[1].toInt()
            val word = m.groupValues[2].lowercase().removeSuffix(".")
            var month = MONTHS_GEN.indexOf(word) + 1
            if (month == 0) month = MONTHS_SHORT.indexOf(word) + 1
            val y = m.groupValues[3].toIntOrNull()?.takeIf { it in years } ?: findYear(s, years)
            if (month in 1..12 && valid(day, month)) return DateInfo(day, month, y)
        }
        numericDate.find(s)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val y = m.groupValues[3].toInt()
            if (y in years && month in 1..12 && valid(day, month)) return DateInfo(day, month, y)
        }
        if (allowRoman) {
            romanDate.find(s)?.let { m ->
                val day = m.groupValues[1].toInt()
                val month = ROMAN.indexOf(m.groupValues[2]) + 1
                val y = m.groupValues[3].toIntOrNull()?.takeIf { it in years } ?: findYear(s, years)
                if (month in 1..12 && valid(day, month)) return DateInfo(day, month, y)
            }
        }
        return DateInfo(null, null, findYear(s, years))
    }

    /**
     * Похожа ли строка на подпись под стихом: дата (и, возможно, место), без лишнего текста.
     * Например: «Москва, 6 октября 1915», «1916», «<1922>», «Коктебель, 11 мая 1911 г.»
     */
    fun isDateLine(line: String, years: IntRange = DEFAULT_YEARS): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 70) return false
        val d = find(t, allowRoman = true, years = years)
        if (d.isEmpty) return false
        // Убираем всё «датное» и смотрим, что осталось: допустимо до трёх коротких слов (место).
        val rest = t
            .replace(wordDate, " ").replace(romanDate, " ").replace(numericDate, " ").replace(year, " ")
            .replace(Regex("""(?<!\p{L})(г|гг|год|года|ст|нов|стар|ок|около|между|и)(?!\p{L})\.?""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""[\p{P}\p{S}\d]+"""), " ")
            .trim()
        if (rest.isEmpty()) return true
        val words = rest.split(Regex("\\s+"))
        return words.size <= 3 && words.all { it.length <= 16 && it.first().isUpperCase() }
    }

    private fun valid(day: Int, month: Int): Boolean {
        val max = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[month - 1]
        return day in 1..max
    }

    /** Порядковый номер дня в високосном году (1..366) — чтобы 29 февраля тоже имело место. */
    fun dayOfYear(month: Int, day: Int): Int {
        val lens = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var n = day
        for (i in 0 until month - 1) n += lens[i]
        return n
    }

    /** Расстояние в днях между двумя датами без учёта года (по кругу: 31 декабря рядом с 1 января). */
    fun distance(m1: Int, d1: Int, m2: Int, d2: Int): Int {
        val a = dayOfYear(m1, d1)
        val b = dayOfYear(m2, d2)
        val diff = kotlin.math.abs(a - b)
        return minOf(diff, 366 - diff)
    }

    fun monthGenitive(month: Int): String = MONTHS_GEN[month - 1]
}
