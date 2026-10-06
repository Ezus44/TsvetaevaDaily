package ru.tsvetaeva.daily.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.core.Poem
import java.io.File

/** Офлайн-копия собранных стихов: по JSON-файлу на автора во внутреннем хранилище приложения. */
class PoemStore(context: Context, author: Author) {
    // У Цветаевой — прежнее имя файла, чтобы не потерять уже собранную базу.
    private val name = if (author == Author.TSVETAEVA) "poems.json" else "poems_${author.key}.json"
    private val file = File(context.filesDir, name)

    fun exists(): Boolean = file.exists()

    fun delete() {
        file.delete()
    }

    fun load(): List<Poem> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).map { i ->
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(poems: List<Poem>) {
        val arr = JSONArray()
        for (p in poems) arr.put(
            JSONObject()
                .put("id", p.id).put("page", p.pageTitle).put("title", p.title).put("text", p.text)
                .putOpt("dateText", p.dateText).putOpt("day", p.day).putOpt("month", p.month).putOpt("year", p.year),
        )
        val tmp = File(file.parentFile, "$name.tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null
}
