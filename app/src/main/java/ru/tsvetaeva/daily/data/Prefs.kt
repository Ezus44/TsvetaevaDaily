package ru.tsvetaeva.daily.data

import android.content.Context
import ru.tsvetaeva.daily.core.Author

/** Общие настройки приложения. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Основной автор: открывается при запуске, его стих приходит в уведомлении. */
    var mainAuthor: Author
        get() = Author.byKey(sp.getString("main_author", null)) ?: Author.TSVETAEVA
        set(v) = sp.edit().putString("main_author", v.key).apply()

    /** true — стихи, написанные в этот день или около него; false — совсем случайные. */
    var nearDate: Boolean
        get() = sp.getBoolean("near_date", true)
        set(v) = sp.edit().putBoolean("near_date", v).apply()

    var notifyEnabled: Boolean
        get() = sp.getBoolean("notify", true)
        set(v) = sp.edit().putBoolean("notify", v).apply()

    var notifyHour: Int
        get() = sp.getInt("notify_hour", 9)
        set(v) = sp.edit().putInt("notify_hour", v).apply()

    var notifyMinute: Int
        get() = sp.getInt("notify_minute", 0)
        set(v) = sp.edit().putInt("notify_minute", v).apply()

    var askedNotificationPermission: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) = sp.edit().putBoolean("asked_notif", v).apply()
}

/** Состояние одного автора: стих дня, недавно показанные, время обновления базы. */
class AuthorPrefs(context: Context, author: Author) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // У Цветаевой — прежние ключи, чтобы сохранились стих дня и история после обновления.
    private val suffix = if (author == Author.TSVETAEVA) "" else "_${author.key}"

    /** День (epochDay), для которого выбрано [todayId]. */
    var todayEpochDay: Long
        get() = sp.getLong("today_day$suffix", Long.MIN_VALUE)
        set(v) = sp.edit().putLong("today_day$suffix", v).apply()

    var todayId: String?
        get() = sp.getString("today_id$suffix", null)
        set(v) = sp.edit().putString("today_id$suffix", v).apply()

    /** Недавно показанные стихи — чтобы не повторяться. */
    var recent: List<String>
        get() = sp.getString("recent$suffix", "")!!.split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("recent$suffix", v.joinToString("\n")).apply()

    fun addRecent(id: String, max: Int) {
        recent = (listOf(id) + recent.filter { it != id }).take(max)
    }

    var lastRefreshMillis: Long
        get() = sp.getLong("last_refresh$suffix", 0L)
        set(v) = sp.edit().putLong("last_refresh$suffix", v).apply()

    fun clear() {
        sp.edit().remove("today_day$suffix").remove("today_id$suffix").remove("recent$suffix")
            .remove("last_refresh$suffix").apply()
    }
}
