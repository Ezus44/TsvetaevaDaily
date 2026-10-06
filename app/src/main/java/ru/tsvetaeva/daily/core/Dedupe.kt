package ru.tsvetaeva.daily.core

/**
 * Одно и то же стихотворение может лежать на отдельной странице, внутри сборника
 * и в нескольких изданиях (в том числе в дореформенной орфографии, как у Пушкина).
 */
object Dedupe {

    private val oldLetters = Regex("[ѣѢіІѳѲѵѴ]|ъ(?![а-яё])", RegexOption.IGNORE_CASE)

    fun dedupe(poems: List<Poem>): List<Poem> {
        val byKey = LinkedHashMap<String, Poem>()
        for (p in poems) {
            val key = key(p.text)
            val prev = byKey[key]
            byKey[key] = when {
                prev == null -> p
                score(p) > score(prev) -> withDate(p, prev)
                else -> withDate(prev, p)
            }
        }
        return byKey.values.toList()
    }

    /** Ключ сравнения: первые буквы текста, старая орфография приведена к новой. */
    fun key(text: String): String {
        val s = text.lowercase()
            .replace('ѣ', 'е').replace('і', 'и').replace('ѳ', 'ф').replace('ѵ', 'и')
            .replace(Regex("ъ(?![а-яё])"), "")
            .replace(Regex("([ая])го(?![а-яё])")) { if (it.groupValues[1] == "а") "ого" else "его" }
            .replace(Regex("([ыи])я(?![а-яё])")) { it.groupValues[1] + "е" }
        return s.filter { it.isLetter() }.take(120)
    }

    fun isOldOrthography(text: String): Boolean = oldLetters.containsMatchIn(text)

    private fun score(p: Poem): Int =
        (if (isOldOrthography(p.text)) 0 else 8) + (if (p.hasDayMonth) 4 else 0) +
            (if (p.year != null) 2 else 0) + (if (p.id == p.pageTitle) 1 else 0)

    /** Дата из второго варианта, если у выбранного её нет. */
    private fun withDate(winner: Poem, other: Poem): Poem {
        val d = DateInfo(winner.day, winner.month, winner.year).orElse(DateInfo(other.day, other.month, other.year))
        return if (d == DateInfo(winner.day, winner.month, winner.year)) winner
        else winner.copy(day = d.day, month = d.month, year = d.year, dateText = winner.dateText ?: other.dateText)
    }
}
