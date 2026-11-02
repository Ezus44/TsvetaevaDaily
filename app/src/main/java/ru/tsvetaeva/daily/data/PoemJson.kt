package ru.tsvetaeva.daily.data

import org.json.JSONArray
import org.json.JSONObject
import ru.tsvetaeva.daily.core.Poem

/** Формат базы стихов: JSON-массив. Общий для приложения и сборщика встроенных стихов (tools/poems). */
object PoemJson {

    fun parse(json: String): List<Poem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Poem(
                id = o.getString("id"),
                pageTitle = o.getString("page"),
                title = o.getString("title"),
                text = o.getString("text"),
                dateText = o.optStringOrNull("dateText"),
                day = o.optIntOrNull("day"),
                month = o.optIntOrNull("month"),
                year = o.optIntOrNull("year"),
            )
        }
    }

    fun write(poems: List<Poem>): String {
        val arr = JSONArray()
        for (p in poems) arr.put(
            JSONObject()
                .put("id", p.id).put("page", p.pageTitle).put("title", p.title).put("text", p.text)
                .putOpt("dateText", p.dateText).putOpt("day", p.day).putOpt("month", p.month).putOpt("year", p.year),
        )
        return arr.toString()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null
}
