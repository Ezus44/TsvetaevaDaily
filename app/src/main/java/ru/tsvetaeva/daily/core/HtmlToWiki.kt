package ru.tsvetaeva.daily.core

/**
 * Страницы, вычитанные по сканам, хранят текст не в викитексте, а во включаемых
 * страницах `<pages index=… />`. Для них берём готовый HTML (action=parse) и превращаем
 * его в простой «викитекст» с блоками `<poem>`, понятный [WikiParser].
 */
object HtmlToWiki {
    private val removeBlocks = listOf(
        Regex("""<span[^>]*class="[^"]*pagenum[^"]*"[^>]*>[\s\S]*?</span>""", RegexOption.IGNORE_CASE),
        Regex("""<sup[^>]*class="[^"]*reference[^"]*"[^>]*>[\s\S]*?</sup>""", RegexOption.IGNORE_CASE),
        Regex("""<ol[^>]*class="[^"]*references[^"]*"[^>]*>[\s\S]*?</ol>""", RegexOption.IGNORE_CASE),
        Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE),
        Regex("""<table[\s\S]*?</table>""", RegexOption.IGNORE_CASE),
        Regex("""<span[^>]*class="[^"]*mw-editsection[^"]*"[^>]*>[\s\S]*?</span>\s*</span>""", RegexOption.IGNORE_CASE),
    )
    private val poemDiv = Regex("""<div[^>]*class="[^"]*\bpoem\b[^"]*"[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
    private val heading = Regex("""<h([2-4])[^>]*>([\s\S]*?)</h\1>""", RegexOption.IGNORE_CASE)

    fun convert(html: String): String {
        // В HTML переводы строк незначимы — значимы только <br> и абзацы.
        var s = html.replace(Regex("""\r?\n"""), " ")
        for (r in removeBlocks) s = r.replace(s, "")
        s = poemDiv.replace(s) { m -> "\n<poem>\n" + flatten(m.groupValues[1]) + "\n</poem>\n" }
        s = heading.replace(s) { m ->
            val eq = "=".repeat(m.groupValues[1].toInt())
            "\n$eq " + flatten(m.groupValues[2]).replace('\n', ' ').trim() + " $eq\n"
        }
        // Остальное — обычный текст; теги <poem> сохраняем.
        s = s.replace(Regex("""<(/?)poem>"""), "\u0000$1POEM\u0000")
        s = flatten(s)
        s = s.replace(Regex("""\u0000(/?)POEM\u0000"""), "<$1poem>")
        return s
    }

    /** HTML-фрагмент → строки текста (переводы строк из <br> и абзацев). */
    private fun flatten(html: String): String = html
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</(p|div|li|dd|h[1-6])>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<[^>\u0000]+>"""), "")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}
