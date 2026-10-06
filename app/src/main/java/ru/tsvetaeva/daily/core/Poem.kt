package ru.tsvetaeva.daily.core

/** Одно стихотворение, извлечённое со страницы Викитеки. */
data class Poem(
    /** Уникальный ключ: название страницы + номер блока на странице. */
    val id: String,
    /** Название страницы на ru.wikisource.org (для ссылки «Открыть в Викитеке»). */
    val pageTitle: String,
    val title: String,
    val text: String,
    /** Подпись с датой/местом как в источнике, например «Москва, 6 октября 1915». */
    val dateText: String? = null,
    val day: Int? = null,
    val month: Int? = null,
    val year: Int? = null,
) {
    val hasDayMonth: Boolean get() = day != null && month != null
}

/** Найденная в тексте дата (день и месяц могут отсутствовать). */
data class DateInfo(val day: Int?, val month: Int?, val year: Int?) {
    val hasDayMonth: Boolean get() = day != null && month != null
    val isEmpty: Boolean get() = day == null && month == null && year == null

    /** Дополняет недостающие поля значениями из [other]. */
    fun orElse(other: DateInfo?): DateInfo {
        if (other == null) return this
        return if (hasDayMonth) DateInfo(day, month, year ?: other.year)
        else DateInfo(other.day, other.month, year ?: other.year)
    }
}
