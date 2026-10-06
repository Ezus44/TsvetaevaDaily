package ru.tsvetaeva.daily.data

import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Минимальный клиент MediaWiki API русской Викитеки. */
class WikiApi(private val base: String = "https://ru.wikisource.org/w/api.php") {

    data class Page(val title: String, val wikitext: String)

    /** Названия страниц основного пространства в категории (со всеми продолжениями). */
    suspend fun categoryMembers(category: String): List<String> {
        val out = ArrayList<String>()
        var cont: Map<String, String> = emptyMap()
        do {
            val json = get(
                mapOf(
                    "action" to "query", "list" to "categorymembers", "cmtitle" to category,
                    "cmnamespace" to "0", "cmlimit" to "max",
                ) + cont,
            )
            json.optJSONObject("query")?.optJSONArray("categorymembers")?.let { arr ->
                for (i in 0 until arr.length()) out += arr.getJSONObject(i).getString("title")
            }
            cont = continuation(json)
        } while (cont.isNotEmpty())
        return out
    }

    /** Викитекст до 50 страниц за запрос; перенаправления раскрываются, несуществующие пропускаются. */
    suspend fun fetchContents(titles: List<String>): List<Page> {
        require(titles.size <= 50)
        val pages = LinkedHashMap<String, String>()
        var cont: Map<String, String> = emptyMap()
        do {
            val json = get(
                mapOf(
                    "action" to "query", "prop" to "revisions", "rvprop" to "content", "rvslots" to "main",
                    "redirects" to "1", "titles" to titles.joinToString("|"),
                ) + cont,
            )
            val arr = json.optJSONObject("query")?.optJSONArray("pages")
            if (arr != null) for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                if (p.optBoolean("missing") || p.optBoolean("invalid")) continue
                val rev = p.optJSONArray("revisions")?.optJSONObject(0) ?: continue
                val content = rev.optJSONObject("slots")?.optJSONObject("main")?.optString("content")
                    ?: rev.optString("content")
                if (!content.isNullOrEmpty()) pages[p.getString("title")] = content
            }
            cont = continuation(json)
        } while (cont.isNotEmpty())
        return pages.map { Page(it.key, it.value) }
    }

    /** Готовый HTML страницы — для текстов, собранных из сканов через `<pages />`. */
    suspend fun parseHtml(title: String): String? {
        val json = get(
            mapOf(
                "action" to "parse", "page" to title, "prop" to "text", "redirects" to "1",
                "disablelimitreport" to "1", "disableeditsection" to "1",
            ),
        )
        return json.optJSONObject("parse")?.optString("text")
    }

    private fun continuation(json: JSONObject): Map<String, String> {
        val c = json.optJSONObject("continue") ?: return emptyMap()
        return c.keys().asSequence().associateWith { c.getString(it) }
    }

    /** Вызывается, когда Викитека просит подождать: сколько секунд ждём. */
    var onWait: ((seconds: Int) -> Unit)? = null

    private var lastRequestAt = 0L

    private suspend fun get(params: Map<String, String>): JSONObject {
        val all = params + mapOf("format" to "json", "formatversion" to "2", "maxlag" to "5")
        val query = all.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        var lastError: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            // Не чаще одного запроса в MIN_INTERVAL_MS — Викимедиа просит не спешить.
            val sinceLast = System.currentTimeMillis() - lastRequestAt
            if (sinceLast < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - sinceLast)
            lastRequestAt = System.currentTimeMillis()

            var waitSeconds = 0
            try {
                // POST: в запросе до 50 кириллических названий, для GET-адреса это слишком длинно.
                val conn = (URL(base).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                try {
                    conn.outputStream.use { it.write(query.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    if (code == 429 || code >= 500) {
                        // Retry-After — сколько секунд просит подождать сервер.
                        val retryAfter = conn.getHeaderField("Retry-After")?.trim()?.toIntOrNull()
                        waitSeconds = (retryAfter ?: backoff(attempt)).coerceIn(2, 120)
                        lastError = if (code == 429) RateLimitedException() else IOException("HTTP $code")
                    } else {
                        if (code != 200) throw IOException("HTTP $code")
                        // Android сам запрашивает gzip и распаковывает ответ.
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val json = JSONObject(body)
                        val err = json.optJSONObject("error")
                        if (err == null) return json
                        if (err.optString("code") != "maxlag") {
                            throw IllegalStateException("Ошибка API Викитеки: " + err.optString("info"))
                        }
                        waitSeconds = backoff(attempt)
                        lastError = IOException("Викитека перегружена, попробуйте позже")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                lastError = e
                waitSeconds = backoff(attempt)
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                onWait?.invoke(waitSeconds)
                delay(waitSeconds * 1000L)
            }
        }
        throw lastError ?: IOException("Нет ответа от Викитеки")
    }

    private fun backoff(attempt: Int): Int = minOf(5 shl attempt, 60) // 5, 10, 20, 40, 60…

    companion object {
        private const val MAX_ATTEMPTS = 7
        private const val MIN_INTERVAL_MS = 500L

        // Политика Викимедиа: User-Agent с названием клиента и ссылкой для связи.
        const val USER_AGENT =
            "TsvetaevaDaily/1.1 (https://github.com/Ezus44/TsvetaevaDaily; Android app, poem of the day)"
    }
}

/** Викитека ответила 429 «слишком много запросов» и после всех повторов. */
class RateLimitedException : IOException("HTTP 429")
