package tools

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.core.Crawler
import ru.tsvetaeva.daily.data.PoemJson
import ru.tsvetaeva.daily.data.WikiApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.system.exitProcess

/**
 * Собирает стихи с Викитеки в папку приложения.
 *
 *   --out <папка>         куда писать (app/src/main/assets/poems)
 *   --bundle a,b,…        авторы, чьи стихи встраиваются в APK (ключи: tsvetaeva, pushkin…)
 *   --check a,b,…         авторы, которых только проверить: обойти и показать сводку, не сохраняя
 */
fun main(args: Array<String>) = runBlocking {
    fun arg(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    fun authors(name: String) = arg(name).orEmpty().split(',').filter { it.isNotBlank() }
        .map { k -> Author.byKey(k.trim()) ?: error("Неизвестный автор: $k") }

    val out = File(arg("--out") ?: "../../app/src/main/assets/poems")
    val bundle = authors("--bundle")
    val check = authors("--check")
    out.mkdirs()

    val indexFile = File(out, "index.json")
    // Перезаписываем список встроенных целиком: кто не в --bundle, тот больше не встроен.
    val index = JSONObject()
    val old = runCatching { JSONObject(indexFile.readText()) }.getOrNull()
    var failed = false

    for (author in bundle + check) {
        val started = System.currentTimeMillis()
        val result = Crawler.crawl(WikiApi(), author) { pages, poems ->
            if (pages % 500 == 0) println("  ${author.tag}: страниц $pages, стихов $poems")
        }
        val poems = result.poems
        val sec = (System.currentTimeMillis() - started) / 1000
        println(
            "${author.tag}: ${poems.size} стихов (с годом ${poems.count { it.year != null }}, " +
                "с точной датой ${poems.count { it.hasDayMonth }}), страниц ${result.pages}, ${sec} с" +
                (result.interrupted?.let { " — ОБХОД ПРЕРВАН: ${it.message}" } ?: ""),
        )
        poems.shuffled(kotlin.random.Random(author.ordinal)).take(3).forEach {
            println("    «${it.title}» — ${it.text.lineSequence().first()} [${it.dateText ?: it.year ?: "без даты"}]")
        }
        if (author !in bundle) continue

        if (result.interrupted != null || poems.size < 5) {
            // Не затираем прежние встроенные стихи неполной выборкой.
            failed = true
            old?.optJSONObject(author.key)?.let { index.put(author.key, it) }
            continue
        }
        val bytes = ByteArrayOutputStream().also { buf ->
            GZIPOutputStream(buf).bufferedWriter(Charsets.UTF_8).use { it.write(PoemJson.write(poems)) }
        }.toByteArray()
        val file = File(out, "${author.key}.json.gz")
        val unchanged = file.exists() && file.readBytes().contentEquals(bytes)
        // Стихи не изменились — оставляем прежнюю дату, чтобы не было пустых коммитов.
        val previous = old?.optJSONObject(author.key)
        if (unchanged && previous != null) {
            index.put(author.key, previous)
        } else {
            file.writeBytes(bytes)
            index.put(author.key, JSONObject().put("count", poems.size).put("generatedAt", System.currentTimeMillis()))
        }
    }
    // Файлы авторов, которых больше не встраиваем, удаляем.
    out.listFiles { f -> f.name.endsWith(".json.gz") }?.forEach { f ->
        if (!index.has(f.name.removeSuffix(".json.gz"))) f.delete()
    }
    indexFile.writeText(index.toString(2) + "\n")
    if (failed) exitProcess(1)
}
