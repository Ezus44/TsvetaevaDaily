import org.json.JSONObject
import ru.tsvetaeva.daily.core.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val API = "https://ru.wikisource.org/w/api.php"
private const val UA = "TsvetaevaDaily/1.2 (https://github.com/Ezus44/TsvetaevaDaily; probe)"

private fun get(params: Map<String, String>): JSONObject {
    val q = (params + mapOf("format" to "json", "formatversion" to "2")).entries
        .joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
    repeat(6) { attempt ->
        try {
            Thread.sleep(300)
            val c = URL(API).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("User-Agent", UA)
            c.outputStream.use { it.write(q.toByteArray()) }
            if (c.responseCode == 200) return JSONObject(c.inputStream.bufferedReader().readText())
            println("HTTP ${c.responseCode}")
        } catch (e: Exception) { println("err $e") }
        Thread.sleep(3000L * (attempt + 1))
    }
    return JSONObject()
}

private fun cont(j: JSONObject): Map<String, String> =
    j.optJSONObject("continue")?.let { c -> c.keys().asSequence().associateWith { c.getString(it) } } ?: emptyMap()

private fun members(cat: String): List<String> {
    val out = ArrayList<String>(); var c = emptyMap<String, String>()
    do {
        val j = get(mapOf("action" to "query", "list" to "categorymembers", "cmtitle" to cat, "cmnamespace" to "0", "cmlimit" to "max") + c)
        j.optJSONObject("query")?.optJSONArray("categorymembers")?.let { a -> for (i in 0 until a.length()) out += a.getJSONObject(i).getString("title") }
        c = cont(j)
    } while (c.isNotEmpty())
    return out
}

private fun contents(titles: List<String>): Map<String, String> {
    val out = LinkedHashMap<String, String>(); var c = emptyMap<String, String>()
    do {
        val j = get(mapOf("action" to "query", "prop" to "revisions", "rvprop" to "content", "rvslots" to "main", "redirects" to "1", "titles" to titles.joinToString("|")) + c)
        j.optJSONObject("query")?.optJSONArray("pages")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                val r = p.optJSONArray("revisions")?.optJSONObject(0) ?: continue
                out[p.getString("title")] = r.optJSONObject("slots")?.optJSONObject("main")?.optString("content") ?: continue
            }
        }
        c = cont(j)
    } while (c.isNotEmpty())
    return out
}

private fun parseHtml(title: String): String? =
    get(mapOf("action" to "parse", "page" to title, "prop" to "text", "redirects" to "1")).optJSONObject("parse")?.optString("text")

fun main(args: Array<String>) {
    val authors = if (args.isEmpty()) Author.entries else args.map { Author.valueOf(it) }
    for (author in authors) {
        val t0 = System.currentTimeMillis()
        val queue = ArrayDeque<String>(); val seen = HashSet<String>()
        val hints = HashMap<String, DateInfo>(); val found = LinkedHashMap<String, Poem>()
        val poemsByPage = HashMap<String, Int>(); var htmlCalls = 0
        fun enqueue(t: String) { if (seen.add(t)) queue.addLast(t) }
        author.seedPages.forEach(::enqueue)
        for (cat in author.seedCategories) members(cat).filter(author::isRelevantTitle).forEach(::enqueue)
        var pages = 0
        while (queue.isNotEmpty() && pages < 4000) {
            val batch = ArrayList<String>(); while (queue.isNotEmpty() && batch.size < 50) batch += queue.removeFirst()
            for ((title, wiki) in contents(batch)) {
                seen += title
                var parsed = WikiParser.parse(title, wiki, hints[title], author)
                if (parsed.poems.isEmpty() && !parsed.isProse && Regex("<pages\\s", RegexOption.IGNORE_CASE).containsMatchIn(wiki)) {
                    htmlCalls++
                    parseHtml(title)?.let { h ->
                        val f = WikiParser.parse(title, HtmlToWiki.convert(h), hints[title], author)
                        parsed = f.copy(links = parsed.links + f.links)
                    }
                }
                hints.putAll(parsed.dateHints)
                parsed.poems.forEach { found[it.id] = it }
                poemsByPage[title] = parsed.poems.size
                if (!parsed.isProse) parsed.links.filter(author::isRelevantTitle).forEach(::enqueue)
            }
            pages += batch.size
        }
        val withHints = found.values.map { p ->
            val h = hints[p.pageTitle]
            if (h == null || p.id != p.pageTitle) p else { val d = DateInfo(p.day, p.month, p.year).orElse(h); p.copy(day = d.day, month = d.month, year = d.year) }
        }
        val result = Dedupe.dedupe(withHints)
        val sec = (System.currentTimeMillis() - t0) / 1000
        println("\n================ ${author.tag}: pages=$pages poems(raw)=${found.size} poems=${result.size} " +
            "withYear=${result.count { it.year != null }} withDayMonth=${result.count { it.hasDayMonth }} " +
            "oldOrth=${result.count { Dedupe.isOldOrthography(it.text) }} htmlCalls=$htmlCalls time=${sec}s queueLeft=${queue.size}")
        println("pages with most poems: " + poemsByPage.entries.sortedByDescending { it.value }.take(8).joinToString { "${it.key}=${it.value}" })
        println("relevant pages with 0 poems (sample): " + poemsByPage.filter { it.value == 0 }.keys.take(25))
        val longest = result.maxByOrNull { it.text.length }
        println("longest: ${longest?.title} (${longest?.pageTitle}) ${longest?.text?.length} chars")
        val sample = result.shuffled(kotlin.random.Random(1)).take(6)
        for (p in sample) {
            println("\n--- ${p.title}  [${p.pageTitle}]  date=${p.dateText} ${p.day}.${p.month}.${p.year}")
            println(p.text.lines().take(8).joinToString("\n"))
        }
    }
}
