package ru.tsvetaeva.daily.core

/**
 * Одно и то же стихотворение может лежать на отдельной странице, внутри сборника
 * и в нескольких изданиях (в том числе в дореформенной орфографии, как у Пушкина).
 */
object Dedupe {

    private val oldLetters = Regex("[ѣѢіІѳѲѵѴ]|ъ(?![а-яё])", RegexOption.IGNORE_CASE)

    fun dedupe(poems: List<Poem>): List<Poem> =
        // Сначала по началу текста, затем — издания одной страницы с той же первой строкой
        // (тексты разных изданий расходятся в разночтениях, а страница скана может оборвать стих).
        merge(merge(poems) { key(it.text) }) { p -> editionKey(p) ?: ("\u0000" + p.id) }

    private fun merge(poems: List<Poem>, keyOf: (Poem) -> String): List<Poem> =
        poems.groupBy(keyOf).values.map { group ->
            // Лучший текст выбираем среди исходных вариантов; при равенстве — полный,
            // а не оборванный на странице скана. Дату он получает от любого варианта, где она есть.
            val best = group.maxWith(compareBy<Poem> { score(it) }.thenBy { it.text.length })
            group.sortedByDescending { (if (it.hasDayMonth) 2 else 0) + (if (it.year != null) 1 else 0) }
                .fold(best) { acc, other -> withDate(acc, other) }
        }

    /** «Ночь, улица… (Блок)/1923 (СО)» и «…/1960 (СО)» → одна страница + первая строка. */
    private fun editionKey(p: Poem): String? {
        val parts = p.pageTitle.split('/')
        if (parts.none { WikiParser.isEdition(it) }) return null
        val base = parts.filterIndexed { i, it -> i == 0 || !WikiParser.isEdition(it) }.joinToString("/")
        val first = p.text.lines().firstOrNull { it.isNotBlank() } ?: return null
        return base + "|" + key(first)
    }

    /** Ключ сравнения: первые буквы текста, старая орфография приведена к новой. */
    fun key(text: String): String {
        val s = text.lowercase()
            .replace('ё', 'е').replace('ѣ', 'е').replace('і', 'и').replace('ѳ', 'ф').replace('ѵ', 'и')
            .replace(Regex("ъ(?![а-яё])"), "")
            .replace(Regex("([ая])го(?![а-яё])")) { if (it.groupValues[1] == "а") "ого" else "его" }
            .replace(Regex("([ыи])я(?![а-яё])")) { it.groupValues[1] + "е" }
            // «безсмысленный», «разсказ», «изходъ» — приставки на «з» перед глухими.
            .replace(Regex("(?<![а-яё])(бе|ра|и|во|ни|чре|че)з(?=[кпстфхцчшщ])")) { it.groupValues[1] + "с" }
        return s.filter { it.isLetter() }.take(120)
    }

    fun isOldOrthography(text: String): Boolean = oldLetters.containsMatchIn(text)

    /** Качество текста: современная орфография, собственная страница. Дату вариант всё равно унаследует. */
    private fun score(p: Poem): Int =
        (if (isOldOrthography(p.text)) 0 else 2) + (if (p.id == p.pageTitle) 1 else 0)

    /** Дата из второго варианта, если у выбранного её нет. */
    private fun withDate(winner: Poem, other: Poem): Poem {
        val d = DateInfo(winner.day, winner.month, winner.year).orElse(DateInfo(other.day, other.month, other.year))
        return if (d == DateInfo(winner.day, winner.month, winner.year)) winner
        else winner.copy(day = d.day, month = d.month, year = d.year, dateText = winner.dateText ?: other.dateText)
    }
}
