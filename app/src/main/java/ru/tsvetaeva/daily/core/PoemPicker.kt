package ru.tsvetaeva.daily.core

import java.time.LocalDate
import kotlin.random.Random

/** Выбор стихотворения дня. */
object PoemPicker {

    data class Pick(
        val poem: Poem,
        /** Сколько дней между датой написания и сегодняшним числом (null — дата неизвестна или режим «случайно»). */
        val distance: Int?,
    )

    /** Насколько далеко (в днях) ещё считаем дату «близкой». */
    const val MAX_NEAR_DAYS = 14

    /** Сколько ближайших по дате стихов участвуют в розыгрыше, если в точности сегодняшней даты нет. */
    private const val NEAR_POOL = 5

    /**
     * @param recent  id недавно показанных стихов — их стараемся не повторять
     * @param nearDate true — сначала стихи, написанные в этот же день (или ближайшие по числу),
     *                 false — совершенно случайное стихотворение
     */
    fun pick(
        poems: List<Poem>,
        today: LocalDate,
        recent: Set<String>,
        nearDate: Boolean,
        random: Random,
        exclude: String? = null,
    ): Pick? {
        if (poems.isEmpty()) return null
        var pool = poems.filter { it.id !in recent && it.id != exclude }
        if (pool.isEmpty()) pool = poems.filter { it.id != exclude }.ifEmpty { poems }

        if (nearDate) {
            val m = today.monthValue
            val d = today.dayOfMonth
            val dated = pool.filter { it.hasDayMonth }
                .map { it to Dates.distance(it.month!!, it.day!!, m, d) }
                .filter { it.second <= MAX_NEAR_DAYS }
                .sortedBy { it.second }
            if (dated.isNotEmpty()) {
                val exact = dated.filter { it.second == 0 }
                val candidates = if (exact.isNotEmpty()) exact else {
                    val limit = dated[minOf(NEAR_POOL, dated.size) - 1].second
                    dated.filter { it.second <= limit }
                }
                val (poem, dist) = candidates[random.nextInt(candidates.size)]
                return Pick(poem, dist)
            }
        }
        val poem = pool[random.nextInt(pool.size)]
        val dist = if (nearDate && poem.hasDayMonth)
            Dates.distance(poem.month!!, poem.day!!, today.monthValue, today.dayOfMonth) else null
        return Pick(poem, dist)
    }

    /** Детерминированный «случай» на конкретный день — чтобы стих дня не менялся при каждом открытии. */
    fun randomForDay(day: LocalDate, salt: Int = 0): Random = Random(day.toEpochDay() * 7919 + salt)
}
