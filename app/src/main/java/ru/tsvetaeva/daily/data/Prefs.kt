package ru.tsvetaeva.daily.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** День (epochDay), для которого выбрано [todayId]. */
    var todayEpochDay: Long
        get() = sp.getLong("today_day", Long.MIN_VALUE)
        set(v) = sp.edit().putLong("today_day", v).apply()

    var todayId: String?
        get() = sp.getString("today_id", null)
        set(v) = sp.edit().putString("today_id", v).apply()

    /** Недавно показанные стихи — чтобы не повторяться. */
    var recent: List<String>
        get() = sp.getString("recent", "")!!.split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("recent", v.joinToString("\n")).apply()

    fun addRecent(id: String, max: Int) {
        recent = (listOf(id) + recent.filter { it != id }).take(max)
    }

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

    var lastRefreshMillis: Long
        get() = sp.getLong("last_refresh", 0L)
        set(v) = sp.edit().putLong("last_refresh", v).apply()

    var askedNotificationPermission: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) = sp.edit().putBoolean("asked_notif", v).apply()
}
