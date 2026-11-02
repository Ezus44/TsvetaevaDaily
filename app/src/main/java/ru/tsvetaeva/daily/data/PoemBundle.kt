package ru.tsvetaeva.daily.data

import android.content.Context
import org.json.JSONObject
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.core.Poem
import java.util.zip.GZIPInputStream

/**
 * Стихи, встроенные в APK: `assets/poems/<автор>.json.gz` и `assets/poems/index.json`
 * (когда собраны и сколько стихов). Их готовит сборщик tools/poems — см. README.
 */
class PoemBundle(context: Context, private val author: Author) {
    private val assets = context.applicationContext.assets
    private val file = "poems/${author.key}.json.gz"

    private val info: JSONObject? by lazy {
        runCatching {
            JSONObject(assets.open("poems/index.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
                .optJSONObject(author.key)
        }.getOrNull()
    }

    val exists: Boolean get() = info != null

    /** Когда стихи были собраны с Викитеки (мс), 0 — если не встроены. */
    val generatedAt: Long get() = info?.optLong("generatedAt") ?: 0L

    fun load(): List<Poem> {
        if (!exists) return emptyList()
        return try {
            val json = GZIPInputStream(assets.open(file)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            PoemJson.parse(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
